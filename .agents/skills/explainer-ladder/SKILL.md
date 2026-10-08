---
name: explainer-ladder
description: Escalate explanation format when prose will not land: controlled-language writing (ASD-STE100), diagrams, single-file interactive HTML, or bespoke explainer videos. Use when the user asks to explain, teach, visualize, or help them understand a complex topic, system, or model output.
---

# Explainer Ladder

Text is the cheapest format to produce, not always the clearest to read. When an explanation has to land, climb this ladder and build one artifact at the rung that fits:

1. Controlled prose (ASD-STE100 style)
2. Diagram
3. Interactive web page
4. Explainer video

Every rung is discardable. Build time is cheap now, comprehension is the goal, so reach for a larger artifact than the last decade of habits would allow.

## Climb rules

First name the one question the explanation must answer. Then pick the lowest rung that can carry it, and climb or stay by what the reader needs:

- Unambiguous procedure or spec: rung 1.
- Many parts, flows, or states to hold in mind at once: rung 2.
- To explore, change inputs, or watch a process run: rung 3.
- A narrative walk-through, onboarding, or a broad audience: rung 4.

Climb immediately when the previous explanation did not land ("I don't get it", "explain simpler"). Keep it at a conversational answer for simple questions; a clean paragraph beats a video for a two-line fact. When a topic is genuinely complex, climb one rung higher than feels necessary. The artifact is cheap to rebuild.

A common case for this whole ladder: the user must understand a long analysis, diff, or design that the model just produced. Treat the artifact as the explanation, not as decoration around one.

Build one artifact, not a set of four.

## Rung 1: controlled prose

Write in the style of ASD-STE100, the controlled language for aerospace maintenance documentation. Ask for full STE100 for maximum constraint, or "80% of the way to STE100" when that is too rigid. The core rules work on their own:

- One idea per sentence. At most 20 words for instructions, 25 for descriptions.
- Active voice, simple present tense. Imperative for steps.
- One word per meaning. Use the same word for the same thing every time.
- No idioms, jargon, or phrasal verbs. Do not use "-ing" as a verb.
- Numbered steps for procedures, one action per step.

Use it for procedures, API contracts, and findings where ambiguity costs something.

## Rung 2: diagram

Pick the notation by where the reader will look: Mermaid for markdown renderers (GitHub included), ASCII for inline terminal output, D2 or Graphviz for larger graphs, hand-written SVG when it needs polish.

One message per diagram. Label every edge with its condition or verb. Split a diagram past 15 nodes. A diagram that needs a legend has become a second document.

## Rung 3: interactive web page

Build a single self-contained `index.html` with inline CSS and JS. No build step, no network dependency, one idea explained well.

What makes it land: one idea per screen; controls (slider, toggle, step button) that change what is shown; animation of the process itself; annotated code next to its output; hover details on dense parts.

Write it to `/tmp/opencode/explainers/<slug>/index.html` so it stays out of the repo. Open it with `xdg-open` and give the user the path. Load the `ui-ux-pro-max` skill for design quality when it is available.

## Rung 4: explainer video

Pipeline: outline, narration script, TTS audio, timed scenes, render, open. Keep the first cut 60 to 120 seconds and centered on one idea.

Pick the renderer by content and what is installed:

- Manim: math, algorithms, 3b1b style.
- Remotion: React, UI, data-driven scenes. Load the `remotion-best-practices` skill when it is available.
- Motion Canvas: general 2D animation.

Narration: use `ELEVENLABS_API_KEY` from the environment or `.env` when set. Otherwise use local TTS, Piper first (small and fast), Coqui next, espeak-ng as the floor. If no TTS works, put every word on screen with captions and render anyway.

Check the toolchain before promising a video (`manim --version`, `npx remotion --version`, `piper --version`). If the renderer is missing and installation is heavy, state the plan and the cost first.

## Artifact hygiene

Keep these artifacts in `/tmp/opencode/`. Do not commit them and do not treat them as deliverables to maintain. Copy one into the repo only when the user asks.

## Done

Done when exactly one artifact exists at a stated path, it answers the one named question, and the user can open it. Close with the path (or the open window) and one sentence on what it shows.
