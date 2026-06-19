# MySQL → Supabase(PostgreSQL) 마이그레이션

## 1. 배경 및 목적

- 기존 SYNK 백엔드는 MySQL을 사용 중이었음
- EC2에 배포 시 DB 연결 편의성을 위해 **Supabase(PostgreSQL)**로 전환
- Supabase는 관리형 PostgreSQL + 커넥션 풀링을 제공하여 EC2에서의 연결이 더 간단함

## 2. 작업 흐름

1. GitHub 이슈 생성 (#42)
2. 작업 브랜치 생성 (`feature/supabase-migration-#42`)
3. Supabase 프로젝트 생성
4. 의존성 및 설정 파일 변경
5. 로컬에서 연결 테스트
6. PR 생성 (#43) 및 머지 → `main`
7. GitHub Secrets 업데이트
8. GitHub Actions 자동 배포 확인

## 3. Supabase 프로젝트 설정

### 3.1 연결 방식 선택

Supabase는 3가지 연결 방식을 제공함:

| 연결 방식 | 특징 |
|---|---|
| Direct connection | 기본적으로 IPv6, EC2(IPv4) 환경에 부적합 |
| Transaction Pooler | IPv6 |
| **Session Pooler** | **IPv4 호환, EC2 환경에 권장** |

→ **Session Pooler**를 선택하여 연결

### 3.2 연결 정보

```
host: aws-1-ap-northeast-2.pooler.supabase.com
port: 5432
database: postgres
user: postgres.gwfzdqvzhtxggjhofvts
```

## 4. 코드 변경 사항

### 4.1 `build.gradle` - DB 드라이버 교체

```gradle
// 변경 전
runtimeOnly 'com.mysql:mysql-connector-j'

// 변경 후
runtimeOnly 'org.postgresql:postgresql'
```

### 4.2 `application.yaml` - Hibernate Dialect 변경

```yaml
spring:
  application:
    name: synk

  profiles:
    active: local

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect   # MySQLDialect → PostgreSQLDialect
        format_sql: true
    show-sql: true
```

### 4.3 `application-prod.yaml` - 환경변수 기반 접속 정보

```yaml
server:
  address: 0.0.0.0

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}?sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

kakao:
  client-id: ${KAKAO_CLIENT_ID}
  client-secret: ${KAKAO_CLIENT_SECRET}

google:
  client-id: ${GOOGLE_CLIENT_ID}
  client-secret: ${GOOGLE_CLIENT_SECRET}
```

### 4.4 `application-local.yaml` - 로컬 개발용 (gitignore, 미커밋)

```yaml
server:
  address: 0.0.0.0

spring:
  datasource:
    url: jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
    username: postgres.gwfzdqvzhtxggjhofvts
    password: {SUPABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver

jwt:
  secret: {JWT_SECRET}
  expiration: 86400000

kakao:
  client-id: {KAKAO_CLIENT_ID}
  client-secret: {KAKAO_CLIENT_SECRET}

google:
  client-id: {GOOGLE_CLIENT_ID}
  client-secret: {GOOGLE_CLIENT_SECRET}
```

## 5. 엔티티 호환성 검토

기존 엔티티가 PostgreSQL과 호환되는지 검토함 (변경 없음, 모두 호환):

- 모든 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 사용 → PostgreSQL의 `SERIAL`과 호환
- enum 필드는 `@Enumerated(EnumType.STRING)` 사용 → 호환
- `RoomChat.java`의 긴 텍스트 필드는 `columnDefinition = "TEXT"` 사용 → 호환

검토 대상 엔티티: `ChatReaction`, `Room`, `Submission`, `CollectionRecord`, `RoomMember`, `Notification`, `MissionTimeSlot`, `User`, `Synklog`, `MissionTemplate`, `RoomChat`, `Collage`, `Mission`

## 6. 로컬 연결 테스트

```bash
./gradlew bootRun
```

**결과:**
- Hibernate `ddl-auto: update`가 13개 테이블을 자동 생성
- `DataInitializer`가 미션 템플릿 90개 초기화 완료
- `Started SynkApplication in 4.721 seconds` 로그 확인

## 7. PR 및 배포

- PR #43 ("feat(db): MySQL -> Supabase(PostgreSQL) 마이그레이션") 생성 → 머지 (merge commit `3f951cd`)
- 이슈 #42 자동 종료
- GitHub Secrets에 Supabase 접속 정보(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) 등록/업데이트
- `main` 브랜치 push → GitHub Actions 워크플로우(`Deploy to AWS EC2`) 자동 실행 → 성공

## 8. 배포 후 검증

### 8.1 컨테이너 상태 확인 (EC2 SSH 접속)

```bash
docker ps
docker logs -f synk
```

**확인된 정상 로그:**

```
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
HikariPool-1 - Start completed.

Database JDBC URL [jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require]
Database driver: PostgreSQL JDBC Driver
Database dialect: PostgreSQLDialect
Database version: 17.6

Tomcat started on port 8080 (http)
Started SynkApplication in 15.14 seconds
```

### 8.2 외부 접근 확인

```bash
# 로컬 맥 터미널
curl -i http://<EC2_PUBLIC_IP>:8080
```

→ `HTTP/1.1 403` 응답 확인 (Spring Security가 인증 없는 요청을 차단한 정상 응답, 네트워크 경로는 정상)

### 8.3 트러블슈팅 - 보안그룹 이슈

- 인스턴스에 실제로 연결된 보안그룹이 `launch-wizard-1`이었고, 여기에 8080 포트 규칙이 없어서 외부 접속이 막혀있었음
- `launch-wizard-1` 보안그룹의 인바운드 규칙에 아래 규칙 추가 후 정상화:

| 유형 | 포트 범위 | 소스 |
|---|---|---|
| 사용자 지정 TCP | 8080 | 0.0.0.0/0 |

## 9. 결과

- MySQL → Supabase(PostgreSQL) 마이그레이션 완료
- EC2 배포 + Supabase 연결 정상 동작 확인
- 이후 `main` push 시마다 GitHub Actions가 자동으로 Supabase 환경변수를 사용해 재배포
