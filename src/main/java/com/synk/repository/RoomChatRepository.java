// 채팅 메시지 조회
// findByRoomOrderByCreatedAtAsc() : 채팅방 입장 시 메시지를 오래된 순(위 -> 아래)으로 가져온다.

package com.synk.repository;

import com.synk.entity.Room;
import com.synk.entity.RoomChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomChatRepository extends
        JpaRepository<RoomChat, Long> {

    List<RoomChat> findByRoomOrderByCreatedAtAsc(Room room);
}
