package com.trecapps.comm.messages.repos;

import com.trecapps.comm.messages.models.Message;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import java.util.UUID;

public interface MessageRepo extends ReactiveMongoRepository<Message, UUID> {
}
