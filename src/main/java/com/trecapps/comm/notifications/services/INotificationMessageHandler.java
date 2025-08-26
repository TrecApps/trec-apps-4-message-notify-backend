package com.trecapps.comm.notifications.services;

import com.trecapps.base.notify.models.MessageObject;
import com.trecapps.comm.notifications.model.NotificationPost;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface INotificationMessageHandler {
    Mono<Boolean> processNotification(NotificationPost messageObject);
}
