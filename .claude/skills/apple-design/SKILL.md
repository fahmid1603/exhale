---
name: apple-design
description: Apple/iOS design rules for Exhale — type scale, spacing, tap targets, grouped lists, sheets, tab bar and Apple Music layout conventions. Load before changing any Exhale UI, or when asked to make a screen look like Apple Music / iOS.
---

# Apple design rules for Exhale

Exhale is an iOS-looking app built in Compose. These are the numbers to build to. When a screen is
called "Walmart", it is almost always one of these being wrong: type too large and too bold, an
accent colour where grey belongs, or a container where Apple has none.

## Type (iOS Dynamic Type at default size)

| Role | Size | Weight | Used in Exhale for |
|---|---|---|---|
| Large Title | 34 | Bold | Page titles: Home, Settings, Library |
| Title 1 | 28 | Bold | Rare; big empty-state headings |
| Title 2 | 22 | Bold | Shelf headers ("Top Picks for You ›") |
| Title 3 | 20 | Semibold | Account name, sheet headings |
| Headline | 17 | Semibold | Row titles that lead a group, mini-player title |
| Body | 17 | Regular | Settings row titles |
| Callout | 16 | Regular | — |
| Subhead | 15 | Regular | Secondary lines, queue row titles |
| Footnote | 13 | Regular | Captions under artwork, group headers, subtitles |
| Caption | 11–12 | Regular/Medium | Eyebrows, badges |

Tracking is negative at display sizes and ~0 at caption sizes. Exhale sets this centrally in
`ui/theme/Type.kt`; use the roles, don't hand-set sizes unless matching a reference exactly.

**The font is Linotte** (UI and lyrics), never swap it — see the `exhale-design-identity` memory.
Linotte is wider than SF, so body/label roles use 0 tracking rather than SF's negative values.

## Spacing and targets

- Screen side margin: **16dp**. Everything on a page lines up on it, including shelf headers.
- Grid: multiples of **8dp**; 4dp only for text-to-text gaps.
- Minimum tap target: **44dp**, even when the glyph is 18–22dp.
- Corner radii: grouped cards 16–26dp, artwork tiles 8–12dp, artist artwork circular,
  capsules/pills fully round.

## Grouped lists (Settings, Account, menus)

- Rows sit in a rounded card on the page background, with hairline dividers **inset to the label**,
  never full-bleed, and never above the first or below the last row.
- Row icon: a **solid colour rounded square, 30dp, radius 8dp, white glyph 18dp**. Colours come from
  the iOS system palette (`IosSystemColors`), not from the app's pastel theme accents.
- Row title Body 17 regular; value/subtitle Footnote 13 grey; chevron `navigate_next` at 22dp,
  `onSurfaceVariant` at ~45% alpha.
- Group headers are Footnote grey, sentence case, quiet — never Title-sized and never bold.
- A pressed row fills edge to edge with a neutral grey wash and does not move or scale.

## Shelves and artwork (Apple Music)

- Header is `Title ›` on one line — no thumbnail, no uppercase eyebrow above it.
- Captions under artwork: title 14 medium white, subtitle 13 grey. Never bold-17, never accent
  colour for a subtitle like "1.1M views".
- A card is **its artwork**: title drawn over the art under a soft bottom fade, caption underneath
  in grey. A solid colour panel bolted under artwork reads as Spotify/YouTube Music.
- Recently Played: square tiles ~150dp; artists circular.

## Chrome

- Dock: floating capsules on a glass plate; the collapsed state is a **wide** now-playing pill
  between two small (52dp) circles — the pill owns the width, not the circles.
- The now-playing accessory is **one shared element** between its wide and inline positions
  (`nowPlayingAccessory`), so it flies rather than crossfades.
- Sheets: grabber, large title, grouped content, one filled capsule for the primary action
  (52dp tall, full width), destructive actions red-on-tint rather than filled red.
- Alerts: iOS shape — 290dp wide, 16dp radius, centred 17sp semibold title, 13sp message, buttons
  divided by hairlines, Cancel left.
- Chrome text must never be readable through glass: the mini player's tint is deliberately heavy
  because blur alone does not hide a headline scrolling under it.

## Checking the work

Judge Apple fidelity **on the running app**, not on offscreen renders: install to the MuMu
emulator (`adb connect 127.0.0.1:16384`), screenshot, and compare against the reference the user
gave. Frame-by-frame checks of animations: `adb shell screenrecord`, then `ffmpeg` to extract.
