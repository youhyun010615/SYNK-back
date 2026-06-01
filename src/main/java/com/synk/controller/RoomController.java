package com.synk.controller;

import com.synk.dto.request.CreateRoomRequest;
import com.synk.dto.request.JoinRoomRequest;
import com.synk.dto.request.UpdateRoomRequest;
import com.synk.dto.response.CreateRoomResponse;
import com.synk.dto.response.InviteResponse;
import com.synk.dto.response.JoinRoomResponse;
import com.synk.dto.response.RoomDetailResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.synk.dto.response.MyRoomsResponse;


@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>>
    createRoom(@RequestBody CreateRoomRequest request) {
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
    public ResponseEntity<ApiResponse<Void>> updateRoom(@PathVariable
                                                        Long roomId,
                                                        @RequestBody
                                                        UpdateRoomRequest request) {
        roomService.updateRoom(roomId, request);
        return ResponseEntity.ok(ApiResponse.success("방 설정 수정 완료"));
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(@PathVariable
                                                       Long roomId) {
        roomService.leaveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("방 나가기 완료"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<MyRoomsResponse>>
    getMyRooms() {
        MyRoomsResponse response = roomService.getMyRooms();
        return ResponseEntity.ok(ApiResponse.success(response,
                "방 목록 조회 성공"));
    }

}

