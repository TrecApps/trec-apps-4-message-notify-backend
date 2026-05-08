package com.trecapps.comm.messages.controllers;

import com.azure.core.annotation.Post;
import com.trecapps.comm.common.ResponseObj;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.services.ConversationService;
import com.trecauth.common.model.TrecauthAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/Conversations")
public class ConversationController extends BaseController{

    @Autowired
    ConversationService service;

    @PostMapping
    Mono<ResponseEntity<ResponseObj>> setConversation(
            Authentication authentication,
            @RequestParam String appId,
            @RequestBody List<UUID> profiles
    ) {
        TrecauthAuthentication trecAuthentication = (TrecauthAuthentication) authentication;
        return service.establishConversation(
                trecAuthentication.getList(),
                appId,
                profiles)
                .map(this::responseObjToEntity);
    }


    @GetMapping
    Mono<List<Conversation>> getConversations(
            Authentication authentication,
            @RequestParam(required = false) String appId
    ){
        return service.getConversations((TrecauthAuthentication) authentication, appId);
    }
}
