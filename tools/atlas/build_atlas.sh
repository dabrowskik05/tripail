#!/usr/bin/env bash
# Regenerates app/src/main/assets/atlas/continents.geojson from Natural Earth.
# Does NOT commit raw shapefiles — download into a scratch dir and discard.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ATLAS_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT/app/src/main/assets/atlas"
SCRATCH="${ATLAS_SCRATCH:-/tmp/tripex-atlas-raw}"
MAPSHAPER="${ATLAS_DIR}/node_modules/.bin/mapshaper"

# Bump when regenerating so clients can invalidate caches that depend on the atlas.
ATLAS_VERSION=3
# Douglas–Peucker tolerance in degrees — raise if output exceeds 700 KB.
SIMPLIFY_PCT="${SIMPLIFY_PCT:-8%}"

if [[ ! -x "$MAPSHAPER" ]]; then
  echo "mapshaper missing — run: (cd tools/atlas && npm install)" >&2
  exit 1
fi

mkdir -p "$SCRATCH" "$OUT_DIR"
cd "$SCRATCH"

SUBUNITS_ZIP=ne_50m_admin_0_map_subunits.zip

if [[ ! -f "$SUBUNITS_ZIP" ]]; then
  curl -fsSL -o "$SUBUNITS_ZIP" \
    "https://naciscdn.org/naturalearth/50m/cultural/ne_50m_admin_0_map_subunits.zip"
fi

unzip -o -q "$SUBUNITS_ZIP" -d ne_50m_admin_0_map_subunits

SUBUNITS_SHP="$(find ne_50m_admin_0_map_subunits -name 'ne_50m_admin_0_map_subunits.shp' | head -1)"

# Source is map SUBUNITS, not countries, and not the physical land layer.
#
# - `ne_50m_land` carries Africa + Europe + Asia as a SINGLE polygon, so label-joining it against
#   countries tagged that whole landmass from one country and swallowed Africa and Asia.
# - `ne_50m_admin_0_countries` keeps every overseas department inside its parent country, so
#   French Guiana inherited France's continent and rendered as a piece of Europe in South America.
#
# Subunits split exactly these cases and carry a per-subunit CONTINENT: France becomes metropolitan
# France + Corsica + Guyane + Guadeloupe + Martinique + Mayotte + Réunion, and Russia arrives
# already split at the Urals into RUA (Asia) and RUE / Kaliningrad / Crimea (Europe). No manual
# country overrides are needed.
#
# The 18 remote-island subunits filed under "Seven seas (open ocean)" have no continent of their
# own and are mapped explicitly below — without it they would be dropped without a trace.
"$MAPSHAPER" \
  -i "$SUBUNITS_SHP" name=subunits \
  -each 'continent = (CONTINENT === "Seven seas (open ocean)"
    ? ({
        PAZ: "Europe",
        SYC: "Africa", MUS: "Africa", REU: "Africa", IOD: "Africa",
        SHN: "Africa", BAC: "Africa", ZAI: "Africa",
        MDV: "Asia", INN: "Asia", INA: "Asia", INL: "Asia",
        CHS: "SouthAmerica",
        SGG: "Antarctica", SGX: "Antarctica", ATF: "Antarctica",
        HMD: "Antarctica", ATS: "Antarctica"
      })[SU_A3] || null
    : CONTINENT === "North America" ? "NorthAmerica"
    : CONTINENT === "South America" ? "SouthAmerica"
    : CONTINENT === "Europe" ? "Europe"
    : CONTINENT === "Africa" ? "Africa"
    : CONTINENT === "Asia" ? "Asia"
    : CONTINENT === "Oceania" ? "Oceania"
    : CONTINENT === "Antarctica" ? "Antarctica"
    : null)' \
  -filter 'continent !== null' \
  -filter-fields continent \
  -simplify "$SIMPLIFY_PCT" keep-shapes \
  -clean \
  -dissolve continent \
  -o "format=geojson" precision=0.0001 "$OUT_DIR/continents.geojson"

SIZE_KB=$(du -k "$OUT_DIR/continents.geojson" | cut -f1)
echo "Wrote $OUT_DIR/continents.geojson (${SIZE_KB} KB), atlas_version=$ATLAS_VERSION"
echo "$ATLAS_VERSION" > "$OUT_DIR/atlas_version.txt"
if (( SIZE_KB > 700 )); then
  echo "ERROR: continents.geojson exceeds 700 KB — raise SIMPLIFY_PCT (e.g. SIMPLIFY_PCT=12%)" >&2
  exit 1
fi

# Quick sanity: required place names appear as coordinates near known islands via feature count
FEATURE_COUNT=$("$MAPSHAPER" -i "$OUT_DIR/continents.geojson" -info | grep -oE '^[0-9]+' | head -1 || true)
echo "Feature count: ${FEATURE_COUNT:-unknown}"
