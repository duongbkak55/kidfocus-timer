# Codex task: desktop + iPad layouts for the web app and /tu-duy/

Branch feat/web-app. Do after (or together with) WEB_APP_CLAUDE_REVIEW_1.md. Phone layout (360–480 px) must not change.
Today everything is a single ~480 px column centred on the screen: on a laptop or iPad most of the screen is empty,
the week calendar is cramped and parents have to scroll a lot.

## Breakpoints (CSS only where possible, no second app)
| width | layout |
|---|---|
| < 600 px | current phone layout, bottom tab bar |
| 600–1023 px (iPad portrait, small laptop) | left rail with icons + labels; content max 760 px; 2-column card grids |
| ≥ 1024 px (iPad landscape, desktop) | sidebar 240 px (profile switcher + nav + sync chip); content max 1200 px; split views below |

## Screens at ≥ 1024 px
- **Hôm nay:** timeline left (60%), right panel = "Đang tập trung" mini timer + today's goal + routines due.
- **Lịch:** true 7-column week grid (Mon–Sun, hours on the left, tasks/routines as blocks); click a block → side panel
  editor instead of a full-screen form; "+" in any day column pre-fills that day/time.
- **Tập trung:** large centred timer (fits 1024×768 without scroll), task list beside it.
- **Đã hoàn thành / Nhật ký ngày:** chart + list side by side; list rows show more columns (task, duration, status, time).
- **Hồ sơ / Cài đặt:** two-column forms, max 720 px.
- 600–1023 px: same content as the rail layout, single main column, side panels become bottom sheets.

## Input & platform
- Mouse/keyboard: visible focus rings, Enter/Escape in dialogs, hover states, `Space` starts/pauses the focus timer,
  arrow keys move between days in Lịch. Touch targets stay ≥ 44 px on iPad.
- iPad Safari: `100dvh`, safe-area insets, no hover-only actions, Add-to-Home-Screen icon + `apple-touch-icon`,
  `manifest.webmanifest` (`display: standalone`, theme colour) so the app opens full-screen from the home screen.
  No service worker (offline behaviour unchanged).
- Orientation change keeps the current screen and any unsaved form state.

## /tu-duy/
- ≥ 768 px: content max 720 px, chooser grid 3–4 columns, puzzle figures scale up (respect the existing layout tests'
  ratios), keypad max 520 px wide centred; ≥ 1024 px: explanation panel beside the puzzle instead of below.
  Must keep the 96 Learning Hub tests and byte-identical tuduy asset rule (layout via the page's own CSS or an
  option in tuduy-ui.js, backward compatible).

## Tests
- Playwright (Brave/Chromium) screenshots + overflow check at 360×740, 390×844, 768×1024, 820×1180, 1024×768,
  1366×1024, 1440×900 for every screen (emulator data, Bé Na). No horizontal scroll; no text clipped; sidebar/rail
  visible at the right breakpoints; keyboard: Tab order reaches every control, Escape closes dialogs.
- WebKit (Playwright webkit) smoke at 820×1180 for iPad Safari quirks if available on the Mac; note if not.
- Report the screenshots in docs/tasks/WEB_APP_CODEX_REPORT.md (new section). No push/deploy.
