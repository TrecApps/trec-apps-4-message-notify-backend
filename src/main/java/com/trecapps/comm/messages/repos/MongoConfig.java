package com.trecapps.comm.messages.repos;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.bson.UuidRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.springframework.data.mongodb.core.ReactiveMongoTemplate.NO_OP_REF_RESOLVER;

@Configuration
public class MongoConfig {

    @Autowired
    private ApplicationContext appContext;

    @Bean
    public MongoClient messageMongoClient(
            @Value("${spring.mongodb.uri}")String mongoUri
    ) {
        return MongoClients.create(MongoClientSettings.builder().uuidRepresentation(UuidRepresentation.STANDARD)
                .applyConnectionString(new ConnectionString(mongoUri)).build());
    }

    @Bean
    @Primary
    public ReactiveMongoDatabaseFactory messageMongoFactory(
            @Qualifier("messageMongoClient") MongoClient client,
            @Value("${spring.mongodb.database}") String database
    ){
        return new SimpleReactiveMongoDatabaseFactory(client, database);
    }

    public List<Converter<?, ?>> customConversions() {
        List<Converter<?, ?>> converterList = new ArrayList<Converter<?, ?>>();
        converterList.add(new MongoLocalDateTimeFromStringConverter());
        converterList.add(new OffsetDateTimeWriteConverter());
        converterList.add(new OffsetDateTimeReadConverter());
        return converterList;
    }

    public static class OffsetDateTimeWriteConverter implements Converter<OffsetDateTime, Date> {
        @Override
        public Date convert(OffsetDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    private static final class MongoLocalDateTimeFromStringConverter implements Converter<String, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(String source) {
            return source == null ? null : OffsetDateTime.parse(source);
        }
    }

    public static class OffsetDateTimeReadConverter implements Converter<Date, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(Date source) {
            return source.toInstant().atOffset(ZoneOffset.UTC);
        }
    }

    private MappingMongoConverter getDefaultMongoConverter(ReactiveMongoDatabaseFactory factory) {

        MongoCustomConversions conversions = new MongoCustomConversions(customConversions());

        MongoMappingContext context = mongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();

        MappingMongoConverter converter = new MappingMongoConverter(NO_OP_REF_RESOLVER, context);
        converter.setCustomConversions(conversions);
        converter.setCodecRegistryProvider(factory);
        converter.afterPropertiesSet();

        return converter;
    }

    public MongoMappingContext mongoMappingContext() {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setApplicationContext(appContext);
        return mappingContext;
    }

    @Bean(name="reactiveMongoTemplate")
    public ReactiveMongoTemplate messageMongoTemplate(
            @Qualifier("messageMongoFactory") ReactiveMongoDatabaseFactory factory
    ) {
        final MappingMongoConverter converter =getDefaultMongoConverter(factory);
        return new ReactiveMongoTemplate(factory, converter);
    }
}
