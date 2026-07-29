# AttentionOS visual system — Signal Garden

Signal Garden makes the local notification pipeline visible without turning the app into a
technical dashboard. Notifications move through paths, protected information stays inside nested
forms, and personal learning grows as a branching structure.

## Principles

1. **Calm before novelty.** Motion explains sorting, protection, and learning; it never exists only
   to attract attention.
2. **One feature surface per screen.** The primary story gets a deep ink panel. Supporting content
   uses open layouts, dividers, and matte cards.
3. **Real data only.** Counts, progress, timing, storage, and notification content always come from
   existing state. The UI does not invent scores or weekly history.
4. **Same identity in both themes.** Light mode renders the system on warm paper; dark mode renders
   it in ink teal. Tangerine, mint, and sun keep the same meaning.
5. **Native controls stay native.** Text, toggles, buttons, progress, and semantics are Compose
   elements. Raster artwork is restricted to the three onboarding backgrounds.

## Palette

| Role | Dark | Light |
|---|---|---|
| Canvas | `#06171B` | `#F5F0E4` |
| Raised surface | `#0D2529` | `#FFFCF4` |
| Primary text | `#F6EEDC` | `#10262A` |
| Secondary text | `#AFC1BB` | `#526367` |
| Action / important | `#FF7346` | `#C64A24` |
| Calm / local / quiet | `#66E0BE` | `#0E6B59` |
| Normal / learning | `#F4C95D` | `#765607` |

Priority colors remain semantic:

- urgent — coral;
- important — tangerine;
- normal — sun;
- can wait — mint;
- quiet — blue-grey.

## Type and shape

Inter is used throughout. Screen headings are deliberately large and compact; uppercase eyebrows
use additional tracking. Cards use 24dp corners, feature surfaces use 28dp, controls use fully
rounded pills, and screen gutters remain 20dp.

## Motion

- small control response: spring, roughly 180ms perceived;
- content entrance and chart changes: gentle spring, 280–420ms;
- onboarding background drift: 8 seconds;
- sorting particles: 5.6 seconds;
- privacy shell pulse: staggered 5.6-second loop;
- learning milestones: sequential pulse along seven points.

The existing Reduced Motion preference collapses all app motion to a static state through the
shared `Motion` helpers and `LocalMotionEnabled`.

## Components

One canonical set, in `ui/components/SignalComponents.kt`. Screens build from these and nothing
else, so a change lands everywhere at once:

| Component | Role |
|---|---|
| `SignalCard` | standard container: matte fill, one hairline |
| `SignalFeatureSurface` | the single inverted ink surface per screen, in both themes |
| `SignalScreenHeader` | screen title and subtitle |
| `SignalSectionHeader` | section title with an optional action |
| `SignalEyebrow` | uppercase label above a heading |
| `SignalDot` | status dot |
| `AttentionBrand`, `OnDeviceBadge` | wordmark and the on-device assurance pill |

`SignalFeatureSurface` provides its own content colour — do not restate `color =` on text inside
it. Doing so is how a light-mode-only invisible-text bug gets in.

Every user-visible string lives in `res/values/strings.xml`; screens read them with
`stringResource`. Counts use `<plurals>`. Compose animation `label =` arguments are diagnostic
identifiers, not copy, and stay as literals.

Charts encode data. A lane, bar or ring whose geometry is fixed regardless of the value is not
allowed — an empty measurement must render empty.

## Screen mapping

- **Onboarding:** three full-bleed chapters—prioritization, privacy, learning. Notification access
  and interruption preferences remain available inside those pages.
- **Home:** protection status, live attention-flow lanes, real focus estimate, review prompt, and
  recent decisions.
- **Review:** decision history plus an inverse-surface swipe deck with explicit accessible actions.
- **Summary:** today’s distribution, real inference timing, learning progress, safety floors, and
  the existing local test lab.
- **Settings:** control-center status followed by every existing preference, storage summary,
  export, reset, replay, retention, reminders, and deletion.
