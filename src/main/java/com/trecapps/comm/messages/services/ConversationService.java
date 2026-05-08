package com.trecapps.comm.messages.services;


import com.trecapps.comm.common.ObjectResponseException;
import com.trecapps.comm.common.ResponseObj;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.repos.ConversationRepo;
import com.trecauth.common.model.Account;
import com.trecauth.common.model.AccountList;
import com.trecauth.common.model.AccountType;
import com.trecauth.common.model.TrecauthAuthentication;
import com.trecauth.webflux.repos.AccountReactiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class ConversationService extends ProfileSorterService{

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    @Autowired
    ConversationRepo conversationRepo;

    @Autowired
    AccountReactiveRepository userStorageService;


    void checkIsBlocking(AccountList list, List<UUID> profiles){
        List<Account> blockers = list
                .getBrandAccounts()
                .stream()
                .filter((Account a) -> AccountType.BLOCK.equals(a.getType()))
                .toList();

        List<UUID> blockingProfiles = new ArrayList<>();

        for(Account blocker : blockers){
            if(profiles.contains(blocker.getBrandId())){
                blockingProfiles.add(blocker.getBrandId());
            }
            if(profiles.contains(blocker.getCreator())){
                blockingProfiles.add(blocker.getCreator());
            }
        }

        if(blockingProfiles.isEmpty()) return;

        log.warn("Messaging Warn: user {} is being blocked by the following accounts: {}", list.getMainUserAccount().getId(), blockingProfiles);
        throw new ObjectResponseException(HttpStatus.FORBIDDEN, "");
    }

    public Mono<ResponseObj> establishConversation(AccountList list, String appId, List<UUID> profiles) {
        return Mono.just(list)
                .map((AccountList list1) -> {
                    // ToDo - check for users blocking this user
                    checkIsBlocking(list1, profiles);
                    // End ToDo

                    Conversation conversation = new Conversation();
                    conversation.getApps().add(appId);
                    conversation.setId(UUID.randomUUID());
                    conversation.getProfiles().addAll(profiles);

                    conversation.getProfiles().add(list1.getCurrentAccount().getId());
                    return conversation;
                })
                .flatMap((Conversation conversation) -> {

                    return conversationRepo.getConversationsByProfileAndApp(list.getCurrentAccount().getId(), appId)
                            .collectList()
                            .doOnNext((List<Conversation> conversations) -> {
                                TreeSet<UUID> currentProfiles = new TreeSet<>(conversation.getProfiles());

                                for(Conversation existingCon : conversations){
                                    TreeSet<UUID> profilesList = new TreeSet<>(existingCon.getProfiles());
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

    public Mono<List<Conversation>> getConversations(TrecauthAuthentication auth, String appId){

                    return appId != null ?
                            conversationRepo.getConversationsByProfileAndApp(auth.getList().getMainAccount().getId(), appId).collectList() :
                            conversationRepo.getConversationsByProfile(auth.getList().getMainAccount().getId()).collectList();


    }

}
