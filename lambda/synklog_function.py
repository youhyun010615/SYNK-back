"""
synklog_function.py — SYNKLOG "Day in My Life" style (9:16 릴스)

구조:
  [날짜 인트로 2s]
  → MISSION 01 타이틀 카드 1.5s → 콜라주 영상 (9:16 블러백 + 하단 미션 제목)
  → MISSION 02 타이틀 카드 1.5s → 콜라주 영상
  → ...
  → [SYNK 아웃트로 1.5s]

Payload (BE):
{
  "synklogId": 123,
  "videos": [{"url": "https://...", "title": "미션제목"}, ...],
  "date": "2026-07-03",
  "callbackUrl": "...",
  "callbackSecret": "..."
}
"""

import json, os, subprocess, tempfile, urllib.request, uuid, re
import boto3

S3_BUCKET = os.environ.get("S3_BUCKET", "synk-videos")
FFMPEG = "/opt/ffmpeg"
FONT   = os.path.join(os.path.dirname(os.path.abspath(__file__)), "NotoSansKR-Bold.otf")

W, H, FPS = 1080, 1920, 30
FADE = 0.3

ENC = [
    "-c:v", "libx264", "-preset", "faster", "-crf", "22",
    "-pix_fmt", "yuv420p", "-r", str(FPS),
    "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2",
    "-video_track_timescale", "90000",
]

s3 = boto3.client("s3")


# ── 이미지 생성 (Pillow) ──────────────────────────────────────────────────────

def _make_image(tmp: str, name: str, draw_fn) -> str:
    from PIL import Image, ImageDraw, ImageFont
    img  = Image.new("RGB", (W, H), (10, 13, 42))
    draw = ImageDraw.Draw(img)

    def load(size):
        try:    return ImageFont.truetype(FONT, size)
        except: return ImageFont.load_default()

    draw_fn(img, draw, load)
    path = os.path.join(tmp, name)
    img.save(path)
    return path


def _centered(draw, text, font, y, color, img_w=W):
    from PIL import ImageDraw
    bbox = draw.textbbox((0, 0), text, font=font)
    x = (img_w - (bbox[2] - bbox[0])) // 2
    draw.text((x, y), text, font=font, fill=color)


def make_date_intro_image(tmp: str, date_str: str) -> str:
    """날짜 인트로 이미지 — 2026.07.03 / TODAY'S SYNKLOG / SYNK"""
    parts = date_str.split("-")
    if len(parts) == 3:
        label = f"{parts[0]}.{parts[1]}.{parts[2]}"
    else:
        label = date_str

    def draw_fn(img, draw, load):
        from PIL import Image
        # 상단 그라데이션 느낌: 오버레이 레이어
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        # 날짜
        _centered(draw, label,             load(96),  H // 2 - 120, (255, 255, 255))
        _centered(draw, "TODAY'S SYNKLOG", load(44),  H // 2 + 20,  (232, 115, 90))
        # 구분선
        draw.line([(W//2 - 120, H//2 - 10), (W//2 + 120, H//2 - 10)],
                  fill=(255, 255, 255, 80), width=1)
        # 하단 SYNK
        _centered(draw, "SYNK", load(40), H - 200, (255, 255, 255, 100))

    return _make_image(tmp, "date_intro.png", draw_fn)


def make_title_card_image(tmp: str, index: int, title: str) -> str:
    """미션 타이틀 카드 이미지."""
    def draw_fn(img, draw, load):
        label = f"MISSION {index + 1:02d}"
        _centered(draw, label, load(44), H // 2 - 130, (232, 115, 90))
        # 구분선
        draw.line([(W//2 - 100, H//2 - 60), (W//2 + 100, H//2 - 60)],
                  fill=(255, 255, 255, 60), width=1)
        _centered(draw, title, load(70), H // 2 - 30, (255, 255, 255))
        _centered(draw, "SYNK", load(36), H - 200, (255, 255, 255, 80))

    return _make_image(tmp, f"card_{index:03d}.png", draw_fn)


def make_caption_overlay_image(tmp: str, index: int, title: str) -> str:
    """영상 위 하단 캡션 오버레이 (반투명 배경 + 미션 제목)."""
    from PIL import Image, ImageDraw, ImageFont
    img  = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    try:    font = ImageFont.truetype(FONT, 46)
    except: font = ImageFont.load_default()

    # 하단 반투명 바
    bar_h = 120
    bar_y = H - bar_h - 100
    draw.rectangle([(0, bar_y), (W, bar_y + bar_h)], fill=(0, 0, 0, 160))

    # 미션 제목
    bbox = draw.textbbox((0, 0), title, font=font)
    tx = (W - (bbox[2] - bbox[0])) // 2
    ty = bar_y + (bar_h - (bbox[3] - bbox[1])) // 2
    draw.text((tx, ty), title, font=font, fill=(255, 255, 255, 255))

    path = os.path.join(tmp, f"caption_{index:03d}.png")
    img.save(path)
    return path


# ── FFmpeg 유틸 ───────────────────────────────────────────────────────────────

def run(cmd, timeout=600):
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if r.returncode != 0:
        print("FFmpeg 실패:", r.stderr[-3000:])
        raise RuntimeError("ffmpeg failed")
    return r


def probe_duration(path: str) -> float:
    r = subprocess.run([FFMPEG, "-i", path], capture_output=True, text=True, timeout=30)
    m = re.search(r"Duration:\s*(\d+):(\d+):([\d.]+)", r.stderr)
    if m:
        return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))
    return 5.0


def has_audio(path: str) -> bool:
    r = subprocess.run([FFMPEG, "-i", path], capture_output=True, text=True, timeout=30)
    return "Audio:" in r.stderr


def image_to_video(img_path: str, duration: float, out: str, tmp: str):
    """정지 이미지 → 페이드인/아웃 영상 (무음 포함)."""
    fade_out_st = max(duration - FADE, 0)
    run([
        FFMPEG, "-y",
        "-loop", "1", "-i", img_path,
        "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo",
        "-t", str(duration),
        "-vf", (f"scale={W}:{H},setsar=1,"
                f"fade=t=in:st=0:d={FADE},"
                f"fade=t=out:st={fade_out_st}:d={FADE}"),
        "-map", "0:v", "-map", "1:a",
        *ENC, "-shortest", out,
    ], timeout=120)


def video_to_9x16(src: str, caption_png: str, out: str, tmp: str):
    """콜라주 영상 → 9:16 블러 배경 + 하단 캡션 오버레이."""
    dur = probe_duration(src)
    fade_out_st = max(dur - FADE, 0)

    # 캡션 오버레이를 영상 위에 합성
    fc = (
        # 블러 배경
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,"
        f"crop={W}:{H},boxblur=20:2,setsar=1[bg];"
        # 메인 영상 (세로 중앙)
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=decrease,setsar=1[fg];"
        # 캡션 오버레이 이미지
        f"[1:v]scale={W}:{H}[cap];"
        # 합성: bg + fg + cap
        f"[bg][fg]overlay=(W-w)/2:(H-h)/2[base];"
        f"[base][cap]overlay=0:0,"
        f"fade=t=in:st=0:d={FADE},"
        f"fade=t=out:st={fade_out_st}:d={FADE}[v]"
    )

    cmd = [FFMPEG, "-y", "-i", src, "-i", caption_png]
    if has_audio(src):
        audio_map = ["-map", "0:a"]
    else:
        cmd += ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
        audio_map = ["-map", "2:a", "-shortest"]

    run(cmd + [
        "-filter_complex", fc,
        "-map", "[v]", *audio_map,
        *ENC, out,
    ])


def make_thumbnail(video_path: str, out_path: str):
    subprocess.run(
        [FFMPEG, "-y", "-ss", "0.5", "-i", video_path,
         "-vframes", "1", "-vf", f"scale={W}:{H}", out_path],
        capture_output=True, timeout=60,
    )


# ── 다운로드 ──────────────────────────────────────────────────────────────────

def download(url: str, dest: str) -> bool:
    try:
        urllib.request.urlretrieve(url, dest)
        return os.path.getsize(dest) > 0
    except Exception as e:
        print(f"  [download] 실패: {e}")
        return False


# ── Lambda 핸들러 ─────────────────────────────────────────────────────────────

def lambda_handler(event, context):
    synklog_id   = event["synklogId"]
    callback_url = event["callbackUrl"]
    callback_sec = event["callbackSecret"]
    date_str     = event.get("date", "")

    # 신버전: videos [{url, title}] / 구버전 호환: videoUrls [str]
    videos = event.get("videos") or [
        {"url": u, "title": ""} for u in event.get("videoUrls", [])
    ]

    print(f"SYNKLOG Lambda 시작: synklogId={synklog_id}, videos={len(videos)}, date={date_str}")
    print(f"폰트 존재: {os.path.isfile(FONT)}")

    try:
        with tempfile.TemporaryDirectory() as tmp:
            segments = []

            # 1. 날짜 인트로
            if date_str:
                intro_img = make_date_intro_image(tmp, date_str)
                intro_vid = os.path.join(tmp, "seg_intro.mp4")
                image_to_video(intro_img, 2.0, intro_vid, tmp)
                segments.append(intro_vid)
                print("날짜 인트로 생성 완료")

            # 2. 각 미션: 타이틀카드 + 영상
            for i, v in enumerate(videos):
                src = os.path.join(tmp, f"src_{i:03d}.mp4")
                if not download(v["url"], src):
                    print(f"  collage[{i}] 다운로드 실패 — 건너뜀")
                    continue

                title = (v.get("title") or "").strip()
                print(f"  collage[{i}] downloaded, title={title!r}")

                # 타이틀 카드
                if title:
                    card_img = make_title_card_image(tmp, i, title)
                    card_vid = os.path.join(tmp, f"seg_card_{i:03d}.mp4")
                    image_to_video(card_img, 1.5, card_vid, tmp)
                    segments.append(card_vid)

                # 영상 (9:16 + 캡션)
                cap_png = make_caption_overlay_image(tmp, i, title) if title else None
                seg_vid = os.path.join(tmp, f"seg_vid_{i:03d}.mp4")
                if cap_png:
                    video_to_9x16(src, cap_png, seg_vid, tmp)
                else:
                    # 제목 없으면 캡션 없이 9:16만
                    dur = probe_duration(src)
                    fade_out_st = max(dur - FADE, 0)
                    fc = (
                        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,"
                        f"crop={W}:{H},boxblur=20:2,setsar=1[bg];"
                        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=decrease,setsar=1[fg];"
                        f"[bg][fg]overlay=(W-w)/2:(H-h)/2,"
                        f"fade=t=in:st=0:d={FADE},fade=t=out:st={fade_out_st}:d={FADE}[v]"
                    )
                    cmd = [FFMPEG, "-y", "-i", src]
                    if has_audio(src):
                        audio_map = ["-map", "0:a"]
                    else:
                        cmd += ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
                        audio_map = ["-map", "1:a", "-shortest"]
                    run(cmd + ["-filter_complex", fc, "-map", "[v]", *audio_map, *ENC, seg_vid])
                segments.append(seg_vid)

            # 3. 아웃트로
            def draw_outro(img, draw, load):
                _centered(draw, "SYNK",           load(96), H // 2 - 80,  (255, 255, 255))
                _centered(draw, "오늘도 함께해줘서 고마워요", load(38), H // 2 + 60,  (200, 200, 200))
                _centered(draw, date_str.replace("-", "."), load(32), H // 2 + 130, (232, 115, 90))

            outro_img = _make_image(tmp, "outro.png", draw_outro)
            outro_vid = os.path.join(tmp, "seg_outro.mp4")
            image_to_video(outro_img, 2.0, outro_vid, tmp)
            segments.append(outro_vid)
            print("아웃트로 생성 완료")

            if not segments:
                _callback(callback_url, callback_sec, synklog_id, False, error="생성된 세그먼트 없음")
                return

            # 4. 전체 concat (stream copy — 동일 인코딩이므로 빠름)
            concat_list = os.path.join(tmp, "concat.txt")
            with open(concat_list, "w") as f:
                for p in segments:
                    f.write(f"file '{p}'\n")

            out_video = os.path.join(tmp, "synklog.mp4")
            run([
                FFMPEG, "-y",
                "-f", "concat", "-safe", "0", "-i", concat_list,
                "-c", "copy",
                "-movflags", "+faststart",
                out_video,
            ])
            print(f"concat 완료: {len(segments)}개 세그먼트")

            # 5. 썸네일 (날짜 인트로 첫 프레임)
            thumb_path = os.path.join(tmp, "thumbnail.jpg")
            make_thumbnail(out_video, thumb_path)

            # 6. S3 업로드
            key_prefix = f"synklogs/{synklog_id}/{uuid.uuid4().hex}"
            video_key  = f"{key_prefix}/synklog.mp4"
            thumb_key  = f"{key_prefix}/thumbnail.jpg"

            s3.upload_file(out_video, S3_BUCKET, video_key,
                           ExtraArgs={"ContentType": "video/mp4"})
            video_url_out = f"https://{S3_BUCKET}.s3.ap-northeast-2.amazonaws.com/{video_key}"

            thumb_url_out = None
            if os.path.exists(thumb_path) and os.path.getsize(thumb_path) > 0:
                s3.upload_file(thumb_path, S3_BUCKET, thumb_key,
                               ExtraArgs={"ContentType": "image/jpeg"})
                thumb_url_out = f"https://{S3_BUCKET}.s3.ap-northeast-2.amazonaws.com/{thumb_key}"

            print(f"S3 업로드 완료: {video_url_out}")
            _callback(callback_url, callback_sec, synklog_id, True,
                      synklog_video_url=video_url_out, thumbnail_url=thumb_url_out)

    except Exception as e:
        print(f"[ERROR] 처리 실패: {e}")
        _callback(callback_url, callback_sec, synklog_id, False, error=str(e)[:500])
        raise


def _callback(url, secret, synklog_id, success,
              synklog_video_url=None, thumbnail_url=None, error=None):
    body = json.dumps({
        "synklogId": synklog_id, "success": success,
        "synklogVideoUrl": synklog_video_url,
        "thumbnailUrl": thumbnail_url, "error": error,
    }).encode()
    req = urllib.request.Request(
        url, data=body,
        headers={"Content-Type": "application/json", "X-Callback-Secret": secret},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            print(f"Callback 응답: {resp.status}")
    except Exception as e:
        print(f"Callback 실패: {e}")
