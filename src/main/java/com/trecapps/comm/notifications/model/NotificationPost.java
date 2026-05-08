package com.trecapps.comm.notifications.model;

import com.trecapps.base.notify.models.ImageEndpointType;
import com.trecapps.base.notify.models.NotificationStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class NotificationPost {

    UUID accountId;     // The Account to notify
    String appId;       // The App that sent this

    String relevantId;  // The id of the content this is relevant to
    String relevantIdSecondary;

    // Image Information
    ImageEndpointType type = ImageEndpointType.REGULAR;
    UUID imageId;

    String message;
    String category;
}
