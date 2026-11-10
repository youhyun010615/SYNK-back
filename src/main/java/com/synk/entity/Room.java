package com.synk.entity;


import com.synk.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    // 이 시각 이후에 생성된 방부터만 미션 발동·푸시 알림 대상이다. (KST, 2026-11-10 정식 시작)
    // 그 이전에 만들어진 기존 방은 알림·미션 발동을 하지 않는다. DB 컬럼/마이그레이션 없이
    // 생성 시각으로 판단하므로 배포 시점과 무관하고, 되돌리려면 이 값을 조정하면 된다.
    public static final LocalDateTime PUSH_START_AT = LocalDateTime.of(2026, 11, 10, 0, 0);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length =
            10)
    private String code;

    @Column(length = 1000)
    private String thumbnail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "daily_mission_count", nullable =
            false)
    private int dailyMissionCount;

    @Column(name = "mission_start_time", nullable =
            false)
    private LocalTime missionStartTime;

    @Column(name = "mission_end_time", nullable = false)
    private LocalTime missionEndTime;

    @Builder
    public Room(String name, String code, String
                        thumbnail, User owner,
                int maxMembers, int dailyMissionCount,
                LocalTime missionStartTime, LocalTime
                        missionEndTime) {
        this.name = name;
        this.code = code;
        this.thumbnail = thumbnail;
        this.owner = owner;
        this.maxMembers = maxMembers;
        this.dailyMissionCount = dailyMissionCount;
        this.missionStartTime = missionStartTime;
        this.missionEndTime = missionEndTime;
    }

    public void updateSettings(String name, String
                                       thumbnail, Integer maxMembers,
                               Integer dailyMissionCount,
                               LocalTime missionStartTime,
                               LocalTime missionEndTime)
    {
        if (name != null) this.name = name;
        if (thumbnail != null) this.thumbnail = thumbnail;
        if (maxMembers != null) this.maxMembers = maxMembers;
        if (dailyMissionCount != null) this.dailyMissionCount = dailyMissionCount;
        if (missionStartTime != null) this.missionStartTime = missionStartTime;
        if (missionEndTime != null) this.missionEndTime = missionEndTime;
    }

    public void changeOwner(User newOwner) {
        this.owner = newOwner;
    }

    // 미션 발동·푸시 알림 대상 여부: PUSH_START_AT 이후에 만들어진 신규 방만 true
    public boolean isPushEnabled() {
        LocalDateTime createdAt = getCreatedAt();
        return createdAt == null || !createdAt.isBefore(PUSH_START_AT); // 아직 저장 전이면 신규 방
    }

}
