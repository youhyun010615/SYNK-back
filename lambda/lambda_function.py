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


def build_missed_image(path, w, h, name):
    """미제출자 칸 placeholder — ffmpeg에 drawtext가 없어 Pillow로 직접 그려서
    PNG로 저장한 뒤 ffmpeg 입력 이미지로 합성한다."""
    img = Image.new("RGB", (w, h), (45, 42, 85))
    draw = ImageDraw.Draw(img)

    stripe_color = (58, 54, 102)
    spacing = max(int(h * 0.06), 20)
    for x in range(-h, w, spacing):
        draw.line([(x, 0), (x + h, h)], fill=stripe_color, width=2)

    cx, cy = w // 2, int(h * 0.34)
    r = max(int(h * 0.11), 18)
    icon_color = (150, 140, 220)
    lw = max(int(h * 0.006), 3)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=icon_color, width=lw)
    bw, bh = int(r * 0.8), int(r * 0.55)
    draw.rounded_rectangle(
        [cx - bw, cy - bh // 2, cx + bw // 2, cy + bh // 2],
        radius=max(int(r * 0.1), 4), outline=icon_color, width=lw,
    )
    tri = int(bw * 0.45)
    draw.polygon(
        [
            (cx + bw // 2, cy - bh // 3),
            (cx + bw // 2 + tri, cy - tri),
            (cx + bw // 2 + tri, cy + tri),
            (cx + bw // 2, cy + bh // 3),
        ],
        outline=icon_color, width=lw,
    )
    draw.line([(cx - r + lw, cy - r + lw), (cx + r - lw, cy + r - lw)], fill=icon_color, width=lw + 2)

    name_size = max(int(h * 0.05), 14)
    sub_size = max(int(h * 0.038), 12)
    name_font = ImageFont.truetype(FONT_PATH, name_size)
    sub_font = ImageFont.truetype(FONT_PATH, sub_size)

    display_name = (name or "").strip()
    if display_name:
        tw = draw.textlength(display_name, font=name_font)
        draw.text((cx - tw / 2, int(h * 0.48)), display_name, font=name_font, fill=(201, 196, 240))

    sub_text = "다음엔 꼭.."
    tw = draw.textlength(sub_text, font=sub_font)
    draw.text((cx - tw / 2, int(h * 0.58)), sub_text, font=sub_font, fill=(157, 151, 209))

    img.save(path)


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

        if not durations:
            send_callback(callback_url, callback_secret, {
                "missionId": mission_id, "success": False, "error": "No valid submitted videos"
            })
            return {"statusCode": 500}

        # 가장 길게 찍은 사람 기준으로 맞추고, 짧게 찍은 사람은 무한 루프로 채운다
        duration = min(max(durations), MAX_COLLAGE_DURATION)

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
