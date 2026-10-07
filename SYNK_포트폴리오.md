# SYNK — 랜덤 미션 숏폼 콜라주 SNS

> "우리는 지금, 같은 순간을 산다."
> 예고 없는 랜덤 알림으로 친구 그룹이 동시에 짧은 영상 미션을 수행하고,
> 전원의 순간을 하나의 콜라주 영상으로 자동 합성·보관하는 실시간 동기화 SNS.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 서비스명 | SYNK |
| 한 줄 소개 | 랜덤 사이렌이 울리면 5분 안에 지금 그 순간을 찍어, 친구들과 하나의 콜라주로 합쳐지는 앱 |
| 프로젝트 기간 | 2026.05.27 ~ 2026.07.19 (약 8주, 이후 테스트 배포 운영) |
| 팀 구성 | 2인 (KB IT's Your Life 26회차 권유현 · 이아영) |
| 플랫폼 | PWA (iOS Safari / Android Chrome "홈 화면에 추가" 설치형) |
| 깃허브(BE) | https://github.com/youhyun010615/SYNK-back |
| 깃허브(FE) | https://github.com/Ahyoung00/SYNK_front |
| 배포 URL | https://synk-front.vercel.app (API: https://api.synk.ai.kr) |
| 운영 상태 | KB IT's Your Life 교육생 대상 실사용 테스트 배포 완료 |

---

## 2. 핵심 기능 (서비스 플로우)

1. **랜덤 사이렌** — 하루 1~5회, 예고 없이 방 멤버 전원에게 동시 푸시 알림(FCM)
2. **5분 타임어택** — 알림 확인 후 5분 이내 3~5초 영상 미션 제출 (꾸밀 시간 없이 '지금 이 순간'을 찍는 것이 콘셉트)
3. **Group Collage** — 전원 제출 또는 제한 시간 종료 시, 멤버 영상을 1~10명 대응 분할 화면 콜라주로 자동 합성 (AWS Lambda + FFmpeg)
4. **SYNKLOG** — 당일 생성된 콜라주들을 이어붙인 9:16 일별 릴스 영상 (방 단위 공유)
5. **도감(Collection)** — 완료한 미션이 개인 도감에 자동 수집, 카테고리별 수집률 제공
6. **실시간 채팅** — 방별 WebSocket(STOMP) 채팅 + 꺼진 화면에도 오는 푸시 알림
7. **튜토리얼 온보딩** — 방이 꽉 차는 순간 +15분/+1시간에 고정 튜토리얼 미션 2개 자동 발동 (당일 바로 체험 가능)

---

## 3. 기술 스택

### 백엔드
- **Spring Boot (Java 17)**, Spring Security, Spring WebMVC, **Spring WebSocket(STOMP)**
- **JPA / Hibernate**, **PostgreSQL (Supabase)**
- **JWT 인증** (jjwt, Access/Refresh Token) + 카카오·구글 OAuth2 소셜 로그인
- **AWS SDK v2** (S3, Lambda 비동기 호출)
- **Firebase Admin SDK** (FCM 푸시, APNs 설정 포함)
- SpringDoc OpenAPI (Swagger)

### 프론트엔드
- **React 18 + TypeScript + Vite**
- **PWA** (vite-plugin-pwa, Workbox, injectManifest 커스텀 Service Worker)
- Zustand(상태관리), React Router, Axios
- **@stomp/stompjs + SockJS** (실시간 채팅)
- **Firebase Cloud Messaging** (웹 푸시)
- 배포: Vercel (main push 시 자동 배포)

### 영상 처리 / 인프라
- **AWS Lambda (Python 3.11/3.12) + FFmpeg** — 콜라주/SYNKLOG 영상 합성, Pillow로 오버레이 렌더링
- **AWS S3** (`synk-videos`, ap-northeast-2) — 영상/썸네일 저장
- **AWS EC2 (Ubuntu) + Docker** — 백엔드 호스팅
- **GitHub Actions** — BE main push 시 Docker 빌드·배포 자동화
- 도메인/HTTPS: `api.synk.ai.kr` (SSL)

---

## 4. 시스템 아키텍처

```
┌──────────────┐     HTTPS/REST + WebSocket(STOMP)    ┌─────────────────────┐
│  PWA (React) │ ───────────────────────────────────▶ │ Spring Boot (EC2)   │
│  Vercel      │ ◀─── FCM 푸시 ──┐                     │  + Docker           │
└──────────────┘                │                     └─────────┬───────────┘
      ▲                         │                               │ JPA
      │ 웹푸시                   │                     ┌─────────▼───────────┐
┌─────┴────────┐          ┌─────┴──────┐              │ PostgreSQL(Supabase)│
│ Firebase FCM │◀─ Admin ─│  FcmService │              └─────────────────────┘
└──────────────┘   SDK    └────────────┘
                                                    비동기 Invoke
      ┌──────────────────────────────────────────────────┐
      │ AWS Lambda (Python + FFmpeg)                      │
      │  synk-collage  : 멤버 영상 → 분할 콜라주 합성       │
      │  synk-synklog  : 당일 콜라주 → 9:16 릴스 합성       │
      └──────────────┬───────────────────────────────────┘
                     │ S3 저장 후 콜백(POST)
                     ▼
              ┌──────────────┐        ┌──────────────┐
              │   AWS S3     │        │ 콜백 → 상태   │
              │ synk-videos  │        │ COMPLETED 갱신│
              └──────────────┘        └──────────────┘
```

**콜라주 합성 비동기 파이프라인**
```
미션 만료/전원제출
  → CollageService가 submissions[] + callbackUrl 구성
  → Lambda 비동기 Invoke(EVENT) — 응답 안 기다림
  → Lambda: S3 영상 다운 → FFmpeg filter_complex 합성 → S3 업로드
  → 백엔드 콜백 URL로 결과 POST → Collage 상태 PROCESSING→COMPLETED
```

---

## 5. 데이터베이스 (ERD 요약)

**15개 테이블** 주요 구조:

- **users** — 소셜 로그인(auth_provider: kakao/google), email, 알림 설정(mission/result/highlight), status
- **user_fcm_tokens** — 기기별 FCM 토큰 (유저 1:N, token UNIQUE)
- **rooms** — 방(초대코드, 정원, 일일 미션 횟수, 미션 시작/종료 시간)
- **room_members** — 방-유저 N:M, 방장 여부, 채팅 읽음 위치(lastReadMessageId), 방별 채팅알림 on/off
- **mission_templates** — 미션 풀(제목, 카테고리) + 튜토리얼 미션
- **mission_time_slots** — 미션 발동 시각 슬롯
- **missions** — 방별 당일 미션 (PENDING→ACTIVE→COMPLETED/EXPIRED)
- **submissions** — 제출 영상 (SUBMITTED/MISSED, videoUrl, 회전 메타: horizontal/facingMode/width/height)
- **collages** — 미션당 1개 콜라주 (PROCESSING→COMPLETED/FAILED)
- **synklogs** — 방+날짜당 1개 일별 릴스 (createdBy 생성자)
- **collection_records** — 개인 도감 기록
- **room_chats / chat_reactions** — 채팅 메시지/리액션
- **notifications** — 알림 이력
- **room_bans** — 강퇴 기록

**주요 관계**: missions N:1 rooms · submissions N:1 missions·users · collages 1:1 missions · synklogs N:1 rooms · collection_records N:1 users·templates·submissions

---

## 6. 담당 기능 (본인 — 백엔드 전반 + 인프라)

### 6-1. 콜라주 자동 합성 파이프라인 (핵심)
- AWS Lambda + FFmpeg 영상 합성 함수 설계·구현 (1~10명 분할 레이아웃)
- 백엔드 비동기 Invoke + **콜백 기반 결과 수신** 구조 (Lambda 응답을 기다리지 않아 API 블로킹 없음)
- 전원 제출 즉시 / 미션 만료 두 시점에서 자동 트리거
- S3 저장 + 썸네일(첫 프레임) 추출, 콜백 시크릿으로 위변조 방지

### 6-2. 랜덤 미션 스케줄링 엔진
- 자정 배치로 방별 당일 미션 랜덤 생성(풀방만), 시간슬롯 랜덤 배정 + 최소 간격 보장
- 매분 스케줄러로 PENDING→ACTIVE 전환 + FCM 푸시, deadline 경과 시 미제출자 MISSED 자동 INSERT
- **방이 꽉 차는 순간 튜토리얼 미션 2개(+15분/+1시간) 자동 예약** (23시 이후면 익일로 폴백)

### 6-3. 푸시 알림(FCM) 시스템
- Firebase Admin SDK 연동, 기기별 다중 토큰 관리, iOS APNs 설정(alert/sound)
- 채팅 메시지 data payload 푸시 + 딥링크(알림 클릭 시 해당 방/콜라주로 이동)
- **iOS PWA 유령 토큰 자가복구 로직** (아래 트러블슈팅 참고)

### 6-4. 실시간 채팅 (WebSocket/STOMP)
- REST/WebSocket 양 경로 메시지 저장 + 방 전체 브로드캐스트
- 방별 채팅 알림 on/off, 읽음 처리

### 6-5. 인증·회원·도감·방·참여율 API
- 카카오/구글 OAuth2 + JWT(Access/Refresh)
- 도감 수집률, 주별 참여율(공동 순위 포함), 방 생성/참여/관리 전반

### 6-6. 인프라·CI/CD
- EC2 + Docker 배포, GitHub Actions 자동 배포 파이프라인
- MySQL → Supabase(PostgreSQL) 마이그레이션, HTTPS 도메인 세팅

---

## 7. 트러블슈팅 (대표 사례)

### ① iOS PWA FCM "유령 토큰" — 서버는 성공, 폰은 무음
- **증상**: FCM 발송 시 HTTP 200인데 실제 알림이 오지 않음. 미션·채팅 알림 전부 무음.
- **진단**: 서비스 계정으로 FCM에 직접 테스트 발송 → DB의 토큰은 200인데 미배달, "방금 발급된" 토큰은 404 UNREGISTERED. iOS PWA 재설치/구독 만료 시 APNs 웹푸시 구독은 죽지만 Firebase SDK가 IndexedDB에 **캐시된 옛 토큰을 계속 반환**하는 것이 원인.
- **해결**: `requestNotificationPermission()`에서 `pushManager.getSubscription()`으로 실제 구독 생존을 확인 → 없으면 `deleteToken()` 후 `getToken()`으로 **새 구독·새 토큰 재발급**. 앱 실행마다 자동 복구되어 재설치 없이 해결.

### ② 콜라주 영상 FFmpeg 합성 실패 & 멈춤
- Pillow 버전이 Lambda 런타임과 불일치(`cannot import name '_imaging'`) → 런타임에 맞춘 manylinux 휠로 재빌드.
- 콜라주 길이가 재생 시간보다 짧아 뒤 3초가 프리즈 → `-stream_loop -1 -t 5`로 정확히 5초 루프 처리.
- 5·6명일 때 1열 레이아웃이 과도하게 눌려 얼굴이 잘림 → **5명 이상 2열 레이아웃**으로 변경.

### ③ 채팅 전송이 통째로 롤백되던 버그
- **증상**: 채팅 보내면 화면엔 떴다가 나가면 사라지고, 상대는 아예 못 받음. (`room_chats` 테이블이 비어 있었음)
- **원인**: 채팅 저장 `@Transactional` 안에서 FCM 발송 시 `notifications` 테이블의 `type` CHECK 제약에 `CHAT`이 빠져 있어 제약 위반 → 트랜잭션이 rollback-only로 오염 → 채팅 저장까지 롤백.
- **해결**: CHECK 제약에 `CHAT` 추가 + FCM 발송을 try/catch로 분리해 푸시 실패가 채팅 저장을 막지 않도록 함.

### ④ JVM 타임존 미지정으로 인한 미션 중복 생성
- UTC/KST 혼선으로 자정 배치가 날짜 경계에서 미션을 중복 생성 → **JVM 타임존 KST 전역 고정** + (room, date, slot) 유니크 제약으로 방지.

### ⑤ 콜라주 멤버 셀 위치가 매번 랜덤
- `findByRoom`에 정렬이 없어 DB 반환 순서가 매번 달라짐 → **입장 순서(RoomMember id) 정렬**로 같은 방이면 셀 위치 고정.

### ⑥ 서버 장애 복구
- EC2 백엔드 다운 장애를 로그 분석으로 원인 파악 후 복구, Docker 재배포 및 근본 원인 수정.

---

## 8. 실사용 성과 / 피드백

- **배포 채널**: KB IT's Your Life 교육생 대상 Slack 커뮤니티에 테스트 배포
- **반응**: 배포 당일 하루 만에 신규 가입자 다수 유입 (최종 확인 시점 기준 누적 가입자 약 26명, 교육생 중심 자발적 가입/방 생성·초대 코드 공유 발생)
- **수집 피드백 및 반영**:
  - "같은 방인데 미션이 두 번 울린다", "콜라주 화면이 잘린다", "알림 눌러도 해당 방으로 안 간다" 등 실사용 버그를 실시간 수집·수정
  - "미션 제목 디자인/위치 조정", "튜토리얼 카테고리 추가", "주별 참여율 공동 순위" 등 UX 개선 요청 반영
- **운영 메모**: 프로젝트 교육 일정(반 개편) 종료 후 테스트 배포 종료. (현재 Supabase 인스턴스 비활성으로 실시간 통계 조회는 불가 — 위 수치는 운영 중 마지막 확인값)

> ※ 정확한 누적 사용자/영상 수치는 서비스 운영 종료로 DB 조회가 불가합니다. 수치가 꼭 필요하면 운영 당시 캡처/스크린샷으로 보강 권장.

---

## 9. 추가 제출 자료 체크리스트

| 요청 자료 | 상태 / 위치 |
|---|---|
| GitHub 주소 | BE: youhyun010615/SYNK-back · FE: Ahyoung00/SYNK_front |
| 발표자료/기획서 | `docs/개발작업_전체정리.md`, `ERD.md`, `API.md` 보유 (별도 PPT는 추가 작성 필요 시) |
| 시연 영상/화면 | 광고 영상 보유 (SYNK 폴더) · 서비스 스크린샷 다수 |
| 프로젝트 기간/팀 | 2026.05~07, 2인 (권유현·이아영) |
| 담당 기능 | 위 6번 섹션 (백엔드 전반 + 영상 파이프라인 + 인프라) |
| 사용자 수/피드백 | 위 8번 섹션 |
| 배포 URL/종료 여부 | synk-front.vercel.app · 테스트 배포 후 교육 종료로 종료 |
| ERD / 아키텍처 | 위 4·5번 섹션 (`ERD.md` 원본 보유) |
| 트러블슈팅 | 위 7번 섹션 |
