package com.synk.controller;

import com.synk.dto.request.CreateRoomRequest;
import com.synk.dto.request.JoinRoomRequest;
import com.synk.dto.request.UpdateRoomRequest;
import com.synk.dto.response.CreateRoomResponse;
import com.synk.dto.response.InviteResponse;
import com.synk.dto.response.JoinRoomResponse;
import com.synk.dto.response.RoomDetailResponse;
import com.synk.dto.response.RoomMemberResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.synk.dto.response.MyRoomsResponse;


@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>>
    createRoom(@Valid @RequestBody CreateRoomRequest request) {
        CreateRoomResponse response = roomService.createRoom(request);
        return ResponseEntity.ok(ApiResponse.success(response, "방 생성 완료"));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>>
    joinRoom(@RequestBody JoinRoomRequest request) {
        JoinRoomResponse response = roomService.joinRoom(request);
        return ResponseEntity.ok(ApiResponse.success(response, "방 참가 완료"));
    }

    @GetMapping("/{roomId}/invite")
    public ResponseEntity<ApiResponse<InviteResponse>>
    getInviteInfo(@PathVariable Long roomId) {
        InviteResponse response = roomService.getInviteInfo(roomId);
        return ResponseEntity.ok(ApiResponse.success(response, "초대 정보 조회 성공"));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>>
    getRoomDetail(@PathVariable Long roomId) {
        RoomDetailResponse response =
                roomService.getRoomDetail(roomId);
        return ResponseEntity.ok(ApiResponse.success(response, "방 상세 조회 성공"));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> updateRoom(@PathVariable
                                                        Long roomId,
                                                        @RequestBody
                                                        UpdateRoomRequest request) {
        RoomDetailResponse response = roomService.updateRoom(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "방 설정 수정 완료"));
    }

    // 방별 채팅 알림 on/off (종 아이콘). body: {"enabled": true/false}
    @PatchMapping("/{roomId}/chat-alert")
    public ResponseEntity<ApiResponse<Boolean>> updateChatAlert(
            @PathVariable Long roomId,
            @RequestBody java.util.Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        boolean result = roomService.updateChatAlert(roomId, enabled);
        return ResponseEntity.ok(ApiResponse.success(result,
                result ? "채팅 알림 켜짐" : "채팅 알림 꺼짐"));
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(@PathVariable Long roomId) {
        roomService.leaveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("방 나가기 완료"));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Long roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("방 삭제 완료"));
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(@PathVariable Long roomId,
                                                        @PathVariable Long userId) {
        roomService.kickMember(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success("멤버 강퇴 완료"));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>>
    getRoomMembers(@PathVariable Long roomId) {
        List<RoomMemberResponse> response = roomService.getRoomMembers(roomId);
        return ResponseEntity.ok(ApiResponse.success(response, "멤버 목록 조회 성공"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<MyRoomsResponse>>
    getMyRooms() {
        MyRoomsResponse response = roomService.getMyRooms();
        return ResponseEntity.ok(ApiResponse.success(response,
                "방 목록 조회 성공"));
    }

    // 임시 테스트용 - FCM 알림 수동 발송
    @PostMapping("/{roomId}/test-notification")
    public ResponseEntity<ApiResponse<Long>> testNotification(@PathVariable Long roomId) {
        Long missionId = roomService.sendTestNotification(roomId);
        return ResponseEntity.ok(ApiResponse.success(missionId, "테스트 알림 발송 완료"));
    }

}

