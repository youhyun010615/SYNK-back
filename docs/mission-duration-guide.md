# 미션 제한 시간 변경 가이드

미션 제한 시간은 현재 **5분**이다. 이 값을 바꿀 때 수정해야 하는 곳을 저장소별로 정리한다.
(조사 기준일: 2026-11-14, 줄 번호는 바뀔 수 있으므로 파일·함수 이름으로 찾는다.)

## 1. 백엔드 (SYNK-back) — 핵심

| 위치 | 내용 |
|---|---|
| `Mission.activate()` | 미션이 발동될 때 `deadline = 지금 + 5분` 으로 저장한다. **실제 마감 시간을 정하는 곳.** |
| `Mission.getDeadline()` | `deadline` 이 비어 있는 예전 데이터용 보조 계산 (`발동 예정 시각 + 5분`). 같은 값으로 맞춘다. |

- 두 곳이 같은 값을 쓰도록 상수 하나로 묶는 것을 권장한다.
- **DB 스키마 변경은 없다.** 마감 시각은 `missions.deadline` 컬럼에 미션마다 저장된다.
- **진행 중인 미션은 영향이 없다.** 배포 시점에 이미 ACTIVE 인 미션은 저장된 마감(예전 값)으로 끝나고, 배포 이후 발동되는 미션부터 새 값이 적용된다.
- 마감 처리(`MissionScheduler.processExpiredMissions`), 남은 시간 계산(`ActiveMissionResponse` 등)은 저장된 `deadline` 을 쓰므로 수정할 필요가 없다.
- 같은 날 미션 사이 최소 간격 `MIN_SLOT_GAP_MINUTES`(30분)보다 짧으면 겹치지 않는다. 제한 시간을 30분 이상으로 늘리려면 이 값도 함께 검토한다.

## 2. 프론트 (SYNK_front)

**동작이 바뀌는 곳 — 반드시 수정**

| 파일 | 내용 |
|---|---|
| `components/mission/CountdownTimer.tsx` | 링 진행률 기준 `total = 300`(초). 안 바꾸면 새 시간에서 링이 처음부터 꽉 차 있다가 뒤늦게 줄어든다. |
| `pages/collage/MissionResultPage.tsx` | `MISSION_MAX_SEC = 5 * 60`. 완료 시간이 이 값에서 잘린다. |
| `sw.ts`, `hooks/useFcm.ts` | 푸시 알림 본문 "…· 5분 안에 찍어 올려요!" (두 파일 모두) |

**화면 문구 — 바꿔야 어색하지 않다**

- `pages/home/HomePage.tsx`, `pages/mission/MissionDetailPage.tsx`: "5분 안에 참여해야 기록돼요"
- `pages/profile/HelpPage.tsx`: 도움말 문구 2곳
- `pages/auth/LoginPage.tsx`: "5분 안에" 알약 문구
- `pages/auth/OnboardingPage.tsx`: "5분의 진심", "5분 안에"

관련 없는 5분이라 바꾸지 않는다: 채팅 묶음 기준(`RoomChatPage`), SYNKLOG 생성 대기 시간(`SynklogCompletePage`), 알림 시간대 슬라이더 단위(`RoomSettingsPage`).

## 3. 합성 서버 (Lambda)

콜라주 합성 코드에는 5분이 없다. SYNKLOG 미션 알림 카드를 추가하는 경우 카드의 "5분 안에 찍어요!" 문구는 함께 바꾼다.

## 4. 문서·홍보 문구

`README.md`, `SYNK_포트폴리오.md`, `docs/개발작업_전체정리.md` 의 "5분 타임어택" 등. 광고 영상과 홍보 문구에도 있다면 함께 확인한다.

## 5. 바꾸기 전에 정할 것

- **브랜드 문구**: "5분의 진심", "5분 타임어택" 은 서비스 정체성이라 시간을 바꾸면 문구도 바꿀지 먼저 정한다.
- **동시성**: 제한 시간이 길수록 "같은 순간을 산다"는 느낌이 옅어질 수 있다. (참고: BeReal 은 2분)
- **콜라주 대기 시간**: 마감이 길어지면 미제출자가 있는 미션의 콜라주는 마감 이후에 만들어지므로 결과가 나오기까지 최대 제한 시간만큼 늦어진다. 전원이 일찍 제출하면 바로 만들어진다.
