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

LAYOUTS = {
    1:  [[1]],
    2:  [[1], [1]],
    3:  [[1], [2]],
    4:  [[2], [2]],
    5:  [[2], [3]],
    6:  [[3], [3]],
    7:  [[3], [4]],
    8:  [[4], [4]],
    9:  [[3], [3], [3]],
    10: [[3], [3], [4]],
}

CANVAS_W = 540
CANVAS_H = 960
FPS = 24
MAX_COLLAGE_DURATION = 15.0  # 폭주 방지용 상한선
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "NanumGothic.ttc")


def build_missed_image(path, w, h, name, dark=True):
    """미제출자 placeholder PNG — synk-missed-tile CSS 스펙 기반, dark/light 모드 지원.
    dark=True: 네이비 #0c0e1a / dark=False: 크림 #f0ece2"""
    import random
    from PIL import ImageFilter

    # CSS 기준 타일 220x280 → 현재 셀 크기로 비례 스케일
    REF_W, REF_H = 220, 280
    def sx(v): return int(v * w / REF_W)
    def sy(v): return int(v * h / REF_H)
    def sc(v): return int(v * (w / REF_W + h / REF_H) / 2)

    # ── 배경 ──────────────────────────────────────────────────────────────────
    bg_rgb = (12, 14, 26) if dark else (240, 236, 226)
    img = Image.new("RGBA", (w, h), (*bg_rgb, 255))

    # ── 보케 블롭 (CSS: filter blur(28px), 절대 위치) ────────────────────────
    blob = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(blob)
    if dark:
        c1 = (245, 158, 11, 46)   # rgba(245,158,11,.18)
        c2 = (245, 158, 11, 31)   # rgba(245,158,11,.12)
        c3 = (124, 111, 240, 26)  # rgba(124,111,240,.10)
    else:
        c1 = (245, 158, 11, 51)   # rgba(245,158,11,.20)
        c2 = (245, 158, 11, 38)   # rgba(245,158,11,.15)
        c3 = (124, 111, 240, 20)  # rgba(124,111,240,.08)
    # blob-1: 140x140, top:-30 left:-30
    bd.ellipse([sx(-30), sy(-30), sx(-30)+sx(140), sy(-30)+sy(140)], fill=c1)
    # blob-2: 100x100, bottom:10 right:-20
    bd.ellipse([w+sx(-20)-sx(100), h-sy(10)-sy(100), w+sx(-20), h-sy(10)], fill=c2)
    # blob-3: 70x70, top:50% left:60%
    bd.ellipse([int(w*0.60), int(h*0.50), int(w*0.60)+sx(70), int(h*0.50)+sy(70)], fill=c3)
    blob = blob.filter(ImageFilter.GaussianBlur(radius=max(sc(28), 8)))
    img = Image.alpha_composite(img, blob)

    # ── 필름 그레인 (CSS: SVG fractalNoise opacity .55, 타일링으로 근사) ─────
    tile = 64
    rng = random.Random(7)
    gt = Image.new("L", (tile, tile))
    gtp = gt.load()
    for gy in range(tile):
        for gx in range(tile):
            gtp[gx, gy] = rng.randint(0, 255)
    grain = Image.new("L", (w, h))
    for ty in range(0, h, tile):
        for tx in range(0, w, tile):
            grain.paste(gt, (tx, ty))
    img = Image.alpha_composite(img,
        Image.merge("RGBA", [grain, grain, grain, Image.new("L", (w, h), 14)]))

    draw = ImageDraw.Draw(img)
    cx, cy = w // 2, h // 2

    # ── 이름 첫 글자 대형 아웃라인 (CSS: 120px 900weight, text-stroke 1.5px) ─
    initial = ((name or "").strip() or " ")[0]
    init_px = sc(120)
    s_rgb  = (255, 255, 255) if dark else (0, 0, 0)
    s_a    = 36 if dark else 31        # .14 / .12
    try:
        ifont = ImageFont.truetype(FONT_PATH, init_px)
        bb = draw.textbbox((0, 0), initial, font=ifont)
        tx0 = cx - (bb[2]-bb[0])//2 - bb[0]
        ty0 = cy - (bb[3]-bb[1])//2 - bb[1]
        draw.text((tx0, ty0), initial, font=ifont,
                  fill=(*bg_rgb, 255),
                  stroke_width=max(2, int(init_px*0.013)),
                  stroke_fill=(*s_rgb, s_a))
    except Exception:
        pass

    # ── 아이콘 링 (CSS: 40x40, rgba(255,255,255,.06) bg, border .14) ─────────
    rrx, rry = sx(20), sy(20)
    rl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rd_ = ImageDraw.Draw(rl)
    if dark:
        rfill, rout = (255,255,255,15), (255,255,255,36)
    else:
        rfill, rout = (0,0,0,13), (0,0,0,31)
    rd_.ellipse([cx-rrx, cy-rry, cx+rrx, cy+rry],
                fill=rfill, outline=rout, width=max(1, sc(1)))
    img = Image.alpha_composite(img, rl)
    draw = ImageDraw.Draw(img)

    # ── video-off 아이콘 (Feather icons, viewBox 0 0 24 24, 17x17px 기준) ────
    iw, ih_ = sx(17), sy(17)
    iox, ioy = cx - iw//2, cy - ih_//2
    scx_, scy_ = iw/24.0, ih_/24.0

    def vp(x, y):
        return (iox + x*scx_, ioy + y*scy_)

    ic_s = (255,255,255,179) if dark else (30,20,10,153)
    ilw  = max(1, round(1.8 * (scx_+scy_)/2))
    # rect x=1 y=5 w=15 h=14 rx=2 → camera body
    draw.rounded_rectangle([vp(1,5), vp(16,19)],
                           radius=max(1, round(2*(scx_+scy_)/2)),
                           outline=ic_s, width=ilw)
    # M23 7 l-7 5 l7 5 V7 → video lens triangle
    draw.line([vp(23,7), vp(16,12), vp(23,17), vp(23,7)], fill=ic_s, width=ilw)
    # (1,1)→(23,23) → slash
    draw.line([vp(1,1), vp(23,23)], fill=ic_s, width=ilw+1)

    # ── 이름 라벨 (CSS: 13px 700, color rgba(255,255,255,.45)) ───────────────
    name_px = max(sc(13), 9)
    name_col = (255,255,255,115) if dark else (30,20,10,97)
    display_name = (name or "").strip()
    if display_name:
        try:
            nf = ImageFont.truetype(FONT_PATH, name_px)
            draw.text((cx - draw.textlength(display_name, font=nf)/2, cy+rry+sy(2)),
                      display_name, font=nf, fill=name_col)
        except Exception:
            pass

    # ── 하단 그라디언트 (CSS: rgba(0,0,0,.7) / rgba(220,210,190,.85)) ─────────
    grad_h_ = sy(80)
    glay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd_ = ImageDraw.Draw(glay)
    g_rgb = (0,0,0) if dark else (220,210,190)
    g_max = 179 if dark else 217
    for gy_ in range(grad_h_):
        a = int(g_max * (grad_h_-gy_) / grad_h_)
        yp = h - grad_h_ + gy_
        gd_.rectangle([0, yp, w, yp+1], fill=(*g_rgb, a))
    img = Image.alpha_composite(img, glay)
    draw = ImageDraw.Draw(img)

    # ── 하단 텍스트 (CSS: line1 11.5px 800, line2 10.5px 600) ────────────────
    l1_px = max(sc(11), 9)
    l2_px = max(sc(10), 8)
    l1_col = (255,255,255,191) if dark else (30,20,10,153)
    l2_col = (255,255,255, 97) if dark else (30,20,10, 89)
    l2_y = h - sy(20)
    l1_y = h - sy(36)
    try:
        f1 = ImageFont.truetype(FONT_PATH, l1_px)
        f2 = ImageFont.truetype(FONT_PATH, l2_px)
        t1 = "다음엔 꼭 함께해요"         # 이모지 제거 (NanumGothic 미지원)
        t2 = "이번 미션은 참여하지 않았어요"
        draw.text((cx - draw.textlength(t1, font=f1)/2, l1_y), t1, font=f1, fill=l1_col)
        draw.text((cx - draw.textlength(t2, font=f2)/2, l2_y), t2, font=f2, fill=l2_col)
    except Exception:
        pass

    img.convert("RGB").save(path)


def probe_duration(local_video):
    """ffprobe가 레이어에 없어 길이를 직접 계산한다.
    브라우저 MediaRecorder로 녹화된 webm/mp4는 헤더에 Duration이 없어(N/A)
    -i 만으로는 알 수 없으므로, 전체를 디코딩(-f null)해 마지막 time= 값을 읽는다."""
    result = subprocess.run(
        ["/opt/ffmpeg", "-i", local_video, "-f", "null", "-"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True
    )
    times = re.findall(r"time=(\d+):(\d+):(\d+(?:\.\d+)?)", result.stderr)
    if not times:
        return None
    h, m, s = times[-1]
    return int(h) * 3600 + int(m) * 60 + float(s)


def get_cells(rows_config):
    n_rows = len(rows_config)
    row_h = CANVAS_H // n_rows
    cells = []
    y = 0
    for row in rows_config:
        cols = row[0]
        cell_w = CANVAS_W // cols
        for col in range(cols):
            cells.append((cell_w, row_h, col * cell_w, y))
        y += row_h
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
        # 1) 제출된 영상 다운로드 + 길이 측정 (미제출자는 videoUrl 없음 → 검은 화면으로 채움)
        local_videos = [None] * n
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
            d = probe_duration(local_video)
            if d:
                durations.append(d)

        # 전원 미제출이면 정적 이미지 콜라주를 3초짜리 영상으로 생성
        duration = min(max(durations), MAX_COLLAGE_DURATION) if durations else 3.0

        # 2) 콜라주 영상 합성 (제출자는 실제 영상을 가장 긴 사람 기준으로 루프, 미제출자는 검은 화면 영상)
        collage_path = f"{tmp}/collage_{mission_id}.mp4"

        filter_parts = []
        inputs = []
        for i, (w, h, _, _) in enumerate(cells[:n]):
            if local_videos[i]:
                # 영상이 duration보다 짧으면 반복, 길면 트림으로 길이를 통일
                inputs += ["-stream_loop", "-1", "-i", local_videos[i]]
                filter_parts.append(
                    f"[{i}:v]trim=duration={duration},setpts=PTS-STARTPTS,"
                    f"scale={w}:{h}:force_original_aspect_ratio=increase,"
                    f"crop={w}:{h},setsar=1,fps={FPS}[f{i}]"
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
             "-vframes", "1", "-q:v", "2", thumb_path],
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
