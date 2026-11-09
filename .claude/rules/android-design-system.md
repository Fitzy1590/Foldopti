---
paths:
  - "app/src/main/java/**/ui/**/*.kt"
---
# Design system (adapted from heydamir/unslop, CC BY 4.0, https://github.com/heydamir/unslop)

## Direction
- Product: optimizer for a foldable phone and a streaming box, for one technical owner.
- Character: calm, exact, instrument-like.
- World: a device diagnostics panel at night.
- Moment: ONE rich element per screen, the thermal/health gauge on the home screen. Everything else stays quiet.
- Signature: a small live status dot (ok / warm / hot) beside device names and scan state.

## Rules
- Spacing on a 4dp grid: 4, 8, 12, 16, 20, 24, 32, 40, 48. Same thing, same size. Space inside a group is smaller than between groups.
- Color: one graphite neutral ramp for all surfaces (dark: bg #141415, card #19191b, raised #232325; light: bg #fafafa, card #fff, raised #f4f4f5). Text: primary #ededed / #171717, secondary #a1a1a6 / #525252, muted #85858b / #737373.
- One accent (teal, `#3CCFA8` dark / `#0B7A63` light), used only for primary action, selection and one key figure. Text on accent at least 4.5:1.
- Semantic only: positive #4cc38a, negative #e5484d. Warm/hot gauge states may use amber then red.
- No gradients between hues, no glows, no colored shadows, no gradient text. Dark depth comes from surface steps and 8%-opacity hairlines.
- Type: Geist or IBM Plex Sans for UI, IBM Plex Mono for figures and meta. Weights 400 and 500 only. Sentence case, no uppercase tracked labels. Numbers use tabular figures.
- Icons: one set (Material Symbols Rounded, 1.5-equivalent weight), 18dp, secondary/muted color, never emoji.
- Radius: controls 8-10dp, cards 12-16dp, panels 14-20dp. Inner radius = outer minus padding, minimum 4.
- Stat cards: label, figure, change with its comparison ("3 fewer than last scan"). No sparklines or filler icons.
- Copy: specific and short. No exclamation marks, no emoji, no hype words, no greeting headlines. Titles name the context ("Cache", "Thermals").
- Motion: one easing `cubic-bezier(0.22, 1, 0.36, 1)`; 120-180ms for press/toggle, 300-500ms for sheets and layout changes. Respect the system animation scale. Press scale 0.97.
- Notifications follow the same copy rules: state the finding and the number ("1.2 GB of cache in 38 apps"), then actions.
