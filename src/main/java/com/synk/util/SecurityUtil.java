// 현재 로그인한 유저의 ID를 꺼내는 유틸 클래스.
// 나중에 서비스에서 지금 요청한 사람이 누구인지 알아야 할 때 매번 같은 코드 반복 안 하고 이걸 호출

package com.synk.util;

import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil() {}

    public static Long getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(authentication.getName());
    }
}
