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
FONT      = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Korean.ttf")
FONT_BOLD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "NanumGothicBold.ttf")
LOGO      = os.path.join(os.path.dirname(os.path.abspath(__file__)), "SYNK 로고.jpeg")

# SYNK 브랜드 컬러
PURPLE = (155, 107, 255)   # #9B6BFF
CORAL  = (232, 115,  90)   # #E8735A
WHITE  = (255, 255, 255)
DIM    = (200, 200, 220)
BG_TOP = ( 14,  18,  38)   # #0e1226
BG_BOT = (  8,  11,  24)   # #08111b

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

def _gradient_bg() -> "Image":
    """SYNK 브랜드 그라데이션 배경 생성."""
    from PIL import Image
    col = Image.new("RGB", (1, H))
    for y in range(H):
        t = y / H
        r = int(BG_TOP[0] * (1 - t) + BG_BOT[0] * t)
        g = int(BG_TOP[1] * (1 - t) + BG_BOT[1] * t)
        b = int(BG_TOP[2] * (1 - t) + BG_BOT[2] * t)
        col.putpixel((0, y), (r, g, b))
    return col.resize((W, H), Image.NEAREST)


def _paste_logo_center(img, y_center: int, size: int = 220):
    """인트로/아웃트로 상단에 SYNK 로고 중앙 배치."""
    from PIL import Image
    if not os.path.isfile(LOGO):
        print(f"로고 파일 없음: {LOGO}")
        return
    try:
        logo = Image.open(LOGO).convert("RGB")
        logo.thumbnail((size, size), Image.LANCZOS)
        lw, lh = logo.size
        x = (W - lw) // 2
        y = y_center - lh // 2
        img.paste(logo, (x, y))
    except Exception as e:
        print(f"로고 붙이기 실패: {e}")


def _load_font(size: int, bold: bool = False):
    from PIL import ImageFont
    path = FONT_BOLD if bold else FONT
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        try:
            return ImageFont.truetype(FONT, size)
        except Exception:
            return ImageFont.load_default()


def _centered(draw, text, font, y, color, img_w=W):
    bbox = draw.textbbox((0, 0), text, font=font)
    x = (img_w - (bbox[2] - bbox[0])) // 2
    draw.text((x, y), text, font=font, fill=color)


def _draw_pill(draw, x1, y1, x2, y2, fill):
    """모서리가 둥근 pill 박스."""
    r = (y2 - y1) // 2
    draw.rounded_rectangle([(x1, y1), (x2, y2)], radius=r, fill=fill)


def make_date_intro_image(tmp: str, date_str: str) -> str:
    """날짜 인트로 — SYNK 로고 + 날짜 + TODAY'S SYNKLOG"""
    from PIL import Image, ImageDraw
    parts = date_str.split("-")
    label = f"{parts[0]}.{parts[1]}.{parts[2]}" if len(parts) == 3 else date_str

    img  = _gradient_bg().convert("RGB")
    draw = ImageDraw.Draw(img)

    # 상단 보라 액센트 바
    draw.rectangle([(0, 0), (W, 8)], fill=PURPLE)

    # SYNK 로고 상단 중앙
    _paste_logo_center(img, y_center=400, size=240)

    # 코랄 구분선
    line_y = H // 2 - 60
    draw.rectangle([(W//2 - 200, line_y), (W//2 + 200, line_y + 3)], fill=CORAL)

    # 날짜 (볼드, 대형)
    _centered(draw, label, _load_font(148, bold=True), H // 2 - 30, WHITE)

    # TODAY'S SYNKLOG 서브타이틀
    _centered(draw, "TODAY'S SYNKLOG", _load_font(58), H // 2 + 160, CORAL)

    # 하단 보라 액센트 바
    draw.rectangle([(0, H - 8), (W, H)], fill=PURPLE)

    path = os.path.join(tmp, "date_intro.jpg")
    img.save(path, format="JPEG", quality=95)
    return path


def _radial_glow(canvas_size, cx, cy, r, color, alpha):
    """중심에서 바깥으로 투명해지는 원형 글로우 레이어."""
    from PIL import Image as PILImage, ImageDraw as PILDraw
    try:
        import numpy as np
        cw, ch = canvas_size
        Y, X = np.mgrid[0:ch, 0:cw].astype(np.float32)
        dist = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        t = np.clip(dist / r, 0.0, 1.0)
        a = ((1.0 - t) * alpha * 255).astype(np.uint8)
        arr = np.zeros((ch, cw, 4), dtype=np.uint8)
        arr[:, :, 0] = color[0]
        arr[:, :, 1] = color[1]
        arr[:, :, 2] = color[2]
        arr[:, :, 3] = a
        return PILImage.fromarray(arr, "RGBA")
    except ImportError:
        img = PILImage.new("RGBA", canvas_size, (0, 0, 0, 0))
        d = PILDraw.Draw(img)
        steps = 12
        for i in range(steps, 0, -1):
            ri = int(r * i / steps)
            ai = int(alpha * (steps - i) / steps * 255)
            d.ellipse([int(cx-ri), int(cy-ri), int(cx+ri), int(cy+ri)], fill=(*color, ai))
        return img


def make_title_card_image(tmp: str, index: int, title: str,
                           mission_time: str = "", members: list = None) -> str:
    """Frame 3 스타일 미션 타이틀 카드 — 참여 현황 그리드 포함."""
    from PIL import Image, ImageDraw

    # 원본 디자인 390×693 → 1080×1920 스케일 (×2.769)
    S = 1080 / 390

    BG_CARD   = (7,  13,  42)
    BG_DEEP_C = (15, 30,  90)
    ORANGE_C  = (255, 140, 58)
    WHITE_C   = (255, 255, 255)
    RED_SOFT  = (255, 60,  60)
    CYAN_C    = (70, 215, 255)

    def sc(v):
        return int(v * S)

    img = Image.new("RGBA", (W, H), (*BG_CARD, 255))

    # 배경 글로우
    img = Image.alpha_composite(img, _radial_glow((W, H), W/2, -sc(20), max(W,H)*0.9, BG_DEEP_C, 0.70))
    img = Image.alpha_composite(img, _radial_glow((W, H), W/2, -sc(10), sc(280), CYAN_C,    0.14))
    img = Image.alpha_composite(img, _radial_glow((W, H), W+sc(30), H+sc(30), sc(200), ORANGE_C, 0.12))

    draw = ImageDraw.Draw(img)

    f_badge   = _load_font(sc(10), bold=True)
    f_sub     = _load_font(sc(11))
    f_name    = _load_font(sc(12))
    f_small   = _load_font(sc(9))
    f_title   = _load_font(sc(22), bold=True)

    HEADER_Y = sc(20)

    # MISSION 번호 태그
    tag_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tag_d = ImageDraw.Draw(tag_layer)
    tag_d.rounded_rectangle(
        [sc(20), HEADER_Y, sc(170), HEADER_Y + sc(24)],
        radius=sc(8), fill=(*ORANGE_C, 26)
    )
    tag_d.rounded_rectangle(
        [sc(20), HEADER_Y, sc(170), HEADER_Y + sc(24)],
        radius=sc(8), outline=(*ORANGE_C, 56), width=2
    )
    img = Image.alpha_composite(img, tag_layer)
    draw = ImageDraw.Draw(img)

    draw.ellipse(
        [sc(30), HEADER_Y + sc(9), sc(34), HEADER_Y + sc(13)],
        fill=(*ORANGE_C, 220)
    )
    draw.text((sc(38), HEADER_Y + sc(6)), f"MISSION {index + 1:02d}",
              font=f_badge, fill=(*ORANGE_C, 230))

    # 진행 도트 (우측)
    dx = W - sc(20) - sc(38)
    dy = HEADER_Y + sc(10)
    dot_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dot_d = ImageDraw.Draw(dot_layer)
    dot_d.rounded_rectangle([dx, dy-sc(1), dx+sc(18), dy+sc(2)], radius=sc(2), fill=(*ORANGE_C, 200))
    dot_d.rounded_rectangle([dx+sc(22), dy-sc(1), dx+sc(28), dy+sc(2)], radius=sc(2), fill=(*WHITE_C, 38))
    dot_d.rounded_rectangle([dx+sc(32), dy-sc(1), dx+sc(38), dy+sc(2)], radius=sc(2), fill=(*WHITE_C, 38))
    img = Image.alpha_composite(img, dot_layer)
    draw = ImageDraw.Draw(img)

    # 미션 제목
    TITLE_Y = HEADER_Y + sc(24) + sc(16)
    draw.text((sc(20), TITLE_Y), title, font=f_title, fill=(*WHITE_C, 255))

    participated_count = sum(1 for m in (members or []) if m.get("participated"))
    sub_text = f"{mission_time}  ·  {participated_count}명 참여" if mission_time else f"{participated_count}명 참여"
    draw.text((sc(20), TITLE_Y + sc(30)), sub_text, font=f_sub, fill=(*WHITE_C, 102))

    # 2×2 참여 그리드
    GRID_TOP   = TITLE_Y + sc(30) + sc(18) + sc(16)
    GRID_PAD_X = sc(20)
    GRID_GAP   = sc(5)
    RADIUS_C   = sc(14)
    CELL_W = (W - GRID_PAD_X * 2 - GRID_GAP) // 2
    CELL_H = (H - GRID_TOP - sc(20) - GRID_GAP) // 2

    display_members = (members or [])[:4]
    # 4명 미만이면 빈 슬롯 채우기
    while len(display_members) < 4:
        display_members.append({"name": "", "participated": False})

    for idx, member in enumerate(display_members):
        col = idx % 2
        row = idx // 2
        cx  = GRID_PAD_X + col * (CELL_W + GRID_GAP)
        cy  = GRID_TOP   + row * (CELL_H + GRID_GAP)

        cell_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        cell_d = ImageDraw.Draw(cell_layer)

        if member.get("participated"):
            bg_col = (20, 28, 72, 255) if col == 0 else (26, 20, 69, 255)
            cell_d.rounded_rectangle([cx, cy, cx+CELL_W, cy+CELL_H], radius=RADIUS_C, fill=bg_col)
            cell_d.rounded_rectangle([cx, cy, cx+CELL_W, cy+CELL_H], radius=RADIUS_C,
                                      outline=(150, 180, 255, 30), width=2)
            img = Image.alpha_composite(img, cell_layer)

            # 하단 그라데이션
            grad = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            grad_d = ImageDraw.Draw(grad)
            grad_h = sc(44)
            for py in range(cy + CELL_H - grad_h, cy + CELL_H):
                a = int(235 * (py - (cy + CELL_H - grad_h)) / grad_h)
                grad_d.rectangle([cx, py, cx+CELL_W, py+1], fill=(5, 8, 25, a))
            img = Image.alpha_composite(img, grad)
            draw = ImageDraw.Draw(img)

            # 체크 배지
            bx = cx + sc(10)
            by = cy + CELL_H - sc(28)
            badge_l = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            badge_d = ImageDraw.Draw(badge_l)
            badge_d.rounded_rectangle([bx, by, bx+sc(16), by+sc(16)], radius=sc(5),
                                       fill=(210, 100, 60, 230))
            img = Image.alpha_composite(img, badge_l)
            draw = ImageDraw.Draw(img)
            draw.text((bx + sc(3), by + sc(2)), "✓", font=f_small, fill=(*WHITE_C, 255))
            if member.get("name"):
                draw.text((bx + sc(20), by + sc(2)), member["name"], font=f_name, fill=(*WHITE_C, 230))

        else:
            cell_d.rounded_rectangle([cx, cy, cx+CELL_W, cy+CELL_H], radius=RADIUS_C,
                                      fill=(*WHITE_C, 6))
            cell_d.rounded_rectangle([cx, cy, cx+CELL_W, cy+CELL_H], radius=RADIUS_C,
                                      outline=(*WHITE_C, 15), width=2)
            img = Image.alpha_composite(img, cell_layer)
            draw = ImageDraw.Draw(img)

            mid_x = cx + CELL_W // 2
            mid_y = cy + CELL_H // 2

            av_l = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            av_d = ImageDraw.Draw(av_l)
            av_d.ellipse([mid_x-sc(18), mid_y-sc(42), mid_x+sc(18), mid_y-sc(6)],
                          fill=(*WHITE_C, 15), outline=(*WHITE_C, 25), width=2)
            img = Image.alpha_composite(img, av_l)
            draw = ImageDraw.Draw(img)

            draw.ellipse([mid_x-sc(5), mid_y-sc(38), mid_x+sc(5), mid_y-sc(28)],
                          fill=(*WHITE_C, 100))
            draw.arc([mid_x-sc(9), mid_y-sc(28), mid_x+sc(9), mid_y-sc(16)],
                      start=0, end=180, fill=(*WHITE_C, 100), width=sc(2))

            if member.get("name"):
                nb = draw.textbbox((0, 0), member["name"], font=f_name)
                nw = nb[2] - nb[0]
                draw.text((mid_x - nw // 2, mid_y - sc(10)), member["name"],
                           font=f_name, fill=(*WHITE_C, 97))

            # 미참여 배지
            badge_text = "미참여"
            bb2 = draw.textbbox((0, 0), badge_text, font=f_small)
            bw2 = bb2[2] - bb2[0] + sc(14)
            bx2 = mid_x - bw2 // 2
            by2 = mid_y + sc(8)
            ab_l = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ab_d = ImageDraw.Draw(ab_l)
            ab_d.rounded_rectangle([bx2, by2, bx2+bw2, by2+sc(17)], radius=sc(5),
                                     fill=(*RED_SOFT, 26))
            ab_d.rounded_rectangle([bx2, by2, bx2+bw2, by2+sc(17)], radius=sc(5),
                                     outline=(*RED_SOFT, 40), width=2)
            img = Image.alpha_composite(img, ab_l)
            draw = ImageDraw.Draw(img)
            draw.text((bx2 + sc(7), by2 + sc(3)), badge_text, font=f_small,
                       fill=(*RED_SOFT, 140))

    # SYNK 워터마크
    draw = ImageDraw.Draw(img)
    wm = "SYNK"
    wbb = draw.textbbox((0, 0), wm, font=f_badge)
    ww = wbb[2] - wbb[0]
    draw.text(((W - ww) // 2, H - sc(22)), wm, font=f_badge, fill=(*WHITE_C, 77))

    path = os.path.join(tmp, f"card_{index:03d}.jpg")
    img.convert("RGB").save(path, format="JPEG", quality=95)
    return path


def make_caption_overlay_image(tmp: str, index: int, title: str) -> str:
    """영상 상단 좌측 pill 배지 오버레이 — 사람을 가리지 않도록 최상단 배치."""
    from PIL import Image, ImageDraw, ImageFont
    img  = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    try:
        font = ImageFont.truetype(FONT_BOLD, 52)
    except Exception:
        try:
            font = ImageFont.truetype(FONT, 52)
        except Exception:
            font = ImageFont.load_default()

    bbox = draw.textbbox((0, 0), title, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    pad_x, pad_y = 40, 22
    margin = 60
    pill_x1 = margin
    pill_y1 = 100
    pill_x2 = pill_x1 + tw + pad_x * 2 + 10  # +10: 코랄 인디케이터 공간
    pill_y2 = pill_y1 + th + pad_y * 2

    # 반투명 다크 pill 배경
    draw.rounded_rectangle(
        [(pill_x1, pill_y1), (pill_x2, pill_y2)],
        radius=(pill_y2 - pill_y1) // 2,
        fill=(0, 0, 0, 195),
    )

    # 왼쪽 코랄 인디케이터 도트
    dot_r = 10
    dot_x = pill_x1 + pad_x - 4
    dot_y = (pill_y1 + pill_y2) // 2
    draw.ellipse(
        [(dot_x - dot_r, dot_y - dot_r), (dot_x + dot_r, dot_y + dot_r)],
        fill=CORAL + (255,),
    )

    # 텍스트
    tx = pill_x1 + pad_x + dot_r * 2 + 8
    ty = pill_y1 + pad_y - bbox[1]
    draw.text((tx, ty), title, font=font, fill=WHITE + (240,))

    path = os.path.join(tmp, f"caption_{index:03d}.png")
    img.save(path, format="PNG")
    return path


def make_outro_image(tmp: str, date_str: str) -> str:
    """아웃트로 — SYNK 로고 중앙 + 감사 메시지."""
    from PIL import Image, ImageDraw
    img  = _gradient_bg().convert("RGB")
    draw = ImageDraw.Draw(img)

    # 상단/하단 보라 액센트
    draw.rectangle([(0, 0), (W, 8)], fill=PURPLE)
    draw.rectangle([(0, H - 8), (W, H)], fill=PURPLE)

    # SYNK 로고 중앙
    _paste_logo_center(img, y_center=H // 2 - 160, size=280)

    # 코랄 구분선
    line_y = H // 2 + 60
    draw.rectangle([(W//2 - 160, line_y), (W//2 + 160, line_y + 3)], fill=CORAL)

    # 감사 메시지
    _centered(draw, "오늘도 함께해줘서 고마워요", _load_font(52), H // 2 + 100, DIM)

    # 날짜
    date_label = date_str.replace("-", ".")
    _centered(draw, date_label, _load_font(44), H // 2 + 190, CORAL)

    path = os.path.join(tmp, "outro.jpg")
    img.save(path, format="JPEG", quality=95)
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


def video_to_9x16_plain(src: str, out: str, tmp: str, target_duration: float = 5.0):
    """콜라주 영상 → 9:16 블러 배경, 무조건 target_duration초로 루프 처리."""
    fade_out_st = max(target_duration - FADE, 0)
    # -stream_loop -1 로 무한 루프, -t 로 target_duration에서 잘라냄
    fc = (
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=increase,"
        f"crop={W}:{H},boxblur=20:2,setsar=1[bg];"
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=decrease,setsar=1[fg];"
        f"[bg][fg]overlay=(W-w)/2:(H-h)/2,"
        f"fade=t=in:st=0:d={FADE},"
        f"fade=t=out:st={fade_out_st}:d={FADE}[v]"
    )
    cmd = [FFMPEG, "-y", "-stream_loop", "-1", "-i", src]
    if has_audio(src):
        audio_map = ["-map", "0:a"]
    else:
        cmd += ["-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo"]
        audio_map = ["-map", "1:a"]
    run(cmd + [
        "-t", str(target_duration),
        "-filter_complex", fc,
        "-map", "[v]", *audio_map,
        *ENC, out,
    ])


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

            # 콜라주 영상만 9:16으로 변환 후 이어붙이기
            for i, v in enumerate(videos):
                src = os.path.join(tmp, f"src_{i:03d}.mp4")
                if not download(v["url"], src):
                    print(f"  collage[{i}] 다운로드 실패 — 건너뜀")
                    continue

                print(f"  collage[{i}] downloaded")
                seg_vid = os.path.join(tmp, f"seg_vid_{i:03d}.mp4")
                video_to_9x16_plain(src, seg_vid, tmp)
                segments.append(seg_vid)

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
                *ENC,
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
