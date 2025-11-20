package com.trecapps.comm.messages.repos;

import com.trecapps.comm.messages.models.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

public interface MessageRepo extends ReactiveMongoRepository<Message, UUID> {

    @Query("{'conversationId': ?0, 'page': ?1}")
    Flux<Message> findByConversationAndPageNumber(UUID id, int page, Sort sort);


    default Flux<Message> findByConversationAndPageNumber(UUID id, int page){
        return findByConversationAndPageNumber(id, page, Sort.by(Sort.Direction.ASC, "firstMade"));
    }

    @Query("{'conversationId': ?0, 'firstMade': {'$gt' : ?1}}")
    Flux<Message> findMessagesAfter(UUID id, Date after, Sort sort);


    default Flux<Message> findMessagesAfter(UUID id, OffsetDateTime after){
        return findMessagesAfter(id, Date.from(after.toInstant()), Sort.by(Sort.Direction.ASC, "firstMade"));
    }

    @Query(value = "{'conversationId': ?0, 'page': ?1}", count = true)
    Mono<Long> countByConversationAndPageNumber(UUID id, int page);


}
