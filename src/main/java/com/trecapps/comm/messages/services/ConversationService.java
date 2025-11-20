package com.trecapps.comm.messages.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TcUser;
import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.auth.webflux.services.IUserStorageServiceAsync;
import com.trecapps.comm.common.ObjectResponseException;
import com.trecapps.comm.common.ResponseObj;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.repos.ConversationRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class ConversationService extends ProfileSorterService{

    @Autowired
    ConversationRepo conversationRepo;

    @Autowired
    IUserStorageServiceAsync userStorageService;

    Mono<Optional<TcUser>> fromProfile(String profile){
        if(profile.startsWith("User-"))
            return userStorageService.getAccountById(profile.substring(5));
        if(profile.startsWith("Brand-"))
            return userStorageService.getBrandById(profile.substring(6))
                    .flatMap((Optional<TcBrands> oBrands) -> {
                        Optional<TcUser> oUser = Optional.empty();
                        if(oBrands.isEmpty()) return Mono.just(oUser);
                        TcBrands brand = oBrands.get();

                        String dedOwner = brand.getDedicatedOwner();
                        if(dedOwner == null && brand.getOwners().size() == 1){
                            dedOwner = brand.getOwners().stream().toList().getFirst();
                        }

                        if(dedOwner != null)
                            return userStorageService.getAccountById(dedOwner);
                        return Mono.just(oUser);
                    });
        return Mono.just(Optional.empty());
    }

    Mono<Set<TcUser>> getUsers(List<String> profiles){
        return Flux.fromIterable(profiles)
                .flatMap(this::fromProfile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collectList()
                .map(HashSet::new);


    }

    public Mono<ResponseObj> establishConversation(TcUser user, TcBrands brand, String appId, List<String> profiles) {
        return getUsers(profiles)
                .map((Set<TcUser> users) -> {
                    // ToDo - check for users blocking this user

                    // End ToDo

                    Conversation conversation = new Conversation();
                    conversation.getApps().add(appId);
                    conversation.setId(UUID.randomUUID());
                    conversation.getProfiles().addAll(profiles);
                    String callerProfile =
                            brand == null ?
                                    String.format("User-%s", user.getId()) :
                                    String.format("Brand-%s", brand.getId());
                    conversation.getProfiles().add(callerProfile);
                    return conversation;
                })
                .flatMap((Conversation conversation) -> {
                    String callerProfile =
                            brand == null ?
                                    String.format("User-%s", user.getId()) :
                                    String.format("Brand-%s", brand.getId());
                    return conversationRepo.getConversationsByProfileAndApp(callerProfile, appId)
                            .collectList()
                            .doOnNext((List<Conversation> conversations) -> {
                                TreeSet<String> currentProfiles = new TreeSet<>(conversation.getProfiles());

                                for(Conversation existingCon : conversations){
                                    TreeSet<String> profilesList = new TreeSet<>(existingCon.getProfiles());
                                    if(currentProfiles.equals(profilesList))
                                        throw new ObjectResponseException(HttpStatus.OK, existingCon.getId().toString());
                                }
                            })
                            .thenReturn(conversation)
                            .flatMap((Conversation conversation1) -> conversationRepo.save(conversation1))
                            ;

                })

                .map((Conversation conversation) ->
                    ResponseObj.getInstance("Success!", conversation.getId().toString())
                )
                // ToDo - error handling
                .onErrorResume(ObjectResponseException.class, (ObjectResponseException e) -> {
                    return Mono.just(e)
                            .map(ex -> {
                                if(ex.getStatus().equals(HttpStatus.OK))
                                {
                                    ResponseObj ret = new ResponseObj();
                                    ret.setId(ex.getMessage());
                                    ret.setMessage("Already Exists");
                                    ret.setStatus(HttpStatus.OK.value());
                                    ret.setHttpStatus(HttpStatus.OK);
                                    return ret;
                                }
                                return ex.toResponseObj();
                            });
                })
                ;
    }

    public Mono<List<Conversation>> getConversations(TrecAuthentication auth, String appId){

        return useProfile(auth)
                .flatMap((String requester) -> {
                    return appId != null ?
                            conversationRepo.getConversationsByProfileAndApp(requester, appId).collectList() :
                            conversationRepo.getConversationsByProfile(requester).collectList();
                });

    }

}
