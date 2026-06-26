package com.synk.repository;

import com.synk.entity.Room;
import com.synk.entity.RoomBan;
import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBanRepository extends JpaRepository<RoomBan, Long> {

    boolean existsByRoomAndUser(Room room, User user);

    void deleteAllByRoom(Room room);
}
