#!/usr/bin/env bash
# Builds app/src/main/assets/tiles/boundaries.pmtiles (ADM0 + ADM1) from Natural Earth 10m.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ATLAS_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT/app/src/main/assets/tiles"
SCRATCH="${ATLAS_SCRATCH:-/tmp/tripex-atlas-raw}"
MAPSHAPER="${ATLAS_DIR}/node_modules/.bin/mapshaper"
PMTILES="${ATLAS_DIR}/node_modules/.bin/pmtiles"
LOCAL_PREFIX="${ATLAS_DIR}/../.local"
export PATH="${LOCAL_PREFIX}/usr/bin:${PATH}"
export LD_LIBRARY_PATH="${LOCAL_PREFIX}/usr/lib/x86_64-linux-gnu:${LOCAL_PREFIX}/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}"

# Integer bumped on every regeneration — invalidates coverage cache (M3.4).
BOUNDARIES_VERSION=4
# Lower ADM1 maxzoom if the archive exceeds 12 MB (plan: try 6).
MAXZOOM="${MAXZOOM:-8}"

if [[ ! -x "$MAPSHAPER" ]]; then
  echo "mapshaper missing — run: (cd tools/atlas && npm install)" >&2
  exit 1
fi
if ! command -v tippecanoe >/dev/null 2>&1; then
  echo "tippecanoe not on PATH (install system package or extract to tools/.local)" >&2
  exit 1
fi

mkdir -p "$SCRATCH" "$OUT_DIR"
cd "$SCRATCH"

ADM0_ZIP=ne_10m_admin_0_countries.zip
ADM1_ZIP=ne_10m_admin_1_states_provinces.zip

if [[ ! -f "$ADM0_ZIP" ]]; then
  curl -fsSL -o "$ADM0_ZIP" \
    "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_countries.zip"
fi
if [[ ! -f "$ADM1_ZIP" ]]; then
  curl -fsSL -o "$ADM1_ZIP" \
    "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_1_states_provinces.zip"
fi

unzip -o -q "$ADM0_ZIP" -d ne_10m_admin_0_countries
unzip -o -q "$ADM1_ZIP" -d ne_10m_admin_1_states_provinces

ADM0_SHP="$(find ne_10m_admin_0_countries -name 'ne_10m_admin_0_countries.shp' | head -1)"
ADM1_SHP="$(find ne_10m_admin_1_states_provinces -name 'ne_10m_admin_1_states_provinces.shp' | head -1)"

WORK="$SCRATCH/boundaries_work"
rm -rf "$WORK"
mkdir -p "$WORK"

# Field names MUST match domain BoundaryFields (iso_a2, name, name_pl, adm1_code, continent).
#
# Russia is forced to Asia. Natural Earth files it under Europe, which put it in the European
# continent filter and made "isolate Europe" show Siberia. The overview atlas splits Russia at the
# Urals via map subunits; admin-0 has it as one feature, so the whole country goes to Asia here.
#
# ISO_A2 is NOT usable on its own: Natural Earth stores "-99" there for France, Norway and
# Kosovo (among others), so filtering on it silently deleted three countries from the map —
# France and Kosovo were simply missing and unclickable in Europe. ISO_A2_EH ("editorial
# handling") carries FR / NO / XK for exactly these cases, so it is the primary source and
# ISO_A2 / WB_A2 are fallbacks. Features where all three are "-99" (Somaliland, N. Cyprus,
# buffer zones, uninhabited reefs) genuinely have no ISO code and stay out.
"$MAPSHAPER" -i "$ADM0_SHP" \
  -each 'iso_a2 =
    (ISO_A2_EH && ISO_A2_EH !== "-99") ? ISO_A2_EH :
    (ISO_A2 && ISO_A2 !== "-99") ? ISO_A2 :
    (WB_A2 && WB_A2 !== "-99") ? WB_A2 : null' \
  -filter 'iso_a2 !== null && iso_a2 !== ""' \
  -each 'name=NAME; name_pl=(NAME_PL && NAME_PL !== "") ? NAME_PL : NAME; continent=
    ADM0_A3==="RUS" ? "Asia" :
    CONTINENT==="North America" ? "NorthAmerica" :
    CONTINENT==="South America" ? "SouthAmerica" :
    CONTINENT==="Europe" ? "Europe" :
    CONTINENT==="Africa" ? "Africa" :
    CONTINENT==="Asia" ? "Asia" :
    CONTINENT==="Oceania" ? "Oceania" :
    CONTINENT==="Antarctica" ? "Antarctica" : CONTINENT' \
  -filter-fields iso_a2,name,name_pl,continent \
  -simplify 10% keep-shapes \
  -o format=geojson precision=0.0001 "$WORK/adm0.geojson"

# ADM1, exploded into single polygons.
#
# Natural Earth models an Aegean region as ONE MultiPolygon of up to 36 islands, so a tap on any
# island selected — and would unlock — all of them. `-explode` turns every physical landmass into
# its own feature, and each part gets a unique `adm1_code` suffix so selection and the
# `unlocked_region` table address one island at a time.
#
# Polish names come from `name_pl`, which Natural Earth ships for both admin levels; `name` is
# English and is kept only as a fallback.
"$MAPSHAPER" -i "$ADM1_SHP" \
  -filter 'adm1_code !== null && adm1_code !== "" && iso_a2 !== "-99" && iso_a2 !== null && iso_a2 !== ""' \
  -explode \
  -each 'name_pl = (name_pl && name_pl !== "") ? name_pl : name;
         adm1_code = adm1_code + "#" + this.id' \
  -filter-fields adm1_code,iso_a2,name,name_pl \
  -simplify 12% keep-shapes \
  -o format=geojson precision=0.0001 "$WORK/adm1.geojson"

tippecanoe -o "$WORK/boundaries.pmtiles" --force \
  --drop-densest-as-needed \
  --coalesce-densest-as-needed \
  --minimum-zoom=0 \
  --maximum-zoom="$MAXZOOM" \
  -L "adm0:$WORK/adm0.geojson" \
  -L "adm1:$WORK/adm1.geojson" \
  -N "Tripail boundaries v${BOUNDARIES_VERSION}" \
  -A "Natural Earth / public domain"

SIZE_MB=$(du -m "$WORK/boundaries.pmtiles" | cut -f1)
echo "boundaries.pmtiles = ${SIZE_MB} MB, boundaries_version=$BOUNDARIES_VERSION"
if (( SIZE_MB > 12 )); then
  echo "ERROR: exceeds 12 MB — re-run with MAXZOOM=6" >&2
  exit 1
fi

cp "$WORK/boundaries.pmtiles" "$OUT_DIR/boundaries.pmtiles"
echo "$BOUNDARIES_VERSION" > "$OUT_DIR/boundaries_version.txt"

if [[ -x "$PMTILES" ]]; then
  "$PMTILES" show "$OUT_DIR/boundaries.pmtiles" || true
fi

echo "Wrote $OUT_DIR/boundaries.pmtiles"
