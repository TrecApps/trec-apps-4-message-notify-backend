package com.trecapps.comm.notifications.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.*;
import com.trecapps.comm.notifications.model.NotificationEntry;
import com.trecapps.comm.notifications.model.NotificationEntryId;
import com.trecapps.comm.notifications.model.NotificationPost;
import com.trecapps.comm.notifications.model.NotificationRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
                    String brandId = notifyPost.getBrandId();
                    id.setProfileId(
                            brandId == null ?
                                    String.format("User-%s", notifyPost.getUserId()) :
                                    String.format("Brand-%s", brandId)
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

    Mono<ResponseObj> markNotification(String userId, String brandId, String appId, NotificationMarkPost markPost)
    {
        return Mono.just(markPost)
                .flatMap((NotificationMarkPost mp) ->
                    this.notificationRepo.findAllByUniqueIds(mp.getNotifications()).collectList()
                )
                .map((List<NotificationEntry> entries) -> {
                    for(NotificationEntry entry: entries)
                    {
                        if(!entry.isOwner(userId, brandId, appId))
                            return ResponseObj.getInstance(HttpStatus.UNAUTHORIZED, "Notification does not belong to you");
                    }

                    for(NotificationEntry entry:entries){
                        entry.setStatus(markPost.getStatus());
                    }

                    this.notificationRepo.saveAll(entries);
                    return ResponseObj.getInstance(HttpStatus.OK, "Success");
                });
    }

    Mono<List<Notification>> getNotificationsAfter(TrecAuthentication auth, String appId, OffsetDateTime time){
        return Mono.just(auth)
                .flatMap((TrecAuthentication notifyAuth) -> {
                    TcBrands brandId = notifyAuth.getBrand();
                    if(brandId != null)
                        return notificationRepo.getNotificationsByAfter(String.format("Brand-%s",brandId.getId()), appId, time.toInstant()).collectList();
                    return notificationRepo.getNotificationsByAfter(
                            String.format("User-%s",notifyAuth.getUser().getId()), appId, time.toInstant()).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    return entries.stream().map((NotificationEntry entry) -> {
                        Notification notification = new Notification();
                        notification.setPost(entry.getNotifyPost());
                        notification.setNotificationId(entry.getId().getUniqueId());
                        notification.setStatus(entry.getStatus());
                        notification.setApp(entry.getId().getAppId());
                        return notification;
                    }).toList();
                });
    }

    Mono<List<Notification>> getNotifications(TrecAuthentication auth, String appId, int size, int page)
    {
        return Mono.just(auth)
                .flatMap((TrecAuthentication notifyAuth) -> {
                    TcBrands brandId = notifyAuth.getBrand();
                    if(brandId != null)
                        return notificationRepo.getNotificationsByBrandIdAndAppId(brandId.getId(), appId, size, page).collectList();
                    return notificationRepo.getNotificationsByUserIdAndAppId(notifyAuth.getUser().getId(), appId, size, page).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    return entries.stream().map((NotificationEntry entry) -> {
                        Notification notification = new Notification();
                        notification.setPost(entry.getNotifyPost());
                        notification.setNotificationId(entry.getId().getUniqueId());
                        notification.setStatus(entry.getStatus());
                        notification.setApp(entry.getId().getAppId());
                        return notification;
                    }).toList();
                });
    }

    Mono<ResponseObj> deleteNotifications(TrecAuthentication auth, String appId, List<String> ids)
    {
        return Mono.just(ids)
                .flatMap((List<String> notifyIds) ->
                        this.notificationRepo.findAllByUniqueIds(notifyIds).collectList()
                )
                .map((List<NotificationEntry> entries) -> {
                    for(NotificationEntry entry: entries)
                    {
                        TcBrands brandId = auth.getBrand();
                        if(!entry.isOwner(auth.getUser().getId(), brandId == null ? null : brandId.getId(), appId))
                            return ResponseObj.getInstance(HttpStatus.UNAUTHORIZED, "Notification does not belong to you");
                    }

                    this.notificationRepo.deleteAll(entries).subscribe();
                    return ResponseObj.getInstance(HttpStatus.OK, "Success!");
                });
    }

    Mono<ResponseObj> deleteNotifications(TrecAuthentication auth, String appId, int size)
    {
        return Mono.just(auth)
                .flatMap((TrecAuthentication notifyAuth) -> {
                    TcBrands brandId = notifyAuth.getBrand();
                    if(brandId != null)
                        return notificationRepo.getNotificationsByBrandIdAndAppId(brandId.getId(), appId, size, 0).collectList();
                    return notificationRepo.getNotificationsByUserIdAndAppId(notifyAuth.getUser().getId(), appId, size, 0).collectList();
                })
                .map((List<NotificationEntry> entries) -> {
                    if(entries.isEmpty())
                        return ResponseObj.getInstance(HttpStatus.OK, "Success!");

                    NotificationEntry entry = entries.get(entries.size()-1);

                    String profile = entry.getId().getProfileId();
                    String appId1 = entry.getId().getAppId();


                        if(appId1 == null)
                            notificationRepo.deleteEntriesByProfile(profile, entry.getUpdateTime()).subscribe();
                        else notificationRepo.deleteEntriesByProfile(profile, appId1, entry.getUpdateTime()).subscribe();

                    return ResponseObj.getInstance(HttpStatus.OK, "Success!");
                });
    }


}
