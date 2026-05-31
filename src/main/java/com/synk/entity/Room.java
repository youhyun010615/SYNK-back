package com.synk.entity;


import com.synk.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length =
            10)
    private String code;

    @Column(length = 255)
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
                                       thumbnail, int maxMembers,
                               int dailyMissionCount,
                               LocalTime missionStartTime,
                               LocalTime missionEndTime)
    {
        this.name = name;
        this.thumbnail = thumbnail;
        this.maxMembers = maxMembers;
        this.dailyMissionCount = dailyMissionCount;
        this.missionStartTime = missionStartTime;
        this.missionEndTime = missionEndTime;
    }

}

