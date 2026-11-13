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

        // 도감 목록에는 튜토리얼도 포함(전용 카테고리로 노출).
        // 교체되어 비활성화된 옛 미션은 모은 기록이 있어도 도감에서 숨긴다 (현재 미션만 노출).
        List<CollectionRecord> records = collectionRecordRepository.findByUser(user);

        Map<Long, List<CollectionRecord>> recordsByTemplateId = records.stream()
                .collect(Collectors.groupingBy(r -> r.getMissionTemplate().getId()));

        // 템플릿별 가장 최근 완료일 (정렬용)
        Map<Long, java.time.LocalDate> latestCompletedByTemplateId = records.stream()
                .collect(Collectors.toMap(
                        r -> r.getMissionTemplate().getId(),
                        CollectionRecord::getDate,
                        (a, b) -> a.isAfter(b) ? a : b));

        List<MissionTemplate> allTemplates = orderForDisplay(
                missionTemplateRepository.findAll().stream()
                        .filter(MissionTemplate::isActive)
                        .toList(),
                latestCompletedByTemplateId);

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

        // 수집률: 도감에 보이는 현재 미션(활성, 튜토리얼 포함) 기준
        int totalCount = allTemplates.size();
        int completedCount = (int) allTemplates.stream()
                .filter(t -> recordsByTemplateId.containsKey(t.getId()))
                .count();
        int completionRate = totalCount > 0 ? (int) ((double) completedCount / totalCount * 100) : 0;

        return CollectionResponse.builder()
                .completionRate(completionRate)
                .completedCount(completedCount)
                .totalCount(totalCount)
                .missions(missions)
                .build();
    }

    /**
     * 도감 목록 정렬.
     * - 카테고리 순서: 미션 id 기준 처음 등장한 순서를 유지한다. (프론트가 카테고리 탭과 색을 이 순서로 정하므로 바뀌면 안 됨)
     * - 같은 카테고리 안: 완료한 미션을 위로(가장 최근에 완료한 것이 맨 위), 그다음 미완료 미션을 id 순으로.
     */
    static List<MissionTemplate> orderForDisplay(List<MissionTemplate> templates,
                                                 Map<Long, java.time.LocalDate> latestCompletedByTemplateId) {
        Map<String, Long> categoryFirstId = new java.util.HashMap<>();
        templates.stream()
                .sorted(java.util.Comparator.comparing(MissionTemplate::getId))
                .forEach(t -> categoryFirstId.putIfAbsent(String.valueOf(t.getDescription()), t.getId()));

        return templates.stream()
                .sorted(java.util.Comparator
                        .comparing((MissionTemplate t) -> categoryFirstId.get(String.valueOf(t.getDescription())))
                        // 완료일이 있는 것(완료) 먼저, 최근 완료일이 위 / 완료일이 없는 것(미완료)은 뒤로
                        .thenComparing(t -> latestCompletedByTemplateId.get(t.getId()),
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(MissionTemplate::getId))
                .toList();
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

        List<MissionTemplate> allTemplates = missionTemplateRepository.findByActiveTrueAndDescriptionNot("튜토리얼");
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


