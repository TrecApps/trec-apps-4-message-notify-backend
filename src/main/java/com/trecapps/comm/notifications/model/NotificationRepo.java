package com.trecapps.comm.notifications.model;

import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepo extends ReactiveCassandraRepository<NotificationEntry, NotificationEntryId> {

    @Modifying
    @Query("delete from notification_entry where profile_id = :profileId and update_time < :time")
    Mono<Void> deleteEntriesByProfile(String profileId, Instant time);

    @Modifying
    @Query("delete from notification_entry where profile_id = :profileId and app_id = :appId and update_time < :time")
    Mono<Void> deleteEntriesByProfile(String profileId, String appId, Instant time);

    default Mono<Void> deleteEntriesByUser(String userId, Instant time) {
        return deleteEntriesByProfile(String.format("User-%s", userId), time);
    }

    default Mono<Void> deleteEntriesByBrand(String brandId, Instant time) {
        return deleteEntriesByProfile(String.format("Brand-%s", brandId), time);
    }

    default Mono<Void> deleteEntriesByUser(String userId, String appId, Instant time) {
        return deleteEntriesByProfile(String.format("User-%s", userId), appId, time);
    }

    default Mono<Void> deleteEntriesByBrand(String brandId, String appId, Instant time) {
        return deleteEntriesByProfile(String.format("Brand-%s", brandId), appId, time);
    }


    @Query("select * from notification_entry where profile_id = :profileId")
    Flux<NotificationEntry> getNotificationsByProfile(String profileId, Pageable page);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId")
    Flux<NotificationEntry> getNotificationsByProfile(String profileId, String appId, Pageable page);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId and update_time > :time ALLOW FILTERING")
    Flux<NotificationEntry> getNotificationsByAfter(String profileId, String appId, Instant time);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId " +
            "and create_time = :createTime and unique_id = :uniqueId ALLOW FILTERING")
    Flux<NotificationEntry> findByUniqueId(String profileId, String appId, Instant createTime, String uniqueId);

    @Query("select * from notification_entry where unique_id IN :uniqueIds ALLOW FILTERING")
    Flux<NotificationEntry> findAllByUniqueIds(List<String> uniqueIds);

    default Flux<NotificationEntry> getNotificationsByUserIdAndAppId(String userId, String appId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByProfile(String.format("User-%s", userId), appId, pRequest);
    }
    default Flux<NotificationEntry> getNotificationsByBrandIdAndAppId(String brandId, String appId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByProfile(String.format("Brand-%s",brandId), appId, pRequest);
    }

    default Flux<NotificationEntry> getNotificationsByUserId(String userId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByProfile(String.format("User-%s", userId), pRequest);
    }
    default Flux<NotificationEntry> getNotificationsByBrandId(String brandId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByProfile(String.format("Brand-%s",brandId), pRequest);
    }


}
