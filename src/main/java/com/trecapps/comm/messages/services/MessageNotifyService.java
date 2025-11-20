package com.trecapps.comm.messages.services;

import com.trecapps.base.notify.models.ImageEndpointType;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.models.Message;
import com.trecapps.comm.notifications.model.NotificationPost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
//@ConditionalOnBean(value = IMessageProducer.class)
//@Order(1)
public class MessageNotifyService {

    IMessageProducer messageProducer;

    @Autowired
    MessageNotifyService(@Autowired(required = false) IMessageProducer producer){
        this.messageProducer = producer;
    }

    Mono<Message> notifyOnMessage(Message newMessage, Conversation conversation, String senderDisplayName) {
        if(this.messageProducer == null) return Mono.just(newMessage);
        return Mono.just(newMessage)
                .flatMapMany((Message message) -> {
                    List<String> profiles = conversation.getProfiles().stream()
                            .filter((String str) -> !str.equals(message.getProfile())).toList();

                    List<NotificationPost> posts = profiles.stream().map((String profile) -> {
                        NotificationPost post = new NotificationPost();
                        String messageContent = message.getMessageVersions().getLast().getMessage();
                        if(messageContent.length() > 47){
                            messageContent = messageContent.substring(0, 47).concat("...");
                        }
                        post.setMessage(
                                String.format(
                                        "%s sent a new message: %s",
                                        senderDisplayName,
                                        messageContent
                                        )
                        );

                        String apps = conversation.getApps().toString();
                        if(apps != null){
                            if(apps.startsWith("["))
                                apps = apps.substring(1);
                            if(apps.endsWith("]"))
                                apps = apps.substring(0,apps.length() - 1);
                            apps = apps.trim();
                        }

                        post.setAppId(apps);

                        if(profile.startsWith("User-")){
                            post.setType(ImageEndpointType.USER_PROFILE);
                            post.setUserId(profile.substring(5));
                        }else {
                            post.setType(ImageEndpointType.BRAND_PROFILE);
                            post.setBrandId(profile.substring(6));
                        }
                        post.setCategory("Message");
                        post.setImageId(profile);
                        post.setRelevantId(conversation.getId().toString());
                        post.setRelevantIdSecondary(message.getId().toString());

                        return post;
                    }).toList();
                    return Flux.fromIterable(posts);
                })
                .flatMap((NotificationPost post) -> this.messageProducer.sendNotification(post))
                .collectList()
                .thenReturn(newMessage);
    }
}
