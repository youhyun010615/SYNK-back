# 백엔드 HTTPS 도메인 적용 및 최종 마무리

## 1. 배경 및 목적

- 프론트엔드(Vercel)는 HTTPS로 배포되어 있음
- 백엔드(EC2)는 `http://13.125.99.225:8080`으로 HTTP만 지원
- 브라우저의 Mixed Content 정책으로 인해 HTTPS 프론트에서 HTTP 백엔드를 호출할 수 없음
- → 도메인 연결 + Nginx 리버스 프록시 + Let's Encrypt로 백엔드에 HTTPS 적용

## 2. 사용 도메인

- 가비아(Gabia)에서 구매한 `synk.ai.kr` 도메인 사용
- 백엔드용 서브도메인: `api.synk.ai.kr`

## 3. 작업 순서

### 3.1 가비아 DNS 설정

가비아 → 도메인 관리 → DNS 관리 → 레코드 수정에서 A 레코드 추가:

| 타입 | 호스트 | 값 | TTL |
|---|---|---|---|
| A | api | 13.125.99.225 | 3600 |

전파 확인:

```bash
dig api.synk.ai.kr +short
# 13.125.99.225
```

### 3.2 AWS 보안그룹에 80/443 포트 추가

인스턴스에 실제 연결된 보안그룹은 `launch-wizard-1 (sg-04e353cc4e569c63f)`였음.
인바운드 규칙에 아래 추가:

| 유형 | 포트 | 소스 |
|---|---|---|
| HTTP | 80 | 0.0.0.0/0 |
| HTTPS | 443 | 0.0.0.0/0 |

(기존 22, 8080 규칙은 유지)

### 3.3 EC2에 Nginx 설치

```bash
ssh -i ~/Desktop/SYNKback.pem ubuntu@13.125.99.225

sudo apt update
sudo apt install -y nginx
```

### 3.4 Nginx 리버스 프록시 설정

`/etc/nginx/sites-available/api.synk.ai.kr` 생성:

```nginx
server {
    listen 80;
    server_name api.synk.ai.kr;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

설정 활성화:

```bash
sudo ln -s /etc/nginx/sites-available/api.synk.ai.kr /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

확인 (`syntax is ok`, `test is successful`):

```bash
curl -i http://api.synk.ai.kr
# HTTP/1.1 403 (Nginx 프록시 → Spring Security 정상 응답)
```

### 3.5 Certbot으로 Let's Encrypt 인증서 발급

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.synk.ai.kr
```

- 이메일 입력 → 약관 동의(`Y`) → 뉴스레터 수신(`N`)
- 결과: `Congratulations! You have successfully enabled HTTPS on https://api.synk.ai.kr`
- 인증서는 자동 갱신 스케줄 등록됨 (만료일 2026-09-13)

최종 확인:

```bash
curl -i https://api.synk.ai.kr
# HTTP/1.1 403 (HTTPS 정상 적용)
```

## 4. 프론트엔드 연동 트러블슈팅

### 4.1 문제 발견

- 프론트엔드(`https://synk-front.vercel.app`)에서 카카오/구글 로그인 시도 시 실패
- 브라우저 Network 탭 확인 결과, 실제 요청 주소가 `https://synk-back-production.up.railway.app/auth/kakao`였음 (404, CORS 에러)
- → 프론트엔드가 오늘 작업한 EC2(`api.synk.ai.kr`)가 아닌, 별도의 **Railway 배포본**을 바라보고 있었음

### 4.2 조치

- 프론트엔드 API base URL을 `https://api.synk.ai.kr`로 변경
- 변경 과정에서 프론트엔드 Vercel 배포 도메인 자체도 `synkfront.vercel.app` → `synk-front.vercel.app`으로 변경됨

### 4.3 백엔드 CORS 설정 업데이트

`src/main/java/com/synk/config/CorsConfig.java`의 허용 origin을 정리:

```java
// 변경 전
config.setAllowedOriginPatterns(List.of(
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://192.168.*.*:3000",
        "http://192.168.*.*:5173",
        "http://172.*.*.*:3000",
        "http://172.*.*.*:5173",
        "http://10.*.*.*:3000",
        "http://10.*.*.*:5173",
        "https://explain-evasion-jailhouse.ngrok-free.app",
        "https://synkfront.vercel.app"
));

// 변경 후
config.setAllowedOriginPatterns(List.of(
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "https://synk-front.vercel.app"
));
```

- 사용하지 않는 ngrok 주소, 사설망 IP 패턴 제거
- 옛 Vercel 도메인 → 새 Vercel 도메인으로 교체

### 4.4 배포

- PR [#44](https://github.com/youhyun010615/SYNK-back/pull/44) "fix(cors): 프론트엔드 배포 도메인 변경에 따른 CORS 설정 정리" 생성 및 머지
- GitHub Actions(`Deploy to AWS EC2`) 자동 실행 → 성공

## 5. 최종 결과

- 백엔드: `https://api.synk.ai.kr` (HTTPS, Let's Encrypt 인증서)
- 프론트엔드: `https://synk-front.vercel.app`
- CORS 허용 origin: 로컬 개발 주소 + 프론트엔드 배포 주소만 유지
- 프론트엔드 → 백엔드 HTTPS 통신 정상화, Mixed Content 문제 해결
