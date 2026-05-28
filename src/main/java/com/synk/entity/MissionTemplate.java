// 미션들을 모아둔 파일

package com.synk.entity;

import com.synk.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mission_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @Builder
    public MissionTemplate(String title, String description){
        this.title = title;
        this.description = description;
    }
}
