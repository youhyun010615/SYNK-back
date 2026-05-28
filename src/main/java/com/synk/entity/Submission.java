// 유조저가 미션에 제출한 영상을 저장하는 테이블
// 제출했으면 SUBMITTED + 영상 URL, 시간 안에 못 냈으면 스케줄러가 MISSED로 row를 INSERT

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"mission_id",
        "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)


public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "video_url", length = 255)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_at", nullable = false,
            updatable = false)
    private LocalDateTime submittedAt;

    @Builder
    public Submission(User user, Room room, Mission
            mission, String videoUrl, SubmissionStatus status) {
        this.user = user;
        this.room = room;
        this.mission = mission;
        this.videoUrl = videoUrl;
        this.status = status;
        this.submittedAt = LocalDateTime.now();
    }

    public enum SubmissionStatus {
        SUBMITTED, MISSED
    }



}

