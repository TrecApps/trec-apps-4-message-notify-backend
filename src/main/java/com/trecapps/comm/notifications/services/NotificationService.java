package com.trecapps.comm.notifications.services;

import com.trecapps.base.notify.models.*;
import com.trecapps.comm.notifications.model.*;
import com.trecauth.common.model.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    @Autowired
    NotificationRepo notificationRepo;

    String cutString(String cut, int size){
        int cutSize = cut.length();
        return cut.substring(0, Math.min(size, cutSize));
    }

    Mono<ResponseObj> postNotify(NotificationPost post) {
        return Mono.just(post)
                .map((NotificationPost notifyPost) -> {
                    NotificationEntry entry = new NotificationEntry();
                    NotificationEntryId id = new NotificationEntryId();

                    id.setAppId(notifyPost.getAppId());
                    id.setProfileId(
                            notifyPost.getAccountId()
                    );
                    id.setCreateTime(Instant.now());
                    id.setUniqueId(UUID.randomUUID().toString());

                    entry.setId(id);
                    entry.setStatus(NotificationStatus.UNSEEN);
                    entry.setUpdateTime(id.getCreateTime());

                    entry.setCategory(notifyPost.getCategory());
                    entry.setMessage(notifyPost.getMessage());
                    entry.setRelevantId(notifyPost.getRelevantId());
                    entry.setRelevantIdSecond(notifyPost.getRelevantIdSecondary());
                    entry.setImageId(notifyPost.getImageId());
                    entry.setType(notifyPost.getType());
                    return entry;
                })
                .flatMap((NotificationEntry entry) -> this.notificationRepo.save(entry))
                .doOnNext((NotificationEntry entry) -> {
                    // ToDo - mechanism to push the notification to user
                })
                .thenReturn(ResponseObj.getInstance(HttpStatus.OK, "Success"));
    }

    Mono<ResponseObj> markNotification(UUID accountId, String appId, NotificationMarkPost markPost, OffsetDateTime time)
    {
        return Mono.just(markPost)
                .flatMap((NotificationMarkPost mp) -> {
                    if(mp.getNotifications().size() > 1)
                        return this.notificationRepo.findAllByUniqueIds(mp.getNotifications()).collectList();
                    if(mp.getNotifications().size() == 1 && time != null)
                        return this.notificationRepo.findByUniqueId(
                                accountId,
                                appId,
                                time.toInstant(),
                                mp.getNotifications().getFirst()
                        ).collectList();
                    List<NotificationEntry> ret = new ArrayList<>();
                    return Mono.just(ret);
                }

                )
                .flatMap((List<NotificationEntry> entries) -> {
                    for(NotificationEntry entry: entries)
                    {
                        if(!entry.isOwner(accountId, appId))
                            return Mono.just(ResponseObj.getInstance(HttpStatus.UNAUTHORIZED, "Notification does not belong to you"));
                    }

                    for(NotificationEntry entry:entries){
                        entry.setStatus(markPost.getStatus());
                    }

                    return this.notificationRepo.saveAll(entries)
                            .collectList()
                            .map((List<NotificationEntry> entries1) -> {
                                ResponseObj ret = ResponseObj.getInstance(HttpStatus.OK, "Success");
                                List<String> idList = entries1.stream().map((NotificationEntry e) -> e.getId().getUniqueId()).toList();
                                ret.setId(String.join(";", idList));
                                return ret;
                            });
                });
    }

    Mono<List<NotificationDto>> getNotificationsAfter(Account auth, String appId, OffsetDateTime time){
        return Mono.just(auth)
                .flatMap((Account notifyAuth) -> {
                    return notificationRepo.getNotificationsByAfter(
                            notifyAuth.getId(), appId, time.toInstant()).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    return entries.stream()
                            .filter((NotificationEntry entry) -> {
                                Instant timeAsInstant = entry.getTime();
                                OffsetDateTime timeAsOffset =timeAsInstant.atOffset(ZoneOffset.UTC);
                                return timeAsOffset.isAfter(time);
                            })
                            .map((NotificationEntry entry) -> {
                                Notification notification = new Notification();
                                notification.setPost(entry.getNotifyPost());
                                notification.setNotificationId(entry.getId().getUniqueId());
                                notification.setStatus(entry.getStatus());
                                notification.setApp(entry.getId().getAppId());
                                return notification;
                            })
                            .map(NotificationDto::getInstance).toList();
                });
    }

    Mono<List<NotificationDto>> getNotifications(Account auth, String appId, int size, int page)
    {
        return Mono.just(auth)
                .flatMap((Account notifyAuth) -> {
                    return notificationRepo.getNotificationsByProfile(notifyAuth.getId(), appId, PageRequest.of(size, page)).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    return entries.stream().map((NotificationEntry entry) -> {
                        Notification notification = new Notification();
                        notification.setPost(entry.getNotifyPost());
                        notification.setNotificationId(entry.getId().getUniqueId());
                        notification.setStatus(entry.getStatus());
                        notification.setApp(entry.getId().getAppId());
                        return notification;
                    })
                            .map(NotificationDto::getInstance).toList();
                });
    }

    Mono<ResponseObj> deleteNotifications(Account auth, String appId, List<String> ids)
    {
        return Mono.just(ids)
                .flatMap((List<String> notifyIds) ->
                        this.notificationRepo.findAllByUniqueIds(notifyIds).collectList()
                )
                .map((List<NotificationEntry> entries) -> {
                    for(NotificationEntry entry: entries)
                    {

                        if(!entry.isOwner(auth.getId(), appId))
                            return ResponseObj.getInstance(HttpStatus.UNAUTHORIZED, "Notification does not belong to you");
                    }

                    this.notificationRepo.deleteAll(entries).subscribe();
                    return ResponseObj.getInstance(HttpStatus.OK, "Success!");
                });
    }

    Mono<ResponseObj> deleteNotifications(Account auth, String appId, int size)
    {
        return Mono.just(auth)
                .flatMap((Account notifyAuth) -> {
                    return notificationRepo.getNotificationsByProfile(notifyAuth.getId(), appId, PageRequest.of(size, 0)).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    if(entries.isEmpty())
                        return ResponseObj.getInstance(HttpStatus.OK, "Success!");

                    NotificationEntry entry = entries.getLast();

                    UUID profile = entry.getId().getProfileId();
                    String appId1 = entry.getId().getAppId();


                        if(appId1 == null)
                            notificationRepo.deleteEntriesByProfile(profile, entry.getUpdateTime()).subscribe();
                        else notificationRepo.deleteEntriesByProfile(profile, appId1, entry.getUpdateTime()).subscribe();

                    return ResponseObj.getInstance(HttpStatus.OK, "Success!");
                });
    }


}
