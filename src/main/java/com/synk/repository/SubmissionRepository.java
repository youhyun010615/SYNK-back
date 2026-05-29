// 미션 제출 관련 조회
// findByMission() : 미션의 전체 제출 목록 (콜라주 생성, 참여 현황 조회 시 사용)
// findByMissionAndUser() : 특정 유저가 해당 미션에 제출했는지 확인
// existsByMissionAndUser() : 중복 제출 방지

package com.synk.repository;

import com.synk.entity.Mission;
import com.synk.entity.Submission;
import com.synk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByMission(Mission mission);

    Optional<Submission> findByMissionAndUser(Mission mission, User user);

    boolean existsByMissionAndUser(Mission mission, User user);
}
