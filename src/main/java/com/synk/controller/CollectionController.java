package com.synk.controller;

import com.synk.dto.response.CollectionDetailResponse;
import com.synk.dto.response.CollectionResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<CollectionResponse>>
    getCollections() {
        CollectionResponse response =
                collectionService.getCollections();
        return
                ResponseEntity.ok(ApiResponse.success(response, "도감 조회 성공"));
    }

    @GetMapping("/missions/{missionId}")
    public
    ResponseEntity<ApiResponse<CollectionDetailResponse>>
    getCollectionDetail(
            @PathVariable Long missionId) {
        CollectionDetailResponse response =
                collectionService.getCollectionDetail(missionId);
        return
                ResponseEntity.ok(ApiResponse.success(response, "미션 상세 조회 성공"));
    }
}
