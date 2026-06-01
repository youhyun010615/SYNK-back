package com.synk.controller;

import com.synk.dto.response.AlbumResponse;
import com.synk.dto.response.SynklogResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms/{roomId}/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlbumResponse>>>
    getAlbums(@PathVariable Long roomId) {
        List<AlbumResponse> response = albumService.getAlbums(roomId);
        return ResponseEntity.ok(ApiResponse.success(response, "앨범 목록 조회 성공"));
    }

    @PostMapping("/{date}/synklog")
    public ResponseEntity<ApiResponse<SynklogResponse>>
    createSynklog(@PathVariable Long roomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SynklogResponse response = albumService.createSynklog(roomId, date);
        return ResponseEntity.ok(ApiResponse.success(response, "SYNKLOG 생성 요청 완료"));
    }

    @GetMapping("/{date}/synklog")
    public ResponseEntity<ApiResponse<SynklogResponse>>
    getSynklog(@PathVariable Long roomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SynklogResponse response = albumService.getSynklog(roomId, date);
        return ResponseEntity.ok(ApiResponse.success(response, "SYNKLOG 조회 성공"));
    }
}
