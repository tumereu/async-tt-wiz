// Pure timing logic, shared by the Remotion composition and chapters.ts (node).
// No imports, so node can run it with type stripping.

export const FPS = 30;
export const PORTRAIT = { w: 1080, h: 2340 };
export const LANDSCAPE = { w: 2340, h: 1080 };

export type Rect = { x: number; y: number; w: number; h: number };
export type Media = { src: string; from?: number; to?: number; rate?: number };
export type Mark =
  | { type: 'circle'; x: number; y: number; r: number }
  | { type: 'rect'; x: number; y: number; w: number; h: number }
  | { type: 'arrow'; x1: number; y1: number; x2: number; y2: number }
  | { type: 'label'; x: number; y: number; text: string; anchor?: 'start' | 'middle' | 'end' }
  | { type: 'tap'; x: number; y: number; kind?: 'tap' | 'double' | 'hold' };
export type TimedMark = Mark & { at?: number; until?: number; device?: number };

export type Line = {
  text: string;
  say?: string;
  // One entry per device (two-phone scenes use two). Omitted = keep playing the previous line's media.
  media?: Media | Media[];
  zoom?: Rect | null;
  marks?: TimedMark[];
  // Minimum on-screen seconds (e.g. to let a clip finish).
  hold?: number;
};

export type Scene = {
  id: string;
  chapter: number;
  layout?: 'phone' | 'two-phones' | 'landscape' | 'title' | 'chapter' | 'end';
  devices?: string[]; // labels above phones in two-phone scenes
  lines?: Line[];
  seconds?: number; // for cards without narration
};

export type Script = { chapters: { n: number; title: string }[]; scenes: Scene[] };
export type Durations = Record<string, { seconds: number }>;

export type TimedLine = Line & {
  key: string;
  start: number; // frame, relative to the scene
  frames: number;
  audioFrames: number;
  media: Media[];
  zoomFrom: Rect | null;
};
export type TimedScene = Scene & { start: number; frames: number; timed: TimedLine[] };

const LEAD = 0.25; // silence before each line
const TAIL = 0.55; // silence after each line

export function buildTimeline(script: Script, durations: Durations): { scenes: TimedScene[]; frames: number } {
  const scenes: TimedScene[] = [];
  let cursor = 0;
  for (const scene of script.scenes) {
    const timed: TimedLine[] = [];
    let at = 0;
    let media: Media[] = [];
    let zoom: Rect | null = null;
    (scene.lines ?? []).forEach((line, i) => {
      const key = `${scene.id}-${i}`;
      const seconds = durations[key]?.seconds ?? Math.max(2, line.text.split(/\s+/).length / 2.6);
      const frames = Math.round(Math.max(LEAD + seconds + TAIL, line.hold ?? 0) * FPS);
      if (line.media) {
        media = (Array.isArray(line.media) ? line.media : [line.media]).map((m) => ({ ...m }));
      } else if (timed.length) {
        // Continue the previous clip where the previous line left it.
        const prev = timed[timed.length - 1];
        media = prev.media.map((m) => ({ ...m, from: (m.from ?? 0) + (prev.frames / FPS) * (m.rate ?? 1) }));
      }
      timed.push({ ...line, key, start: at, frames, audioFrames: Math.round(seconds * FPS), media,
        zoomFrom: zoom });
      if (line.zoom !== undefined) zoom = line.zoom;
      at += frames;
    });
    const frames = scene.lines?.length ? at : Math.round((scene.seconds ?? 2.5) * FPS);
    scenes.push({ ...scene, start: cursor, frames, timed });
    cursor += frames;
  }
  return { scenes, frames: cursor };
}

export function chapterStarts(script: Script, timeline: { scenes: TimedScene[] }) {
  return script.chapters.map((c) => ({
    ...c,
    start: timeline.scenes.find((s) => s.chapter === c.n)?.start ?? 0,
  }));
}
