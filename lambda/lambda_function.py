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
    7:  [[2], [2], [2], [2, 1]],
    8:  [[2], [2], [2], [2]],
    9:  [[2], [2], [2], [2], [2, 1]],
    10: [[2], [2], [2], [2], [2]],
}

CANVAS_W = 720
CANVAS_H = 1280
FPS = 30
MAX_COLLAGE_DURATION = 15.0  # 폭주 방지용 상한선
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "NanumGothic.ttc")


def build_missed_image(path, w, h, name, dark=True):
    """미제출자 placeholder PNG — FE .missed-panel 스펙 재현
    (base #0c0e1c + 상단 블루 글로우 + 대각선 스트라이프 + 글래스 카드 + 미참여 배지)"""
    from PIL import ImageFilter

    short = min(w, h)
    cx = w // 2

    # ── base ────────────────────────────────────────────────────────────────
    img = Image.new("RGBA", (w, h), (12, 14, 28, 255))  # #0c0e1c

    # ── 상단 블루 글로우 (radial, 상단 중앙) ─────────────────────────────────
    sw, sh = max(2, w // 6), max(2, h // 6)
    gcx, gcy = sw * 0.5, sh * 0.16
    grad = sh * 0.62  # 글로우 반경
    glow_col = (58, 96, 178)  # 파랑
    small = []
    for y in range(sh):
        for x in range(sw):
            d = (((x - gcx) ** 2 + (y - gcy) ** 2) ** 0.5) / grad
            a = 0.0 if d >= 1.0 else (1.0 - d) ** 1.6
            small.append((glow_col[0], glow_col[1], glow_col[2], int(150 * a)))
    glow = Image.new("RGBA", (sw, sh))
    glow.putdata(small)
    img = Image.alpha_composite(img, glow.resize((w, h), Image.BILINEAR))

    # ── 대각선 스트라이프 텍스처 (-55deg, opacity 0.025) ─────────────────────
    stripe = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(stripe)
    step = max(14, int(short * 0.06))
    dx = int(h * 1.5)  # -55도 기울기용 수평 이동량
    sa = 7  # 0.025 * 255
    for i in range(-dx, w + dx, step):
        sd.line([(i, h), (i + dx, 0)], fill=(255, 255, 255, sa), width=1)
    img = Image.alpha_composite(img, stripe)

    def font(px, bold=False):
        try:
            return ImageFont.truetype(FONT_PATH, px, index=1 if bold else 0)
        except Exception:
            try:
                return ImageFont.truetype(FONT_PATH, px)
            except Exception:
                return None

    # 반투명 요소는 별도 레이어에 그린 뒤 합성해야 알파 블렌딩됨
    # (ImageDraw는 RGBA 이미지에 반투명 fill을 덮어써버려 불투명해짐)
    ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov, "RGBA")

    # ── 글래스 카드 (아주 옅은 반투명 rounded rect) ──────────────────────────
    card = int(short * 0.30)
    cyc = int(h * 0.27)
    x0, y0 = cx - card // 2, cyc - card // 2
    d.rounded_rectangle([x0, y0, x0 + card, y0 + card], radius=int(card * 0.30),
                        fill=(255, 255, 255, 20), outline=(255, 255, 255, 40),
                        width=max(2, card // 55))

    # ── video-off 아이콘 (카드 중앙, 얇은 회백색 선) ─────────────────────────
    isize = int(card * 0.44)
    iox, ioy = cx - isize // 2, cyc - isize // 2
    sx_ = sy_ = isize / 24.0

    def vp(x, y):
        return (iox + x * sx_, ioy + y * sy_)

    ic = (200, 206, 220, 150)
    ilw = max(2, round(1.6 * sx_))
    d.rounded_rectangle([vp(1, 5), vp(16, 19)], radius=max(1, round(2 * sx_)),
                        outline=ic, width=ilw)
    d.line([vp(23, 7), vp(16, 12), vp(23, 17), vp(23, 7)], fill=ic, width=ilw)
    d.line([vp(1, 1), vp(23, 23)], fill=ic, width=ilw + 1)

    # ── 이름 (밝은 흰색, Bold) ───────────────────────────────────────────────
    display_name = (name or "").strip()
    name_y = cyc + card // 2 + int(short * 0.08)
    nf = font(max(int(short * 0.085), 14), bold=True)
    if display_name and nf:
        d.text((cx - d.textlength(display_name, font=nf) / 2, name_y),
               display_name, font=nf, fill=(236, 239, 247, 255))
        name_h = (nf.getbbox(display_name)[3] - nf.getbbox(display_name)[1])
    else:
        name_h = int(short * 0.085)

    # ── 미참여 배지 (은은한 빨간 글로우 pill + 빨간 점) ──────────────────────
    bf = font(max(int(short * 0.058), 11), bold=True)
    pill_y = name_y + name_h + int(short * 0.10)
    if bf:
        label = "미참여"
        tw = d.textlength(label, font=bf)
        dot_r = max(2, int(short * 0.011))
        pad_x = int(short * 0.05)
        gap = int(short * 0.028)
        pill_w = dot_r * 2 + gap + tw + pad_x * 2
        pill_h = int(short * 0.10)
        px0 = cx - pill_w / 2

        # 은은한 빨간 외곽 글로우 (base 위에 먼저 합성)
        glow_pill = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        gp = ImageDraw.Draw(glow_pill)
        gm = int(short * 0.022)
        gp.rounded_rectangle([px0 - gm, pill_y - gm, px0 + pill_w + gm, pill_y + pill_h + gm],
                             radius=(pill_h + 2 * gm) // 2, fill=(226, 62, 78, 55))
        glow_pill = glow_pill.filter(ImageFilter.GaussianBlur(int(short * 0.018)))
        img = Image.alpha_composite(img, glow_pill)

        # pill 본체 (반투명, overlay 레이어에)
        d.rounded_rectangle([px0, pill_y, px0 + pill_w, pill_y + pill_h],
                            radius=pill_h // 2, fill=(150, 44, 56, 210),
                            outline=(220, 84, 98, 165), width=max(1, int(short * 0.004)))
        dcy = pill_y + pill_h / 2
        dcx = px0 + pad_x + dot_r
        d.ellipse([dcx - dot_r, dcy - dot_r, dcx + dot_r, dcy + dot_r],
                  fill=(242, 96, 108, 255))
        bbox = bf.getbbox(label)
        d.text((dcx + dot_r + gap, dcy - (bbox[3] - bbox[1]) / 2 - bbox[1]),
               label, font=bf, fill=(246, 174, 182, 255))

    # ── 안내문구 ──────────────────────────────────────────────────────────────
    f1 = font(max(int(short * 0.046), 9))
    if f1:
        t1 = "다음엔 꼭 함께해요"
        d.text((cx - d.textlength(t1, font=f1) / 2, pill_y + int(short * 0.17)),
               t1, font=f1, fill=(140, 147, 166, 210))

    img = Image.alpha_composite(img, ov)
    img.convert("RGB").save(path)


def build_name_overlay(path, w, h, name):
    """제출된 영상 셀 오른쪽 하단에 이름 표시하는 투명 PNG 생성 (작은 pill)"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if not name:
        img.save(path)
        return

    d = ImageDraw.Draw(img)
    font_size = max(int(h * 0.035), 10)   # 기존 0.07 → 0.035 (절반으로)
    try:
        font = ImageFont.truetype(FONT_PATH, font_size)
    except Exception:
        font = ImageFont.load_default()

    pad_x = int(h * 0.018)
    pad_y = int(h * 0.012)
    text_w = int(d.textlength(name, font=font))
    bbox = font.getbbox(name)
    text_h = bbox[3] - bbox[1]

    bar_w = text_w + pad_x * 2
    bar_h = text_h + pad_y * 2

    # 오른쪽 하단 배치
    margin = int(h * 0.03)
    bx = w - bar_w - margin
    by = h - bar_h - margin

    bar = Image.new("RGBA", (bar_w, bar_h), (0, 0, 0, 160))
    img.paste(bar, (bx, by), bar)
    d.text((bx + pad_x, by + pad_y - bbox[1]), name, font=font, fill=(255, 255, 255, 220))
    img.save(path)


def build_mission_title_overlay(path, canvas_w, canvas_h, title):
    """콜라주 전체 왼쪽 상단에 미션 제목 오버레이 (SYNK 브랜드 스타일)"""
    img = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    if not title:
        img.save(path)
        return

    d = ImageDraw.Draw(img)
    font_size = max(int(canvas_h * 0.026), 11)  # 조금 작게
    try:
        font = ImageFont.truetype(FONT_PATH, font_size, index=1)  # bold
    except Exception:
        try:
            font = ImageFont.truetype(FONT_PATH, font_size)
        except Exception:
            font = ImageFont.load_default()

    pad_x = int(canvas_h * 0.018)
    pad_y = int(canvas_h * 0.012)
    margin = int(canvas_h * 0.025)

    text_w = int(d.textlength(title, font=font))
    bbox = font.getbbox(title)
    text_h = bbox[3] - bbox[1]

    bar_w = text_w + pad_x * 2
    bar_h = text_h + pad_y * 2

    # 반투명 둥근 pill 배경 (도트 없음, 모서리 라운드)
    radius = int(bar_h * 0.38)
    d.rounded_rectangle([margin, margin, margin + bar_w, margin + bar_h],
                        radius=radius, fill=(0, 0, 0, 170))

    # 미션 제목 텍스트
    tx = margin + pad_x
    ty = margin + pad_y - bbox[1]
    d.text((tx, ty), title, font=font, fill=(255, 255, 255, 240))
    img.save(path)


def probe_video_info(local_video):
    """ffprobe가 레이어에 없어 ffmpeg stderr에서 직접 파싱한다.
    Returns: (duration_seconds, width, height, rotation_degrees)"""
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

    dim_match = re.search(r'Video:.*?\s(\d{2,4})x(\d{2,4})', stderr)
    width = int(dim_match.group(1)) if dim_match else None
    height = int(dim_match.group(2)) if dim_match else None

    # 폰 가로 녹화 시 MP4에 rotation 메타데이터가 있을 수 있음 (e.g. rotate: 90)
    rotate_match = re.search(r'rotate\s*:\s*(-?\d+)', stderr)
    rotation = int(rotate_match.group(1)) if rotate_match else 0

    return duration, width, height, rotation


CELL_GAP = 6  # 셀 간 간격 (px)

def get_cells(rows_config):
    n_rows = len(rows_config)
    total_gap_h = CELL_GAP * (n_rows + 1)
    row_h = (CANVAS_H - total_gap_h) // n_rows  # 캔버스 높이를 행 수로 균등 분할
    cells = []
    y = CELL_GAP
    for row in rows_config:
        cols = row[0]                                   # 그리드 열 수 (셀 폭 계산 기준)
        placed = row[1] if len(row) > 1 else cols       # 이 행에 실제 배치할 셀 개수
        total_gap_w = CELL_GAP * (cols + 1)
        cell_w = (CANVAS_W - total_gap_w) // cols
        # 부분 행(placed < cols)은 좌우 가운데 정렬
        placed_w = cell_w * placed + CELL_GAP * (placed - 1)
        x = (CANVAS_W - placed_w) // 2 if placed < cols else CELL_GAP
        for _ in range(placed):
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
    mission_title = event.get("missionTitle", "")
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
        # 1) 제출된 영상 다운로드 + 길이·해상도·rotation 측정
        local_videos = [None] * n
        video_dims = {}     # index → (width, height)
        video_rotations = {}  # index → rotation degrees (0/90/180/270)
        durations = []
        for i, sub in enumerate(submissions):
            video_url = sub.get("videoUrl")
            if not video_url:
                continue
            bucket, key = parse_s3_url(video_url)
            local_raw = f"{tmp}/raw_{i}.mp4"
            print(f"Downloading s3://{bucket}/{key}")
            s3.download_file(bucket, key, local_raw)

            # raw에서 rotation 메타데이터 먼저 읽기
            d, vid_w, vid_h, rotation = probe_video_info(local_raw)
            horizontal_flag = submissions[i].get("horizontal", False)
            facing_flag = submissions[i].get("facingMode")
            print(f"  → video[{i}] {vid_w}x{vid_h}, duration={d}, rotation={rotation}, horizontal={horizontal_flag}, facingMode={facing_flag}")

            # rotation 메타데이터 제거 (stream copy) — FFmpeg autorotate 방지
            # filter_complex에서 rotation을 수동으로 보정할 것
            local_video = f"{tmp}/input_{i}.mp4"
            strip = subprocess.run(
                ["/opt/ffmpeg", "-y", "-loglevel", "error",
                 "-i", local_raw, "-c", "copy", "-map_metadata", "-1", "-map", "0",
                 local_video],
                stderr=subprocess.PIPE, text=True
            )
            if strip.returncode != 0:
                print(f"  metadata strip failed, using raw: {strip.stderr}")
                local_video = local_raw

            local_videos[i] = local_video
            if d:
                durations.append(d)
            if vid_w and vid_h:
                video_dims[i] = (vid_w, vid_h)
            video_rotations[i] = rotation

        # 전원 미제출이면 정적 이미지 콜라주를 3초짜리 영상으로 생성
        duration = min(max(durations), MAX_COLLAGE_DURATION) if durations else 3.0

        # 2) 콜라주 영상 합성 (제출자는 실제 영상을 가장 긴 사람 기준으로 루프, 미제출자는 검은 화면 영상)
        collage_path = f"{tmp}/collage_{mission_id}.mp4"

        filter_parts = []
        inputs = []
        for i, (w, h, _, _) in enumerate(cells[:n]):
            if local_videos[i]:
                # rotation 메타데이터 기반 수동 보정 (메타데이터는 이미 제거됨)
                rotation = video_rotations.get(i, 0)
                is_portrait_horizontal = submissions[i].get("horizontal") and video_dims.get(i, (1, 0))[0] < video_dims.get(i, (0, 1))[1]

                # 신규 FE는 녹화 시 캔버스로 회전·미러를 파일에 직접 굽는다(WYSIWYG).
                # → 새 파일(가로 픽셀)은 필터 없이 그대로 사용.
                # 아래 분기는 rotation 메타데이터가 있는 파일과 구버전 FE(세로 픽셀 + horizontal)용 레거시 보정.
                if rotation == 90:
                    rotate_filter = "transpose=1,"
                elif rotation in (270, -90):
                    rotate_filter = "transpose=2,"
                elif abs(rotation) == 180:
                    rotate_filter = "vflip,hflip,"
                elif is_portrait_horizontal:
                    # 레거시: Chrome Android 가로 촬영 (세로 픽셀에 회전된 얼굴)
                    if submissions[i].get("facingMode") == "environment":
                        rotate_filter = "transpose=1,vflip,hflip,"  # 후면: 회전 + 180도 보정
                    else:
                        rotate_filter = "transpose=1,hflip,"        # 전면: 회전 + 미러
                else:
                    rotate_filter = ""

                inputs += ["-stream_loop", "-1", "-i", local_videos[i]]
                filter_parts.append(
                    f"[{i}:v]trim=duration={duration},setpts=PTS-STARTPTS,"
                    f"{rotate_filter}"
                    f"scale={w}:{h}:force_original_aspect_ratio=increase,"
                    f"crop={w}:{h}:(in_w-{w})/2:(in_h-{h})/2,setsar=1,fps={FPS}[f{i}_raw]"
                )
            else:
                missed_img_path = f"{tmp}/missed_{i}.png"
                build_missed_image(missed_img_path, w, h, submissions[i].get("name"))
                inputs += ["-loop", "1", "-i", missed_img_path]
                filter_parts.append(
                    f"[{i}:v]trim=duration={duration},setpts=PTS-STARTPTS,setsar=1,fps={FPS}[f{i}]"
                )

        # 제출된 영상 셀에 이름 오버레이 PNG 추가 (입력 인덱스 n부터 시작)
        name_input_idx = n
        for i, (w, h, _, _) in enumerate(cells[:n]):
            if local_videos[i]:
                name = submissions[i].get("name", "")
                name_png = f"{tmp}/name_{i}.png"
                build_name_overlay(name_png, w, h, name)
                inputs += ["-loop", "1", "-i", name_png]
                filter_parts.append(
                    f"[f{i}_raw][{name_input_idx}:v]overlay=0:0[f{i}]"
                )
                name_input_idx += 1

        filter_parts.append(
            f"color=black:size={CANVAS_W}x{CANVAS_H}:duration={duration}:rate={FPS}[base]"
        )
        prev = "base"
        for i in range(n):
            _, _, x, y = cells[i]
            next_label = "assembled" if i == n - 1 else f"o{i}"
            filter_parts.append(f"[{prev}][f{i}]overlay={x}:{y}[{next_label}]")
            prev = f"o{i}"

        # 미션 제목 오버레이 (콜라주 전체 왼쪽 상단)
        title_png = f"{tmp}/mission_title.png"
        build_mission_title_overlay(title_png, CANVAS_W, CANVAS_H, mission_title)
        inputs += ["-loop", "1", "-i", title_png]
        filter_parts.append(f"[assembled][{name_input_idx}:v]overlay=0:0[out]")

        filter_complex = ";".join(filter_parts)

        cmd = (
            ["/opt/ffmpeg", "-y", "-nostats", "-loglevel", "error"]
            + inputs
            + ["-filter_complex", filter_complex, "-map", "[out]",
               "-t", str(duration), "-r", str(FPS), "-pix_fmt", "yuv420p",
               "-c:v", "libx264", "-preset", "faster", "-crf", "20",
               "-movflags", "+faststart",
               collage_path]
        )

        result = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        if result.returncode != 0 or not os.path.exists(collage_path):
            print(f"Collage video creation failed:\n{result.stderr[-3000:]}")
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
             "-vf", f"scale=1280:720:force_original_aspect_ratio=decrease,"
                    f"pad=1280:720:(ow-iw)/2:(oh-ih)/2:black",
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
