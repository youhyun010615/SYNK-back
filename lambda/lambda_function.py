import json
import boto3
import os
import re
import subprocess
import tempfile
import time
import urllib.request
import urllib.error

from PIL import Image, ImageDraw, ImageFont

s3 = boto3.client('s3', region_name='ap-northeast-2')
BUCKET = os.environ.get('S3_BUCKET', 'synk-videos')
REGION = os.environ.get('AWS_REGION_NAME', 'ap-northeast-2')

# 1~6명: 1열(전체 너비), 7명~: 2열
LAYOUTS = {
    1:  [[1]],
    2:  [[1], [1]],
    3:  [[1], [1], [1]],
    4:  [[1], [1], [1], [1]],
    5:  [[1], [1], [1], [1], [1]],
    6:  [[1], [1], [1], [1], [1], [1]],
    7:  [[2], [2], [2], [1]],
    8:  [[2], [2], [2], [2]],
    9:  [[2], [2], [2], [2], [1]],
    10: [[2], [2], [2], [2], [2]],
}

CANVAS_W = 540
CANVAS_H = 960
FPS = 24
MAX_COLLAGE_DURATION = 15.0  # 폭주 방지용 상한선
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "NanumGothic.ttc")


def build_missed_image(path, w, h, name, dark=True):
    """미제출자 placeholder PNG — TV 지지직 노이즈 배경"""
    import random

    short = min(w, h)
    cx = w // 2

    # ── TV 지지직 노이즈 배경 ─────────────────────────────────────────────────
    rng = random.Random()
    if dark:
        lo, hi = 8, 52
    else:
        lo, hi = 155, 215
    pixels = []
    for y in range(h):
        scanline = 0.72 if (y % 2 == 0) else 1.0  # 수평 스캔라인 효과
        for x in range(w):
            v = int(rng.randint(lo, hi) * scanline)
            pixels.append((v, v, v, 255))
    img = Image.new("RGBA", (w, h))
    img.putdata(pixels)

    # ── 아이콘 중심 위치 ──────────────────────────────────────────────────────
    rr = max(int(short * 0.10), 12)
    cy = int(h * 0.38)

    # ── 아이콘 링 (반투명 원) ─────────────────────────────────────────────────
    rl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rl)
    if dark:
        rfill = (255, 255, 255, 45)
        rout  = (255, 255, 255, 130)
    else:
        rfill = (0, 0, 0, 45)
        rout  = (0, 0, 0, 130)
    rd.ellipse([cx-rr, cy-rr, cx+rr, cy+rr],
               fill=rfill, outline=rout, width=max(2, rr//10))
    img = Image.alpha_composite(img, rl)
    draw = ImageDraw.Draw(img)

    # ── video-off 아이콘 ──────────────────────────────────────────────────────
    isize = int(rr * 1.05)
    iox, ioy = cx - isize//2, cy - isize//2
    sx_, sy_ = isize/24.0, isize/24.0

    def vp(x, y):
        return (iox + x*sx_, ioy + y*sy_)

    ic_col = (255, 255, 255, 220) if dark else (20, 15, 10, 210)
    ilw = max(1, round(1.8 * (sx_+sy_)/2))
    draw.rounded_rectangle([vp(1,5), vp(16,19)],
                           radius=max(1, round(2*(sx_+sy_)/2)),
                           outline=ic_col, width=ilw)
    draw.line([vp(23,7), vp(16,12), vp(23,17), vp(23,7)], fill=ic_col, width=ilw)
    draw.line([vp(1,1), vp(23,23)], fill=ic_col, width=ilw+1)

    # ── 이름 라벨 ─────────────────────────────────────────────────────────────
    display_name = (name or "").strip()
    if display_name:
        name_px = max(int(short * 0.07), 11)
        name_col = (255, 255, 255, 210) if dark else (20, 15, 10, 190)
        try:
            nf = ImageFont.truetype(FONT_PATH, name_px)
            draw.text((cx - draw.textlength(display_name, font=nf)/2,
                       cy + rr + int(short * 0.03)),
                      display_name, font=nf, fill=name_col)
        except Exception:
            pass

    # ── 하단 텍스트 (그라디언트 없이 노이즈 위에 직접) ──────────────────────
    l1_px = max(int(short * 0.055), 9)
    l2_px = max(int(short * 0.045), 8)
    l1_col = (255, 255, 255, 230) if dark else (20, 15, 10, 210)
    l2_col = (200, 200, 200, 200) if dark else (40, 35, 30, 180)
    l1_y = int(h * 0.80)
    l2_y = int(h * 0.89)
    try:
        f1 = ImageFont.truetype(FONT_PATH, l1_px)
        f2 = ImageFont.truetype(FONT_PATH, l2_px)
        t1 = "다음엔 꼭 함께해요"
        t2 = "이번 미션은 참여하지 않았어요"
        draw.text((cx - draw.textlength(t1, font=f1)/2, l1_y), t1, font=f1, fill=l1_col)
        draw.text((cx - draw.textlength(t2, font=f2)/2, l2_y), t2, font=f2, fill=l2_col)
    except Exception:
        pass

    img.convert("RGB").save(path)


def probe_video_info(local_video):
    """ffprobe가 레이어에 없어 ffmpeg stderr에서 직접 파싱한다.
    브라우저 MediaRecorder로 녹화된 webm/mp4는 헤더에 Duration이 없어(N/A)
    전체를 디코딩(-f null)해 마지막 time= 값을 읽는다.
    Returns: (duration_seconds, width, height) — 파싱 실패 시 None"""
    result = subprocess.run(
        ["/opt/ffmpeg", "-i", local_video, "-f", "null", "-"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True
    )
    stderr = result.stderr

    times = re.findall(r"time=(\d+):(\d+):(\d+(?:\.\d+)?)", stderr)
    duration = None
    if times:
        h, m, s = times[-1]
        duration = int(h) * 3600 + int(m) * 60 + float(s)

    # "Video: vp9, yuv420p(tv), 640x480" 같은 패턴에서 해상도 추출
    dim_match = re.search(r'Video:.*?\s(\d{2,4})x(\d{2,4})', stderr)
    width = int(dim_match.group(1)) if dim_match else None
    height = int(dim_match.group(2)) if dim_match else None

    return duration, width, height


CELL_GAP = 6  # 셀 간 간격 (px)

def get_cells(rows_config):
    n_rows = len(rows_config)
    total_gap_h = CELL_GAP * (n_rows + 1)
    row_h = (CANVAS_H - total_gap_h) // n_rows  # 캔버스 높이를 행 수로 균등 분할
    cells = []
    y = CELL_GAP
    for row in rows_config:
        cols = row[0]
        total_gap_w = CELL_GAP * (cols + 1)
        cell_w = (CANVAS_W - total_gap_w) // cols
        x = CELL_GAP
        for _ in range(cols):
            cells.append((cell_w, row_h, x, y))
            x += cell_w + CELL_GAP
        y += row_h + CELL_GAP
    return cells


def parse_s3_url(video_url):
    if video_url.startswith("s3://"):
        path = video_url[5:]
        bucket, key = path.split("/", 1)
        return bucket, key
    if ".amazonaws.com/" in video_url:
        key = video_url.split(".amazonaws.com/", 1)[1]
        return BUCKET, key
    return BUCKET, video_url


def send_callback(url, secret, payload):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "X-Callback-Secret": secret},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            print(f"Callback sent: {resp.status}")
    except urllib.error.URLError as e:
        print(f"Callback failed: {e}")


def lambda_handler(event, context):
    mission_id = event["missionId"]
    submissions = event["submissions"]
    callback_url = event["callbackUrl"]
    callback_secret = event["callbackSecret"]

    n = len(submissions)
    if n == 0:
        send_callback(callback_url, callback_secret, {
            "missionId": mission_id, "success": False, "error": "No submissions"
        })
        return {"statusCode": 400}

    layout = LAYOUTS.get(n, LAYOUTS[min(n, 10)])
    cells = get_cells(layout)

    with tempfile.TemporaryDirectory() as tmp:
        # 1) 제출된 영상 다운로드 + 길이·해상도 측정 (미제출자는 videoUrl 없음 → 검은 화면으로 채움)
        local_videos = [None] * n
        video_dims = {}  # index → (width, height)
        durations = []
        for i, sub in enumerate(submissions):
            video_url = sub.get("videoUrl")
            if not video_url:
                continue
            bucket, key = parse_s3_url(video_url)
            local_video = f"{tmp}/input_{i}.mp4"
            print(f"Downloading s3://{bucket}/{key}")
            s3.download_file(bucket, key, local_video)
            local_videos[i] = local_video
            d, vid_w, vid_h = probe_video_info(local_video)
            if d:
                durations.append(d)
            if vid_w and vid_h:
                video_dims[i] = (vid_w, vid_h)
                print(f"  → video[{i}] {vid_w}x{vid_h}, duration={d}")

        # 전원 미제출이면 정적 이미지 콜라주를 3초짜리 영상으로 생성
        duration = min(max(durations), MAX_COLLAGE_DURATION) if durations else 3.0

        # 2) 콜라주 영상 합성 (제출자는 실제 영상을 가장 긴 사람 기준으로 루프, 미제출자는 검은 화면 영상)
        collage_path = f"{tmp}/collage_{mission_id}.mp4"

        filter_parts = []
        inputs = []
        for i, (w, h, _, _) in enumerate(cells[:n]):
            if local_videos[i]:
                # hflip: FE CSS scaleX(-1) 미러 프리뷰와 저장 영상 일치
                # transpose 불필요: landscape 영상도 얼굴 픽셀은 정방향, scale+crop이 portrait 크롭 처리
                inputs += ["-stream_loop", "-1", "-i", local_videos[i]]
                filter_parts.append(
                    f"[{i}:v]trim=duration={duration},setpts=PTS-STARTPTS,"
                    f"hflip,"
                    f"scale={w}:{h}:force_original_aspect_ratio=increase,"
                    f"crop={w}:{h}:(in_w-{w})/2:(in_h-{h})/2,setsar=1,fps={FPS}[f{i}]"
                )
            else:
                missed_img_path = f"{tmp}/missed_{i}.png"
                build_missed_image(missed_img_path, w, h, submissions[i].get("name"))
                inputs += ["-loop", "1", "-i", missed_img_path]
                filter_parts.append(
                    f"[{i}:v]trim=duration={duration},setpts=PTS-STARTPTS,setsar=1,fps={FPS}[f{i}]"
                )

        filter_parts.append(
            f"color=black:size={CANVAS_W}x{CANVAS_H}:duration={duration}:rate={FPS}[base]"
        )
        prev = "base"
        for i in range(n):
            _, _, x, y = cells[i]
            next_label = "out" if i == n - 1 else f"o{i}"
            filter_parts.append(f"[{prev}][f{i}]overlay={x}:{y}[{next_label}]")
            prev = f"o{i}"

        filter_complex = ";".join(filter_parts)

        cmd = (
            ["/opt/ffmpeg", "-y", "-nostats", "-loglevel", "error"]
            + inputs
            + ["-filter_complex", filter_complex, "-map", "[out]",
               "-t", str(duration), "-r", str(FPS), "-pix_fmt", "yuv420p",
               "-c:v", "libx264", "-preset", "veryfast", "-movflags", "+faststart",
               collage_path]
        )

        result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode != 0 or not os.path.exists(collage_path):
            print("Collage video creation failed")
            send_callback(callback_url, callback_secret, {
                "missionId": mission_id, "success": False, "error": "Collage video creation failed"
            })
            return {"statusCode": 500}

        # 3) 합쳐진 콜라주 영상에서 썸네일 프레임 추출 (영상-썸네일 1:1 일치 보장)
        thumb_path = f"{tmp}/thumbnail_{mission_id}.jpg"
        seek = duration * 0.3
        subprocess.run(
            ["/opt/ffmpeg", "-y", "-nostats", "-loglevel", "error",
             "-ss", str(seek), "-i", collage_path,
             "-vframes", "1", "-q:v", "2",
             "-vf", f"scale=960:540:force_original_aspect_ratio=decrease,"
                    f"pad=960:540:(ow-iw)/2:(oh-ih)/2:black",
             thumb_path],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        if not os.path.exists(thumb_path):
            send_callback(callback_url, callback_secret, {
                "missionId": mission_id, "success": False, "error": "Thumbnail extraction failed"
            })
            return {"statusCode": 500}

        # 4) S3 업로드
        # mission_id는 DB 리셋 시 재사용될 수 있어 같은 키로 덮어써질 때 브라우저가
        # 이전 캐시를 그대로 보여주는 문제가 있었음 → no-cache 헤더 + 캐시 버스팅 쿼리스트링 부여
        cache_bust = int(time.time())

        video_key = f"collages/{mission_id}/collage.mp4"
        s3.upload_file(collage_path, BUCKET, video_key, ExtraArgs={
            "ContentType": "video/mp4", "CacheControl": "no-cache, max-age=0, must-revalidate"
        })
        collage_video_url = f"https://{BUCKET}.s3.{REGION}.amazonaws.com/{video_key}?v={cache_bust}"

        thumb_key = f"collages/{mission_id}/thumbnail.jpg"
        s3.upload_file(thumb_path, BUCKET, thumb_key, ExtraArgs={
            "ContentType": "image/jpeg", "CacheControl": "no-cache, max-age=0, must-revalidate"
        })
        thumbnail_url = f"https://{BUCKET}.s3.{REGION}.amazonaws.com/{thumb_key}?v={cache_bust}"

        print(f"Collage video uploaded: {collage_video_url}")
        print(f"Thumbnail uploaded: {thumbnail_url}")

        submitted_count = len([s for s in submissions if s.get("status") == "SUBMITTED"])

        send_callback(callback_url, callback_secret, {
            "missionId": mission_id,
            "success": True,
            "collageVideoUrl": collage_video_url,
            "thumbnailUrl": thumbnail_url,
            "submittedCount": submitted_count,
        })

        return {"statusCode": 200, "collageVideoUrl": collage_video_url, "thumbnailUrl": thumbnail_url}
