//  JpaRepository를 상속하면 save(), findById(), findAll(), delete() 등
//  기본 CRUD가 자동으로 생겨요

package com.synk.repository;

import com.synk.entity.MissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {

    // 튜토리얼 미션 템플릿 (description = '튜토리얼')
    List<MissionTemplate> findByDescription(String description);

    // 일반 미션 풀 (튜토리얼 제외)
    List<MissionTemplate> findByDescriptionNot(String description);
}
