// 카카오/구글 로그인 시 클라이언트가 보내는 요청 형식
// 앱에서 OAuth 로그인 후 받은 액세스 토큰을 여기 담아서 보낸다.

package com.synk.dto.request;

import lombok.Getter;

@Getter
public class AuthRequest {
    private String accessToken;
}
