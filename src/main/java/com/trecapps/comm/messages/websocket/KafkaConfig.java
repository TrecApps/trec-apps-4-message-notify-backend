package com.trecapps.comm.messages.websocket;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spring Kafka configuration for the WebSocket real-time push feature.
 *
 * <p>This configuration is only activated when
 * {@code trecapps.messaging.websocket.enabled=true}. In HTTP-only mode the
 * entire Kafka subsystem is absent from the Spring context.
 *
 * <h3>Authentication strategy</h3>
 * <ul>
 *   <li>If {@code trecapps.messaging.kafka.connection-string} is non-empty,
 *       SASL/SSL is configured using the Azure Event Hubs connection string
 *       (PLAIN mechanism with the connection string as the password).</li>
 *   <li>If the property is empty or absent, Entra-based authentication is used
 *       via the {@code OAUTHBEARER} mechanism with the
 *       {@code KafkaAccessTokenProvider} login callback handler that wraps
 *       {@link com.azure.identity.DefaultAzureCredential}.</li>
 * </ul>
 *
 * <h3>Consumer group</h3>
 * Each application instance generates a unique consumer group ID at startup
 * ({@code "messaging-ws-" + UUID.randomUUID()}) so that every instance
 * receives every event published to the topic — there is no competing-consumer
 * behaviour across instances.
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4</strong>
 */
@Configuration
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class KafkaConfig {

    // -------------------------------------------------------------------------
    // SASL JAAS template for connection-string (PLAIN) authentication.
    // The Event Hubs Kafka endpoint expects the connection string as the
    // password with the literal username "$ConnectionString".
    // -------------------------------------------------------------------------
    private static final String SASL_JAAS_CONNECTION_STRING_TEMPLATE =
            "org.apache.kafka.common.security.plain.PlainLoginModule required "
                    + "username=\"$ConnectionString\" "
                    + "password=\"%s\";";

    // -------------------------------------------------------------------------
    // SASL JAAS template for Entra / managed-identity (OAUTHBEARER) auth.
    // The Azure Event Hubs Kafka endpoint accepts OAuth 2.0 bearer tokens
    // obtained via DefaultAzureCredential.
    // -------------------------------------------------------------------------
    private static final String SASL_JAAS_OAUTHBEARER_TEMPLATE =
            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;";

    private static final String OAUTHBEARER_TOKEN_PROVIDER_CLASS =
            "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler";

    @Value("${trecapps.messaging.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${trecapps.messaging.kafka.connection-string:}")
    private String connectionString;

    // -------------------------------------------------------------------------
    // Consumer group ID — unique per instance (Requirement 7.1)
    // -------------------------------------------------------------------------

    /**
     * Returns a unique Kafka consumer group ID for this application instance.
     *
     * <p>The UUID suffix ensures that every running instance receives every
     * event from the topic rather than competing for records in a shared group.
     * The bean is evaluated once at startup and reused for the lifetime of the
     * application context.
     *
     * @return a unique consumer group ID string
     */
    @Bean
    public String kafkaConsumerGroupId() {
        String groupId = "messaging-ws-" + UUID.randomUUID();
        log.info("Kafka consumer group ID for this instance: {}", groupId);
        return groupId;
    }

    // -------------------------------------------------------------------------
    // Producer beans
    // -------------------------------------------------------------------------

    /**
     * Creates the Kafka {@link ProducerFactory} used by
     * {@link KafkaConversationProducer}.
     *
     * @return a {@link DefaultKafkaProducerFactory} configured for Azure Event Hubs
     */
    @Bean
    public ProducerFactory<String, String> kafkaProducerFactory() {
        Map<String, Object> props = new HashMap<>(commonClientProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Reasonable defaults for reliability without sacrificing throughput.
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates the {@link KafkaTemplate} used to publish {@code ConversationEvent}
     * records to the configured topic.
     *
     * @param producerFactory the producer factory defined above
     * @return a {@link KafkaTemplate} wrapping the producer factory
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // -------------------------------------------------------------------------
    // Consumer beans
    // -------------------------------------------------------------------------

    /**
     * Creates the Kafka {@link ConsumerFactory} used by
     * {@link KafkaConversationConsumer}.
     *
     * @param kafkaConsumerGroupId the unique group ID bean defined above
     * @return a {@link DefaultKafkaConsumerFactory} configured for Azure Event Hubs
     */
    @Bean
    public ConsumerFactory<String, String> kafkaConsumerFactory(String kafkaConsumerGroupId) {
        Map<String, Object> props = new HashMap<>(commonClientProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConsumerGroupId);
        // Start from the latest offset — we only care about events that arrive
        // while this instance is running, not historical records.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates the {@link ConcurrentKafkaListenerContainerFactory} that backs
     * the {@code @KafkaListener} in {@link KafkaConversationConsumer}.
     *
     * @param kafkaConsumerGroupId the unique group ID bean defined above
     * @return a configured listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            String kafkaConsumerGroupId) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory(kafkaConsumerGroupId));
        return factory;
    }

    // -------------------------------------------------------------------------
    // Shared SASL/SSL properties
    // -------------------------------------------------------------------------

    /**
     * Builds the common Kafka client properties shared by both the producer and
     * consumer, including bootstrap servers and SASL/SSL configuration for
     * Azure Event Hubs.
     *
     * @return a mutable map of Kafka client properties
     */
    private Map<String, Object> commonClientProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        boolean useConnectionString = connectionString != null && !connectionString.isBlank();

        if (useConnectionString) {
            configureConnectionStringAuth(props);
        } else {
            configureEntraAuth(props);
        }

        return props;
    }

    /**
     * Configures SASL/SSL properties for connection-string (PLAIN) authentication.
     *
     * <p>Azure Event Hubs Kafka endpoint expects:
     * <ul>
     *   <li>Security protocol: {@code SASL_SSL}</li>
     *   <li>SASL mechanism: {@code PLAIN}</li>
     *   <li>Username: the literal string {@code $ConnectionString}</li>
     *   <li>Password: the full Event Hubs connection string</li>
     * </ul>
     *
     * @param props the properties map to populate
     */
    private void configureConnectionStringAuth(Map<String, Object> props) {
        log.debug("Configuring Kafka SASL/SSL with connection-string (PLAIN) authentication");
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        props.put(SaslConfigs.SASL_JAAS_CONFIG,
                String.format(SASL_JAAS_CONNECTION_STRING_TEMPLATE, connectionString));
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");
    }

    /**
     * Configures SASL/SSL properties for Entra / managed-identity
     * (OAUTHBEARER) authentication.
     *
     * <p>The {@code OAuthBearerLoginCallbackHandler} obtains a token from the
     * Azure Event Hubs scope using {@link com.azure.identity.DefaultAzureCredential},
     * which supports managed identity, workload identity, and local developer
     * credentials transparently.
     *
     * @param props the properties map to populate
     */
    private void configureEntraAuth(Map<String, Object> props) {
        log.debug("Configuring Kafka SASL/SSL with Entra (OAUTHBEARER) authentication");
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
        props.put(SaslConfigs.SASL_JAAS_CONFIG, SASL_JAAS_OAUTHBEARER_TEMPLATE);
        props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, OAUTHBEARER_TOKEN_PROVIDER_CLASS);
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");
    }
}
