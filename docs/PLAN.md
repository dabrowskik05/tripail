# PLAN.md — plan realizacji (dla Claude, nie dla użytkownika)

Źródło decyzji produktowych: `docs/POMYSLY.md` (✅ = zatwierdzone, *(na później)* = nie ruszać).
Twarde zasady starego produktu: `docs/TRIPAIL_VISION.md` — **część z nich ten plan świadomie zmienia** (lista w §0.3). Po każdym etapie zaktualizuj VISION, żeby nie kłamała.

Status etapów prowadź w tym pliku (`[ ]` → `[x]`).

---

## 0. Kontekst, którego nie da się wyczytać z kodu

### 0.1 Użytkownik
- **Apka ma być dla zwykłych, nietechnicznych ludzi.** Każda funkcja: zrozumiała bez instrukcji, max 1–2 kliknięcia, zero żargonu, duże przyciski. Jeśli coś wymaga tłumaczenia — uprość albo wyrzuć.
- **I dla ludzi łatwo się przebodźcowujących — „ADHD" w cudzysłowie:** nie chodzi dosłownie o osoby z diagnozą, tylko o to, że dziś większość ludzi ma problem z koncentracją i szybko się męczy nadmiarem bodźców. Mało elementów naraz, spokojne kolory i animacje, żadnych wyskakujących komunikatów bez potrzeby, jedna rzecz na ekranie na raz. Tryb spokojny (E10) to nie dodatek, tylko kierunek dla całej apki.
- Pisze po polsku, luźno. Odpowiadaj po polsku, **krótko, punktami** (ADHD — zero ścian tekstu).
- Nie jest programistą Androida na co dzień. Tłumacz decyzje „po ludzku", bez żargonu.
- **Zero kosztów.** Żadnych płatnych API, żadnych kart. Google Maps/Places odpada.
- **Pytaj o decyzje** wyglądu/nazw/kolorów zamiast zgadywać (AskUserQuestion, max 1–2 pytania naraz).
- Nie lubi przebodźcowania: mało elementów naraz, spokojne animacje.
- Nie chce „znajdziek"/grindu (wyrzucił: pierwszy odkrywca, rzadkość miejsc, porównania „3 Luksemburgi").
- Słowa „powiat", „gmina", „heks", „H3", „komórka" **nie mogą** pojawić się w UI.
- `docs/POMYSLY.md`, `docs/PLAN.md` i `prototype/` są w repo na gałęzi `v2` (żeby były dostępne na każdym komputerze).

### 0.2 Środowisko
- Teraz: Windows, brak Android Studio. Jest drugi komputer z Linuxem + Android Studio.
- Na Windowsie: `./gradlew test` (wymaga JDK 17), `./gradlew assembleDebug`. Instalacja na telefon: `platform-tools` + `adb install` — tylko jeśli użytkownik zainstaluje.
- Prototypy wizualne rób jako **HTML + MapLibre GL JS** i pokazuj w built-in browser — to główna pętla feedbacku dla wyglądu.
- Commity tylko na prośbę, wszystko na jednej gałęzi `v2`. Nazwy commitów krótkie i „ludzkie" (jedna linia, np. „Switch to OpenFreeMap and Photon"), bez list i esejów. **Zero wzmianek o Claude/AI** — bez `Co-Authored-By`, bez „Generated with Claude Code" (także w PR-ach).

### 0.3 Zmiany względem TRIPAIL_VISION.md
| Stara zasada | Nowa |
|---|---|
| Ślad = komórki H3 | Ślad = okruszki (punkty) + bufor JTS; H3 tylko do statystyk |
| Mgła 2-stanowa | 3 stany: gęsta / lekka / czysto |
| Menu kontynentów = osobny Compose Canvas | Jedna mapa MapLibre, „winda" pięter (+ opcjonalny globus) |
| MapTiler (styl + geokoder) | OpenFreeMap (styl) + Photon (geokoder) |
| Śledzenie w tle zawsze | Tryb podróży (włączany ręcznie) + jednorazowe „odkryj, gdzie jestem" |
| % = powierzchnia | % = odwiedzone „okolice" (niewidoczne jednostki), + liczniki miast/regionów/krajów |
| Po wejściu w kontynent cała mapa (ląd, woda, granice) w odcieniach koloru kontynentu | Kolor kontynentu = **podpis, nie farba**: pełne kolory tylko na piętrze Świat; dalej tylko akcenty (lekki odcień mgły nad kontynentem, pieczątki w paszporcie, przyciski/akcenty UI). Odkryty teren zawsze w żywych, naturalnych kolorach mapy |
| Mgła = jasny pergamin | Mgła **ciemniejsza i bardziej wyblakła** niż odkryty teren (w dzień i w nocy) — odkryte musi wyglądać jaśniej/żywiej |
| Auto-odkrycie miasta po dwell w promieniu 1.2 km | Tylko gdy dwell **wewnątrz** miasta (wewnętrzne ~50% promienia), obwodnica nie liczy się |

---

## 1. Mapa kodu (co jest gdzie)

- Moduły: `:app` (service, DI), `:ui` (Compose, MapLibre), `:domain` (czysty Kotlin + JTS), `:data` (Room, H3, Retrofit, PMTiles), `:core`.
- **Pipeline mgły:** `domain/usecase/ObserveFogGeoJsonUseCase` → `RevealUnion` (JTS union) → `FogGeoJsonBuilder` (świat z dziurami) → `ui/map/host/MapHostViewModel.fogGeoJson` → `TripailMapSurface.addFogLayers` (`reveal-wash` FillLayer + `reveal-edge` LineLayer, źródło `reveal-source`).
- **Ślad:** `TrackingService.onFix` → `UnlockAreaUseCase` → `H3Utils.revealAround(…, WALK_REVEAL_RADIUS_M)` + `TrailCorridor.stepsBetween` → `unlocked_hex` (res 11, z `parentRes9`, `parentRes7`, `discoveredAt`).
- **Rysowanie śladu:** `h3.outline(cells res9)` + `RingSmoothing` (Chaikin) — **to jest źródło sześciokątów**.
- **Miasta:** `unlocked_place` (lat, lng, radiusMeters, source MANUAL/AUTO) → `GeoCircle.ring`. Promień: `RevealRadiusPolicy` (bbox geokodera + 1 km).
- **Auto-miasto:** `TrackingService.updateDwell` → `DwellDetector` (5 min w 1.2 km) → `AutoUnlockCityUseCase` (reverse geocode → settlement → unlock AUTO).
- **Regiony:** `unlocked_region` (id) + `BoundaryGeometrySource` z `assets/tiles/boundaries.pmtiles` (ADM0/ADM1, Natural Earth, `name_pl`).
- **Statystyki:** `ObserveAreaCoverageUseCase` (H3 `cellsForPolygon` vs odkryte parenty).
- **Styl mapy:** `data/map/MapTilerStyleProvider` (klucz z `local.properties` `MAPTILER_API_KEY`, fallback demotiles). Geokoder: `data/network/MapTilerGeocodingApi` + `MapTilerGeocodingRepository` + cache Room `GeocodeCacheEntity`.
- **Ekrany:** loading (`TripLoadingScreen`, proca) → `continent/ContinentMapScreen` (Canvas + `WorldPan` suwak) → `explore/{Continent,Country,Region}Screen` na wspólnym `TripailMapSurface` (761 linii — największy plik, ostrożnie).
- minSdk 26, targetSdk 36. MapLibre Android 13.6.1, H3 4.4.0, JTS 1.20.0.

---

## 2. Etapy

Każdy etap: commit(y) na `v2`, testy jednostkowe zielone (`./gradlew test`), na końcu krótkie podsumowanie dla użytkownika + pytanie o następny krok.

### [x] E1. Prototyp HTML (wygląd mgły) — zatwierdzony 2026-11-13 (parametry w §3)
Cel: ustalić wygląd **zanim** dotkniemy Kotlina.
- Jeden plik `prototype/fog.html` (poza repo albo w scratchpad; spytać), MapLibre GL JS (cdnjs, wersja przypięta), styl OpenFreeMap `https://tiles.openfreemap.org/styles/liberty` (sprawdzić aktualne URL-e stylów przez WebFetch).
- Turf.js do buforów (`buffer`, `union`) — odpowiednik JTS.
- Sztuczny ślad: kliknięcia/ścieżka po Włoszech + 2–3 miasta (kółka) + 1 region.
- Warstwy do porównania (przełączniki):
  1. czysto = bufor śladu 1 km (pieszo) / 1.5–2 km (auto),
  2. lekka mgła = bufor 6–8 km, niższe krycie,
  3. brzeg: 3–4 LineLayery z rosnącym `line-width` i `line-blur` w kolorze mgły (efekt tuszu),
  4. `fill-pattern` z teksturą papieru (wygenerować w canvas → `map.addImage`),
  5. łatanie dziur: usuwać wewnętrzne pierścienie < X km²,
  6. animacja odkrycia miasta: promień 0 → R w ~1.5 s (setData co klatkę) + „ink burst".
  7. tryb nocny: ciemny styl + `circle` z `circle-blur` jako lampiony nad odkrytymi miastami.
- Wynik: lista parametrów (kolory, szerokości, krycia, blur) zapisana w §3 tego pliku.

### [~] E2. Darmowa mapa — kod gotowy na gałęzi `v2`  (2026-11-13), NIE skompilowany (Windows bez Android SDK). Do sprawdzenia na Linuxie: `./gradlew test ktlintCheck detekt assembleDebug` + na telefonie: etykiety PL/EN, tap w miasto, wyszukiwarka, auto-odkrycie miasta.
Decyzja użytkownika: **Photon zostaje mimo braku polskich egzonimów** („Rzym" ≠ Rome; szukamy po nazwach lokalnych/angielskich). PL UI → `lang=default`, EN → `lang=en`. Mgła wstawiana po **ostatniej** nie-symbolowej warstwie (`labelsStartId`) — Liberty ma symbole przemieszane z drogami.
Znane skutki: id miast zmieniły się z MapTiler na `osm:R123` → miasta odkryte wcześniej automatycznie mogą zostać odkryte drugi raz (duplikat kółka, wizualnie bez znaczenia).
- `MapTilerStyleProvider` → `OpenFreeMapStyleProvider` (bez klucza). Zachować interfejs `MapStyleProvider`.
- Etykiety PL/EN: OpenFreeMap używa schematu OpenMapTiles → `name:pl` / `name:en` / `name_int`. Sprawdzić, jak dziś działa podmiana języka (grep `name:pl`) i przepiąć.
- Geokoder: `PhotonGeocodingApi` (`https://photon.komoot.io/api?q=…&lang=pl`, reverse: `/reverse?lat&lon`). Zasady użycia: rozsądne limity, własny User-Agent **bez danych osobowych**. Zmapować `osm_value` (city/town/village…) → `PlaceKind`, `extent` → `GeoBounds`.
- Zachować cache Room geokodera.
- Atrybucja: OpenStreetMap + OpenFreeMap (`MapAttribution.kt`), usunąć logo MapTiler.
- Usunąć `MAPTILER_API_KEY` z buildów i README.
- Testy: mapper Photon (analogicznie do `MapTilerMapperTest`), dekodowanie JSON.

### [ ] E3. Kółka zamiast sześciokątów
- Nowa tabela `trail_point` (Room migracja 6→7): `id`, `lat`, `lng` (kwantyzowane do ~150 m — np. środek komórki H3 res 9 albo siatka), `mode` (WALK/VEHICLE/UNKNOWN), `segmentId` (ciągłość trasy), `discoveredAt`. Dedup po skwantyzowanej pozycji+trybie.
- Migracja danych: dla `unlocked_hex` weź distinct `parentRes9` → środki → `trail_point` (mode UNKNOWN, segmentId null). Starej tabeli **nie kasować** w tej samej migracji (bezpieczeństwo); usunąć w kolejnej wersji.
- `UnlockAreaUseCase`: zapisuje punkt zamiast dysku komórek. `TrailCorridor` zostaje tylko do wykrywania luk (>20 km = nowy segment).
- Rysowanie (`ObserveFogGeoJsonUseCase`): segment → `LineString` → JTS `BufferOp` z `BufferParameters` (round caps/joins, quadrantSegments ~8). Buforowanie w metrach: projekcja lokalna (equirectangular wokół środka segmentu) albo WebMercator z `domain/geo/projection` i korekta `cos(lat)`. Pojedyncze punkty → bufor punktu.
- `RingSmoothing`, rysowanie z H3 outline — usunąć z pipeline'u.
- Statystyki dalej przez H3: z `trail_point` liczyć komórki on-the-fly albo trzymać `parentRes7` w `trail_point`.
- Wydajność: union tysięcy buforów — najpierw `LineString` per segment (mało geometrii), `DouglasPeuckerSimplifier` na wyniku (tolerancja ~20 m). Zmierzyć w teście na syntetycznym śladzie 50 000 punktów.
- Testy: kształt okrągły (pole bufora punktu ≈ πr² ±2%), ciągłość segmentu, migracja.

### [ ] E4. Nowa mgła w apce
- Parametry z E1.
- 3 stany: dwa źródła GeoJSON — `fog-dense` (świat − bufor lekki) i `fog-light` (bufor lekki − bufor czysty); albo jedno źródło z property `level`. Union obu w `RevealUnion`.
- Miasta: `czysto` całe. Regiony: `czysto` (ręczne odkrycie).
- Pieszo czyściej: bufor czysty liczony tylko z segmentów WALK; VEHICLE dostaje węższy czysty + szeroki lekki (wartości z E1).
- Brzeg tuszem: kilka LineLayerów z `line-blur` (już używany w `addBoundaryLayers` — wzorzec).
- Tekstura: `fill-pattern` (bitmapa w `ui/res/drawable`, generowana proceduralnie raz).
- Łatanie dziur: w `FogGeoJsonBuilder` pomijać wewnętrzne pierścienie o polu < próg (prototyp: 8 km²). **Tylko małe dziury** — objechanie dużego obszaru dookoła NIE może go odkryć (użytkownik to wprost zastrzegł). Próg stały w km², nie zależny od wielkości otaczającego kształtu.
- Kolor kontynentu w mgle: delikatny odcień papieru nad danym kontynentem (np. 5–10% domieszki koloru z VISION §4), nigdy na odkrytym terenie.
- „Miękkie z daleka": `HeatmapLayer` z punktów śladu widoczna przy zoom < ~7, czysta geometria przy większym zoomie (crossfade przez `*-opacity` interpolowane po zoomie).
- **Shader AGSL** (płynące chmury, Android 13+) — osobny podetap, tylko jeśli użytkownik zechce po zobaczeniu A+B. Wymaga MapView w `TextureView` mode + Compose overlay z `RuntimeShader` i przekazaniem pozycji ekranowych dziur. Ryzykowny — najpierw spike.

### [ ] E5. Miasta
- `AutoUnlockCityUseCase`: warunek „wewnątrz": dystans od środka miasta (z geokodera) < 0.5 × promień miasta **i** dwell ≥ ~3–5 min (`DwellDetector` — rozważyć mniejszy `DWELL_RADIUS_M` dla miast). Obwodnica (tylko krawędź) → nic.
- Animacja odkrycia: event z service → UI (Flow `RecentlyUnlocked`). Promień rośnie 0 → R od punktu wjazdu (~2 s), potem nazwa miasta na środku (~1.5 s), opcjonalnie dźwięk (krótki, z przełącznikiem w ustawieniach). Fajerwerki „atramentowe" — `SymbolLayer`/Compose overlay; zapytać o styl.
- Ikonki miast: `SymbolLayer` na miastach z warstwy `place` stylu (OpenMapTiles `class=city|town`), dwa warianty ikony: szara (nieodkryte — `within` nie działa na dziurach, więc liczyć w domenie listę odkrytych id i filtrować przez `["in", id, …]`) i kolorowa.
- Wieżyczki: punkty widokowe tylko „znane" — OSM `tourism=viewpoint` **z tagiem `wikidata`/`wikipedia`** (Overpass API lub zbudować paczkę offline w `tools/atlas`). Dotarcie (< 300 m) → większe kółko (np. 5 km). Ikonka wieżyczki.

### [ ] E6. Tryb podróży
- `TrackingIntentRepository` już trzyma intencję — dodać `travelUntil: Long?` (auto-wyłączenie; wybór 1/3/7/14 dni / bez limitu).
- UI: duży przycisk na mapie (nazwa robocza, **zapytać** o nazwę). Notyfikacja foreground tylko gdy aktywny; kanał `IMPORTANCE_LOW`, treść „dziś: N miast, X km".
- „Odkryj, gdzie jestem": jednorazowy `getCurrentLocation` (Fused, high accuracy) → jeden punkt śladu + sprawdzenie miasta (bez dwell — użytkownik świadomie kliknął; zapytać, czy to ma odkrywać całe miasto).
- Podpowiedź wyjazdu: bez śledzenia w tle tego nie wykryjemy ciągle. Opcje: (a) przy otwarciu apki porównać bieżącą lokalizację z „domem" (najczęstszy punkt), > 100 km → pytanie; (b) geofence wokół domu (tani, systemowy). Zacząć od (a).
- Transport: `ActivityRecognitionClient` (Play Services, uprawnienie `ACTIVITY_RECOGNITION`) → `mode` w `trail_point`. Fallback: prędkość (> 25 km/h = VEHICLE). Samolot: luka + > 300 km w < 6 h → zapis łuku (osobna tabela `flight_arc` z dwoma punktami). Prom: ruch po wodzie — na później.

### [ ] E7. Mapa jak winda
- Usunąć `ContinentMenuCanvas`, `WorldPan`, suwak; poziom „Świat" na MapLibre: styl uproszczony (kontynenty jako fill z `continents.geojson` w kolorach z VISION §4, bez etykiet), `minZoom=maxZoom` per piętro, gesty zoomu wyłączone poza ostatnim piętrem.
- Przejścia: `flyTo` z kadrowaniem jak dziś (`ContinentFocusPolicy`, kadrowanie głównego lądu kraju).
- Skoki: sąsiednie kraje klikalne na piętrze Kraj (przelot w bok); lupka z dowolnego piętra; 3 chipy „ostatnie"; pinch-out = piętro wyżej (`OnScaleListener`).
- Globus: sprawdzić, czy MapLibre Android 13.x obsługuje `projection: globe` w stylu (WebFetch changelog). Jeśli tak — przełącznik w ustawieniach + animacja. Jeśli nie — odłożyć.
- `BackResolver` / `TripailTopBar` — zachować semantykę „Wstecz = piętro wyżej".

### [ ] E8. Statystyki + paszport
- Liczniki: miasta (odkryte/znane w kraju — mianownik z warstwy `place` lub paczki offline), regiony (ADM1 z PMTiles), kraje.
- % kraju = odwiedzone „okolice": niewidoczne jednostki = komórki H3 res 5 (~250 km²) lub 6 — decyzja po teście, czy liczby „czują się" sprawiedliwie (cel: ktoś, kto zjeździł Polskę → 70–90%).
- Paszport: ekran z pieczątkami krajów (data pierwszego wjazdu = pierwsza pozycja w ADM0). Pieczątki generowane (kształt + kolor kontynentu + nazwa), nie grafiki z sieci. Animacja przy przekroczeniu granicy.
- Wibracja: krótki `VibrationEffect` przy pierwszym punkcie w nowej „okolicy" (throttle, przełącznik).

### [ ] E9. Import historii
- Ekran przy pierwszym uruchomieniu: „Wczytaj swoją historię": Google / zdjęcia / pomiń.
- Google: eksport Osi czasu z telefonu (Ustawienia → Lokalizacja → Oś czasu → Eksportuj) = JSON. Parser odporny na oba formaty (stary Takeout `Records.json`/semantic i nowy on-device `Timeline.json`) — sprawdzić aktualny format przez WebFetch. Import przez SAF (`OpenDocument`), wszystko lokalnie.
- Zdjęcia: `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION`, `ExifInterface.latLong`. Pokazać postęp, tylko lokalnie.
- Import → `trail_point` (mode UNKNOWN) + auto-miasta wg tych samych reguł (bez dwell — wystarczy punkt wewnątrz).

### [ ] E10. Dodatki
- Tryb nocny: ciemna wersja stylu + lampiony (`circle-blur`) nad odkrytymi miastami; auto wg godziny/zachodu słońca lub systemu.
- „Co jeszcze w okolicy?": Wikipedia GeoSearch (`/w/api.php?action=query&list=geosearch&gscoord=…&gsradius=10000`; dla większych promieni — kilka zapytań lub Wikidata SPARQL) + filtr „nieodkryte". Max 3 na ekranie.
- Panel „co tu zobaczyć": to samo źródło, 3 pozycje + „więcej".
- Notatki do miejsc: tabela `place_note` (placeId, text, updatedAt) + udostępnianie przez systemowy `ACTION_SEND` (bez serwera).
- Moja historia: suwak czasu filtrujący `discoveredAt` + ▶️ odtwarzanie. Wymaga `discoveredAt` w `trail_point`, `unlocked_place`, `unlocked_region` (jest/dodać).
- Tryb spokojny: jeden przełącznik → wyłącza wibracje/dźwięki, chowa liczniki poza jednym, wolniejsze animacje.

### [ ] E11. Społeczność (ostatnie)
- Backend darmowy: Supabase (free tier). Najpierw zapytać, czy w ogóle.
- „Moja ulubiona rzecz w mieście" (1 na osobę na miasto), podgląd mapy znajomego. Żadnych tras innych osób — tylko agregaty.

### [ ] E12. Szlif
- Przegląd wszystkich stringów PL/EN (`ui/res/values*/strings.xml`, `app/res/values*/strings.xml`) — naturalny język, spójne nazwy.
- Nowy ekran ładowania, nowe logo — **pytać** o kierunek, pokazywać warianty w HTML.

### [ ] E13. Na później (nie robić bez prośby; każdy punkt = osobna decyzja użytkownika)

Status w POMYSLY: *(na później)* albo bez ✅ (niezatwierdzone). „Kompas ciekawości" z §5 POMYSLY = scalony z „Co jeszcze w okolicy?" (E10).

**Wygląd / klimat**
- **Słoje drzewa** (stare odkrycia wyblakłe): property `ageDays` w GeoJSON czystej warstwy, rozbić na 3–4 pasma wieku (osobne features po `discoveredAt`), `fill-color` interpolowany do sepii. Uwaga: koliduje z jednolitym unionem — union per pasmo, młodsze nad starszymi.
- **Pora roku w śladzie** (szron zimą): to samo pasmowanie co słoje, ale po miesiącu `discoveredAt`; zimowe pasma z `fill-pattern` szronu o niskim kryciu. Robić razem ze słojami albo wcale.
- **Nocne odkrycia** (granatowy odcień): `discoveredAt` vs godzina zachodu słońca (algorytm NOAA, offline) → flaga `night` w `trail_point`; delikatny `fill-color` overlay.
- **Pogoda odkrycia** (ikonka deszcz/słońce): przy zapisie miasta zapytanie do Open-Meteo (`api.open-meteo.com/v1/forecast?current=weather_code`, darmowe, bez klucza) → kolumna `weatherCode` w `unlocked_place`. Tylko gdy jest internet; brak = brak ikonki.
- **Mgła czuje pogodę** (deszcz na mapie, gdy pada naprawdę): Open-Meteo dla bieżącej pozycji co ~30 min, gdy apka na ekranie → overlay Compose z cząsteczkami deszczu/śniegu (Canvas, 60–100 kropli, tani). Wyłączane w trybie spokojnym.
- **Przelot 3D nad miastem**: po animacji odkrycia `easeTo(pitch=60, bearing+30, zoom=15)` → 3 s → powrót do poprzedniej kamery. Budynki 3D: `FillExtrusionLayer` z warstwy `building` OpenMapTiles (OpenFreeMap ją ma). Konflikt z VISION „region/miasto nie ruszają kamery" — to świadomy wyjątek, pytać.

**Ślady**
- **Prom**: segment, którego punkty leżą na wodzie (test: brak lądu z `continents.geojson`/PMTiles ADM0 pod punktem) → `mode=FERRY`, rysowany `LineLayer` z `line-dasharray` falowanym (lub symbol fali co N px).
- **Drogi jako trasy**: map-matching jest drogi obliczeniowo. Tanio: przy zoomie lokalnym `queryRenderedFeatures` warstwy `transportation` (OpenMapTiles) w buforze śladu → podświetlić odcinki `class=motorway|trunk|primary` przecinające ślad. Tylko wizualnie, bez zapisu. Spike przed decyzją.

**Podróże**
- **Kraj offline**: MapLibre `OfflineManager.createOfflineRegion` z `OfflineTilePyramidRegionDefinition` (bbox kraju z PMTiles, zoom 0–12). Uwaga: sprawdzić zasady OpenFreeMap dot. masowego pobierania (WebFetch); jeśli zabronione — pominąć albo ograniczyć zoom. Pokazać rozmiar przed pobraniem.
- **Dom**: najczęstsza „okolica" z `trail_point` (lub ręczne ustawienie) → `SymbolLayer` z ikonką domu + statystyka „najdalej od domu" (max dystans po wszystkich punktach, liczony przyrostowo). Dom przydaje się też w E6 (podpowiedź wyjazdu) — można zrobić razem z E6.
- **Dziennik miesiąca**: lokalnie, bez AI: szablon „N nowych miast, najdalej: X, najdłuższy dzień: Y km" z danych `discoveredAt`. Pokazywany raz w miesiącu jako karta na mapie (nie powiadomienie, w trybie spokojnym — wyłączony).
- **Moje zdjęcia w miejscu** (2–3 na miasto, tylko dla siebie): Photo Picker (`PickMultipleVisualMedia`, bez uprawnień), kopia miniatury do `filesDir`, tabela `place_photo(placeId, path, addedAt)`. Pokazywane w panelu miasta.

**Nagroda = wiedza**
- **Ciekawostka po odkryciu**: Wikipedia REST `/{lang}.wikipedia.org/api/rest_v1/page/summary/{title}` (tytuł z Wikidata ID miasta z geokodera / OSM `wikidata`) → pierwsze zdanie `extract`, pokazane pod nazwą miasta po animacji. Cache w Room. Brak internetu = pomijamy.

**Do pokazania**
- **Pocztówka z odkrycia**: `MapView.snapshot` (MapLibre `MapSnapshotter` z bbox miasta/regionu) + Compose-owa ramka (nazwa, data, kolor kontynentu) → bitmapa → `ACTION_SEND`. Bez serwera.
- **Plakat** (tylko jeśli mapa ładna): `MapSnapshotter` w dużej rozdzielczości (np. 4961×3508 = A3 300 dpi, kafelkowo jeśli limit tekstur) → PDF przez `PdfDocument` → zapis przez SAF.

**Społeczność (po E11, wymaga backendu)**
- **Mapa znajomego**: Supabase — tabela z uproszczoną geometrią czystej warstwy (publikowana świadomie przez użytkownika, nie automatycznie) + kod zaproszenia. Czat: odradzić budowę własnego — link do istniejącego komunikatora.
- **List w butelce**: wiadomość z (lat, lng, promień 200 m); serwer zwraca tylko listy w pobliżu bieżącej pozycji i tylko gdy klient jest w promieniu (sprawdzenie serwerowe nie da się w pełni wymusić — świadomie miękkie). Moderacja = problem; dlatego „może".
- **Mapa skarbów od znajomego**: deeplink `tripail://treasure?lat=…&lng=…&note=…` wysyłany przez `ACTION_SEND`; odbiorca widzi kropkę na mgle, odkrywa się po dotarciu (< 300 m). **Działa bez serwera** — można zrobić wcześniej niż reszta społeczności.
- **Ślady innych** (poświata popularności): agregaty po H3 res 7 liczone serwerowo z k-anonimowością (pokazywać tylko komórki z ≥ 10 osobami). Najbardziej wrażliwe prywatnościowo — robić ostatnie albo wcale.

**Platformy**
- **Widget**: Glance (Jetpack) — miniatura mapy (snapshot z cache) + przycisk trybu podróży. Po E6.
- **iOS**: Kotlin Multiplatform — `:domain` jest czystym Kotlinem (+JTS — **JTS to Java, nie działa na iOS**; trzeba zamiennika, np. port na `kotlin-multiplatform` geometry lub natywny GEOS/własny bufor). H3 ma bindingi C → cinterop. UI: Compose Multiplatform + MapLibre iOS przez interop albo natywny SwiftUI. Śledzenie w tle: `CLLocationManager` significant changes. Build wymaga Maca. Duży projekt — osobna decyzja.

---

## 3. Parametry ustalone w prototypie
Prototyp: `prototype/fog.html` (lokalnie, wykluczony z gita; serwer: `.claude/launch.json` → `prototype`, port 8765). Stan na 2026-11-13, **zatwierdzony przez użytkownika** (wygląd do przeniesienia w E4):
- mgła dzień `#CBBE9F`, krycie 0.95; noc `#1C2038`, odkryte przyciemnione tylko o 25%, brzeg złoty `#FFD38A`.
- **tekstura:** `fill-pattern` w MapLibre ma stały rozmiar w pikselach ekranu → duże plamy/włókna „pływają" przy zoomie i nie wyglądają jak papier (użytkownik odrzucił). Zostaje tylko drobne ziarno 1 px. Prawdziwy papier = dopiero shader (E4 poziom C), z teksturą zakotwiczoną w świecie i skalowaną po zoomie.
- promienie: pieszo 1 km, autem 0.5 km (czysto), okolica 6 km. **Lekka mgła: krycie 0.2** — różnica czysto/lekka ma być mała (0.35 i 0.6 — za duża).
- brzeg: linia tuszu `#4E3B26` (0.6) + rozmyta smuga **tylko na zewnętrznej granicy** (gęsta mgła ↔ reszta). Granica czysto ↔ lekka mgła **bez żadnej linii**, sam miękki gradient o kryciu lekkiej mgły — użytkownikowi najbardziej przeszkadzała widoczna krawędź w tym miejscu. Postrzępienie ±120 m (czyste) / ±600 m (lekka). Postrzępiony brzeg — użytkownik: ✅.
- łatanie dziur < 8 km².
- odkrycie miasta: promień 0 → R w 1.8 s (ease-out cubic) + wybuch kropli w kolorach kontynentów + nazwa (Georgia, ~44 px).
- nie zrobione w prototypie (dopracować od razu w apce, E4/E10): rozmycie z daleka, mocniejsze lampiony.

## 4. Otwarte pytania do użytkownika
- Nazwa przycisku trybu podróży.
- Czy „odkryj, gdzie jestem" odkrywa całe miasto, czy tylko kółko.
- Styl animacji odkrycia (fajerwerki tuszem vs inne), dźwięk tak/nie.
- Gdzie trzymać prototyp HTML (repo `prototype/` vs poza repo).
