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

    // 미션 풀 사용 여부. 교체된 옛 미션은 지우지 않고(과거 미션·도감 기록이 참조) false로 보관한다.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Builder
    public MissionTemplate(String title, String description){
        this.title = title;
        this.description = description;
    }

    // 미션 풀에서 제외 (과거 기록 보존을 위해 삭제 대신 비활성화)
    public void deactivate() {
        this.active = false;
    }
}
