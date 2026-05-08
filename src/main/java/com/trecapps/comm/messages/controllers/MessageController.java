package com.trecapps.comm.messages.controllers;

import com.trecapps.comm.common.ResponseObj;
import com.trecapps.comm.messages.models.Message;
import com.trecapps.comm.messages.services.MessageService;
import com.trecauth.common.model.TrecauthAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
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
                ((TrecauthAuthentication) authentication).getList(),
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
                    ((TrecauthAuthentication) authentication).getList(),
                    conversationId,
                    page
            );
        }
    }

    @GetMapping("/latest")
    Mono<List<Message>> getLatestMessages(
            Authentication authentication,
            @RequestParam String conversationId,
            @RequestParam OffsetDateTime time){
        {
            return service.getLatestMessages(
                    ((TrecauthAuthentication) authentication).getList(),
                    conversationId,
                    time
            );
        }
    }

    @PatchMapping("/seen")
    Mono<ResponseEntity<ResponseObj>> seeMessage(
            Authentication authentication,
            @RequestBody List<String> messages
    ) {
        return service.markReaction(
                ((TrecauthAuthentication) authentication).getList(),
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
                ((TrecauthAuthentication) authentication).getList(),
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
                ((TrecauthAuthentication) authentication).getList(),
                messageId,
                message
        ).map(this::responseObjToEntity);
    }
}
