// SYNKlogRepository 조회
// findByRoomAndDate() : 특정 날짜에 이미 SYNKLOG가 생성됐는지 확인 (중복 생성 방지)
// findByRoomOrderByDateDesc() : 방 앨범 목록에서 날짜 최신순으로 SYNKLLOG 목록 조회

package com.synk.repository;

import com.synk.entity.Room;
import com.synk.entity.Synklog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SynklogRepository extends JpaRepository<Synklog,
        Long> {

    Optional<Synklog> findByRoomAndDate(Room room, LocalDate
            date);

    List<Synklog> findByRoomOrderByDateDesc(Room room);
}
