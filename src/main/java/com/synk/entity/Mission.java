// 실제로 발동된 미션, "어떤 방에서, 어떤 날, 어떤 시간 슬롯에, 어떤 미션 템플릿으로 발동 됐는지"를 저장
// 매일 자정에 스케줄러가 당일 미션들을 미리 생성해두는 방식
// getTargetedAt(), getDeadline()으로 계산

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "missions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "date", "time_slot_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_template_id", nullable =
            false)
    private MissionTemplate missionTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private MissionTimeSlot timeSlot;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionStatus status;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;


    @Builder
    public Mission(Room room, MissionTemplate
            missionTemplate, MissionTimeSlot timeSlot, LocalDate
                           date) {
        this.room = room;
        this.missionTemplate = missionTemplate;
        this.timeSlot = timeSlot;
        this.date = date;
        this.status = MissionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public enum MissionStatus {
        PENDING, ACTIVE, COMPLETED, EXPIRED
    }

    public LocalDateTime getTargetedAt() {
        return LocalDateTime.of(date,
                timeSlot.getSlotTime());
    }

    public LocalDateTime getDeadline() {
        return getTargetedAt().plusMinutes(5);
    }

    public void activate() {
        this.status = MissionStatus.ACTIVE;
    }

    public void complete() {
        this.status = MissionStatus.COMPLETED;
    }

    public void expire() {
        this.status = MissionStatus.EXPIRED;
    }

}
