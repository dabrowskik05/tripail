# Tripail

**A fog-of-war map for the real world.** Undiscovered land looks like aged parchment;
walk somewhere and that patch snaps back to vivid map colour — permanently.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## What it is

Tripail turns exploring into a map you fill in yourself. A parchment wash covers places
you have never visited. As you move through the real world, the app records where you have
been and punches permanent holes in that wash so the vivid basemap shows through.

There is no score, no streak and no social feed — just a personal map that gets more interesting
the more of the world you have actually walked.

## Features

- **Parchment reveal** — desaturated wash over the world; unlocked H3 cells are geographic holes
  into the full-colour MapLibre basemap (not a black fog overlay).
- **Reveal as you walk** — a battery-conscious foreground service follows your position and opens
  the map in real time along your route.
- **Unlock a city by name** — search for any place and uncover a radius around it.
- **Continent overview** — after loading, a cartoon SVG world shows real per-continent coverage;
  explore opens MapLibre centred on that continent (back returns to the overview).
- **Cartoon shell UI** — Tripail loading logo (Fredoka), pill search, FAB menu, sheets and dialogs.
- **Tiny footprint** — visited history is a set of 64-bit H3 indices.
- **Location stays on your device** — local Room only; Nominatim search sends just the typed text.

## How it works

```
Loading → Continent SVG overview → MapLibre (camera on continent bbox)
MapLibre Back → Continents
```

Unlocked cells are outlined with Uber H3 `cellsToMultiPolygon` at walking resolution and fed as
one GeoJSON source (world polygon with holes). LOD parent indices are never used as outline
geometry, so hex size stays geographic across zoom.

## Architecture

Clean Architecture + MVI across `:app`, `:ui`, `:domain`, `:data`, `:core`.
`:ui` never imports `:data`. Package id remains `com.tripex.pose` (display name is Tripail).

## Getting started

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test ktlintCheck detekt
```

Create `local.properties` with `sdk.dir` and optional `MAPTILER_API_KEY`.

## Attribution

Map tiles by [MapTiler](https://www.maptiler.com) / [MapLibre](https://maplibre.org).
Search © [OpenStreetMap](https://www.openstreetmap.org/copyright) (Nominatim).
Spatial index [Uber H3](https://h3geo.org).

## License

MIT — see [LICENSE](LICENSE).
