// 채팅 리액션 조회
// findByChat() : 특정 메시지에 달린 모든 이모지 래액션을 가져온다. 채팅 목록 조회 시 각 메시지와
//                리액션 표시할 때 사용

package com.synk.repository;

import com.synk.entity.ChatReaction;
import com.synk.entity.RoomChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatReactionRepository extends
        JpaRepository<ChatReaction, Long> {

    List<ChatReaction> findByChat(RoomChat chat);
}
