package com.trecapps.comm.messages.services;

import com.trecapps.comm.notifications.model.NotificationPost;
import reactor.core.publisher.Mono;

public interface IMessageProducer {

    Mono<Boolean> sendNotification(NotificationPost post);

}
