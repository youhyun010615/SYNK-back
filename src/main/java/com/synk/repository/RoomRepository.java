// 방 조회용
// findByCode()는 유저가 초대 코드를 입력해서 방에 참여할 때 해당 방을 찾는 데 사용

package com.synk.repository;

import com.synk.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);
}
