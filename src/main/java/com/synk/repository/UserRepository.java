
// OAuth 로그인할 때 "이미 가입한 유저인지" 확인하는 게 핵심
// findByAuthProviderAndAuthProviderId() 로 카카오/구글 ID로 유저를 조회

package com.synk.repository;

import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAuthProviderAndAuthProviderId(
            User.AuthProvider authProvider, String authProviderId);
}
