# ngrok 로컬 연결 가이드

각자 노트북에서 백엔드(Spring Boot)를 실행하고, ngrok으로 외부 접근 가능한 HTTPS URL을 만들어 프론트엔드와 연결하는 방법.

---

## 1. ngrok 설치 및 초기 설정

### 1-1. ngrok 설치

**macOS (Homebrew)**
```bash
brew install ngrok
```

**직접 다운로드**  
https://ngrok.com/download 에서 OS에 맞는 버전 다운로드

---

### 1-2. ngrok 계정 생성 및 토큰 발급

1. https://ngrok.com 에서 회원가입 또는 로그인
2. 좌측 메뉴에서 **Your Authtoken** 클릭
3. 토큰 복사 (`ngpXXXXXXXXXXXXXXXXXXXXXXX` 형태)

---

### 1-3. 터미널에 토큰 등록

```bash
ngrok config add-authtoken {발급받은_토큰}
```

예시:
```bash
ngrok config add-authtoken ngpABCDEF1234567890abcdef1234567890ABCDEFGHIJ
```

> 한 번만 실행하면 이후부터는 매번 입력 안 해도 됨. `~/.config/ngrok/ngrok.yml`에 저장됨.

---

## 2. 백엔드 ngrok 실행

### 2-1. Spring Boot 서버 실행

```bash
./gradlew bootRun
```

서버가 `8080` 포트에서 실행 중인지 확인.

---

### 2-2. ngrok 터널 열기

```bash
ngrok http 8080
```

실행 후 아래와 같은 화면이 뜸:

```
Session Status    online
Account           홍길동 (Plan: Free)
Version           3.x.x
Region            Asia Pacific (ap)
Web Interface     http://127.0.0.1:4040
Forwarding        https://xxxx-xxx-xxx-xxx-xxx.ngrok-free.app -> http://localhost:8080
```

`Forwarding` 줄의 `https://xxxx-xxx-xxx-xxx-xxx.ngrok-free.app` 가 **백엔드 ngrok URL**.

> **주의:** 무료 플랜은 ngrok 재시작할 때마다 URL이 바뀜. 재시작하면 아래 설정들을 모두 업데이트해야 함.

---

## 3. 각 위치에 들어가야 하는 주소값

### 백엔드에서 설정할 것

#### `CorsConfig.java` — 프론트엔드 ngrok URL 추가

프론트가 ngrok URL로 API 호출할 때 CORS 에러가 나지 않도록 허용 목록에 추가.

```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:3000",
    "http://localhost:5173",
    // ...
    "https://{프론트_ngrok_URL}"   // ← 여기에 프론트 ngrok URL 추가
));
```

---

### 프론트엔드에서 설정할 것

#### `.env` 또는 API 설정 파일 — 백엔드 ngrok URL

```env
VITE_API_BASE_URL=https://{백엔드_ngrok_URL}
```

#### 로그인 코드 — redirect_uri

```js
// 카카오 로그인
const KAKAO_REDIRECT_URI = "https://{프론트_ngrok_URL}/oauth/kakao/callback";

// 구글 로그인
const GOOGLE_REDIRECT_URI = "https://{프론트_ngrok_URL}/oauth/google/callback";
```

---

### 카카오 / 구글 OAuth 콘솔에서 설정할 것

redirect_uri는 OAuth 제공자 콘솔에도 등록해야 함. 미등록 시 로그인 시 오류 발생.

| OAuth | 콘솔 위치 | 등록할 값 |
|---|---|---|
| 카카오 | [카카오 개발자 콘솔](https://developers.kakao.com) → 앱 선택 → 카카오 로그인 → Redirect URI | `https://{프론트_ngrok_URL}/oauth/kakao/callback` |
| 구글 | [Google Cloud Console](https://console.cloud.google.com) → API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 → 승인된 리디렉션 URI | `https://{프론트_ngrok_URL}/oauth/google/callback` |

---

## 4. URL 교체 시 체크리스트

ngrok을 재시작해서 URL이 바뀌면 아래를 순서대로 업데이트.

- [ ] **백엔드** `CorsConfig.java` — 새 프론트 ngrok URL로 변경 후 서버 재시작
- [ ] **카카오 개발자 콘솔** — Redirect URI 업데이트
- [ ] **구글 Cloud Console** — Redirect URI 업데이트
- [ ] **프론트** `.env` — 새 백엔드 ngrok URL로 변경

---

## 5. 전체 흐름 요약

```
[프론트 노트북]                    [백엔드 노트북]
  React (localhost:5173)            Spring Boot (localhost:8080)
       ↕ ngrok                            ↕ ngrok
  https://프론트-ngrok-URL    →    https://백엔드-ngrok-URL/api/...
       ↕
  카카오/구글 OAuth
  (redirect_uri = 프론트 ngrok URL)
```

1. 사용자가 프론트에서 카카오/구글 로그인 버튼 클릭
2. OAuth 로그인 완료 후 `프론트 ngrok URL/oauth/{provider}/callback` 으로 code 전달
3. 프론트가 code와 redirect_uri를 `백엔드 ngrok URL/api/auth/{provider}` 로 POST
4. 백엔드가 access token 발급 → 사용자 정보 조회 → JWT 반환