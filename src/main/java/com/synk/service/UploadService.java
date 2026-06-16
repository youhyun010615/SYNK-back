package com.synk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".mp4", "video/mp4",
            ".mov", "video/quicktime"
    );

    private static final Map<String, String> FOLDER_MAP = Map.of(
            "profile", "profiles/",
            "room", "rooms/",
            "video", "videos/"
    );

    public Map<String, String> generatePresignedUrl(String filename, String type) {
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf(".")).toLowerCase() : ".mp4";
        String contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        String folder = FOLDER_MAP.getOrDefault(type, "videos/");
        String key = folder + UUID.randomUUID() + ext;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        String fileUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
        return Map.of("presignedUrl", presigned.url().toString(), "fileUrl", fileUrl);
    }
}
