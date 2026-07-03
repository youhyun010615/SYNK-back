package com.synk.repository;

import com.synk.entity.Collage;
import com.synk.entity.Mission;
import com.synk.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CollageRepository extends JpaRepository<Collage, Long> {

    Optional<Collage> findByMission(Mission mission);

    List<Collage> findByRoomOrderByCreatedAtDesc(Room room);

    // 명시적 JPQL: Spring Data 파생 쿼리 대신 직접 JOIN+date 필터 지정 (타임존 혼선 방지)
    @Query("SELECT c FROM Collage c WHERE c.room = :room AND c.mission.date = :date")
    List<Collage> findByRoomAndMission_Date(@Param("room") Room room, @Param("date") LocalDate date);

    void deleteAllByRoom(Room room);
}
