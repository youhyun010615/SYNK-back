# Lambda 배포·검증·롤백 가이드

콜라주 합성(`synk-collage`)과 SYNKLOG(`synk-synklog`) Lambda 를 수정·배포할 때의 절차를 정리한다.
(조사 기준일: 2026-11-20)

> 이 Lambda 는 `main` push 시 도는 GitHub Actions 배포 워크플로(`deploy.yml`)로 **배포되지 않는다.**
> `lambda/**` 변경은 서버 배포를 건너뛰도록 설정되어 있고(`paths-ignore`), Lambda 는 아래 절차로 직접 배포한다.

## 1. 함수 정보

| | `synk-collage` | `synk-synklog` |
|---|---|---|
| 역할 | 미션 콜라주 합성 (제출 영상 → 1080×1920 영상 + 썸네일) | 콜라주들을 이어붙여 SYNKLOG 영상 생성 |
| 소스 | `lambda/lambda_function.py` | `lambda/synklog_function.py` |
| 핸들러 | `lambda_function.lambda_handler` | `synklog_function.lambda_handler` |
| 런타임 | Python 3.11 | Python 3.12 |
| 메모리 / 제한 시간 | 3008 MB / 900초 | 2048 MB / 600초 |
| 레이어 | `ffmpeg` (`/opt/ffmpeg`) | `ffmpeg` (`/opt/ffmpeg`) |
| 호출 | 백엔드가 비동기 Invoke 후 콜백으로 결과 수신 | 백엔드가 Invoke 후 콜백으로 결과 수신 |

공통 파일: 폰트(`NanumGothic.ttc`, `Korean.ttf`), 로고(`SYNK 로고.jpeg`), Pillow(배포 zip 안에 포함).

## 2. 저장소에 없는 것 (중요)

`.gitignore` 에 `lambda/*.zip`, `lambda/deps/`, `lambda/deps_collage/` 가 들어 있어 **배포 zip 과 Pillow 는 GitHub 에 없다.**
(작업한 사람의 로컬에만 남아 있고, 로컬 zip 은 최신 배포본과 다를 수 있다.)

- 콜라주는 `lambda/deploy.sh` 가 Pillow 를 받아 새로 패키징한다. SYNKLOG 는 빌드 스크립트가 없다.
- 그래서 **현재 배포된 코드를 내려받아 `.py` 파일만 교체**하는 방식을 권장한다. (아래 4번) 다른 파일(Pillow, 폰트)은 건드리지 않는다.

## 3. 배포 전 로컬 검증

실제 서버와 같은 코드를 로컬에서 돌려 보고 올린다. 합성 서버가 쓰는 ffmpeg 는 리눅스용 바이너리라 맥에서는 따로 설치한 ffmpeg 를 쓴다.

1. `brew install ffmpeg`
2. 핸들러를 직접 호출하는 작은 스크립트를 만든다. 외부와 닿는 부분만 바꿔서 실행한다.
   - `boto3` 는 가짜 모듈로 대체, S3 `download_file`/`upload_file` 는 로컬 파일 복사로 대체
   - 콜백(`send_callback` / `_callback`)은 출력만 하도록 대체
   - ffmpeg 경로 `/opt/ffmpeg` 를 로컬 ffmpeg 로 바꿔서 호출
3. 인원 구성(1·2·3·4·5·6·10명, 전원 미제출)을 바꿔 가며 렌더링하고 해상도·길이·처리 시간·디코딩 오류를 확인한다.
   - 결과: `ffmpeg -v error -i 결과.mp4 -f null -` 에 출력이 없으면 정상

## 4. 배포 절차

**순서: SYNKLOG 먼저, 콜라주 나중.** SYNKLOG 가 기존·신규 콜라주(720×1280, 1080×1920)를 모두 처리하므로 이 순서가 안전하다.

```bash
FN=synk-synklog            # 또는 synk-collage
PY=synklog_function.py     # 또는 lambda_function.py
mkdir -p /tmp/lambda-deploy && cd /tmp/lambda-deploy

# 1) 현재 배포된 코드를 내려받는다 (롤백용 원본으로도 쓴다 — 지우지 말 것)
curl -s -o base.zip "$(aws lambda get-function --function-name $FN --region ap-northeast-2 --query Code.Location --output text)"

# 2) .py 한 개만 교체한 새 zip 을 만든다
cp base.zip new.zip
cp /경로/SYNK-back/lambda/$PY .
zip -u new.zip $PY
unzip -p new.zip $PY | diff - /경로/SYNK-back/lambda/$PY && echo "OK: zip 안의 코드 == 저장소 코드"

# 3) 배포하고 완료될 때까지 확인한다
aws lambda update-function-code --function-name $FN --zip-file fileb://new.zip --region ap-northeast-2
aws lambda get-function-configuration --function-name $FN --region ap-northeast-2 --query LastUpdateStatus --output text   # Successful 이 될 때까지 반복

# 4) 올린 파일과 배포된 코드의 해시가 같은지 확인한다
aws lambda get-function-configuration --function-name $FN --region ap-northeast-2 --query CodeSha256 --output text
openssl dgst -sha256 -binary new.zip | base64
```

폰트·로고 등 새 파일이 필요한 변경이면 `zip -u new.zip 새파일` 로 함께 넣는다. (예: SYNKLOG 에서 `NanumGothic.ttc` 를 쓰려면 SYNKLOG zip 에 추가해야 한다)

## 5. 배포 후 스모크 테스트

운영 데이터를 건드리지 않게 **가짜 ID 와 막아둔 콜백 주소**로 실제 서버에서 한 번 돌려 본다. 입력은 실제 S3 영상을 쓴다.

- 가짜 ID 예: `synklogId: 9990001`, `missionId: 9990002`
- 콜백 주소: `http://127.0.0.1:9/none` (연결 거부 → 백엔드로 결과가 가지 않는다. 로그에 `Callback 실패` 가 찍히는 것이 정상)

```bash
aws lambda invoke --function-name synk-synklog --region ap-northeast-2 \
  --cli-binary-format raw-in-base64-out --payload file://event.json \
  --log-type Tail --query LogResult --output text out.json | base64 -d
```

확인할 것: 성공 로그(`concat 완료`, `Collage video uploaded`), 결과 해상도(1080×1920)와 길이, 처리 시간·메모리(`REPORT` 줄).
테스트로 만든 S3 파일(`collages/9990002/`, `synklogs/9990001/`)은 확인 후 정리한다.

## 6. 롤백

배포 전 내려받아 둔 `base.zip` 으로 되돌린다.

```bash
aws lambda update-function-code --function-name $FN --zip-file fileb://base.zip --region ap-northeast-2
```

## 7. 주의사항

- **`NanumGothicBold.ttf`, `NotoSansKR-Bold.otf` 는 폰트가 아니라 HTML 문서**(다운로드가 잘못 저장된 파일)다. 사용하면 `OSError: unknown file format` 이 나거나 코드의 폴백 폰트로 대체된다. 굵은 글씨는 `NanumGothic.ttc` 의 index 1 (`ImageFont.truetype(path, size, index=1)`), 보통 글씨는 index 0 을 쓴다.
- 로컬 ffmpeg 와 Lambda 레이어 ffmpeg 버전이 다르다. 로컬에서 성공해도 **배포 후 스모크 테스트를 꼭 한다.**
- 콜라주 입력(제출 영상) 해상도는 FE 의 `getUserMedia` 설정에 달려 있다. 2026-11-14 부터 1280×720 으로 들어오고(그 이전은 640×480), 합성 로그의 `→ video[i] WxH` 줄로 확인할 수 있다.
- 합성 시간은 해상도와 인원이 늘수록 늘어난다. 2026-11-13 ~ 20 사이 콜라주 합성은 약 13~24초 걸렸다. 제한 시간(콜라주 900초, SYNKLOG 600초)에는 여유가 크지만 큰 변경 뒤에는 `REPORT` 의 소요 시간을 확인한다.
