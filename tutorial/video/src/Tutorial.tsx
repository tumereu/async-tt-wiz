import React from 'react';
import {
  AbsoluteFill, Audio, Easing, Freeze, Img, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame,
} from 'remotion';
import { loadFont } from '@remotion/google-fonts/Inter';
import {
  FPS, LANDSCAPE, PORTRAIT, type Media, type Rect, type Script, type TimedLine, type TimedMark, type TimedScene,
} from './timeline';

const { fontFamily } = loadFont('normal', { weights: ['400', '600', '800'], subsets: ['latin'] });

const C = {
  bg: '#12141a',
  bg2: '#1b1f29',
  ink: '#f2f3f5',
  dim: '#8b93a3',
  accent: '#ffcc2e',
  shadow: 'rgba(0,0,0,.55)',
};

type Box = { x: number; y: number; w: number; h: number };

export const Tutorial: React.FC<{ script: Script; scenes: TimedScene[] }> = ({ script, scenes }) => (
  <AbsoluteFill style={{ background: `radial-gradient(circle at 30% 20%, ${C.bg2}, ${C.bg} 70%)`, fontFamily, color: C.ink }}>
    {scenes.map((scene) => (
      <Sequence key={scene.id} from={scene.start} durationInFrames={scene.frames} name={scene.id}>
        <SceneView scene={scene} chapter={script.chapters.find((c) => c.n === scene.chapter)} />
      </Sequence>
    ))}
  </AbsoluteFill>
);

const SceneView: React.FC<{ scene: TimedScene; chapter?: { n: number; title: string } }> = ({ scene, chapter }) => {
  if (scene.layout === 'title') return <TitleCard />;
  if (scene.layout === 'chapter') return <ChapterCard n={scene.chapter} title={chapter?.title ?? ''} />;
  if (scene.layout === 'end') return <EndCard />;
  return (
    <>
      {scene.timed.map((line) => (
        <Sequence key={line.key} from={line.start} durationInFrames={line.frames} name={line.key}>
          <LineView scene={scene} line={line} chapter={chapter} />
        </Sequence>
      ))}
    </>
  );
};

const LineView: React.FC<{ scene: TimedScene; line: TimedLine; chapter?: { n: number; title: string } }> = ({
  scene, line, chapter,
}) => {
  const layout = scene.layout ?? 'phone';
  const label = chapter ? `${chapter.n} · ${chapter.title}` : '';
  const audio = (
    <Sequence from={Math.round(0.25 * FPS)}>
      <Audio src={staticFile(`audio/${line.key}.wav`)} />
    </Sequence>
  );
  if (layout === 'landscape') {
    const s = 1700 / LANDSCAPE.w;
    const box = { x: (1920 - 1700) / 2, y: 36, w: 1700, h: LANDSCAPE.h * s };
    return (
      <>
        <Device box={box} dims={LANDSCAPE} media={line.media[0]} line={line} device={0} />
        <Caption label={label} text={line.text} box={{ x: 110, y: 860, w: 1700, h: 200 }} compact />
        {audio}
      </>
    );
  }
  if (layout === 'two-phones') {
    const h = 930;
    const w = (PORTRAIT.w / PORTRAIT.h) * h;
    return (
      <>
        {line.media.slice(0, 2).map((m, i) => {
          const box = { x: 70 + i * (w + 50), y: 110, w, h };
          return (
            <React.Fragment key={i}>
              <div style={{ position: 'absolute', left: box.x, top: 40, width: w, textAlign: 'center', fontSize: 34,
                fontWeight: 800, color: i === 0 ? '#7fc7ff' : '#ff9f8a' }}>{scene.devices?.[i] ?? ''}</div>
              <Device box={box} dims={PORTRAIT} media={m} line={line} device={i} />
            </React.Fragment>
          );
        })}
        <Caption label={label} text={line.text} box={{ x: 70 + 2 * (w + 50) + 10, y: 0, w: 1920 - (70 + 2 * (w + 50) + 10) - 70, h: 1080 }} />
        {audio}
      </>
    );
  }
  const h = 1000;
  const w = (PORTRAIT.w / PORTRAIT.h) * h;
  return (
    <>
      <Device box={{ x: 470 - w / 2, y: 40, w, h }} dims={PORTRAIT} media={line.media[0]} line={line} device={0} />
      <Caption label={label} text={line.text} box={{ x: 900, y: 0, w: 900, h: 1080 }} />
      {audio}
    </>
  );
};

// A phone screen with zoom and annotation overlay. Coordinates in device pixels.
const Device: React.FC<{ box: Box; dims: { w: number; h: number }; media?: Media; line: TimedLine; device: number }> = ({
  box, dims, media, line, device,
}) => {
  const frame = useCurrentFrame();
  const s = box.w / dims.w;
  const full: Rect = { x: 0, y: 0, w: dims.w, h: dims.h };
  const target = (line.zoom === undefined ? line.zoomFrom : line.zoom) ?? full;
  const from = line.zoomFrom ?? full;
  const t = interpolate(frame, [0, 20], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp',
    easing: Easing.inOut(Easing.cubic) });
  const lerp = (a: number, b: number) => a + (b - a) * t;
  const rect = { x: lerp(from.x, target.x), y: lerp(from.y, target.y), w: lerp(from.w, target.w), h: lerp(from.h, target.h) };
  const z = Math.min(dims.w / rect.w, dims.h / rect.h);
  const cx = rect.x + rect.w / 2;
  const cy = rect.y + rect.h / 2;
  // Keep the zoomed view inside the screen.
  const tx = Math.min(0, Math.max(dims.w - dims.w * z, dims.w / 2 - cx * z));
  const ty = Math.min(0, Math.max(dims.h - dims.h * z, dims.h / 2 - cy * z));
  return (
    <div style={{ position: 'absolute', left: box.x - 14, top: box.y - 14, width: box.w + 28, height: box.h + 28,
      borderRadius: 34, background: '#05060a', boxShadow: `0 20px 60px ${C.shadow}`, border: '2px solid #2a2f3b' }}>
      <div style={{ position: 'absolute', left: 14, top: 14, width: box.w, height: box.h, borderRadius: 22, overflow: 'hidden',
        background: '#000' }}>
        <div style={{ width: dims.w, height: dims.h, transformOrigin: '0 0',
          transform: `scale(${s}) translate(${tx}px, ${ty}px) scale(${z})` }}>
          {media && <MediaView media={media} dims={dims} />}
          <Marks marks={(line.marks ?? []).filter((m) => (m.device ?? 0) === device)} dims={dims} lineFrames={line.frames} zoom={z} />
        </div>
      </div>
    </div>
  );
};

const MediaView: React.FC<{ media: Media; dims: { w: number; h: number } }> = ({ media, dims }) => {
  const frame = useCurrentFrame();
  const style: React.CSSProperties = { width: dims.w, height: dims.h, display: 'block' };
  if (/\.(png|jpe?g|webp)$/i.test(media.src)) return <Img src={staticFile(media.src)} style={style} />;
  const rate = media.rate ?? 1;
  const from = media.from ?? 0;
  const available = media.to !== undefined ? Math.floor(((media.to - from) / rate) * FPS) - 1 : Infinity;
  const video = (
    <OffthreadVideo src={staticFile(media.src)} trimBefore={Math.round(from * FPS)} playbackRate={rate} muted style={style} />
  );
  return frame >= available ? <Freeze frame={Math.max(0, available)}>{video}</Freeze> : video;
};

const Marks: React.FC<{ marks: TimedMark[]; dims: { w: number; h: number }; lineFrames: number; zoom: number }> = ({
  marks, dims, lineFrames, zoom,
}) => {
  const frame = useCurrentFrame();
  const sw = 12 / zoom;
  return (
    <svg width={dims.w} height={dims.h} viewBox={`0 0 ${dims.w} ${dims.h}`} style={{ position: 'absolute', left: 0, top: 0 }}>
      <defs>
        <filter id="glow"><feDropShadow dx="0" dy="0" stdDeviation={6 / zoom} floodColor="#000" floodOpacity="0.9" /></filter>
        <marker id="head" markerWidth="4" markerHeight="4" refX="2.2" refY="2" orient="auto">
          <path d="M0,0 L4,2 L0,4 z" fill={C.accent} />
        </marker>
      </defs>
      {marks.map((m, i) => {
        const start = Math.round((m.at ?? 0.3) * FPS);
        const end = m.until !== undefined ? Math.round(m.until * FPS) : lineFrames;
        if (frame < start || frame > end) return null;
        const p = interpolate(frame - start, [0, 12], [0, 1], { extrapolateRight: 'clamp', easing: Easing.out(Easing.cubic) });
        const fade = interpolate(frame, [end - 8, end], [1, 0], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
        const common = { stroke: C.accent, strokeWidth: sw, fill: 'none', filter: 'url(#glow)', opacity: fade, strokeLinecap: 'round' as const };
        switch (m.type) {
          case 'circle': {
            const len = 2 * Math.PI * m.r;
            return <circle key={i} cx={m.x} cy={m.y} r={m.r} {...common} strokeDasharray={len} strokeDashoffset={len * (1 - p)}
              transform={`rotate(-90 ${m.x} ${m.y})`} />;
          }
          case 'rect': {
            const len = 2 * (m.w + m.h);
            return <rect key={i} x={m.x} y={m.y} width={m.w} height={m.h} rx={24 / zoom} {...common} strokeDasharray={len}
              strokeDashoffset={len * (1 - p)} />;
          }
          case 'arrow': {
            const x2 = m.x1 + (m.x2 - m.x1) * p;
            const y2 = m.y1 + (m.y2 - m.y1) * p;
            return <line key={i} x1={m.x1} y1={m.y1} x2={x2} y2={y2} {...common} markerEnd="url(#head)" />;
          }
          case 'label': {
            const size = 90 / zoom;
            return (
              <text key={i} x={m.x} y={m.y} textAnchor={m.anchor ?? 'middle'} fontSize={size} fontWeight={800} fill={C.accent}
                stroke="#000" strokeWidth={size / 5} paintOrder="stroke" opacity={fade * p} fontFamily={fontFamily}>{m.text}</text>
            );
          }
          case 'tap': {
            const kind = m.kind ?? 'tap';
            const cycle = kind === 'double' ? 24 : 30;
            const local = (frame - start) % cycle;
            const rings = kind === 'double' ? [0, 8] : [0];
            return (
              <g key={i} opacity={fade}>
                <circle cx={m.x} cy={m.y} r={44 / zoom} fill={C.accent} opacity={kind === 'hold' ? 0.75 : 0.5} />
                {kind !== 'hold' && rings.map((d) => {
                  const q = interpolate(local - d, [0, 16], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' });
                  return <circle key={d} cx={m.x} cy={m.y} r={(44 + 80 * q) / zoom} stroke={C.accent} strokeWidth={sw * 0.7}
                    fill="none" opacity={1 - q} />;
                })}
                {kind === 'hold' && <circle cx={m.x} cy={m.y} r={70 / zoom} stroke={C.accent} strokeWidth={sw * 0.7} fill="none"
                  strokeDasharray={2 * Math.PI * 70 / zoom} strokeDashoffset={2 * Math.PI * 70 / zoom * (1 - p)} />}
              </g>
            );
          }
        }
      })}
    </svg>
  );
};

const Caption: React.FC<{ label: string; text: string; box: Box; compact?: boolean }> = ({ label, text, box, compact }) => {
  const frame = useCurrentFrame();
  const appear = interpolate(frame, [0, 8], [0, 1], { extrapolateRight: 'clamp' });
  const size = compact ? 38 : text.length > 170 ? 44 : text.length > 110 ? 50 : 56;
  return (
    <div style={{ position: 'absolute', left: box.x, top: box.y, width: box.w, height: box.h, display: 'flex',
      flexDirection: 'column', justifyContent: 'center', gap: compact ? 10 : 28 }}>
      {!compact && <div style={{ fontSize: 28, fontWeight: 600, color: C.dim, letterSpacing: 1.5, textTransform: 'uppercase' }}>{label}</div>}
      <div style={{ fontSize: size, lineHeight: 1.28, fontWeight: 600, opacity: appear,
        transform: `translateY(${(1 - appear) * 14}px)`, textAlign: compact ? 'center' : 'left' }}>{text}</div>
    </div>
  );
};

const TitleCard: React.FC = () => {
  const frame = useCurrentFrame();
  const a = interpolate(frame, [0, 18], [0, 1], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 24, opacity: a }}>
      <Img src={staticFile('brand/logo.svg')} style={{ height: 260 }} />
      <div style={{ fontSize: 92, fontWeight: 800 }}>Async Tabletop Wizards</div>
      <div style={{ fontSize: 40, color: C.dim }}>How to play</div>
    </AbsoluteFill>
  );
};

const ChapterCard: React.FC<{ n: number; title: string }> = ({ n, title }) => {
  const frame = useCurrentFrame();
  const a = interpolate(frame, [0, 10], [0, 1], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 10, opacity: a }}>
      <div style={{ fontSize: 150, fontWeight: 800, color: C.accent, lineHeight: 1 }}>{n}</div>
      <div style={{ fontSize: 76, fontWeight: 800, transform: `translateY(${(1 - a) * 20}px)` }}>{title}</div>
    </AbsoluteFill>
  );
};

const EndCard: React.FC = () => (
  <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 24 }}>
    <Img src={staticFile('brand/logo.svg')} style={{ height: 200 }} />
    <div style={{ fontSize: 84, fontWeight: 800 }}>Have fun!</div>
  </AbsoluteFill>
);
