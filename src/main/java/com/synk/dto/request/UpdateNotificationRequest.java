// 알림 설정 3가지를 on/off 할 때 쓴다 — null이면 기존 값 유지
package com.synk.dto.request;

import lombok.Getter;

@Getter
public class UpdateNotificationRequest {

    private Boolean missionNotification;
    private Boolean resultNotification;
    private Boolean highlightNotification;
}

