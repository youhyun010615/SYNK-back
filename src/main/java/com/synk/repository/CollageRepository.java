package com.synk.repository;

import com.synk.entity.Collage;
import com.synk.entity.Mission;
import com.synk.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollageRepository extends JpaRepository<Collage, Long> {

    Optional<Collage> findByMission(Mission mission);

    List<Collage> findByRoomOrderByCreatedAtDesc(Room room);
}
