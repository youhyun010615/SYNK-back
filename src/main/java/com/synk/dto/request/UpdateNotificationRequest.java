// 알림 설정 3가지를 on/off 할 때 쓴다

package com.synk.dto.request;

import lombok.Getter;

@Getter
public class UpdateNotificationRequest {

    private boolean missionNotification;
    private boolean resultNotification;
    private boolean highlightNotification;
}

