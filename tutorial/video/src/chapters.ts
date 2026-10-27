// Prints YouTube chapter timestamps: node src/chapters.ts > ../out/chapters.txt
import { readFileSync } from 'node:fs';
import { FPS, buildTimeline, chapterStarts } from './timeline.ts';

const script = JSON.parse(readFileSync(new URL('../../scenes.json', import.meta.url), 'utf8'));
const durations = JSON.parse(readFileSync(new URL('../public/audio/durations.json', import.meta.url), 'utf8'));
const timeline = buildTimeline(script, durations);
for (const c of chapterStarts(script, timeline)) {
  const s = Math.floor(c.start / FPS);
  console.log(`${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} ${c.n === 0 ? 'Intro' : c.title}`);
}
