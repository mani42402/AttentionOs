# AttentionOS visual language

Derived from the reference designs supplied for this project (weather, wallet, smart-home and
navigation kits). Those references disagree on subject matter and agree almost perfectly on
treatment, so this document captures the treatment.

## The rule that matters most

**The canvas is never flat.** A single background colour — white or black — was the defining
problem with the previous build. Every screen sits on a deep gradient that shifts across its
surface, and content floats above it on translucent glass.

Everything below follows from that.

## Palette

Dark-first. The gradient canvas is the identity; light mode is a lighter rendering of the same
idea, not a different design.

| Role | Dark | Light |
|---|---|---|
| Canvas top | `#1B1044` deep violet | `#EFE9FF` |
| Canvas mid | `#241556` | `#F5F1FF` |
| Canvas bottom | `#0E0A24` near-black violet | `#FBF9FF` |
| Accent (primary) | `#7C5CFF` violet | `#5B3FE0` |
| Accent glow | `#A78BFA` | `#7C5CFF` |
| Secondary | `#38E0C0` mint | `#0E9E86` |
| Glass fill | `#FFFFFF` @ 8% | `#FFFFFF` @ 62% |
| Glass border | `#FFFFFF` @ 14% | `#FFFFFF` @ 80% |
| Text primary | `#F4F1FF` | `#1A1230` |
| Text secondary | `#B9B2D8` | `#5C5480` |

Priority colours stay semantic and constant across themes, so "amber means it can wait" is
learned once:

| Priority | Colour |
|---|---|
| Urgent | `#FF5A6E` |
| Important | `#FF9F45` |
| Normal | `#7C5CFF` |
| Can wait | `#38E0C0` |
| Quiet | `#8B85AD` |

## Surfaces

**Aurora canvas.** A vertical gradient through the three canvas stops, with two large soft
radial blooms — violet top-left, mint bottom-right — drifting slowly. Motion is barely
perceptible by design: it should read as depth, not as animation.

**Glass card.** Translucent fill over the canvas, a 1dp hairline border at higher opacity along
the top edge, and a 24dp radius. Depth comes from the border catching light, not from shadows.

**Feature card.** For hero content: a gradient fill in the accent family plus a diagonal light
streak running corner to corner, exactly as in the weather references. Reserved for one element
per screen so it stays special.

**Bento tiles.** Small glass squares carrying a single figure each, arranged two or three across.
Some carry a radial gauge, one carries a sparkline. This is what replaces uniform stacked rows.

## Type

Inter throughout, but the hierarchy is far more extreme than before: hero numerals are enormous
and their labels are small and quiet. The references get their impact from that contrast, not
from many different sizes.

| Use | Size / weight |
|---|---|
| Hero numeral | 64sp ExtraBold, −2 tracking |
| Screen title | 30sp Bold, −0.6 |
| Card title | 19sp SemiBold |
| Body | 15sp Regular |
| Label | 12sp SemiBold, +0.4, often uppercase |

## Shape and spacing

Radii: tiles 20dp, cards 24dp, hero 28dp, pills fully rounded. Spacing stays on the 4dp grid,
with 20dp screen gutters and 16dp between cards.

## Motion

Ambient drift on the canvas, content animating in on entry, numerals counting up, gauges
sweeping on change. All of it collapses to nothing when the reduced-motion preference is off —
that is enforced by the `Motion` helpers rather than left to each call site.

## Navigation

Bottom bar as a floating glass pill rather than a full-width slab. The active tab is a filled
violet pill with icon and label; inactive tabs show the icon alone. Taken from the e-commerce and
fintech patterns in the navigation kit.

## Screen mapping

- **Onboarding** — full-bleed aurora, one large illustrative mark per page, oversized headline,
  pill indicator, glass Continue button.
- **Home** — feature card with the day's count as a hero numeral, a bento row beneath
  (needed attention / stayed quiet / focus protected), then notifications as glass rows.
- **Review** — the decision card is a feature card on the aurora; swiping tilts it and bleeds
  the outcome colour into the canvas behind.
- **Insights** — bento grid: attention ring, learning gauge, 7-day sparkline, protection card.
- **Settings** — glass groups on the aurora, with the segmented theme control as a glass pill.
