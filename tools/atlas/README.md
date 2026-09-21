# Atlas & boundary tile toolchain

Scripts regenerate spatial assets for Tripail V2. **Do not commit** raw Natural
Earth shapefiles — only the outputs under `app/src/main/assets/`.

## Versions

| Key | Value |
|-----|-------|
| Natural Earth | 5.1.1 (50m admin-0 map subunits for atlas; 10m admin-0 / admin-1 for boundaries) |
| mapshaper | see `package.json` / `npm ls mapshaper` |
| tippecanoe | 2.49.0 (or newer) |
| `atlas_version` | **3** (bump in `build_atlas.sh` on regenerate) |
| `boundaries_version` | **1** (bump in `build_boundaries.sh` on regenerate; mirrored in `BuildConfig`) |

Alternative country/region source (not used): [geoBoundaries](https://www.geoboundaries.org/)
— considered for ADM1 quality; Natural Earth kept for size and public-domain clarity.

## Setup

```bash
cd tools/atlas
npm install          # mapshaper + pmtiles CLI
# tippecanoe: apt install tippecanoe   OR extract .deb into tools/.local
```

## Atlas (Canvas + land mask) — M1.1

```bash
./tools/atlas/build_atlas.sh
# If > 700 KB:
SIMPLIFY_PCT=12% ./tools/atlas/build_atlas.sh
```

Output: `app/src/main/assets/atlas/continents.geojson`  
Each feature property `continent` ∈ `ContinentId` enum names
(`NorthAmerica`, `SouthAmerica`, `Europe`, `Africa`, `Asia`, `Oceania`, `Antarctica`).

Source: `ne_50m_admin_0_map_subunits`, dissolved by `CONTINENT`, then Douglas–Peucker
via mapshaper `-simplify`. Output is 7 features — one MultiPolygon per continent.

### Why subunits, and not the two obvious alternatives

Both earlier attempts produced wrong data that still looked plausible on screen:

- **`ne_50m_land` + `-join countries largest-overlap` (atlas_version 1).** The land layer holds
  Africa + Europe + Asia as a *single* polygon, so the join tagged that entire landmass with one
  country's continent — Russia's, i.e. `Europe`. Africa and Asia effectively vanished as separate
  continents while the file still parsed and rendered fine.
- **`ne_50m_admin_0_countries` dissolved by continent (atlas_version 2).** Overseas departments
  live inside their parent country's geometry, so French Guiana inherited France's continent and
  drew as a chunk of Europe in South America. Same for Mayotte and Réunion.

Subunits split both cases at the source: France becomes metropolitan France + Corsica + Guyane +
Guadeloupe + Martinique + Mayotte + Réunion, and Russia arrives already divided at the Urals
(`RUA` → Asia; `RUE`, Kaliningrad, Crimea → Europe). No per-country overrides are needed.

**`Seven seas (open ocean)`.** 18 remote-island subunits (Azores, Seychelles, Maldives, Andaman,
St Helena, S. Georgia, …) carry no continent and are mapped explicitly by `SU_A3` in the script.
Adding an island group to Natural Earth without adding it there drops it silently.

**Regression test.** `AtlasRepositoryImplTest` must probe *mainland* points, not only islands —
Greenland, Iceland, Japan and New Zealand all stayed green through the entire lifetime of the
first bug.

## Boundaries PMTiles — M1.5

```bash
./tools/atlas/build_boundaries.sh
# If > 12 MB:
MAXZOOM=6 ./tools/atlas/build_boundaries.sh
```

Output: `app/src/main/assets/tiles/boundaries.pmtiles`  
Layers: `adm0`, `adm1` (names = `BoundaryFields.LAYER_*`).  
Properties trimmed to: `iso_a2`, `name`, `name_pl`, `adm1_code`, `continent`
(ADM1 has `iso_a2` of the parent country — required for country→region filters).

Verify:

```bash
npx --prefix tools/atlas pmtiles show app/src/main/assets/tiles/boundaries.pmtiles
```

## Scratch directory

Downloads land in `/tmp/tripex-atlas-raw` by default (`ATLAS_SCRATCH` to override).
That directory is gitignored and must not enter the repo.
