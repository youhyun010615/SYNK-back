// 어디서든 CustomException이 던져지면 이 클래스가 잡아서 APiResponse 형태로 응답을 만들어준다.

package com.synk.global.exception;

import com.synk.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<?>> handlerCustomException(CustomException e){
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode.getMessage()));
    }

    // ── 클라이언트가 잘못 보낸 요청은 서버 오류(500)가 아니라 알맞은 4xx 로 응답한다 ──
    // 예상 가능한 오류라 ERROR 스택트레이스 대신 짧은 로그만 남긴다. (외부 봇의 경로 탐색 등으로 로그가 오염되지 않게)

    // 존재하지 않는 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResource(NoResourceFoundException e) {
        log.debug("존재하지 않는 경로 요청: {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("요청한 경로를 찾을 수 없습니다."));
    }

    // 깨진 JSON / 파싱할 수 없는 값
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽을 수 없음: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("요청 형식이 올바르지 않습니다."));
    }

    // 입력값 검증 실패 (@Valid) — DTO 에 지정한 메시지를 그대로 알려준다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        log.warn("입력값 검증 실패: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(message));
    }

    // 허용되지 않은 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("허용되지 않은 메서드: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure("지원하지 않는 요청 방식입니다."));
    }

    // 그 외 예상하지 못한 오류만 서버 오류(500)로 처리한다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("서버 오류 발생: {}", e.getMessage(), e);
        return ResponseEntity
                .status(500)
                .body(ApiResponse.failure("서버 오류가 발생했습니다."));
    }

}
