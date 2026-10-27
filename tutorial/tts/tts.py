"""Render every narration line in scenes.json to wav and record durations.

Usage: tts.py <scenes.json> <out-dir>   (skips lines whose text hasn't changed)
"""
import hashlib, json, sys
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro import KPipeline

VOICE, SPEED, RATE = "af_heart", 1.0, 24000

def main(scenes_path, out_dir):
    out = Path(out_dir); out.mkdir(parents=True, exist_ok=True)
    scenes = json.loads(Path(scenes_path).read_text())
    index_path = out / "durations.json"
    index = json.loads(index_path.read_text()) if index_path.exists() else {}
    pipeline = None
    for scene in scenes["scenes"]:
        for i, line in enumerate(scene.get("lines", [])):
            key = f"{scene['id']}-{i}"
            spoken = line.get("say", line["text"])
            digest = hashlib.sha1(f"{VOICE}|{SPEED}|{spoken}".encode()).hexdigest()[:12]
            wav = out / f"{key}.wav"
            if index.get(key, {}).get("hash") == digest and wav.exists():
                continue
            pipeline = pipeline or KPipeline(lang_code="a", repo_id="hexgrad/Kokoro-82M")
            audio = np.concatenate([a for _, _, a in pipeline(spoken, voice=VOICE, speed=SPEED)])
            sf.write(wav, audio, RATE)
            index[key] = {"hash": digest, "seconds": round(len(audio) / RATE, 3)}
            print(f"{key}: {index[key]['seconds']}s  {spoken[:60]}")
    index_path.write_text(json.dumps(index, indent=1, sort_keys=True))

if __name__ == "__main__":
    main(*sys.argv[1:3])
