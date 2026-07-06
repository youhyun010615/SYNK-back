// 도감 목록 조회와 미션별 상세 기록 조회 로직

package com.synk.service;

import com.synk.dto.response.CollectionDetailResponse;
import com.synk.dto.response.CollectionResponse;
import com.synk.entity.CollectionRecord;
import com.synk.entity.MissionTemplate;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.CollectionRecordRepository;
import com.synk.repository.MissionTemplateRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRecordRepository collectionRecordRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Transactional(readOnly = true)
    public CollectionResponse getCollections() {
        User user = getUser();

        List<MissionTemplate> allTemplates = missionTemplateRepository.findByDescriptionNot("튜토리얼");
        List<CollectionRecord> records = collectionRecordRepository.findByUser(user);

        Map<Long, List<CollectionRecord>> recordsByTemplateId = records.stream()
                .collect(Collectors.groupingBy(r -> r.getMissionTemplate().getId()));

        List<CollectionResponse.MissionSummary> missions = allTemplates.stream()
                .map(template -> {
                    List<CollectionRecord> templateRecords = recordsByTemplateId.get(template.getId());
                    if (templateRecords != null && !templateRecords.isEmpty()) {
                        return CollectionResponse.MissionSummary.builder()
                                .missionId(template.getId())
                                .title(template.getTitle())
                                .category(template.getDescription())
                                .thumbnail(templateRecords.get(0).getThumbnail())
                                .completedTimes(templateRecords.size())
                                .lastCompletedDate(templateRecords.get(0).getDate().format(DATE_FORMAT))
                                .build();
                    } else {
                        return CollectionResponse.MissionSummary.builder()
                                .missionId(template.getId())
                                .title(template.getTitle())
                                .category(template.getDescription())
                                .thumbnail(null)
                                .completedTimes(0)
                                .lastCompletedDate(null)
                                .build();
                    }
                })
                .toList();

        int totalCount = allTemplates.size();
        int completedCount = recordsByTemplateId.size();
        int completionRate = totalCount > 0 ? (int) ((double) completedCount / totalCount * 100) : 0;

        return CollectionResponse.builder()
                .completionRate(completionRate)
                .completedCount(completedCount)
                .totalCount(totalCount)
                .missions(missions)
                .build();
    }

    @Transactional(readOnly = true)
    public CollectionDetailResponse getCollectionDetail(Long missionId) {
        User user = getUser();

        MissionTemplate template = missionTemplateRepository.findById(missionId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        List<CollectionRecord> records = collectionRecordRepository
                        .findByUserAndMissionTemplate(user, template);

        return CollectionDetailResponse.from(template, records);
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse.MissionSummary> getMissionCatalog() {
        User user = getUser();

        List<MissionTemplate> allTemplates = missionTemplateRepository.findByDescriptionNot("튜토리얼");
        List<CollectionRecord> records = collectionRecordRepository.findByUser(user);

        java.util.Set<Long> completedTemplateIds = records.stream()
                .map(r -> r.getMissionTemplate().getId())
                .collect(java.util.stream.Collectors.toSet());

        return allTemplates.stream()
                .filter(t -> !completedTemplateIds.contains(t.getId()))
                .map(t -> CollectionResponse.MissionSummary.builder()
                        .missionId(t.getId())
                        .title(t.getTitle())
                        .thumbnail(null)
                        .completedTimes(0)
                        .lastCompletedDate(null)
                        .build())
                .toList();
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}


