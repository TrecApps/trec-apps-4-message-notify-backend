package com.trecapps.comm.messages.repos;

import com.trecapps.comm.messages.models.Message;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface MessageRepo extends ReactiveMongoRepository<Message, UUID> {

    @Query("{'conversationId': ?0, 'page': ?1}")
    Flux<Message> findByConversationAndPageNumber(UUID id, int page);

    @Query("{'conversationId': ?0, '$gt': {'firstMade' : ?1}}")
    Flux<Message> findMessagesAfter(UUID id, OffsetDateTime after);

    @Query(value = "{'conversationId': ?0, 'page': ?1}", count = true)
    Mono<Long> countByConversationAndPageNumber(UUID id, int page);


}
