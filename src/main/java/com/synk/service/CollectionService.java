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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRecordRepository collectionRecordRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final UserRepository userRepository;

    private static final int TOTAL_MISSION_COUNT = 90;

    @Transactional(readOnly = true)
    public CollectionResponse getCollections() {
        User user = getUser();

        List<CollectionRecord> records = collectionRecordRepository.findByUser(user);

        Map<MissionTemplate, List<CollectionRecord>>
                groupedByTemplate = records.stream()
                .collect(Collectors.groupingBy(CollectionRecord::getMissionTemplate));

        List<CollectionResponse.MissionSummary> missions =
                groupedByTemplate.entrySet().stream()
                        .map(entry -> {
                            MissionTemplate template = entry.getKey();
                            List<CollectionRecord> templateRecords = entry.getValue();
                            String lastDate = templateRecords.get(0).getDate().toString();
                            String thumbnail = templateRecords.get(0).getThumbnail();

                            return CollectionResponse.MissionSummary.builder()
                                            .missionId(template.getId())
                                            .title(template.getTitle())
                                            .thumbnail(thumbnail)

                                            .completedTimes(templateRecords.size())
                                            .lastCompletedDate(lastDate)
                                            .build();
                        })
                        .toList();

        int completedCount = groupedByTemplate.size();
        int completionRate = (int) ((double) completedCount / TOTAL_MISSION_COUNT * 100);

        return CollectionResponse.builder()
                .completionRate(completionRate)
                .completedCount(completedCount)
                .totalCount(TOTAL_MISSION_COUNT)
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

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}


