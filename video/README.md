# Architecture tutorial video

A [Manim](https://www.manim.community/) animation that walks through
[`ARCHITECTURE.md`](../ARCHITECTURE.md) — how the **xAI NetBeans** module is
structured and what happens when you send a prompt.

The rendered video is [`architecture-tutorial.mp4`](architecture-tutorial.mp4)
(1080p60, ~5.5 min) with an **English voiceover**. Captions are also exported as
[`architecture-tutorial.srt`](architecture-tutorial.srt).

## What it covers

1. What the module is (a NetBeans `.nbm` embedding an agentic Grok assistant)
2. The high-level component map (`TopComponent → SessionPanel → AgentEngine → ...`)
3. The five packages and their responsibilities
4. The modes (`ASK`/`PLAN`/`DEBUG`/`AGENT`/`MULTITASK`) and what each may do
5. The five callable tools (read-only vs. mutating)
6. The agent loop (`request → tool-call → tool-result`, up to 25 iterations)
7. The threading model (EDT vs. `RequestProcessor` pool + the approval bridge)
8. Notable design choices and limitations

## Prerequisites

- Python 3.10 (used here; 3.9–3.13 also work with Manim 0.19)
- `ffmpeg` on your `PATH` (Manim uses it to encode the MP4)
- A working Cairo/Pango toolchain (installed automatically with the wheels below)
- Internet access on the first render: the English narration is generated with
  [gTTS](https://pypi.org/project/gTTS/) (Google Text-to-Speech). Synthesized
  audio is cached under `media/voiceovers/`, so later renders reuse it offline.

## Setup

```bash
cd video
python3.10 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

## Render

```bash
# High quality (1080p60) -> media/videos/architecture_tutorial/1080p60/
.venv/bin/python -m manim -qh architecture_tutorial.py ArchitectureTutorial

# Fast preview (480p15)
.venv/bin/python -m manim -ql architecture_tutorial.py ArchitectureTutorial
```

The scene lives in [`architecture_tutorial.py`](architecture_tutorial.py) as a
single `VoiceoverScene` (`ArchitectureTutorial`); each chapter is a helper method
called in order from `construct()`, so it renders to one MP4. Animations are
wrapped in `with self.voiceover(text=...)` blocks, which time each chapter to its
narration.

## Voiceover

Narration uses `manim-voiceover` with the gTTS backend (set up in `construct`
via `GTTSService(lang="en")`). To swap voices/engines, replace that line — e.g.
`manim-voiceover` also supports Azure, ElevenLabs, OpenAI, and an offline
`pyttsx3` backend (`pip install manim-voiceover[pyttsx3]`).
