"""Transcribe the rendered narration and print lines whose words differ from the script.

Usage: check.py <scenes.json> <audio-dir>
"""
import difflib, json, re, subprocess, sys
from pathlib import Path

import numpy as np
from faster_whisper import WhisperModel

def load(path):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", "16000", "-"],
        check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32)

def words(s):
    return re.sub(r"[^a-z0-9' ]", " ", s.lower().replace("-", " ")).split()

scenes = json.loads(Path(sys.argv[1]).read_text())
model = WhisperModel("small.en", device="cpu", compute_type="int8")
for scene in scenes["scenes"]:
    for i, line in enumerate(scene.get("lines", [])):
        key = f"{scene['id']}-{i}"
        segments, _ = model.transcribe(load(str(Path(sys.argv[2]) / f"{key}.wav")), beam_size=5)
        heard = " ".join(s.text for s in segments)
        a, b = words(line["text"]), words(heard)
        diff = [f"{' '.join(a[i1:i2])!r}->{' '.join(b[j1:j2])!r}"
                for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b).get_opcodes() if op != "equal"]
        if diff:
            print(f"{key}: {'; '.join(diff)}")
