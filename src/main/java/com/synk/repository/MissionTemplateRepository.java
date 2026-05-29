//  JpaRepository를 상속하면 save(), findById(), findAll(), delete() 등
//  기본 CRUD가 자동으로 생겨요

package com.synk.repository;

import com.synk.entity.MissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, Long> {
}
