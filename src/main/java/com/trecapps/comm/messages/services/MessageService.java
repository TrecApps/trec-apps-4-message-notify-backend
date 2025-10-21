package com.trecapps.comm.messages.services;

import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.ResponseObj;
import com.trecapps.comm.common.ObjectResponseException;
import com.trecapps.comm.messages.models.*;
import com.trecapps.comm.messages.repos.ConversationRepo;
import com.trecapps.comm.messages.repos.MessageRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
//@Order(2)
public class MessageService extends ProfileSorterService{

    MessageRepo messageRepo;

    ConversationRepo conversationRepo;

    MessageNotifyService notifyService;

    int pageSize;

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

//    Mono<Message> nextMessagePage(Conversation conversation1, Message message, int pageCount){
//        return Mono.just(conversation1)
//                .flatMap((Conversation conversation) -> {
//                    conversation.setCurrentPage(conversation.getCurrentPage() + 1);
//
//
//                    ConversationMarker marker = new ConversationMarker();
//                    marker.setMessageId(message.getId());
//                    marker.setMessageMade(message.getFirstMade());
//                    marker.setPreviousMessages(pageCount);
//                    conversation.getMarkers().add()
//                })
//    }

//    Mono<Void> ensureOneConvoAndParticipant(List)

    @Transactional
    public Mono<ResponseObj> postMessage(TrecAuthentication auth, String conversationId, String message){

        return useProfile(auth)
                .flatMap((String profile) -> {
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

                                if(!conversation.getProfiles().contains(profile))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                            }).flatMap((Conversation conversation) -> {
                                Message newMessage = new Message();
                                newMessage.setId(UUID.randomUUID());
                                newMessage.setPage(conversation.getCurrentPage());
                                newMessage.setConversationId(conversation.getId());
                                newMessage.setProfile(profile);


                                OffsetDateTime now = OffsetDateTime.now();
                                newMessage.setFirstMade(now);

                                MessageVersion firstVersion = new MessageVersion();
                                firstVersion.setMessage(message);
                                firstVersion.setMade(now);
                                newMessage.getMessageVersions().add(firstVersion);

                                conversation.getProfiles().forEach((String profile1) -> {
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
                                        .flatMap((Message nm) -> {
                                            //if(this.notifyService == null) return Mono.just(nm);

                                            String displayName = auth.getUser().getDisplayName();
                                            if(auth.getBrand() != null)
                                                displayName = auth.getBrand().getName();

                                            return this.notifyService.notifyOnMessage(nm, conversation, displayName);
                                        });
                            });
                })
                .map((Message newMessage) ->  ResponseObj.getInstance("Created!", newMessage.getId().toString())
                )

                // To-Do - error handling
        ;
    }

    public Mono<List<Message>> getMessages(TrecAuthentication auth, String conversationId, int page){
        return useProfile(auth)
                .flatMap((String profile)-> {
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

                                if(!conversation.getProfiles().contains(profile))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                            });
                })
                .flatMap((Conversation conversation) -> {
                    return messageRepo.findByConversationAndPageNumber(conversation.getId(), page).collectList();
                });
    }

    public Mono<ResponseObj> markReaction(TrecAuthentication auth, List<String> messageIds, String reactionType){
        return useProfile(auth)
                .flatMap((String profile)-> {
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
                                            if(!conv.getProfiles().contains(profile))
                                                throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You are not part of this conversation!");
                                        })
                                        .thenReturn(messages);
                            })
                            .flatMap((List<Message> messages) -> {
                                OffsetDateTime now = OffsetDateTime.now();

                                messages.forEach((Message message) -> {
                                    Reaction reaction = message.getReactions().get(profile);
                                    if(reactionType != null){
                                        reaction.setReaction(reactionType);
                                    }
                                    if(reaction.getSeen() == null)
                                       reaction.setSeen(now);
                                });

                                return messageRepo.saveAll(messages).collectList();
                            })
                            .thenReturn(ResponseObj.getInstance(HttpStatus.OK, "Seen!"));
                });
    }


    public Mono<ResponseObj> editMessage(TrecAuthentication authentication, String messageId, String newMessage){
        return useProfile(authentication)
                .flatMap((String profile) -> {
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
                                if(!profile.equals(message.getProfile()))
                                    throw new ObjectResponseException(HttpStatus.FORBIDDEN, "You can only edit your own messages!");
                            })
                            .flatMap((Message message) -> {
                                MessageVersion newVersion = new MessageVersion();
                                newVersion.setMade(OffsetDateTime.now());
                                newVersion.setMessage(newMessage);
                                message.getMessageVersions().add(newVersion);
                                return messageRepo.save(message);
                            })
                            // ToDo - send message so that when Web Sockets are implemented, users can be informed of any changes

                    ;
                })
                .thenReturn(ResponseObj.getInstance(HttpStatus.OK, "Successfully Updated"));

        // ToDo - error handling
    }
}
