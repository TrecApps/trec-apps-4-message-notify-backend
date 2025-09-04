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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepo extends ReactiveCassandraRepository<NotificationEntry, UUID> {

    @Modifying
    @Query("delete from NotificationEntry ne where ne.userId=:userId and ne.appId=:appId and ne.updateTime < :time")
    Mono<Void> deleteEntriesByUser(String userId, String appId, OffsetDateTime time);

    @Modifying
    @Query("delete from NotificationEntry ne where ne.userId=:userId and ne.updateTime < :time")
    Mono<Void> deleteEntriesByUser(String userId, OffsetDateTime time);

    @Modifying
    @Query("delete from NotificationEntry ne where ne.brandId=:brandId and ne.appId=:appId and ne.updateTime < :time")
    Mono<Void> deleteEntriesByBrand(String brandId, String appId, OffsetDateTime time);

    @Modifying
    @Query("delete from NotificationEntry ne where ne.brandId=:brandId and ne.updateTime < :time")
    Mono<Void> deleteEntriesByBrand(String brandId, OffsetDateTime time);


    @Query("select n from NotificationEntry n where n.userId=:userId and n.appId=:appId")
    Flux<NotificationEntry> getNotificationsByUserIdAndAppId(@Param("userId") String userId, @Param("appId")String appId, Pageable page);

    default Flux<NotificationEntry> getNotificationsByUserIdAndAppId(String userId, String appId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByUserIdAndAppId(userId, appId, pRequest);
    }
    @Query("select n from NotificationEntry n where n.brandId=:brandId and n.appId=:appId")
    Flux<NotificationEntry> getNotificationsByBrandIdAndAppId(@Param("brandId")String brandId,@Param("appId") String appId, Pageable page);

    default Flux<NotificationEntry> getNotificationsByBrandIdAndAppId(String brandId, String appId, int size, int page)
    {
        Sort sort = Sort.by(List.of(new Sort.Order(Sort.Direction.DESC, "updateTime")));
        Pageable pRequest = PageRequest.of(page, size, sort);
        return getNotificationsByBrandIdAndAppId(brandId, appId, pRequest);
    }
}
