# 비밀값(GitHub Secrets) 교체 가이드

비밀값이 노출됐거나 노출이 의심될 때, 또는 주기적으로 교체할 때의 절차를 정리한다.
(이 문서에는 비밀값 자체를 적지 않는다. Secret 이름만 적는다.)

## 1. 기본 원리

`main` 배포 워크플로(`deploy.yml`)는 **배포할 때마다 GitHub Secrets 로 서버의 `~/synk.env` 를 새로 만들고** 컨테이너를 다시 띄운다.
그래서 교체는 보통 이렇게 한다.

1. 새 값을 만든다 (필요하면 외부 콘솔에서 먼저 발급).
2. GitHub 저장소 **Settings → Secrets and variables → Actions** 에서 해당 Secret 을 새 값으로 바꾼다.
3. **Actions 탭 → "Deploy to AWS EC2" → Run workflow** 로 수동 배포한다. (`workflow_dispatch`)
4. 배포가 끝나면 서버가 새 값으로 재시작된다. 동작을 확인한다.

> 문서만 바뀐 push 는 배포가 건너뛰어진다(#134). 교체 후에는 반드시 수동 실행(또는 코드 변경 push)으로 배포해야 새 값이 적용된다.

### 키 형태 비밀값의 안전한 순서

AWS 키, Firebase 서비스 계정 키처럼 **여러 개를 동시에 둘 수 있는 키**는 항상 이 순서로 한다.

**새 키 발급 → Secret 교체 → 배포 → 동작 확인 → 예전 키 삭제**

예전 키를 먼저 지우면 아직 예전 키로 동작 중인 서버가 중단된다.

## 2. 비밀값별 교체

| Secret | 용도 | 교체 방법 | 교체 중 영향 |
|---|---|---|---|
| `COLLAGE_CALLBACK_SECRET` | Lambda → 백엔드 콜백 인증 (콜라주·SYNKLOG **공용**) | 새 랜덤 값 생성 (`openssl rand -hex 32`) 후 Secret 교체 → 배포 | Lambda 는 호출마다 값을 전달받으므로 **Lambda 변경 불필요**. 교체 시점에 합성 중이던 건은 예전 값으로 콜백해 실패할 수 있으니 미션이 끝나 합성이 도는 시간대는 피한다 |
| `JWT_SECRET` | 로그인 토큰 서명 | 새 랜덤 값으로 교체 → 배포 | 이미 발급된 토큰이 모두 무효 → **전원 재로그인** |
| `DB_PASSWORD` (`DB_HOST/PORT/NAME/USERNAME`) | Supabase(PostgreSQL) 접속 | Supabase 에서 비밀번호 변경 → Secret 교체 → **즉시** 배포 | 변경~배포 사이 DB 접속 실패 구간이 생기므로 짧게 진행 |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | S3·Lambda 호출 | IAM 에서 새 액세스 키 발급 → Secret 교체 → 배포 → 정상 확인 후 **예전 키 비활성화·삭제** | 예전 키를 먼저 지우지 않으면 중단 없이 교체 가능 |
| `KAKAO_CLIENT_ID/SECRET`, `GOOGLE_CLIENT_ID/SECRET` | 소셜 로그인 | 각 개발자 콘솔에서 클라이언트 시크릿 재발급 → Secret 교체 → 배포 | 재발급하면 예전 값이 바로 무효가 될 수 있어(제공사마다 다름) **배포 전까지 소셜 로그인이 실패할 수 있다.** 콘솔 설정을 확인하고 사용자가 적은 시간대에 진행 |
| `FIREBASE_SERVICE_ACCOUNT` | FCM 푸시 | **새 키 발급 → Secret 에 JSON 전체 교체 → 배포 → 푸시가 오는지 확인 → 그다음에 예전 키 삭제** | Secret 만 바꾸는 것은 운영 서버에 영향이 없다. **푸시가 끊기는 시점은 예전 키를 삭제하는 때**이므로 반드시 새 키로 푸시가 오는 것을 확인한 뒤에 삭제한다. (먼저 지우면 서버가 쓰던 키가 사라져 푸시가 중단됨) |
| `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` | 이미지 push, 배포 서버의 이미지 pull 로그인 | Docker Hub 에서 토큰 재발급 → Secret 교체 | 배포 때마다 사용하므로 교체 후 **배포가 성공하는지**(로그에 `Login Succeeded`) 확인한다. 토큰 권한은 읽기·쓰기가 필요하다 |
| `EC2_SSH_KEY`, `EC2_HOST`, `EC2_USER` | 배포 접속 | 새 키 쌍 생성 후 서버 `authorized_keys` 와 Secret 교체 | 키를 잘못 바꾸면 배포가 접속에 실패한다 (먼저 새 키를 서버에 추가한 뒤 교체) |

## 3. 문서·코드에 값이 들어갔을 때

- **문서에서 지워도 과거 커밋 기록에는 남는다.** 저장소가 공개라면 누구나 볼 수 있으므로 해당 값은 **교체**해야 한다.
- 현재 파일 점검 (값이 출력되지 않게 주의):

```bash
git grep -nIE "AKIA[0-9A-Z]{16}|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|\"private_key\"|ghp_[0-9A-Za-z]{30,}"
git grep -nIiE "(password|secret|api[_-]?key|token)\s*[:=]\s*['\"]?[A-Za-z0-9/+_-]{8,}"
```

- 과거 기록 점검: `git log --all --format='%h %s' -S'찾을문자열'`
- 오탐 주의: Pillow 같은 라이브러리의 폰트 데이터(base64)에 `AKIA` 같은 글자가 우연히 들어 있을 수 있다.

## 4. 교체 후 확인

- 서버: 컨테이너가 정상 기동했는지(`docker ps`, 시작 후 `ERROR` 로그 없음)
- 로그인(카카오·구글), 푸시 알림, 콜라주·SYNKLOG 합성이 정상인지
- 예전 값이 더 이상 통하지 않는지(예: 콜백은 예전 값으로 호출하면 거부되어야 한다)
