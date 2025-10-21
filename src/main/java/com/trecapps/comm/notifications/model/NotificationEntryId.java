package com.trecapps.comm.notifications.model;

import lombok.Data;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.time.Instant;

@PrimaryKeyClass
@Data
public class NotificationEntryId {
    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 0, name = "profile_id")
    String profileId;
    @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 1, name = "app_id")
    String appId;

    @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 2, name = "create_time")
    Instant createTime;

    @PrimaryKeyColumn(type = PrimaryKeyType.CLUSTERED, ordinal = 3, name = "unique_id")
    String uniqueId;
}
