package com.trecapps.comm.messages.controllers;

import com.azure.core.annotation.Post;
import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.ResponseObj;
import com.trecapps.comm.messages.models.Conversation;
import com.trecapps.comm.messages.services.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/Conversations")
public class ConversationController extends BaseController{

    @Autowired
    ConversationService service;

    @PostMapping
    Mono<ResponseEntity<ResponseObj>> setConversation(
            Authentication authentication,
            @RequestParam String appId,
            @RequestBody List<String> profiles
    ) {
        TrecAuthentication trecAuthentication = (TrecAuthentication) authentication;
        return service.establishConversation(
                trecAuthentication.getUser(),
                trecAuthentication.getBrand(),
                appId,
                profiles)
                .map(this::responseObjToEntity);
    }


    @GetMapping
    Mono<List<Conversation>> getConversations(
            Authentication authentication,
            @RequestParam(required = false) String appId
    ){
        return service.getConversations((TrecAuthentication) authentication, appId);
    }
}
