package com.trecapps.comm.messages.services;

import com.trecapps.comm.common.ResponseObj;
import com.trecapps.comm.common.ObjectResponseException;
import com.trecapps.comm.messages.models.*;
import com.trecapps.comm.messages.repos.ConversationRepo;
import com.trecapps.comm.messages.repos.MessageRepo;
import com.trecapps.comm.messages.websocket.KafkaConversationProducer;
import com.trecauth.common.model.AccountList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MessageService extends ProfileSorterService{

    MessageRepo messageRepo;

    ConversationRepo conversationRepo;

    MessageNotifyService notifyService;

    int pageSize;

    // Task 9.1: optional field — absent when websocket feature flag is off
    @Autowired(required = false)
    KafkaConversationProducer kafkaProducer;

    @Autowired
    MessageService(
            MessageRepo messageRepo,
            ConversationRepo conversationRepo,
            MessageNotifyService notifyService,
            @Value("${trecapps.message.page-size:100}")int pageSize
    ){
        this.notifyService = notifyService;
        this.messageRepo = messageRepo;
        this.pageSize = pageSize;
        this.conversationRepo = conversationRepo;
    }

    @Transactional
    public Mono<ResponseObj> postMessage(AccountList auth, String conversationId, String message){

        return Mono.just(auth)
                .flatMap((AccountList list) -> {
                    UUID conId = null;
                    try{
                        conId = UUID.fromString(conversationId);
                    } catch(IllegalArgumentException e){
                        throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "Conversation needs to be in UUID format!");
                    }

                    UUID finalConId = conId;
                    return conversationRepo.findById(conId).defaultIfEmpty(new Conversation())
                            .doOnNext((Conversation conversation) -> {
                                if(conversation.getId() == null)
                                    throw new ObjectResponseException(HttpStatus.NOT_FOUND, "Conversation not found");

                                if(!conversation.getProfiles().contains(list.getCurrentAccount().getId()))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                            }).flatMap((Conversation conversation) -> {
                                Message newMessage = new Message();
                                newMessage.setId(UUID.randomUUID());
                                newMessage.setPage(conversation.getCurrentPage());
                                newMessage.setConversationId(conversation.getId());
                                newMessage.setProfile(list.getCurrentAccount().getId());

                                Instant now = Instant.now();
                                newMessage.setFirstMade(now);

                                MessageVersion firstVersion = new MessageVersion();
                                firstVersion.setMessage(message);
                                firstVersion.setMade(now);
                                newMessage.getMessageVersions().add(firstVersion);

                                conversation.getProfiles().forEach((UUID profile1) -> {
                                    newMessage.getReactions().put(profile1, new Reaction());
                                });

                                return messageRepo.countByConversationAndPageNumber(finalConId,
                                        conversation.getCurrentPage())
                                        .flatMap((Long count) -> {
                                            if(count >= this.pageSize){
                                                // ToDo - send message to service bus
                                            }

                                            return messageRepo.save(newMessage);
                                        })
                                        // Task 9.2: publish NEW_MESSAGE event after save
                                        .doOnSuccess((Message savedMessage) -> {
                                            try {
                                                if (kafkaProducer != null) {
                                                    ConversationEvent event = new ConversationEvent(
                                                            EventType.NEW_MESSAGE,
                                                            conversation.getId(),
                                                            list.getCurrentAccount().getId(),
                                                            savedMessage
                                                    );
                                                    kafkaProducer.publishEvent(event);
                                                }
                                            } catch (Exception e) {
                                                // producer failure must never break the reactive chain
                                            }
                                        })
                                        .flatMap((Message nm) -> {
                                            String displayName = auth.getCurrentAccount().getDisplayName();
                                            return this.notifyService.notifyOnMessage(nm, conversation, displayName);
                                        });
                            });
                })
                .map((Message newMessage) -> ResponseObj.getInstance("Created!", newMessage.getId().toString()));
    }

    public Mono<List<Message>> getMessages(AccountList auth, String conversationId, int page){
        return Mono.just(auth)
                .flatMap((AccountList list)-> {
                    UUID conId = null;
                    try{
                        conId = UUID.fromString(conversationId);
                    } catch(IllegalArgumentException e){
                        throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "Conversation needs to be in UUID format!");
                    }

                    UUID finalConId = conId;
                    return conversationRepo.findById(conId).defaultIfEmpty(new Conversation())
                            .doOnNext((Conversation conversation) -> {
                                if(conversation.getId() == null)
                                    throw new ObjectResponseException(HttpStatus.NOT_FOUND, "Conversation not found");

                                if(!conversation.getProfiles().contains(list.getCurrentAccount().getId()))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                            });
                })
                .flatMap((Conversation conversation) -> {
                    return messageRepo.findByConversationAndPageNumber(conversation.getId(), page).collectList();
                });
    }

    public Mono<List<Message>> getLatestMessages(AccountList auth, String conversationId, OffsetDateTime time){
        return Mono.just(auth)
                .flatMap((AccountList list)-> {
                    UUID conId = null;
                    try{
                        conId = UUID.fromString(conversationId);
                    } catch(IllegalArgumentException e){
                        throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "Conversation needs to be in UUID format!");
                    }

                    UUID finalConId = conId;
                    return conversationRepo.findById(conId).defaultIfEmpty(new Conversation())
                            .doOnNext((Conversation conversation) -> {
                                if(conversation.getId() == null)
                                    throw new ObjectResponseException(HttpStatus.NOT_FOUND, "Conversation not found");

                                if(!conversation.getProfiles().contains(list.getCurrentAccount().getId()))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                            });
                })
                .flatMap((Conversation conversation) -> {
                    return messageRepo.findMessagesAfter(conversation.getId(), time).collectList();
                });
    }

    public Mono<ResponseObj> markReaction(AccountList auth, List<String> messageIds, String reactionType){
        return Mono.just(auth)
                .flatMap((AccountList list)-> {
                    List<UUID> messageIdsUuid;
                    try{
                        messageIdsUuid = messageIds.stream().map(UUID::fromString).distinct().toList();
                    } catch(IllegalArgumentException ignore){
                        throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "Conversation needs to be in UUID format!");
                    }

                    return messageRepo.findAllById(messageIdsUuid).collectList()
                            .flatMap((List<Message> messages)-> {
                                Set<UUID> conversationIDs = new HashSet<>();
                                messages.forEach((Message message) -> {
                                    conversationIDs.add(message.getConversationId());
                                });
                                if(conversationIDs.size() != 1)
                                    throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "You can only see messages for 1 conversation per request!");

                                return conversationRepo.findById(conversationIDs.stream().toList().getFirst())
                                        .doOnNext((Conversation conv) -> {
                                            if(!conv.getProfiles().contains(list.getCurrentAccount().getId()))
                                                throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                                        })
                                        .thenReturn(messages);
                            })
                            .flatMap((List<Message> messages) -> {
                                Instant now = Instant.now();

                                messages.forEach((Message message) -> {
                                    Reaction reaction = message.getReactions().get(list.getCurrentAccount().getId());
                                    if(reactionType != null){
                                        reaction.setReaction(reactionType);
                                    }
                                    if(reaction.getSeen() == null)
                                       reaction.setSeen(now);
                                });

                                return messageRepo.saveAll(messages).collectList();
                            })
                            // Task 9.3: publish MESSAGE_SEEN or MESSAGE_REACTION event after saveAll
                            .doOnSuccess((List<Message> savedMessages) -> {
                                try {
                                    if (kafkaProducer != null && !savedMessages.isEmpty()) {
                                        UUID conversationId = savedMessages.get(0).getConversationId();
                                        UUID actorProfileId = list.getCurrentAccount().getId();
                                        ConversationEvent event;
                                        if (reactionType == null) {
                                            // MESSAGE_SEEN: payload is the list of message IDs
                                            List<UUID> messageIdList = savedMessages.stream()
                                                    .map(Message::getId)
                                                    .toList();
                                            event = new ConversationEvent(
                                                    EventType.MESSAGE_SEEN,
                                                    conversationId,
                                                    actorProfileId,
                                                    messageIdList
                                            );
                                        } else {
                                            // MESSAGE_REACTION: payload is the first updated message
                                            event = new ConversationEvent(
                                                    EventType.MESSAGE_REACTION,
                                                    conversationId,
                                                    actorProfileId,
                                                    savedMessages.get(0)
                                            );
                                        }
                                        kafkaProducer.publishEvent(event);
                                    }
                                } catch (Exception e) {
                                    // producer failure must never break the reactive chain
                                }
                            })
                            .thenReturn(ResponseObj.getInstance(HttpStatus.OK, "Seen!"));
                });
    }

    public Mono<ResponseObj> editMessage(AccountList authentication, String messageId, String newMessage){
        return Mono.just(authentication)
                .flatMap((AccountList list) -> {
                    UUID mId = null;
                    try{
                        mId = UUID.fromString(messageId);
                    } catch(IllegalArgumentException e){
                        throw new ObjectResponseException(HttpStatus.BAD_REQUEST, "Conversation needs to be in UUID format!");
                    }

                    return messageRepo.findById(mId).defaultIfEmpty(new Message())
                            .doOnNext((Message message) -> {
                                if(message.getId() == null)
                                    throw new ObjectResponseException(HttpStatus.NOT_FOUND, "Conversation not found");
                                if(!list.getCurrentAccount().getId().equals(message.getProfile()))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You can only edit your own messages!");
                            })
                            .flatMap((Message message) -> {
                                MessageVersion newVersion = new MessageVersion();
                                newVersion.setMade(Instant.now());
                                newVersion.setMessage(newMessage);
                                message.getMessageVersions().add(newVersion);
                                return messageRepo.save(message);
                            })
                            // Task 9.4: publish MESSAGE_EDIT event after save
                            .doOnSuccess((Message savedMessage) -> {
                                try {
                                    if (kafkaProducer != null) {
                                        ConversationEvent event = new ConversationEvent(
                                                EventType.MESSAGE_EDIT,
                                                savedMessage.getConversationId(),
                                                list.getCurrentAccount().getId(),
                                                savedMessage
                                        );
                                        kafkaProducer.publishEvent(event);
                                    }
                                } catch (Exception e) {
                                    // producer failure must never break the reactive chain
                                }
                            });
                })
                .thenReturn(ResponseObj.getInstance(HttpStatus.OK, "Successfully Updated"));
    }
}
