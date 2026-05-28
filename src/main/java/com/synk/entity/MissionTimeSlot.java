// 06:00 ~23:30 사이 30분 간격으로 나뉜 36개의 고정 시간 슬롯을 저장하는 테이블
// missions 테이블이 이걸 참조해서 "이 미션은 14:30에 발동됐다"를 표현

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "mission_time_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class MissionTimeSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_time", nullable = false, unique
            = true)
    private LocalTime slotTime;

    @Builder
    public MissionTimeSlot(LocalTime slotTime) {
        this.slotTime = slotTime;
    }

}
