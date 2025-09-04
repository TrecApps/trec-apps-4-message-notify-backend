package com.trecapps.comm.messages.repos;

import com.trecapps.comm.messages.models.Conversation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConversationRepo extends ReactiveMongoRepository<Conversation, UUID> {

    @Query("{'profiles' : profile}")
    Flux<Conversation> getConversationsByProfile(String profile);

    @Query("{'profiles' : profile, 'apps': app}")
    Flux<Conversation> getConversationsByProfileAndApp(String profile, String app);


}
