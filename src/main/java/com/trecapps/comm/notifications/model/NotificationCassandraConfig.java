package com.trecapps.comm.notifications.model;

import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.cassandra.config.*;
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Locale;

/**
 * Needed for customizing the SSL Configuration for Azure Cassandra RU mode
 */
@Configuration
@EnableReactiveCassandraRepositories
public class NotificationCassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    String keyspaceName;

    @Value("${spring.cassandra.local-datacenter}")
    String localDataCenter;

    @Value("${spring.cassandra.contact-points}")
    String contactPoints;

    @Value("${spring.cassandra.port}")
    int port;

    @Value("${spring.cassandra.schema-action}")
    String schemaAction;

    @Value("${spring.cassandra.username}")
    String username;

    @Value("${spring.cassandra.password}")
    String password;

    @Value("${java.cacerts.loc:/lib/security/cacerts}")
    String cacertsLoc;

    @Value("${java.cacerts.pw:changeit")
    String sslKeyStorePassword;


    @Override
    protected String getKeyspaceName() {
        return keyspaceName;
    }

    @Override
    protected String getLocalDataCenter(){
        return localDataCenter;
    }

    @Override
    protected String getContactPoints(){
        return contactPoints;
    }

    @Override
    protected int getPort(){
        return port;
    }

    private File sslKeyStoreFile = null;


    void prepKeyStore() throws Exception {
        if(sslKeyStoreFile != null) return;
        String javaHomeDirectory = System.getenv("JAVA_HOME");
        if (javaHomeDirectory == null || javaHomeDirectory.isEmpty()) {
            throw new Exception("JAVA_HOME not set");
        }
        String ssl_keystore_file_path = javaHomeDirectory + cacertsLoc;

        sslKeyStoreFile = new File(ssl_keystore_file_path);

        if (!sslKeyStoreFile.exists() || !sslKeyStoreFile.canRead()) {
            throw new Exception(String.format("Unable to access the SSL Key Store file from %s", ssl_keystore_file_path));
        }
    }

    ///
    /// Keystore set up
    /// derived from https://github.com/Azure-Samples/azure-cosmos-db-cassandra-java-getting-started
    /// /blob/main/src/main/java/com/azure/cosmosdb/cassandra/util/CassandraUtils.java
    ///
    /// This was the piece needed to get the app to run locally
    ///
    @SneakyThrows
    SSLContext sslContext() {
        final KeyStore keyStore = KeyStore.getInstance("JKS");

        prepKeyStore();

        try (final InputStream is = new FileInputStream(sslKeyStoreFile)) {
            keyStore.load(is, sslKeyStorePassword.toCharArray());
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
        //keyStore.load(null, sslKeyStorePassword.toCharArray());

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
        kmf.init(keyStore, sslKeyStorePassword.toCharArray());
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory
                .getDefaultAlgorithm());
        tmf.init(keyStore);

        // Creates a socket factory for HttpsURLConnection using JKS contents.
        final SSLContext sc = SSLContext.getInstance("TLSv1.2");
        sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new java.security.SecureRandom());
        return sc;
    }


    @Bean
    @Primary
    public SessionFactoryFactoryBean cassandraFactorySession(CqlSessionFactoryBean sessionBean){
        SessionFactoryFactoryBean bean = new SessionFactoryFactoryBean();
        bean.setSession(sessionBean.getObject());
        bean.setSchemaAction(SchemaAction.valueOf(schemaAction.toUpperCase(Locale.ROOT)));
        bean.setConverter(cassandraConverter());



        return bean;
    }

    ///
    /// Custom Cassandra Session
    ///
    /// Provided to enable custom session generation according to Microsoft's suggestion
    ///
    @Override
    @Bean
    public CqlSessionFactoryBean cassandraSession() {

        CqlSessionFactoryBean bean = new CqlSessionFactoryBean();

        bean.setContactPoints(getContactPoints());
        bean.setKeyspaceCreations(getKeyspaceCreations());
        bean.setKeyspaceDrops(getKeyspaceDrops());
        bean.setKeyspaceName(getKeyspaceName());
        bean.setLocalDatacenter(getLocalDataCenter());
        bean.setPort(getPort());

        bean.setUsername(username);
        bean.setPassword(password);

        SessionBuilderConfigurer configurer = (CqlSessionBuilder builder) -> {
            return builder.withSslContext(sslContext())
                    .withConfigLoader(
                            DriverConfigLoader
                                    .programmaticBuilder()
                                    .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofMillis(15000))
                                    .build()
                    )
                    ;
        };

        bean.setSessionBuilderConfigurer(configurer);
        return bean;
    }

}
