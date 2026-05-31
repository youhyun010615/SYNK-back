// 로그인/회원가입 성공시 클라이언트에게 돌려주는 응답 형식
// JWT 토큰과 유저 정보를 담는다.

package com.synk.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String token;
    private boolean isNewUser;
    private Long userId;
    private String name;
    private String profileImage;
}
