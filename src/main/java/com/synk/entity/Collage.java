// 미션이 끝나면 자동으로 생성되는 4~% 분할 합성 영상

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "collages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false,
            unique = true)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "collage_video_url", length = 255)
    private String collageVideoUrl;

    @Column(length = 255)
    private String thumbnail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollageStatus status;

    @Column(name = "participation_rate")
    private Integer participationRate;

    @Column(name = "completion_time")
    private Integer completionTime;

    @Column(name = "total_members")
    private Integer totalMembers;

    @Column(name = "submitted_count")
    private Integer submittedCount;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    @Builder
    public Collage(Mission mission, Room room, int
            totalMembers) {
        this.mission = mission;
        this.room = room;
        this.totalMembers = totalMembers;
        this.status = CollageStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
    }

    public enum CollageStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public void complete(String collageVideoUrl, String
                                 thumbnail,
                         int participationRate, int
                                 completionTime, int submittedCount) {
        this.collageVideoUrl = collageVideoUrl;
        this.thumbnail = thumbnail;
        this.participationRate = participationRate;
        this.completionTime = completionTime;
        this.submittedCount = submittedCount;
        this.status = CollageStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = CollageStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

}
