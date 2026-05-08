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
    Mono<Void> deleteEntriesByProfile(UUID profileId, Instant time);

    @Modifying
    @Query("delete from notification_entry where profile_id = :profileId and app_id = :appId and update_time < :time")
    Mono<Void> deleteEntriesByProfile(UUID profileId, String appId, Instant time);


    @Query("select * from notification_entry where profile_id = :profileId")
    Flux<NotificationEntry> getNotificationsByProfile(UUID profileId, Pageable page);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId")
    Flux<NotificationEntry> getNotificationsByProfile(UUID profileId, String appId, Pageable page);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId and update_time > :time ALLOW FILTERING")
    Flux<NotificationEntry> getNotificationsByAfter(UUID profileId, String appId, Instant time);

    @Query("select * from notification_entry where profile_id = :profileId and app_id = :appId " +
            "and create_time = :createTime and unique_id = :uniqueId ALLOW FILTERING")
    Flux<NotificationEntry> findByUniqueId(UUID profileId, String appId, Instant createTime, String uniqueId);

    @Query("select * from notification_entry where unique_id IN :uniqueIds ALLOW FILTERING")
    Flux<NotificationEntry> findAllByUniqueIds(List<String> uniqueIds);


}
