# SYNK — 랜덤 미션 숏폼 콜라주 SNS

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?logo=postgresql&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-EC2%20·%20Lambda%20·%20S3-FF9900?logo=amazonaws&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)
![Firebase](https://img.shields.io/badge/FCM-Firebase-FFCA28?logo=firebase&logoColor=black)

> "우리는 지금, 같은 순간을 산다."
> 예고 없는 랜덤 알림이 울리면 5분 안에 지금 그 순간을 찍고, 친구들의 순간이 하나의 콜라주 영상으로 자동 합성되는 실시간 동기화 SNS.

- **배포 URL**: https://synk-front.vercel.app
- **API**: https://api.synk.ai.kr
- **Frontend 레포**: https://github.com/Ahyoung00/SYNK_front

## 핵심 기능

1. **랜덤 사이렌** — 하루 1~5회, 예고 없이 방 멤버 전원에게 동시 푸시(FCM)
2. **5분 타임어택** — 알림 후 5분 이내 3~5초 영상 제출
3. **Group Collage** — 전원 제출/만료 시 1~10명 분할 콜라주 자동 합성 (AWS Lambda + FFmpeg)
4. **SYNKLOG** — 당일 콜라주를 이어붙인 9:16 일별 릴스 (방 단위 공유)
5. **도감** — 완료 미션 개인 수집 + 카테고리별 수집률
6. **실시간 채팅** — 방별 WebSocket(STOMP) + 백그라운드 푸시

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Spring Boot (Java 17), Spring Security, WebSocket(STOMP), JPA/Hibernate |
| Database | PostgreSQL (Supabase) |
| 인증 | JWT (Access/Refresh) + Kakao·Google OAuth2 |
| 영상 처리 | AWS Lambda (Python + FFmpeg), AWS S3 |
| 푸시 | Firebase Cloud Messaging |
| 인프라 | AWS EC2 + Docker, GitHub Actions CI/CD |
| Frontend | React 18 + TypeScript + Vite, PWA, Zustand |

## 콜라주 합성 플로우

```
미션 만료 / 전원 제출
  → Backend가 submissions[] + 콜백 URL 구성
  → AWS Lambda 비동기 Invoke (응답 대기 없음, API 논블로킹)
  → Lambda: S3 영상 다운로드 → FFmpeg 분할 합성 → S3 업로드
  → Backend 콜백 URL로 결과 POST
  → Collage 상태 PROCESSING → COMPLETED
```

- 콜라주를 **비동기 Invoke + 콜백**으로 처리해 영상 합성 시간 동안 API가 멈추지 않습니다.
- 당일 콜라주들은 다시 `synk-synklog` Lambda가 이어붙여 9:16 **SYNKLOG** 릴스로 생성합니다.

## 문서

- [프로젝트 포트폴리오](SYNK_포트폴리오.md)
- [시스템 아키텍처 다이어그램](docs/architecture.html)
- [ERD](ERD.md) · [API 명세](API.md)

## 로컬 실행

```bash
./gradlew bootRun
```

프로필별 설정은 `src/main/resources/application-{local,prod}.yaml`을 참고하세요.
