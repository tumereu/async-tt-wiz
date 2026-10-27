import React from 'react';
import { Composition } from 'remotion';
import script from '../../scenes.json';
import durations from '../public/audio/durations.json';
import { FPS, buildTimeline, type Durations, type Script } from './timeline';
import { Tutorial } from './Tutorial';

const timeline = buildTimeline(script as Script, durations as Durations);

export const Root: React.FC = () => (
  <Composition id="Tutorial" component={Tutorial} durationInFrames={timeline.frames} fps={FPS} width={1920} height={1080}
    defaultProps={{ script: script as Script, scenes: timeline.scenes }} />
);
