package com.synk.repository;

import com.synk.entity.User;
import com.synk.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, Long> {

    List<UserFcmToken> findByUser(User user);

    Optional<UserFcmToken> findByToken(String token);

    void deleteByToken(String token);
}
