// 미션 조회
// findByRoomAndDate() : 특정 방의 오늘 미션 목록 조회
// findByStatus() : 스케줄러가 ACTIVE 상태 미션을 찾을 때 사용
// findByRoomAndStatus : 특정 방의 진행 중인 미션 조회

package com.synk.repository;

import com.synk.entity.Mission;
import com.synk.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission,
        Long> {

    List<Mission> findByRoomAndDate(Room room, LocalDate date);

    List<Mission> findByStatus(Mission.MissionStatus status);

    List<Mission> findByRoomAndStatus(Room room, Mission.MissionStatus status);
}
