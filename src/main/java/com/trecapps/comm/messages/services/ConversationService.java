package com.trecapps.comm.messages.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TcUser;
import com.trecapps.auth.webflux.services.IUserStorageServiceAsync;
import com.trecapps.base.notify.models.ResponseObj;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.repos.ConversationRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class ConversationService {

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
                .flatMap((Set<TcUser> users) -> {
                    // ToDo - check for users blocking this user

                    // End ToDo

                    Conversation conversation = new Conversation();
                    conversation.getApps().add(appId);
                    conversation.setId(UUID.randomUUID());
                    conversation.getProfiles().addAll(profiles);
                    return conversationRepo.save(conversation);
                })
                .map((Conversation conversation) ->
                    ResponseObj.getInstance("Success!", conversation.getId().toString())
                )
                // ToDo - error handling
                ;
    }

    public Mono<List<Conversation>> getConversations(TcUser user, TcBrands brand, String appId){
        String profile = brand == null ?
                String.format("User-%s", user.getId()) :
                String.format("Brand-%s", brand.getId());

        return Mono.just(profile)
                .flatMap((String requester) -> {
                    return appId != null ?
                            conversationRepo.getConversationsByProfileAndApp(requester, appId).collectList() :
                            conversationRepo.getConversationsByProfile(requester).collectList();
                });

    }

}
