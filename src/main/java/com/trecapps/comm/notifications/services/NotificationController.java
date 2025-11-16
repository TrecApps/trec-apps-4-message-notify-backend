package com.trecapps.comm.notifications.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TcUser;
import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.Notification;
import com.trecapps.base.notify.models.NotificationMarkPost;
import com.trecapps.base.notify.models.ResponseObj;
import com.trecapps.comm.notifications.model.NotificationDto;
import com.trecapps.comm.notifications.model.NotificationPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/Notifications")
@Slf4j
public class NotificationController {

    INotificationConsumer iNotificationConsumer;
    NotificationService notificationService;

    INotificationMessageHandler iNotificationMessageHandler = (NotificationPost post) -> {
        return notificationService.postNotify(post)
                .map((ResponseObj obj) -> {
                    Boolean worked = !obj.errorOccurred();

                    if(!worked){
                        log.error(
                                "Error making notification related to content {} from app {} for user {} and brand {}",
                                post.getRelevantId(), post.getAppId(), post.getUserId(), post.getBrandId());
                    }
                    return worked;
                });
    };

    @Autowired
    NotificationController(NotificationService notificationService1,
                        @Autowired(required = false) INotificationConsumer iNotificationConsumer){
        this.notificationService = notificationService1;
        this.iNotificationConsumer = iNotificationConsumer;
        if(this.iNotificationConsumer != null)
            this.iNotificationConsumer.initialize(iNotificationMessageHandler);
    }


    @PostMapping("/Mark")
    Mono<ResponseEntity<ResponseObj>> markNotifications(
            Authentication authentication,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) OffsetDateTime time,
            @RequestBody NotificationMarkPost markPost
    ){
        TrecAuthentication trecAuthentication = (TrecAuthentication) authentication;
        TcUser user = trecAuthentication.getUser();
        TcBrands brands = trecAuthentication.getBrand();
        return notificationService.markNotification(
                user.getId(),
                brands == null ? null: brands.getId(),
                appId,
                markPost, time)
                .map((ResponseObj obj) -> new ResponseEntity<>(obj, HttpStatusCode.valueOf(obj.getStatus())));
    }

    @GetMapping
    Mono<List<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam String appId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        return notificationService.getNotifications(
                (TrecAuthentication) authentication, appId, size, page)
                ;

    }

    @GetMapping("/After")
    Mono<List<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam String appId,
            @RequestParam OffsetDateTime time
            ){
        return notificationService.getNotificationsAfter(
                (TrecAuthentication) authentication, appId, time);

    }

    @DeleteMapping("/DeleteByList")
    Mono<ResponseEntity<ResponseObj>> deleteByList(
            Authentication authentication,
            @RequestParam(required = false) String appId,
            @RequestBody List<String> ids
    ) {
        return notificationService.deleteNotifications(
                (TrecAuthentication) authentication,
                appId, ids)
                .map((ResponseObj obj) ->
                        new ResponseEntity<>(obj, HttpStatusCode.valueOf(obj.getStatus())));
    }

    @DeleteMapping("/DeleteBySize")
    Mono<ResponseEntity<ResponseObj>> deleteBySize(
            Authentication authentication,
            @RequestParam(required = false) String appId,
            @RequestParam(defaultValue = "10") int size
    ){
        return notificationService.deleteNotifications(
                        (TrecAuthentication) authentication,
                        appId, size)
                .map((ResponseObj obj) ->
                        new ResponseEntity<>(obj, HttpStatusCode.valueOf(obj.getStatus())));
    }



}
