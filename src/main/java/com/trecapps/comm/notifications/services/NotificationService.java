package com.trecapps.comm.notifications.services;

import com.trecapps.auth.common.models.TcBrands;
import com.trecapps.auth.common.models.TrecAuthentication;
import com.trecapps.base.notify.models.*;
import com.trecapps.comm.notifications.model.NotificationEntry;
import com.trecapps.comm.notifications.model.NotificationPost;
import com.trecapps.comm.notifications.model.NotificationRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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

                    entry.setId(UUID.randomUUID());
                    entry.setCreateTime(OffsetDateTime.now());
                    entry.setStatus(NotificationStatus.UNSEEN);
                    entry.setUpdateTime(entry.getCreateTime());

                    entry.setCategory(notifyPost.getCategory());
                    entry.setMessage(notifyPost.getMessage());
                    entry.setRelevantId(notifyPost.getRelevantId());
                    entry.setImageId(notifyPost.getImageId());
                    entry.setType(notifyPost.getType());
                    entry.setBrandId(notifyPost.getBrandId());
                    entry.setAppId(notifyPost.getAppId());
                    entry.setUserId(notifyPost.getUserId());
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
                    this.notificationRepo.findAllById(mp.getNotifications().stream().map(UUID::fromString).toList()).collectList()
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
                        notification.setNotificationId(entry.getId().toString());
                        notification.setStatus(entry.getStatus());
                        notification.setApp(entry.getAppId());
                        return notification;
                    }).toList();
                });
    }

    Mono<ResponseObj> deleteNotifications(TrecAuthentication auth, String appId, List<String> ids)
    {
        return Mono.just(ids)
                .flatMap((List<String> notifyIds) ->
                        this.notificationRepo.findAllById(notifyIds.stream().map(UUID::fromString).toList()).collectList()
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

                    String brandId = entry.getBrandId();
                    String userId = entry.getUserId();
                    String appId1 = entry.getAppId();

                    if(brandId == null)
                    {
                        if(appId1 == null)
                            notificationRepo.deleteEntriesByUser(userId, entry.getUpdateTime()).subscribe();
                        else notificationRepo.deleteEntriesByUser(userId, appId1, entry.getUpdateTime()).subscribe();
                    } else {
                        if(appId1 == null)
                            notificationRepo.deleteEntriesByBrand(brandId, entry.getUpdateTime()).subscribe();
                        else notificationRepo.deleteEntriesByBrand(brandId, appId1, entry.getUpdateTime()).subscribe();
                    }
                    return ResponseObj.getInstance(HttpStatus.OK, "Success!");
                });
    }


}
