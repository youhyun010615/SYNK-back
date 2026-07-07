package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "synklogs",
        uniqueConstraints =
        @UniqueConstraint(columnNames = {"room_id", "date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Synklog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // SYNKLOG를 생성(또는 최근 재생성)한 유저 — "내 Synklog" 필터용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "synklog_video_url", length = 255)
    private String synklogVideoUrl;

    @Column(length = 255)
    private String thumbnail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SynklogStatus status;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public Synklog(Room room, LocalDate date, User createdBy) {
        this.room = room;
        this.date = date;
        this.createdBy = createdBy;
        this.status = SynklogStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
    }

    public void updateCreatedBy(User user) {
        this.createdBy = user;
    }

    public enum SynklogStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public void complete(String synklogVideoUrl, String
            thumbnail) {
        this.synklogVideoUrl = synklogVideoUrl;
        this.thumbnail = thumbnail;
        this.status = SynklogStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void reprocess() {
        this.status = SynklogStatus.PROCESSING;
        this.synklogVideoUrl = null;
        this.thumbnail = null;
        this.completedAt = null;
    }

    public void fail() {
        this.status = SynklogStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

}
