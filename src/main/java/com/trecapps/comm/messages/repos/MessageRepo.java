package com.trecapps.comm.messages.repos;

import com.trecapps.comm.messages.models.Message;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MessageRepo extends ReactiveMongoRepository<Message, UUID> {

    @Query("{'conversationId': id, 'page': page}")
    Flux<Message> findByConversationAndPageNumber(UUID id, int page);

    @Query(value = "{'conversationId': id, 'page': page}", count = true)
    Mono<Long> countByConversationAndPageNumber(UUID id, int page);
}
