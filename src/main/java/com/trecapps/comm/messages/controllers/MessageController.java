package com.trecapps.comm.messages.controllers;

import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.ResponseObj;
import com.trecapps.comm.messages.models.Message;
import com.trecapps.comm.messages.services.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/Messages")
public class MessageController extends BaseController{

    @Autowired
    MessageService service;

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    Mono<ResponseEntity<ResponseObj>> postMessage(
            Authentication authentication,
            @RequestParam String conversationId,
            @RequestBody String message
    ) {
        return service.postMessage(
                (TrecAuthentication) authentication,
                conversationId,
                message
        ).map(this::responseObjToEntity);
    }

    @GetMapping
    Mono<List<Message>> getMessages(
            Authentication authentication,
            @RequestParam String conversationId,
            @RequestParam int page){
        {
            return service.getMessages(
                    (TrecAuthentication) authentication,
                    conversationId,
                    page
            );
        }
    }

    @PatchMapping("/seen")
    Mono<ResponseEntity<ResponseObj>> seeMessage(
            Authentication authentication,
            @RequestBody List<String> messages
    ) {
        return service.markReaction(
                (TrecAuthentication) authentication,
                messages,
                null
        ).map(this::responseObjToEntity);
    }

    @PatchMapping(value= "/react",consumes = MediaType.TEXT_PLAIN_VALUE)
    Mono<ResponseEntity<ResponseObj>> seeMessage(
            Authentication authentication,
            @RequestParam String messageId,
            @RequestBody String reaction
    ) {
        return service.markReaction(
                (TrecAuthentication) authentication,
                List.of(messageId),
                reaction
        ).map(this::responseObjToEntity);
    }

    @PutMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    Mono<ResponseEntity<ResponseObj>> editMessage(
            Authentication authentication,
            @RequestParam String messageId,
            @RequestBody String message
    ) {
        return service.editMessage(
                (TrecAuthentication) authentication,
                messageId,
                message
        ).map(this::responseObjToEntity);
    }
}
