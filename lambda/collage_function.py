import json
import boto3
import os
import subprocess
import tempfile
import urllib.request
import urllib.error

s3 = boto3.client('s3', region_name='ap-northeast-2')
BUCKET = os.environ.get('S3_BUCKET', 'synk-videos')
REGION = os.environ.get('AWS_REGION_NAME', 'ap-northeast-2')

# Rows config: number of clips per row, top to bottom
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

CANVAS_W = 1080
CANVAS_H = 1920


def get_cells(rows_config):
    """Returns list of (w, h, x, y) for each clip slot in order."""
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


def build_filter_complex(n, cells):
    parts = []
    for i in range(n):
        w, h, _, _ = cells[i]
        parts.append(
            f"[{i}:v]scale={w}:{h}:force_original_aspect_ratio=decrease,"
            f"pad={w}:{h}:(ow-iw)/2:(oh-ih)/2:black,setsar=1[v{i}]"
        )
    parts.append(f"color=black:size={CANVAS_W}x{CANVAS_H}:duration=30[base]")
    prev = "base"
    for i in range(n):
        _, _, x, y = cells[i]
        next_label = "out" if i == n - 1 else f"o{i}"
        parts.append(f"[{prev}][v{i}]overlay={x}:{y}[{next_label}]")
        prev = f"o{i}"
    return ";".join(parts)


def parse_s3_url(video_url):
    """Extract (bucket, key) from s3:// URL or https:// CDN URL."""
    if video_url.startswith("s3://"):
        path = video_url[5:]
        bucket, key = path.split("/", 1)
        return bucket, key
    # https://synk-videos.s3.ap-northeast-2.amazonaws.com/videos/xxx.mp4
    if ".amazonaws.com/" in video_url:
        key = video_url.split(".amazonaws.com/", 1)[1]
        return BUCKET, key
    # Assume it's a key relative to the default bucket
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
    submissions = event["submissions"]  # [{userId, videoUrl}, ...]
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
        input_files = []
        for i, sub in enumerate(submissions):
            bucket, key = parse_s3_url(sub["videoUrl"])
            local_path = f"{tmp}/input_{i}.mp4"
            print(f"Downloading s3://{bucket}/{key}")
            s3.download_file(bucket, key, local_path)
            input_files.append(local_path)

        filter_complex = build_filter_complex(n, cells)
        output_path = f"{tmp}/collage_{mission_id}.mp4"

        inputs = []
        for f in input_files:
            inputs += ["-i", f]

        cmd = (
            ["ffmpeg", "-y"]
            + inputs
            + [
                "-filter_complex", filter_complex,
                "-map", "[out]",
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-movflags", "+faststart",
                "-t", "30",
                output_path,
            ]
        )

        print(f"Running FFmpeg: {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)

        if result.returncode != 0:
            print(f"FFmpeg stderr: {result.stderr[-1000:]}")
            send_callback(callback_url, callback_secret, {
                "missionId": mission_id,
                "success": False,
                "error": result.stderr[-500:],
            })
            return {"statusCode": 500}

        # Upload collage video
        collage_key = f"collages/{mission_id}/collage.mp4"
        s3.upload_file(
            output_path, BUCKET, collage_key,
            ExtraArgs={"ContentType": "video/mp4", "ACL": "public-read"},
        )
        collage_url = (
            f"https://{BUCKET}.s3.{REGION}.amazonaws.com/{collage_key}"
        )

        # Extract thumbnail (first frame)
        thumb_path = f"{tmp}/thumb_{mission_id}.jpg"
        subprocess.run(
            ["ffmpeg", "-y", "-i", output_path, "-vframes", "1", "-q:v", "2", thumb_path],
            capture_output=True,
        )
        thumb_key = f"collages/{mission_id}/thumbnail.jpg"
        s3.upload_file(
            thumb_path, BUCKET, thumb_key,
            ExtraArgs={"ContentType": "image/jpeg", "ACL": "public-read"},
        )
        thumbnail_url = (
            f"https://{BUCKET}.s3.{REGION}.amazonaws.com/{thumb_key}"
        )

        submitted_count = len([s for s in submissions if s.get("status") == "SUBMITTED"])

        send_callback(callback_url, callback_secret, {
            "missionId": mission_id,
            "success": True,
            "collageVideoUrl": collage_url,
            "thumbnailUrl": thumbnail_url,
            "submittedCount": submitted_count,
        })

        return {"statusCode": 200, "collageUrl": collage_url}
