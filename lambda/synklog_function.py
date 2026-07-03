"""
synklog_function.py
Concatenates daily collage videos into a single SYNKLOG video.

Payload (from BE):
{
  "synklogId": 123,
  "videoUrls": ["https://...", "https://..."],   # collage videos in order
  "callbackUrl": "https://api.synk.ai.kr/api/synklogs/callback",
  "callbackSecret": "..."
}
"""

import json
import os
import subprocess
import tempfile
import urllib.request
import boto3
import uuid

S3_BUCKET = os.environ.get("S3_BUCKET", "synk-videos")
s3 = boto3.client("s3")


def download(url: str, dest: str) -> bool:
    try:
        urllib.request.urlretrieve(url, dest)
        return os.path.getsize(dest) > 0
    except Exception as e:
        print(f"  [download] 실패 {url}: {e}")
        return False


def probe_duration(path: str) -> float:
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", path],
            capture_output=True, text=True, timeout=30,
        )
        return float(result.stdout.strip())
    except Exception:
        return 0.0


def make_thumbnail(video_path: str, out_path: str):
    subprocess.run(
        ["ffmpeg", "-y", "-ss", "0", "-i", video_path,
         "-vframes", "1",
         "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
         out_path],
        capture_output=True, timeout=60,
    )


def lambda_handler(event, context):
    synklog_id = event["synklogId"]
    video_urls: list[str] = event["videoUrls"]
    callback_url: str = event["callbackUrl"]
    callback_secret: str = event["callbackSecret"]

    print(f"SYNKLOG Lambda 시작: synklogId={synklog_id}, videos={len(video_urls)}")

    with tempfile.TemporaryDirectory() as tmp:
        # 1. Download all collage videos
        input_paths = []
        for i, url in enumerate(video_urls):
            dest = os.path.join(tmp, f"collage_{i:03d}.mp4")
            ok = download(url, dest)
            if ok:
                dur = probe_duration(dest)
                print(f"  collage[{i}] downloaded, duration={dur:.1f}s")
                input_paths.append(dest)
            else:
                print(f"  collage[{i}] 다운로드 실패 — 건너뜀")

        if not input_paths:
            _callback(callback_url, callback_secret, synklog_id, False, error="다운로드 가능한 영상 없음")
            return

        # 2. Concat with FFmpeg
        concat_list = os.path.join(tmp, "concat.txt")
        with open(concat_list, "w") as f:
            for p in input_paths:
                f.write(f"file '{p}'\n")

        out_video = os.path.join(tmp, "synklog.mp4")
        cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0", "-i", concat_list,
            "-c:v", "libx264", "-preset", "faster", "-crf", "20",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            out_video,
        ]
        print("FFmpeg concat:", " ".join(cmd))
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        if result.returncode != 0 or not os.path.exists(out_video):
            print("FFmpeg 실패:", result.stderr[-2000:])
            _callback(callback_url, callback_secret, synklog_id, False, error="FFmpeg concat 실패")
            return

        # 3. Thumbnail from first frame
        thumb_path = os.path.join(tmp, "thumbnail.jpg")
        make_thumbnail(out_video, thumb_path)

        # 4. Upload to S3
        key_prefix = f"synklogs/{synklog_id}/{uuid.uuid4().hex}"
        video_key = f"{key_prefix}/synklog.mp4"
        thumb_key = f"{key_prefix}/thumbnail.jpg"

        s3.upload_file(out_video, S3_BUCKET, video_key, ExtraArgs={"ContentType": "video/mp4"})
        video_url_out = f"https://{S3_BUCKET}.s3.ap-northeast-2.amazonaws.com/{video_key}"

        thumb_url_out = None
        if os.path.exists(thumb_path) and os.path.getsize(thumb_path) > 0:
            s3.upload_file(thumb_path, S3_BUCKET, thumb_key, ExtraArgs={"ContentType": "image/jpeg"})
            thumb_url_out = f"https://{S3_BUCKET}.s3.ap-northeast-2.amazonaws.com/{thumb_key}"

        print(f"S3 업로드 완료: {video_url_out}")

        _callback(callback_url, callback_secret, synklog_id, True,
                  synklog_video_url=video_url_out, thumbnail_url=thumb_url_out)


def _callback(url: str, secret: str, synklog_id: int, success: bool,
              synklog_video_url: str = None, thumbnail_url: str = None, error: str = None):
    body = {
        "synklogId": synklog_id,
        "success": success,
        "synklogVideoUrl": synklog_video_url,
        "thumbnailUrl": thumbnail_url,
        "error": error,
    }
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json", "X-Callback-Secret": secret},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            print(f"Callback 응답: {resp.status}")
    except Exception as e:
        print(f"Callback 실패: {e}")
