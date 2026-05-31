// 에러 종류와 HTTP 상태코드, 메시지를 한 곳 에 모아놓은 enum
// 나중에 예외 처리할 때 ErrorCode.ROOM_NOT_FOUND 이런 식으로 깔끔하게 쓸 예정

package com.synk.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    //  에러 이름         HTTP 상태코드 (404)          에러 메시지

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // 유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    // 방
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    ROOM_ALREADY_JOINED(HttpStatus.BAD_REQUEST, "이미 참여 중인 방입니다."),
    ROOM_FULL(HttpStatus.BAD_REQUEST, "방이 가득 찼습니다."),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "방 멤버가 아닙니다."),
    ROOM_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "방장만 가능합니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "존재하지 않는 초대 코드입니다."),

    // 미션
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 미션입니다."),
    MISSION_EXPIRED(HttpStatus.BAD_REQUEST, "이미 종료된 미션입니다."),

    // 제출
    ALREADY_SUBMITTED(HttpStatus.BAD_REQUEST, "이미 제출한 미션입니다."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제출입니다."),

    // SYNKLOG
    SYNKLOG_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 생성된 SYNKLOG입니다."),
    SYNKLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "SYNKLOG가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
