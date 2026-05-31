// 개인 도감 조회
// findByUser() : 내 도감 전체 목록 조회
// findByUserAndMissionTemplate() : 특정 미션의 내 기록 목록 조회 (도감 상세)
// countByUserAndMissionTemplate() : 특정 미션을 몇 번 완료했는 지 횟수 조회

package com.synk.repository;

import com.synk.entity.CollectionRecord;
import com.synk.entity.MissionTemplate;
import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRecordRepository extends
        JpaRepository<CollectionRecord, Long> {

    List<CollectionRecord> findByUser(User user);

    List<CollectionRecord> findByUserAndMissionTemplate(User user, MissionTemplate missionTemplate);

    int countByUserAndMissionTemplate(User user, MissionTemplate missionTemplate);
}
