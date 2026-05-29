// 유저가 미션을 완료할 때마다 개인 도감에 기록을 쌓는 테이블
// "내가 어떤 미션을 몇 번 했는지, 어느 방에서 했는지"를 조회할 때 사용

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "collection_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_template_id", nullable =
            false)
    private MissionTemplate missionTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable =
            false)
    private Submission submission;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 255)
    private String thumbnail;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CollectionRecord(User user, MissionTemplate
                                    missionTemplate,
                            Room room, Submission
                                    submission,
                            LocalDate date, String
                                    thumbnail) {
        this.user = user;
        this.missionTemplate = missionTemplate;
        this.room = room;
        this.submission = submission;
        this.date = date;
        this.thumbnail = thumbnail;
        this.createdAt = LocalDateTime.now();
    }

}
