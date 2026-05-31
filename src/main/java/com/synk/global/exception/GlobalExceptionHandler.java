// 어디서든 CustomException이 던져지면 이 클래스가 잡아서 APiResponse 형태로 응답을 만들어준다.

package com.synk.global.exception;

import com.synk.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public ResponseEntity<ApiResponse<?>> handlerCustomException(CustomException e){
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        return ResponseEntity
                .status(500)
                .body(ApiResponse.failure("서버 오류가 발생했습니다."));
    }

}
