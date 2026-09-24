# Tripail

Android app for travellers that covers the world map in fog of war and
permanently reveals the areas you have actually visited. Built with Kotlin,
Jetpack Compose, Clean Architecture, Room, MapLibre, Uber H3 and MapTiler.

---

## Download / Demo

APK available in the [releases section](https://github.com/dabrowskik05/tripail/releases/latest).

Requires Android 8.0+ (`armeabi-v7a` / `arm64-v8a`). No account. Grant location
permission and the map starts clearing as you move.

---

## What it does

- The map starts fully fogged.
- Every GPS fix unlocks a corridor around your position and stores it locally.
- You can also search for a city, region or country and reveal it manually.
- Coverage is shown at four levels: world → continent → country → region.

Visited ground is indexed with [Uber H3](https://h3geo.org). Background tracking
runs in a `location` foreground service so the trail continues with the app in
the background.

---

## Features

- **Fog of war** — one MapLibre fill layer over the world; unlocked areas are
  punched out as holes in a single GeoJSON source (never one layer per hexagon).
- **Reveal by walking** — foreground service, ~1 km corridor along the route,
  gaps between GPS fixes bridged so a lost signal does not leave holes.
- **Search unlock** — MapTiler geocoding; pick a result, then confirm the reveal.
- **Coverage stats** — percentages per continent, country and region from the
  same spatial index used for rendering.
- **Polish / English** — UI, map labels and geocoder follow the same setting.
- **Offline-first** — unlocks live in Room; routes are never uploaded.
- **Battery-conscious tracking** — balanced power, ~10–15 s interval, 50 m
  minimum displacement.

---

## Architecture

Clean Architecture + MVI, five Gradle modules:

```
:app      composition root — Application, MainActivity, Hilt, platform services
:ui       Compose screens, ViewModels, MVI contracts, MapLibre
:domain   pure Kotlin — models, use cases, repository interfaces
:data     Room, DAOs, repository impls, H3 wrapper, LocationTracker, Retrofit
:core     shared utilities, dispatcher qualifiers
```

Dependency rules: `:ui → :domain`, `:data → :domain`, `:domain → nothing`.
Room entities stop at the repository boundary.

Each screen is a ViewModel with immutable `State`, `Intent`s and one-shot
`Effect`s.

### Stack

| Concern | Choice |
|---|---|
| Language | Kotlin, JDK 17 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVI |
| DI | Hilt |
| Async | Coroutines + Flow |
| Persistence | Room + DataStore |
| Maps | MapLibre GL Android |
| Spatial index | Uber H3 |
| Networking | Retrofit + kotlinx.serialization |
| Geocoding | MapTiler (Room-backed cache) |
| Boundaries | Local PMTiles bundle |
| Build | Gradle Kotlin DSL + Version Catalogs |

Google Maps / Mapbox SDKs were skipped on purpose: custom inverted fog needs an
open GL stack, and Mapbox's licence rules it out for this project.

---

## How unlocks are stored

H3 alone does not scale to whole regions. Ownership is stored two ways:

| Scale | Trigger | Stored as |
|---|---|---|
| Micro | GPS fix (~1 km) | `Set<Long>` of H3 indices (res 11) |
| Macro | region / country | boundary feature id |
| Macro | searched place | centre + radius |

Both become holes in the same fog polygon. A Polish voivodeship at resolution 11
is ~17M cells — storing the outline id instead avoids a guaranteed OOM.

---

## Background tracking

A `location`-typed foreground service keeps recording after the app is closed:

1. `stopWithTask="false"` + `START_STICKY`
2. Persisted tracking intent (survives process death)
3. Persisted session (trail continues across restart)
4. Partial wake lock

The app also asks for a battery-optimisation exemption — OEM killers are the
usual reason tracking goes silent.

---

## Build

```bash
git clone https://github.com/<your-account>/tripail.git
cd tripail

# local.properties
#   sdk.dir=/path/to/Android/Sdk
#   MAPTILER_API_KEY=your_key   # optional

./gradlew assembleDebug
./gradlew installDebug
./gradlew test
./gradlew ktlintCheck detekt
```

JDK 17 required. API keys stay in `local.properties` → `BuildConfig`, never in
git.

More detail: [`docs/`](docs) — map provider, H3, GPS service, geocoding.

---

## Quality

- Unit tests across `:domain`, `:data` and `:ui`
- ktlint + detekt; missing string translations fail the build
- GitHub Actions CI on push / PR

Open issues: [TODO.md](TODO.md).

---

## Attribution

Map tiles: [MapTiler](https://www.maptiler.com) via [MapLibre](https://maplibre.org).  
Geocoding © MapTiler © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright).  
Boundaries: [Natural Earth](https://www.naturalearthdata.com).  
Spatial index: [Uber H3](https://h3geo.org).

## License

MIT — see [LICENSE](LICENSE).
