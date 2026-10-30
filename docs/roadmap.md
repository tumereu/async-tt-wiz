# Roadmap

Long-term direction, in order. Rough by design; the owner changes it as plans change.

## 1. Finish the Android client (now)

- Playtest changes and UI improvements as they come up.
- More draft modes.
- History of played games.
- Possibly more game modes (Commander and others).

## 2. Desktop web client

Same game engine and server, its own UI: things are done the desktop way
(mouse, keyboard, larger screen), not as a phone layout in a browser.

## 3. Games with three or more players

Design and trial.

## 4. Possible but unlikely

A mobile web app for devices without Android, or an iOS client.

## What this means for today's code

- Game rules, history and texts belong in the shared modules (`protocol/`,
  `core/`), not in Android UI code, so a web client can reuse them.
- In new code, avoid assuming exactly two players where it costs nothing.
