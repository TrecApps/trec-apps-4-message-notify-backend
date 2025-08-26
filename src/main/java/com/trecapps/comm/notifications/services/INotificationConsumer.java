package com.trecapps.comm.notifications.services;

public interface INotificationConsumer {

    void initialize(INotificationMessageHandler handler);
}
