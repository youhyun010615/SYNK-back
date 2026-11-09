// 미션 풀 교체 (일회성, 멱등)
//
// - 새 미션 목록(7개 카테고리, 100개)을 mission_templates 에 추가한다.
// - 기존 일반 미션은 삭제하지 않고 active=false 로 비활성화한다.
//   (과거 미션 822건/도감 기록이 FK 로 참조하고 있어 삭제하면 기록이 깨진다)
// - 튜토리얼(description='튜토리얼') 미션은 건드리지 않는다.
// - 새 카테고리 미션이 이미 하나라도 있으면 아무것도 하지 않는다 (재시작해도 중복 삽입 없음).
// - 한 트랜잭션이므로 중간에 실패하면 전부 롤백된다.
// - 미션 제목은 이모지를 뺀 텍스트로 저장한다 (콜라주 영상에 제목을 그릴 때 한글 폰트가 이모지를 못 그림).

package com.synk.config;

import com.synk.entity.MissionTemplate;
import com.synk.repository.MissionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(2) // DataInitializer(@Order(1)) 다음에 실행
@RequiredArgsConstructor
public class MissionTemplateMigration implements ApplicationRunner {

    private static final String TUTORIAL = "튜토리얼";

    private final MissionTemplateRepository missionTemplateRepository;

    // 카테고리(=description) → 미션 제목 목록. 순서를 유지해야 id 가 이 순서대로 부여된다.
    private static final Map<String, List<String>> NEW_MISSIONS = new LinkedHashMap<>();

    static {
        NEW_MISSIONS.put("표정·리액션", List.of(
                "무표정으로 버티다가 마지막에 웃기",
                "충격적인 소식 들은 표정 짓기",
                "뭔가 알고 있는 표정 짓기",
                "안 웃긴데 웃어야 하는 표정 짓기",
                "\"뭐라고?\" 하는 표정 짓기",
                "잔소리 듣는 표정 짓기",
                "아무 잘못도 없는 척하기",
                "세상에서 제일 억울한 표정 짓기",
                "카메라 보자마자 멋있는 척하기",
                "웃음 참다가 결국 실패하기",
                "모든 걸 포기한 표정 짓기",
                "의심스러운 눈빛 보내기",
                "화난 척하다가 바로 풀리기",
                "엄청 반가운 사람을 만난 표정 짓기",
                "카메라 발견하고 그대로 얼어붙기"));

        NEW_MISSIONS.put("카메라 가지고 놀기", List.of(
                "카메라 가렸다가 다른 표정으로 등장하기",
                "눈만 보여주다가 얼굴 전체 공개하기",
                "손가락 튕기고 표정 바꾸기",
                "화면 밖으로 나갔다가 다시 등장하기",
                "카메라에 가까이 왔다가 빠르게 멀어지기",
                "카메라와 하이파이브하기",
                "카메라를 손바닥 위에 올린 척하기",
                "카메라를 몰래 훔쳐보기",
                "카메라에 바람불기",
                "주먹으로 화면 가렸다가 다시 열기",
                "고개 돌렸다가 다른 표정으로 돌아오기",
                "화면 아래로 천천히 사라지기",
                "카메라 발견하고 손가락으로 가리키기",
                "카메라에 끌려가는 척 다가가기",
                "두 손으로 얼굴 가렸다가 깜짝 공개하기"));

        NEW_MISSIONS.put("5초 상황극", List.of(
                "통장 잔액 확인한 척하기",
                "갖고 싶던 선물 받은 척하기",
                "늦잠 잔 걸 깨달은 척하기",
                "상 받은 척 소감 말하기",
                "귀신 본 척하기",
                "눈앞에서 버스 놓친 척하기",
                "전화 잘못 건 척하기",
                "갑자기 엄청 추워진 척하기",
                "뜨거운 걸 만진 척하기",
                "무대 위 가수가 된 척하기",
                "좀비가 되어가는 척하기",
                "비밀 임무 전달받은 척하기",
                "충격적인 장면 본 척하기",
                "예상보다 비싼 가격 본 척하기",
                "깜짝 파티 받은 척하기"));

        NEW_MISSIONS.put("일상 상황 연기", List.of(
                "누가 내 이름을 부른 것처럼 뒤돌아보기",
                "거울 속 내 모습에 감탄하는 척하기",
                "비밀을 알고 있지만 아무렇지 않은 척하기",
                "카메라 피하다가 몰래 렌즈 쳐다보기",
                "기다리던 답장이 온 척하기",
                "갑자기 좋은 생각이 떠오른 척하기",
                "말실수한 걸 뒤늦게 깨닫기",
                "졸다가 누가 오는 소리에 정신 차리기",
                "좋아하는 노래가 나온 척 반응하기",
                "누군가 갑자기 문을 연 척하기",
                "알람 끄고 다시 자려는 척하기",
                "맛있는 냄새가 나는 쪽 쳐다보기",
                "사진 찍히는 걸 뒤늦게 발견하기",
                "아는 사람 발견하고 모른 척 지나가기",
                "할 일을 깜빡한 게 갑자기 생각난 척하기"));

        NEW_MISSIONS.put("몸으로 하기", List.of(
                "5초 동안 로봇 되기",
                "상체만으로 춤추기",
                "고양이처럼 카메라 보기",
                "히어로 포즈 잡기",
                "혼자 파도타기",
                "슬로모션으로 등장하기",
                "카메라 향해 허공 복싱하기",
                "모델처럼 화면 앞 지나가기",
                "세상 비장하게 경례하기",
                "엄청 바쁜 사람처럼 지나가기"));

        NEW_MISSIONS.put("후면 카메라 미션", List.of(
                "지금 있는 곳 한 바퀴 보여주기",
                "바닥에서 시작해 위쪽까지 올려 찍기",
                "위에서 시작해 바닥까지 내려 찍기",
                "가까운 곳에서 먼 곳까지 천천히 보여주기",
                "같은 색 두 개 찾아 이어서 찍기",
                "가장 눈에 띄는 색 찾아 가까이 가기",
                "주변에서 동그란 모양 찾아 보여주기",
                "가장 긴 물건 따라 카메라 움직이기",
                "가장 반짝이는 곳 찾아 찍기",
                "어두운 곳에서 밝은 곳으로 카메라 옮기기",
                "작은 부분부터 전체 모습 공개하기",
                "주변을 거꾸로 보이게 찍기",
                "바닥 가까이에서 주변 올려다보기",
                "높은 곳에서 주변 내려다보기",
                "평범한 공간을 영화 오프닝처럼 찍기",
                "모서리 뒤를 살펴보듯 천천히 찍기",
                "주변 세 곳 빠르게 이어서 보여주기",
                "멀리 있는 한 곳을 골라 천천히 확대하기",
                "카메라를 천천히 반 바퀴 돌려보기",
                "바닥부터 시작해서 지금 있는 공간 공개하기"));

        NEW_MISSIONS.put("다 같이 하면 재밌는 미션", List.of(
                "눈치 보지 말고 동시에 가위바위보 내기",
                "눈 감고 아무나 가리킨 뒤 동시에 눈뜨기",
                "서로 안 보고 같은 포즈 도전하기",
                "카메라를 가리키며 차례로 \"너?\" 표정 짓기",
                "빠르게 모였다가 다 같이 천천히 흩어지기",
                "동시에 카메라 말고 다른 곳 쳐다보기",
                "다 같이 화면 밖으로 숨었다가 튀어나오기",
                "자유롭게 움직이다가 동시에 얼음 되기",
                "세상에서 가장 어색한 단체사진 찍기",
                "서로 쳐다보면서 웃음 참기"));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 이미 새 미션이 들어가 있으면 이미 교체된 것 → 아무것도 안 함
        if (missionTemplateRepository.existsByDescriptionIn(NEW_MISSIONS.keySet())) {
            return;
        }

        // 1) 기존 일반 미션 비활성화 (튜토리얼은 유지)
        int deactivated = 0;
        for (MissionTemplate t : missionTemplateRepository.findAll()) {
            if (TUTORIAL.equals(t.getDescription())) continue;
            if (!t.isActive()) continue;
            t.deactivate();
            deactivated++;
        }

        // 2) 새 미션 추가
        int inserted = 0;
        for (Map.Entry<String, List<String>> category : NEW_MISSIONS.entrySet()) {
            for (String title : category.getValue()) {
                missionTemplateRepository.save(MissionTemplate.builder()
                        .title(title)
                        .description(category.getKey())
                        .build());
                inserted++;
            }
        }

        log.info("미션 풀 교체 완료: 기존 {}개 비활성화, 새 미션 {}개 추가 (튜토리얼 유지)", deactivated, inserted);
    }
}
