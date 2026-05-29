package com.synk.repository;

import com.synk.entity.MissionTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.Optional;

public interface MissionTimeSlotRepository extends JpaRepository<MissionTimeSlot, Long> {
    Optional<MissionTimeSlot> findBySlotTime(LocalTime slotTime);
}
