// 알림 목록 조회
// findByUserOrderByCreatedAtDesc() : 내 알림을 최신순으로 가져온다.
// API에서 오늘/이번 주 구분은 Service 레이어에서 처리

package com.synk.repository;

import com.synk.entity.Notification;
import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends
        JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);
}
