package com.trecapps.comm.notifications.model;

import com.trecapps.base.notify.models.ImageEndpointType;
import com.trecapps.base.notify.models.NotificationStatus;
import lombok.Data;

@Data
public class NotificationPost {

    String userId;      // The user this applies to
    String brandId;     // The brand this applies to
    String appId;       // The App that sent this

    String relevantId;  // The id of the content this is relevant to

    // Image Information
    ImageEndpointType type = ImageEndpointType.REGULAR;
    String imageId;

    String message;
    String category;
}
