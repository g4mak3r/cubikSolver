# Cubik visual system

The app uses one native Compose theme across home, scanning, color correction,
scan review and the 3D solver. Shared components and motion tokens live in
`ui/CubecraftDesign.kt`; colors live in `ui/CubecraftColors.kt`.

## Visual rules

- Graphite background, layered dark panels, soft blue for the primary action.
  Reserve saturated cube colors for the stickers and color picker.
- System sans-serif for the interface; monospace only for move notation and counts.
  Body text is 14–16sp, headings 24–38sp. Respect the system font scale.
- Use a 4dp spacing grid, 20dp screen gutters and 12dp gaps. Panels have 24dp
  corners; controls have 16dp corners.
- Buttons are at least 52dp tall; icon controls and color choices are at least
  48dp. Narrow color palettes wrap into two rows.
- Keep the primary action at the bottom and let supporting content scroll.
  Respect system-bar insets. Limit the content width to 600dp on large devices.
- Validation uses a title, readable detail and a disabled solve action when
  correction is required. Color is never the only indication of selection/error.

## Motion and performance

- Press feedback: 120ms. Content/color changes: 220ms. Screen crossfade: 260ms.
- A turn demonstration takes 1400ms, then holds for 500ms before repeating.
  Autoplay advances every 1900ms and pauses when the app enters the background.
- Static cubes do not run an animation loop. Animated cube values are read in
  the draw phase so individual frames do not recompose the screen.
- Seeking updates the preview thumb immediately and rebuilds cube state only
  when the user releases it. This matters for long 5×5 solutions.
- Avoid blur, decorative particles, web views and additional font downloads.

## Verification

`gradle testDebugUnitTest assembleDebug` checks the solver and builds the arm64
APK. `gradle -Pemulator connectedDebugAndroidTest` runs native UI interaction
checks on an x86_64 emulator. CI uses a 360dp-wide Android 10 screen, includes
130% text-size cases, and uploads screenshots and reports as
`cubikSolver-design-checks`.

Emulator checks do not establish camera accuracy or frame rate on a physical
Huawei device; those still need an on-device run.
