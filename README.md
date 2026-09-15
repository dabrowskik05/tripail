# Tripex Pose

**A fog-of-war map for the real world.** The planet starts covered in darkness.
Walk somewhere, and that patch of the map is uncovered — permanently.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<!-- Screenshots: drop images into docs/images/ and link them here -->
<!-- <p align="center"><img src="docs/images/fog.png" width="30%"> … </p> -->

---

## What it is

Tripex Pose turns exploring into a map you fill in yourself. A dark layer covers the entire
world map. As you move through the real world, the app quietly records where you have been and
punches permanent holes in that layer. Places you have never visited stay dark.

There is no score, no streak and no social feed — just a personal map that gets more interesting
the more of the world you have actually walked.

## Features

- **Persistent fog of war** — a dark layer over the whole world, cut open only where you have been.
  Discoveries are permanent; nothing decays or resets.
- **Reveal as you walk** — a battery-conscious foreground service follows your position and opens
  the fog in real time along your route.
- **Unlock a city by name** — search for any place and uncover a radius around it, for the trips
  you took before installing the app.
- **Zoom-aware rendering** — the fog geometry is simplified at low zoom levels, so a world view
  stays smooth even with tens of thousands of visited cells.
- **Tiny footprint** — the entire history of everywhere you have been is a set of 64-bit integers.
  Years of walking fit in kilobytes.
- **Location stays on your device** — visited areas live in a local database and are never uploaded.
  The only outbound request is a city search, and it carries just the text you typed.
- **Light and dark themes**, following the system setting.

## How it works

The world is discretized with [Uber H3](https://h3geo.org), a hierarchical hexagonal grid.
Every position maps to one hexagonal cell — roughly 40 m across at the resolution this app uses —
and visiting a place unlocks a small disk of cells around it.

```
Foreground Service
  → FusedLocationProviderClient emits a location
  → UnlockAreaUseCase(lat, lng)
  → H3: latLngToCell(…) + gridDisk(index, ring)
  → UnlockedAreaRepository.unlock(Set<Long>)
  → Room insertAll(OnConflictStrategy.IGNORE)   // re-walking a cell is a cheap no-op
  → DAO emits Flow<List<Long>>
  → GeoJSON MultiPolygon assembled off the main thread
  → MapLibre GeoJsonSource.setGeoJson(…)        // the fog is punched through
```

Two design decisions carry most of the weight:

**The database stores nothing but cell indices.** No polygon merging, no spatial database, no
geometry tables — just a set of `Long` values with the index as the primary key. Re-walking a
street you already covered inserts nothing new.

**The fog is a single map layer.** Unlocked hexagons are delivered as one GeoJSON `MultiPolygon`
in one `GeoJsonSource`, punched out of one dark `FillLayer`. There is never a layer, source or
annotation per hexagon — that approach dies at a few thousand cells; this one does not.

Deeper write-ups live in [`docs/`](docs/):
[H3 architecture](docs/H3_ARCHITECTURE.md) ·
[location service spec](docs/GPS_SERVICE_SPEC.md) ·
[map provider comparison](docs/MAP_PROVIDER_RESEARCH.md) ·
[geocoding API notes](docs/GEOCODING_API_RESEARCH.md)

## Architecture

Clean Architecture with an MVI presentation layer, split across five Gradle modules:

```
:app      composition root — Application, MainActivity, location service, Hilt wiring
:ui       Compose screens, ViewModels, MVI contracts, MapLibre rendering
:domain   pure Kotlin — models, use cases, repository interfaces
:data     Room, DAOs, repository implementations, H3 wrapper, location tracker, Retrofit
:core     shared abstractions — dispatcher qualifiers, logging facade
```

Dependencies flow one way: `:app → everything`, `:ui → :domain`, `:data → :domain`,
and `:domain → nothing`. `:domain` is a plain JVM module with no Android dependency at all,
which keeps its tests fast and its logic portable. Room entities stop at the repository
boundary; only domain models cross it. `:ui` importing from `:data` is treated as a bug.

Each screen owns a single contract file declaring its `State` (immutable, exposed as a
`StateFlow`), `Intent` (user and system input) and `Effect` (one-shot navigation and messages).
Composables are stateless and take `state` + `onIntent`, which keeps them previewable.

## Tech stack

| Concern | Choice |
|---------|--------|
| Language / build | Kotlin, JDK 17, Gradle Kotlin DSL, version catalogs |
| UI | Jetpack Compose, Material 3, MVI |
| Dependency injection | Hilt |
| Concurrency | Coroutines, Flow |
| Persistence | Room |
| Spatial index | [Uber H3](https://h3geo.org) |
| Map engine | [MapLibre GL Android](https://maplibre.org) |
| Location | `FusedLocationProviderClient` in a `location` foreground service |
| Networking | Retrofit, OkHttp, kotlinx.serialization |
| Testing | JUnit4, Turbine, MockK, Robolectric |
| Static analysis | ktlint, detekt |

**Deliberately not used:** Flutter and React Native (bridge overhead on a map-plus-location
workload), the Google Maps SDK (too rigid for a custom inverted mask), the Mapbox SDK (licensing),
and Spatialite (unnecessary once H3 reduces geometry to integers).

`minSdk 26` · `targetSdk 36` · `compileSdk 37`

## Getting started

### Requirements

- JDK 17
- Android SDK with platform 37
- Android Studio, or just the Gradle wrapper included in the repository

### Configuration

Create `local.properties` in the project root (it is gitignored and must never be committed):

```properties
sdk.dir=/path/to/Android/Sdk
MAPTILER_API_KEY=your_maptiler_key
```

The MapTiler key is **optional**. Without it the app falls back to MapLibre's public demo tiles —
the map looks plainer, but the fog, tracking and search all work. A free key from
[maptiler.com](https://www.maptiler.com) gets you proper street tiles.

### Build and run

```bash
./gradlew assembleDebug     # build a debug APK
./gradlew installDebug      # install on a connected device or emulator
```

On first launch the app asks for precise location permission, and on Android 13+ for notification
permission as well — the tracking service runs in the foreground and needs a visible notification.

### Tests and static analysis

```bash
./gradlew test              # unit tests, all modules
./gradlew :domain:test      # domain only — the fast feedback loop
./gradlew ktlintCheck detekt
```

Every use case and every mapper is unit tested. ViewModels are tested with Turbine over `state`
and `effect`; repositories with fakes rather than mocked Room. Continuous integration runs the
debug build, the full test suite and static analysis on every push and pull request.

## Attribution

Map tiles by [MapTiler](https://www.maptiler.com) and rendering by
[MapLibre](https://maplibre.org). Place search uses [Nominatim](https://nominatim.org);
geocoding data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
Spatial indexing by [Uber H3](https://h3geo.org).

## License

Released under the MIT License. See [LICENSE](LICENSE).
