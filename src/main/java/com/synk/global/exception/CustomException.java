// ErrorCode를 담아서 던지는 커스텀 예의 클래스
// 서비스 로직에서 문제가 생기면 이걸 던지고, 나중에 만들 GlobalExceptionHandler가 잡아서 응답으로 변환

package com.synk.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException{
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
