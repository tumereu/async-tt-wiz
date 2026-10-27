"""Timestamped contact sheet of a clip: sheet.py <clip.mp4> <out.png> [step seconds] [start] [end]"""
import subprocess
import sys

clip, out = sys.argv[1], sys.argv[2]
step = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0
start = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
end = sys.argv[5] if len(sys.argv) > 5 else None
dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", clip],
                           capture_output=True, text=True).stdout)
length = (float(end) if end else dur) - start
n = max(1, int(length / step))
cols = min(10, n)
rows = (n + cols - 1) // cols
vf = (f"fps=1/{step},scale=160:-1,drawtext=text='%{{pts\\:hms\\:{start}}}':x=4:y=4:fontsize=18:"
      f"fontcolor=yellow:box=1:boxcolor=black@0.7,tile={cols}x{rows}")
subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-ss", str(start), "-t", str(length), "-i", clip, "-vf", vf,
                "-frames:v", "1", out], check=True)
