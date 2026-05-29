// 방 조회용
// findByCode() : 유저가 초대 코드를 입력해서 방에 참여할 때 해당 방을 찾는 데 사용
// existBuyUserAndRoom() :  이미 참여 중인지 확인
// countByRoom() : 현재 방 인원 수 (current_members 대신 사용)

package com.synk.repository;

import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    List<RoomMember> findByUser(User user);

    List<RoomMember> findByRoom(Room room);

    Optional<RoomMember> findByUserAndRoom(User user, Room room);

    boolean existsByUserAndRoom(User user, Room room);

    int countByRoom(Room room);


}
