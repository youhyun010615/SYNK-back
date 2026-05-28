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
    public Synklog(Room room, LocalDate date) {
        this.room = room;
        this.date = date;
        this.status = SynklogStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
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

    public void fail() {
        this.status = SynklogStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

}
