// Spring Boot 앱 시작 시 자동으로 초기 데이터를 넣는다.

package com.synk.config;

import com.synk.entity.MissionTemplate;
import com.synk.entity.MissionTimeSlot;
import com.synk.repository.MissionTemplateRepository;
import com.synk.repository.MissionTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final MissionTimeSlotRepository missionTimeSlotRepository;
    private final MissionTemplateRepository missionTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initTimeSlots();
        initMissionTemplates();
    }

    private void initTimeSlots() {
        if (missionTimeSlotRepository.count() > 0) return;

        LocalTime time = LocalTime.of(0, 0);
        int count = 0;
        while (count < 48) {
            missionTimeSlotRepository.save(MissionTimeSlot.builder()
                    .slotTime(time)
                    .build());
            time = time.plusMinutes(30);
            count++;
        }
        log.info("mission_time_slots 48개 초기화 완료");
    }

    private void initMissionTemplates() {
        if (missionTemplateRepository.count() > 0) return;

        List<String> titles = List.of(
                "지금 내 표정 그대로 찍기",
                "지금 손에 있는 것 찍기",
                "발밑 찍기",
                "하늘 찍기",
                "지금 창문 밖 찍기",
                "냉장고 열어서 찍기",
                "지금 신발 찍기",
                "지금 책상 위 찍기",
                "지금 가장 가까운 물건 찍기",
                "지금 앉아 있는 곳 찍기",
                "지금 입고 있는 옷 찍기",
                "지금 보고 있는 화면 찍기",
                "지금 마시고 있는 것 찍기",
                "지금 먹고 있는 것 찍기",
                "지금 손가락 찍기",
                "지금 귀 찍기",
                "지금 코 찍기",
                "지금 눈 찍기",
                "지금 머리 찍기",
                "지금 가방 속 찍기",
                "지금 주머니 속 찍기",
                "지금 방 전체 찍기",
                "지금 침대 찍기",
                "지금 베개 찍기",
                "지금 천장 찍기",
                "지금 바닥 찍기",
                "지금 벽 찍기",
                "지금 문 찍기",
                "지금 창문 찍기",
                "지금 조명 찍기",
                "지금 시계 찍기",
                "지금 거울 속 내 모습 찍기",
                "지금 내 오른손 찍기",
                "지금 내 왼손 찍기",
                "지금 내 발 찍기",
                "지금 내 무릎 찍기",
                "지금 내 팔꿈치 찍기",
                "지금 내 어깨 찍기",
                "지금 내 배꼽 찍기",
                "지금 내 뒷모습 찍기",
                "지금 내 옆모습 찍기",
                "지금 그림자 찍기",
                "지금 반사된 내 모습 찍기",
                "지금 가장 가까운 창문 찍기",
                "지금 가장 오래된 물건 찍기",
                "지금 가장 새것 찍기",
                "지금 가장 큰 물건 찍기",
                "지금 가장 작은 물건 찍기",
                "지금 가장 좋아하는 물건 찍기",
                "지금 가장 싫어하는 물건 찍기",
                "지금 가장 비싼 물건 찍기",
                "지금 가장 싼 물건 찍기",
                "지금 가장 무거운 물건 찍기",
                "지금 가장 가벼운 물건 찍기",
                "지금 가장 긴 물건 찍기",
                "지금 가장 짧은 물건 찍기",
                "지금 빨간 물건 찍기",
                "지금 파란 물건 찍기",
                "지금 노란 물건 찍기",
                "지금 초록 물건 찍기",
                "지금 흰 물건 찍기",
                "지금 검은 물건 찍기",
                "지금 분홍 물건 찍기",
                "지금 주황 물건 찍기",
                "지금 보라 물건 찍기",
                "지금 둥근 물건 찍기",
                "지금 네모 물건 찍기",
                "지금 세모 물건 찍기",
                "지금 투명한 물건 찍기",
                "지금 빛나는 물건 찍기",
                "지금 부드러운 물건 찍기",
                "지금 딱딱한 물건 찍기",
                "지금 뜨거운 물건 찍기",
                "지금 차가운 물건 찍기",
                "지금 책 찍기",
                "지금 펜 찍기",
                "지금 전자기기 찍기",
                "지금 충전기 찍기",
                "지금 이어폰 찍기",
                "지금 지갑 찍기",
                "지금 열쇠 찍기",
                "지금 안경 찍기",
                "지금 모자 찍기",
                "지금 가장 가까운 의자 찍기",
                "지금 가장 가까운 테이블 찍기",
                "지금 식물 찍기",
                "지금 물 찍기",
                "지금 음식 찍기",
                "지금 음료 찍기",
                "지금 약 찍기",
                "지금 휴지 찍기",
                "지금 쓰레기통 찍기",
                "지금 가방 찍기"
        );

        for (String title : titles) {

            missionTemplateRepository.save(MissionTemplate.builder()
                    .title(title)
                    .description("카메라 앞, 있는 그대로!")
                    .build());
        }
        log.info("mission_templates 90개 초기화 완료");
    }
}
