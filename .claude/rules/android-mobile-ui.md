---
paths:
  - "app/src/main/java/**/mobile/**/*.kt"
---
# Mobile UI (Galaxy Z Fold 6)

- Material 3 (`androidx.compose.material3`) with `material3-adaptive` and `material3-window-size-class`.
- Observe posture with Jetpack WindowManager (`WindowInfoTracker`, `FoldingFeature`). Never infer fold state from screen size alone.
- Cover screen (folded, compact width): single pane, bottom navigation bar, one primary action per screen.
- Inner screen (unfolded, medium/expanded width): `NavigableListDetailPaneScaffold`, list left, detail right, `NavigationRail` instead of bottom bar.
- Tabletop/half-opened posture: put content above the hinge, actions below it. Do not place text across the hinge (`FoldingFeature.isSeparating`).
- Survive fold/unfold without losing state: hoist state to `ViewModel`, use `rememberSaveable`, no `android:configChanges` hacks.
- Touch targets at least 48dp. Support edge-to-edge and system insets.
- Follow `android-design-system.md` for color, type, spacing and copy.
