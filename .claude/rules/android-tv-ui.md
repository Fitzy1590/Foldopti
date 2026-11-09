---
paths:
  - "app/src/main/java/**/tv/**/*.kt"
---
# TV UI (NVIDIA Shield, 10-foot)

- Use `androidx.tv.material3.*` (Compose TV 1.0.0): `Card`, `Button`, `ListItem`, `NavigationDrawer`, `TvLazyRow/TvLazyColumn` equivalents. Do not mix in phone Material 3 components for focusable surfaces.
- Every focusable element shows visible focus: scale at least 1.03x, or a border of at least 2dp. Use `Modifier.onFocusChanged` with an animated value, never color change alone.
- Use `FocusRequester` to place initial focus on the primary content when a screen opens.
- Every list container (rows, columns, grids) uses `Modifier.focusRestorer()` so returning to a list resumes at the last focused item.
- D-pad only: no hover, no touch-only gestures. Back always returns one level. OK/Enter activates.
- Safe margins: 48dp horizontal, 27dp vertical overscan padding. Body text at least 16sp, secondary at least 14sp.
- Destructive actions (disable, uninstall) need a confirm dialog with focus on the safe option.
- Follow `android-design-system.md`, using the dark theme only.
