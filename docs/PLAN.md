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
- Po każdym skończonym etapie: commit + push na gałąź `v2` (nigdy na `main`). Nazwy commitów krótkie i „ludzkie" (jedna linia, np. „Switch to OpenFreeMap and Photon"), bez list i esejów. **Zero wzmianek o Claude/AI** — bez `Co-Authored-By`, bez „Generated with Claude Code" (także w PR-ach).

### 0.2a Środowisko — stan faktyczny od 2026-11-14 (zastępuje 0.2)
- Windows + Android Studio. SDK: `%LOCALAPPDATA%\Android\Sdk`, AVD: `Medium_Phone_API_37.0` (1080×2400, Play Store). `java` nie ma w PATH: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- Build: `& .\gradlew.bat assembleDebug test --console=plain -q *> build\pN-build.log` — **zawsze w tle** (`runInBackground`) + `wait`; wywołanie na pierwszym planie kończy się timeoutem narzędzia (~2 min). Potem `Select-String` po logu (`^e: |FAILED|What went wrong`).
- adb: `$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`. Pakiet: `com.tripex.pose`, start: `am start -n com.tripex.pose/.MainActivity`. Uprawnienia bez klikania: `pm grant com.tripex.pose android.permission.ACCESS_FINE_LOCATION` (+ `POST_NOTIFICATIONS`).
- GPS: `adb emu geo fix <lng> <lat>` (**najpierw długość!**). Fused daje fix co 10–15 s, filtr odrzuca ruch < 20 m → wysyłaj 1 fix co ~5–10 s; prędkość liczona z realnego czasu (≥ 25 km/h = pojazd), więc „auto” = skoki ~300–500 m co 10 s, „pieszo” = ~60 m co 10 s. Trasy testowe krótkie (15–25 fixów).
- Zrzuty: `build\dev\shot.ps1 <nazwa> <sekundy> [skala]` (poza gitem; po `clean` trzeba odtworzyć: screencap → pull → zmniejszenie 50%). Ekran 1080×2400, zrzut 540×1200 → współrzędne tapów ×2.
- **Kasowanie plików z powłoki (`Remove-Item`, `del`, `git rm`) wisi** (IDE czeka na zgodę). Nie próbuj: nadpisz plik sensowną treścią albo zostaw i wypisz użytkownikowi listę „do ręcznego usunięcia”.
- Commit: tylko jawne ścieżki (`git add <pliki>`), nigdy `git add .` — `.claude/settings.local.json` i `PROMPT_FABLE.md` nie idą do repo.
- Praca paczkami (decyzja użytkownika 2026-11-16): (1) E2+E3+E4, (2) E5+E6+E7, (3) E8+E9+E10, (4) E12. Cały kod paczki → **jeden** build+testy → poprawki → emulator → zrzuty tylko kluczowych ekranów → commit+push. Problem nie do rozwiązania w 2–3 próbach → wpis „do poprawy” w etapie i dalej.

### 0.4 Zmiany planu zaakceptowane przez użytkownika 2026-11-14 (wiążące, ważniejsze niż stare opisy etapów)
1. **Start apki** tam, gdzie jesteś / byłeś ostatnio (piętro Kraj), nie na piętrze Świat.
2. **Zoom nigdy nie jest „martwy”**: zsunięcie palców = piętro wyżej, rozsunięcie = piętro niżej.
3. **Ekran mapy** = mapa + jedna liczba + lupka + jeden przycisk „Odkrywaj” (arkusz: „Tylko tu” / „Cała podróż”). „Ostatnie” schowane pod lupką.
4. **„Uzupełnij ze zdjęć”** jako stała funkcja (siatka bezpieczeństwa na zapomniany tryb podróży), nie tylko przy pierwszym uruchomieniu.
5. **Auto-wyłączenie podróży**: domyślnie 7 dni, zmiana w ustawieniach; bez pytania przy włączaniu.
6. **Miasta bez mianownika** („12 miast”); mianownik tylko gdy osiągalny (regiony 5/16).
7. **Spokój jest domyślny** — nie ma przełącznika „tryb spokojny”; są dwa zwykłe: dźwięk, wibracja.
8. **Kolejka nagród**: jedna celebracja naraz (kraj > miasto); wibracja tylko przy mieście i kraju.
9. **Transport z prędkości GPS**, bez `ACTIVITY_RECOGNITION`.
10. **Import**: zdjęcia = opcja główna, Google = druga, z instrukcją.
11. **Do E13**: społeczność (E11), globus, wieżyczki, shader AGSL.
12. **E10**: „Co w okolicy” + „co tu zobaczyć” = jeden panel (max 3); „Moja historia” = sam ▶, bez suwaka.
- Do dodania przy okazji: pierwsza minuta (zdanie o prywatności + natychmiastowa nagroda, zgody dopiero przy użyciu), kopia zapasowa Androida, „Cofnij” dla błędnie odkrytego miasta, spokojny komunikat offline, budżet wierzchołków dla postrzępionego brzegu (zrobione w `FogComposer`).

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

Tryb pracy (decyzja użytkownika 2026-11-14): **cały plan E2–E12 za jednym zamachem**, bez zatrzymywania po etapach. Każdy etap: build + testy + sprawdzenie na emulatorze (zrzuty), commit + push na `v2`. Pytania z planu (nazwy, kolory, styl) — nie czekać, wybrać rozsądnie wg §0 i zapisać decyzję w §4. Na końcu: zrzuty + lista zrobione / decyzje / co nie działa; potem poprawki od użytkownika.

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

### [x] E2. Darmowa mapa — **zweryfikowane 2026-11-14**: build + testy OK, na emulatorze mapa OpenFreeMap się ładuje, etykiety PL („Wiedeń”, „Budapeszt”), tap w kraj działa. **Niesprawdzone:** wyszukiwarka Photon i tap w miasto — sprawdzić przy teście paczki 1 (wpisać „Krakow”, wybrać, „Odkryj”). Dodany `gradlew.bat` (commit 3da7d86). Stary nagłówek: E2. Darmowa mapa — kod gotowy na gałęzi `v2`  (2026-11-13), NIE skompilowany (Windows bez Android SDK). Do sprawdzenia na Linuxie: `./gradlew test ktlintCheck detekt assembleDebug` + na telefonie: etykiety PL/EN, tap w miasto, wyszukiwarka, auto-odkrycie miasta.
Decyzja użytkownika: **Photon zostaje mimo braku polskich egzonimów** („Rzym" ≠ Rome; szukamy po nazwach lokalnych/angielskich). PL UI → `lang=default`, EN → `lang=en`. Mgła wstawiana po **ostatniej** nie-symbolowej warstwie (`labelsStartId`) — Liberty ma symbole przemieszane z drogami.
Znane skutki: id miast zmieniły się z MapTiler na `osm:R123` → miasta odkryte wcześniej automatycznie mogą zostać odkryte drugi raz (duplikat kółka, wizualnie bez znaczenia).
- `MapTilerStyleProvider` → `OpenFreeMapStyleProvider` (bez klucza). Zachować interfejs `MapStyleProvider`.
- Etykiety PL/EN: OpenFreeMap używa schematu OpenMapTiles → `name:pl` / `name:en` / `name_int`. Sprawdzić, jak dziś działa podmiana języka (grep `name:pl`) i przepiąć.
- Geokoder: `PhotonGeocodingApi` (`https://photon.komoot.io/api?q=…&lang=pl`, reverse: `/reverse?lat&lon`). Zasady użycia: rozsądne limity, własny User-Agent **bez danych osobowych**. Zmapować `osm_value` (city/town/village…) → `PlaceKind`, `extent` → `GeoBounds`.
- Zachować cache Room geokodera.
- Atrybucja: OpenStreetMap + OpenFreeMap (`MapAttribution.kt`), usunąć logo MapTiler.
- Usunąć `MAPTILER_API_KEY` z buildów i README.
- Testy: mapper Photon (analogicznie do `MapTilerMapperTest`), dekodowanie JSON.

### [x] E3. Kółka zamiast sześciokątów — **zrobione 2026-11-16** (paczka 1)
**Wynik:** build + wszystkie testy OK. Kod skompilował się bez poprawek. Brakujące testy napisane od nowa (pliki z opisu niżej nie istniały na dysku): `TravelModeClassifierTest` (6), `UnlockAreaUseCaseTest` (10, atrapy w `FakeTrail.kt`), `ObserveFogGeoJsonUseCaseTest` (2), `UnlockedAreaRepositoryImplTest` (3), `Migration6To7Test` (2, Robolectric, bez `room-testing`). Test wydajności `FogComposer`: **50 000 punktów = 2,5 s** na JVM. Migracja 6→7 przeszła na emulatorze na prawdziwej bazie v6. Ślad pieszy (Warszawa) zapisany i narysowany jako gładki, okrągły korytarz; auto-odkrycie miasta po 5 min działa.
**Do poprawy (E3):**
- Trasa autem i test „ubij proces → kolejny fix” **niesprawdzone na emulatorze**: po zimnym starcie emulatora `adb emu geo fix` nie dociera do apki, bo apka prosi usługi Google o `BALANCED_POWER_ACCURACY` i dostaje pozycję z sieci, a nie z GPS. Wyłączenie lokalizacji sieciowej nie pomogło. Logikę pokrywają testy jednostkowe (zmiana trybu = nowy odcinek od starego punktu, restart = kontynuacja od zapamiętanego fixu). Do sprawdzenia na telefonie albo po wipe emulatora.
- Martwy kod po E3 — **nie skasowany** (powłoka nie kasuje plików), lista w §4 „Do ręcznego usunięcia”. Metody `H3Converter.outline/revealAround/revealDisk/bridge` zostają, bo używają ich `H3UtilsTest` i `UnlockedHexDaoTest`; usunąć razem z tabelą `unlocked_hex` w kolejnej wersji bazy.

Stary nagłówek: E3. Kółka zamiast sześciokątów — kod NAPISANY 2026-11-16, niezacommitowany, ani razu nie skompilowany
- Nowa tabela `trail_point` (Room migracja 6→7): `id`, `lat`, `lng` (kwantyzowane do ~150 m — np. środek komórki H3 res 9 albo siatka), `mode` (WALK/VEHICLE/UNKNOWN), `segmentId` (ciągłość trasy), `discoveredAt`. Dedup po skwantyzowanej pozycji+trybie.
- Migracja danych: dla `unlocked_hex` weź distinct `parentRes9` → środki → `trail_point` (mode UNKNOWN, segmentId null). Starej tabeli **nie kasować** w tej samej migracji (bezpieczeństwo); usunąć w kolejnej wersji.
- `UnlockAreaUseCase`: zapisuje punkt zamiast dysku komórek. `TrailCorridor` zostaje tylko do wykrywania luk (>20 km = nowy segment).
- Rysowanie (`ObserveFogGeoJsonUseCase`): segment → `LineString` → JTS `BufferOp` z `BufferParameters` (round caps/joins, quadrantSegments ~8). Buforowanie w metrach: projekcja lokalna (equirectangular wokół środka segmentu) albo WebMercator z `domain/geo/projection` i korekta `cos(lat)`. Pojedyncze punkty → bufor punktu.
- `RingSmoothing`, rysowanie z H3 outline — usunąć z pipeline'u.
- Statystyki dalej przez H3: z `trail_point` liczyć komórki on-the-fly albo trzymać `parentRes7` w `trail_point`.
- Wydajność: union tysięcy buforów — najpierw `LineString` per segment (mało geometrii), `DouglasPeuckerSimplifier` na wyniku (tolerancja ~20 m). Zmierzyć w teście na syntetycznym śladzie 50 000 punktów.
- Testy: kształt okrągły (pole bufora punktu ≈ πr² ±2%), ciągłość segmentu, migracja.

#### E3 — stan i co dokończyć
**Model: Opus** — trzeba debugować nieskompilowaną geometrię JTS i migrację bazy, gdzie błąd = utrata danych użytkownika.
**Co jest (wszystko niezacommitowane):**
- `domain/geo/TrailPoint.kt` — `TravelMode {WALK, VEHICLE, UNKNOWN, LEGACY}`, `TrailPoint`, `SEGMENT_LEGACY=0`, `SEGMENT_SINGLE=-1` (punkty z segmentem ≤ 0 nigdy nie są łączone w linię).
- `domain/repository/UnlockedAreaRepository.kt` — `addPoint/addPoints/isKnown/observePoints` zamiast `unlock(hexes)`; `observeTrail/observeFar/observeCount` zostają (statystyki H3 bez zmian).
- `domain/usecase/UnlockAreaUseCase.kt` — teraz `@Singleton` ze stanem (segment, ostatnia komórka). Znany teren nie jest zapisywany drugi raz, ale punkt wejścia/wyjścia na znany teren jest (żeby korytarze się stykały). Zmiana trybu = nowy segment. `single()` i `import()` dla E6/E9.
- `domain/location/TravelModeClassifier.kt` — prędkość ≥ 25 km/h → VEHICLE od razu; powrót do WALK po 3 wolnych fixach (≤ 10 km/h).
- `domain/geo/FogComposer.kt` — cała geometria mgły (patrz E4).
- `domain/usecase/ObserveFogGeoJsonUseCase.kt` — zwraca `Flow<FogLayers>`; `snapshot()` przygotowane pod „Moją historię” (E10).
- `data/local/TrailPointEntity.kt` (encja + `TrailPointDao`), `TripexPoseDatabase` v7, `Migrations.migration6To7(cellCenter)` + `Migrations.all(...)`, `DatabaseModule` (wstrzykuje `H3Converter`), `UnlockedAreaRepositoryImpl` na nowym DAO.
- `app/service/TrackingService.kt` — `modeClassifier` + `unlockArea(location, previous, mode, nowMs)`.
- Testy: nowe `FogComposerTest`, `UnlockAreaUseCaseTest`, `UnlockedAreaRepositoryImplTest`; atrapy poprawione w `ObserveAreaCoverageUseCaseTest`, `ObserveUnlockedCountUseCaseTest`.

**Do zrobienia, w tej kolejności:**
1. Build + testy (w tle!). Spodziewane błędy kompilacji do sprawdzenia w pierwszej kolejności: sygnatura `OverlayNGRobust.union(Collection)` (JTS 1.20; awaryjnie `UnaryUnionOp.union(list)`), `Area.ofRing`, `factory.createMultiPointFromCoords`, dostęp zagnieżdżonej klasy `Roughener` do prywatnych stałych companion (jeśli błąd → stałe `internal`/przenieść do `Roughener`), `Expression.stop(Float, Float)` i `withFilter` w `TripailMapSurface`, pozostałe użycia `Migrations.ALL` i `fogGeoJson` (grep).
2. `ObserveFogGeoJsonUseCase.build(input, until)` — usunąć nieużywany parametr `until`.
3. `FogComposerTest`: jeśli pada `ringCount`/`firstRing` (regexy na JSON-ie) — popraw **test**, nie osłabiaj kodu. Test wydajności (50 000 punktów < 30 s na JVM): zapisz realny wynik tutaj. Jeśli > 10 s → najpierw zmniejsz `quad` i zwiększ `simplifyM` dla `light`/`far`.
4. Plik `ObserveFogGeoJsonUseCaseTest.kt` zawiera testy `TravelModeClassifier` (starego nie dało się skasować). Utwórz `domain/src/test/.../location/TravelModeClassifierTest.kt` z tą treścią, a w starym pliku napisz prawdziwy test use case’u (atrapy repozytoriów + `BoundaryGeometrySource` zwracający błąd): punkt + miasto → `clear` niepuste; pusta baza → `emptyWorld()`.
5. Test migracji 6→7 (Robolectric, wzór: istniejące testy DAO w `data/src/test`): baza v6 z 3 wierszami `unlocked_hex` o 2 różnych `parentRes9` → po migracji 2 wiersze `trail_point`, `mode='LEGACY'`, `segmentId=0`, `unlocked_hex` nietknięte.
6. `data/schemas/.../7.json` wygeneruje się przy buildzie — dodać do commita.
7. Martwy kod po E3 (usunąć tylko jeśli grep nie znajdzie innych użyć; kasowanie plików — patrz 0.2a): `RingSmoothing` (+test), `FogGeoJsonBuilder` (+test), `RevealUnion` (+test), `H3Converter.outline/revealAround/revealDisk/bridge`, `UnlockedHexMapper` (+test), `UnlockedHexDao.insertAll`. Tabela `unlocked_hex` i encja **zostają** (usunięcie w kolejnej wersji bazy).
8. `docs/TRIPAIL_VISION.md` §2: „Nie zapisujemy surowego śladu” → „zapisujemy punkty zaokrąglone do ~150 m”.

**Pułapki:**
- `UnlockAreaUseCase` trzyma stan w pamięci; po ubiciu procesu pierwszy fix startuje nowy segment od `bridgeFrom` (pozycja z `TrackingSession`) — nie usuwać tego argumentu.
- `observePoints()` emituje całą tabelę przy każdym fixie. Do ~50 tys. wierszy OK (kompozytor cache’uje gotowe segmenty po `segmentId+mode`); nie optymalizować teraz.
- Punkty LEGACY (stare heksy) rysowane promieniem 230 m jako chmura — celowo, bo stare dane już pokrywają cały pas 1 km; promień 1 km pogrubiłby stare ślady dwukrotnie.

**Jak sprawdzić (emulator):** tryb pieszy: 15 fixów co 10 s po ~60 m w Warszawie (start 52.2297, 21.0122, na wschód) → jeden gładki korytarz szerokości ~2 km z okrągłymi końcami, zero sześciokątów. Tryb auto: 15 fixów co 10 s po ~400 m → węższy pas (~1 km) + szeroka lekka mgła. Ubij proces (`am force-stop`), uruchom, wyślij kolejny fix 300 m dalej → korytarz ciągły, bez przerwy.

### [x] E4. Nowa mgła w apce — **zrobione 2026-11-16** (paczka 1)
**Wynik:** mgła 3-stanowa działa na emulatorze w dzień i w nocy; tapnięcia w kraj/region/miasto działają przy `AREA_FILL_OPACITY 0.04`; zaznaczony region czytelny (gruba biała granica); przejście czysto ↔ lekka mgła bez linii; brzeg postrzępiony, nie „zębaty”; wyszukiwarka Photon + „Odkryj” (Florencja) działa — to zamyka niesprawdzony punkt z E2. Zrzuty: `build/shots/{a-brzeg-z-bliska,c-kraj-z-daleka,d-noc,e-wyszukiwarka-miasto}.png` (poza gitem).
**Decyzje:**
- **Ziarno papieru usunięte.** `fill-pattern` na warstwach mgły powodował, że MapLibre Android **w ogóle nie rysował wypełnienia** (cała mapa odkryta). Mgła jest teraz jednolitym kolorem; prawdziwa tekstura = shader (E13). Przenikanie far/near po zoomie (5.6–7) zostało, działa.
**Do poprawy (E4):**
- Zrzut (b) „pas z auta” nie zrobiony (patrz GPS w E3). Ślad pieszy z bliska też nie: leży w środku automatycznie odkrytej Warszawy; zamiast tego zrzut (a) pokazuje brzeg Florencji przy zoomie ~11 (wszystkie 3 stany).
- Noc: pas lekkiej mgły (granat 0.2) i miękka smuga przy granicy „czysto” są w nocy wyraźniejsze niż w dzień; złoty tusz na zewnętrznej granicy prawie niewidoczny. Rozważyć `lightOpacity` nocą ~0.12 i grubszy/jaśniejszy tusz.
- Noc: podpis „Dotknij kraju, by zobaczyć jego postęp” — granatowy tekst na granatowej mgle, nieczytelny (kolor tekstu nie zależy od trybu nocnego).
- Bez porównania „obok siebie” z `prototype/fog.html` (brak przeglądarki w tej sesji); porównane z parametrami z §3 — zgodne poza ziarnem.
- Poza zakresem E3/E4, zauważone przy okazji: (1) w wynikach wyszukiwarki pojawia się słowo **„Gmina”** (zakazane w UI wg §0.1) — mapowanie typu miejsca z Photona; (2) puste białe prostokąty tarcz drogowych (polityka etykiet podmienia `text-field` także w warstwach z numerami dróg); (3) apka prosi o lokalizację `BALANCED_POWER_ACCURACY` — w trybie podróży (E6) potrzebny `HIGH_ACCURACY`.

Stary nagłówek: E4. Nowa mgła w apce — kod NAPISANY 2026-11-16 razem z E3, nieskompilowany
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

#### E4 — stan i co dokończyć
**Model: Opus** — ocena wyglądu na zrzutach względem prototypu i strojenie warstw MapLibre wymaga osądu, nie tylko wykonania listy.
**Co jest:**
- `FogComposer.compose(points, circles, regions)` → `FogLayers(dense, light, clear)` (trzy stringi GeoJSON). `dense` ma cechy z `lod: near|far`. Promienie: pieszo 1000 m, auto 500 m, lekka mgła 6 km, „far” 25 km; miasto: +3 km lekkiej; łatanie dziur < 8 km² (stały próg). Buforowanie w metrach (lokalna projekcja na odcinek ≤ 0,6° szer. geogr.).
- Postrzępiony brzeg = stałe pole szumu zależne tylko od pozycji (±120 m na granicy „czysto”, dodatkowo ±600 m na zewnętrznej) — dwie warstwy dzielące granicę rwą się identycznie. Budżet: > 40 tys. wierzchołków → rzadsze zagęszczanie, > 120 tys. → bez zagęszczania.
- `ui/theme/FogStyle.kt` — `RevealStyle` z kolorami dnia/nocy z §3.
- `ui/map/host/TripailMapSurface.kt` — `addFogLayers` (8 warstw + `night-shade`), `applyFogPalette` (dzień/noc + 10% koloru kontynentu, ziarno 128×128 generowane w `grainBitmap`), parametr `night` (na razie = systemowy tryb ciemny, w `TripailApp`). `MapHostState.fog`, `MapHostViewModel.fogGeoJson: StateFlow<FogLayers>`.
- Osłabione farbowanie terenów: `AREA_FILL_OPACITY 0.04`, `SELECTED_FILL_OPACITY 0.14`, `DIMMED_FILL_OPACITY 0.3`, `REST_OF_COUNTRY_OPACITY 0.12`, `LIFT_SHADOW_OPACITY 0.12`; usunięte barwienie tła/wody.

**Decyzje podjęte zamiast pytań:**
- „Miękkie z daleka” = drugi, szeroki kształt (`lod=far`, 25 km) przenikający z prawdziwym między zoomem 5.6 a 7 — **zamiast** `HeatmapLayer` (heatmapa umie tylko dokładać kolor, nie zdejmować mgły).
- Odcień kontynentu = domieszka 10% do koloru papieru na piętrach danego kontynentu (zero geometrii), nie osobny kształt nad lądem.
- Shader AGSL → E13.

**Do zrobienia / sprawdzić na emulatorze:**
1. **Tapnięcia** w kraj/region/miasto po obniżeniu `AREA_FILL_OPACITY` do 0.04 (stary komentarz twierdzi, że 0.02 psuło `queryRenderedFeatures`). Jeśli nie działają: `fillOpacity = 1` + kolor z alfą ~0.03 (`Color.argb(8, r, g, b)`), a nie powrót do 0.22.
2. Ziarno: ma być drobne (1 px urządzenia). Jeśli widać duże/rozmyte plamy — bitmapa dostała złą gęstość: ustaw `bitmap.density = resources.displayMetrics.densityDpi` przed `addImage`.
3. Czy `fill-pattern` + krycie interpolowane po zoomie działa razem (warstwy `fog-dense-fill`, `fog-far-fill`). Jeśli wzór gasi krycie → zostaw stałe krycie i steruj `minZoom/maxZoom` warstw (twarde przełączenie przy 6.3).
4. Przenikanie far/near przy zoomie 5.6–7: nie może „mrugać” ani odsłaniać całej mapy w połowie przejścia.
5. Porównanie z prototypem: ten sam kadr (Toskania, zoom ~8.2; miasto Florencja 43.7696, 11.2558 r=5.5 km przez wyszukiwarkę + ślad) obok `prototype/fog.html`. Kryteria: odkryte jaśniejsze/żywsze niż mgła; **brak widocznej linii** między „czysto” a lekką mgłą; tusz tylko na zewnętrznej granicy; brzeg nierówny, ale nie „zębaty”.
6. Noc: `adb shell cmd uimode night yes` → mgła granatowa `#1C2038`, brzeg złoty, odkryte tylko lekko przyciemnione.
7. Linie mgły rysują też ramkę świata (±180°/±85°) — jeśli widać ją na piętrze Świat, ukryć warstwy mgły na tym piętrze (i tak robi to E7).
8. Sprzątanie: nieużywane `FOG_LAYERS` (przyda się w E7 do chowania mgły) i `BACKGROUND_LAYER`; komentarz KDoc nad `TripailMapSurface` („parchment wash”) zaktualizować.
9. Wygląd zaznaczenia kraju/regionu po osłabieniu wypełnień: zaznaczony kraj ma być czytelny głównie przez **grubą białą granicę** + lekkie przyciemnienie reszty; jeśli nieczytelne → `DIMMED_FILL_OPACITY` do 0.4, nie podnosić `SELECTED_FILL_OPACITY` (zamalowuje odkryty teren).
10. `docs/TRIPAIL_VISION.md` §3: mgła 3-stanowa, kolor kontynentu jako podpis.

**Zrzuty paczki 1:** (a) korytarz pieszy z bliska (zoom ~11), (b) pas z auta + lekka mgła (zoom ~9), (c) kraj z daleka (zoom ~5) — „płachty”, (d) noc, (e) wyszukiwarka + odkryte miasto.

### [ ] E5. Miasta
- `AutoUnlockCityUseCase`: warunek „wewnątrz": dystans od środka miasta (z geokodera) < 0.5 × promień miasta **i** dwell ≥ ~3–5 min (`DwellDetector` — rozważyć mniejszy `DWELL_RADIUS_M` dla miast). Obwodnica (tylko krawędź) → nic.
- Animacja odkrycia: event z service → UI (Flow `RecentlyUnlocked`). Promień rośnie 0 → R od punktu wjazdu (~2 s), potem nazwa miasta na środku (~1.5 s), opcjonalnie dźwięk (krótki, z przełącznikiem w ustawieniach). Fajerwerki „atramentowe" — `SymbolLayer`/Compose overlay; zapytać o styl.
- Ikonki miast: `SymbolLayer` na miastach z warstwy `place` stylu (OpenMapTiles `class=city|town`), dwa warianty ikony: szara (nieodkryte — `within` nie działa na dziurach, więc liczyć w domenie listę odkrytych id i filtrować przez `["in", id, …]`) i kolorowa.
- Wieżyczki: punkty widokowe tylko „znane" — OSM `tourism=viewpoint` **z tagiem `wikidata`/`wikipedia`** (Overpass API lub zbudować paczkę offline w `tools/atlas`). Dotarcie (< 300 m) → większe kółko (np. 5 km). Ikonka wieżyczki.

#### E5 — instrukcja wykonawcza
**Model: Opus** — wyścig zdarzenie↔zapis do bazy, animacja na warstwach mapy i reguła „w środku” łatwo się psują po cichu.
**Pliki:**
- zmienić: `domain/usecase/AutoUnlockCityUseCase.kt`, `domain/location/DwellDetector.kt`, `app/service/TrackingService.kt` (`updateDwell`), `ui/map/host/TripailMapSurface.kt`, `ui/TripailApp.kt`, `ui/map/components/PlaceDetailSheet.kt`, `domain/usecase/ToggleRevealUseCase.kt`.
- utworzyć: `domain/discovery/DiscoveryEvents.kt` (`@Singleton`, `MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 8)`; `sealed interface DiscoveryEvent { CityDiscovered(name, lat, lng, radiusMeters); CountryEntered(iso2, name) }`), `ui/celebration/CelebrationQueue.kt`, `ui/celebration/DiscoveryCelebration.kt`, `ui/celebration/DiscoverySound.kt`.

**Decyzje techniczne:**
- **Warunek „w środku”:** `place = reverseGeocode(fix)`; `R = RevealRadiusPolicy` (ten sam promień, którym miasto zostanie odkryte); odkrywamy tylko gdy `distance(fix, place.center) ≤ 0.5·R`. Inaczej `Result.NothingHere` **bez** oznaczania postoju jako zużytego na stałe — kolejny postój bliżej centrum ma zadziałać.
- **Postój:** `DWELL_RADIUS_M` 1200 → 400, `DWELL_MILLIS` 5 min → 3 min. Do testów: w `TrackingService`, tylko gdy `BuildConfig.DEBUG`, czytaj `debug.tripail.dwell_s` (`adb shell setprop debug.tripail.dwell_s 20`) i przekaż do `DwellDetector.update(..., dwellMillis)` (nowy parametr z wartością domyślną).
- **Animacja bez przeliczania mgły co klatkę:** baza odkrywa miasto od razu, a UI na 1.8 s nakłada „pokrywkę”: źródło `reveal-cover` = pierścień (zewn. okrąg R·1.03, dziura = rosnący okrąg `r = R·(1-(1-t)³)`), `FillLayer` w kolorze i ziarnie mgły, krycie 0.95, **tuż nad warstwami mgły, pod etykietami**. `setGeoJson` co klatkę (`withFrameNanos`) — geometria ma ~100 punktów, to tanie. Na końcu puste źródło. Rośnie od środka miasta (od punktu wjazdu wymagałoby różnicy kształtów — pomijamy).
- **Kolejność:** `events.emit(CityDiscovered)` **przed** zapisem do bazy + `delay(120)` — pokrywka musi leżeć, zanim mgła zniknie, inaczej błyśnie gotowe miasto.
- **Celebracja (Compose overlay nad mapą, `pointerInput` nie przechwytuje dotyku):** nazwa miasta `FontFamily.Serif`, 40 sp, kolor tuszu `#4E3B26` (noc: `#FFD38A`), na 38% wysokości, fade-in 0.5 s, trzymanie 1.5 s, fade-out 0.5 s. „Atramentowy wybuch”: `Canvas`, 40 kropli (prototyp miał 70 — spokojniej), kolory kontynentów z `ContinentPalette`, 1.2 s, bez grawitacyjnego „fajerwerku”. Środek = `map.projection.toScreenLocation(miasto)`; miasto poza ekranem → sama nazwa, kamery nie ruszać.
- **`CelebrationQueue`:** jedna naraz, kolejka max 3, priorytet `CountryEntered` > `CityDiscovered`; reszta czeka. Aplikacja w tle → zdarzenia przepadają (bez `replay`) — powiadomienie z E6 pokaże licznik.
- **Dźwięk:** bez pliku audio — `AudioTrack` z wygenerowanym 0.3 s dźwiękiem (dwie sinusoidy 660→880 Hz, wykładnicze wygaszanie, głośność 0.25). Nie grać, gdy `AudioManager.ringerMode != RINGER_MODE_NORMAL` lub ustawienie „Dźwięk” wyłączone (ustawienie powstaje w E10; do tego czasu stała `true`).
- **Ręczne „Odkryj”** (wyszukiwarka / tap) też puszcza tę samą animację.
- **Ikonki miast — upraszczam względem POMYSLY:** nie robię szarych sylwetek z kafelków (id z kafelków ≠ id z Photona, a tysiące ikon to bodźce). Nieodkryte miasto = zwykła etykieta stylu na mgle (już jest). Odkryte = mała kolorowa kropka z obwódką (własne źródło `discovered-places` z `unlocked_place`, `CircleLayer` r=5, kolor kontynentu, biała obwódka 2) widoczna od zoomu 5. Zapisać w §4.
- **Wieżyczki** → E13.
- **„Cofnij”:** w `PlaceDetailSheet` dla miast `AUTO` przycisk tekstowy „To pomyłka — zakryj” (z potwierdzeniem); `ToggleRevealUseCase` ma przestać odmawiać dla `Source.Auto`. Ślad GPS dalej nieusuwalny.

**Pułapki:** Photon reverse potrafi zwrócić dzielnicę/ulicę — istniejący filtr `SETTLEMENT_KINDS` zostaje. Mała wieś: R ≥ 1 km (bbox + 1 km), więc 0.5·R ≥ 500 m — OK. `AutoUnlockCityUseCaseTest` ma atrapę geokodera — dopisać przypadki, nie przepisywać.

**Testy:** `AutoUnlockCityUseCaseTest`: fix w centrum → `Unlocked`; fix na 0.8·R → `NothingHere`; drugi postój w centrum po odmowie → `Unlocked`. `DwellDetectorTest`: nowe progi + parametr czasu. `CelebrationQueueTest`: kraj wyprzedza miasto; czwarte zdarzenie odrzucone.

**GPS (z `setprop debug.tripail.dwell_s 20`):**
- A — przez miasto: Łowicz. Dojazd z zachodu po ~400 m co 10 s do centrum (52.1070, 19.9450), 4 fixy w promieniu 100 m przez 40 s → miasto odsłania się animacją; zrzut w ~0.8 s animacji i po niej.
- B — obwodnicą: A2 na południe od Łowicza/Strykowa (ok. 52.03, 19.90 → 52.03, 20.05), z „postojem” 40 s na autostradzie → **żadnego miasta**, tylko wąski pas.
- Zrzuty: animacja w połowie, nazwa na środku, mapa po (kropka miasta), wariant B.

### [ ] E6. Tryb podróży
- `TrackingIntentRepository` już trzyma intencję — dodać `travelUntil: Long?` (auto-wyłączenie; wybór 1/3/7/14 dni / bez limitu).
- UI: duży przycisk na mapie (nazwa robocza, **zapytać** o nazwę). Notyfikacja foreground tylko gdy aktywny; kanał `IMPORTANCE_LOW`, treść „dziś: N miast, X km".
- „Odkryj, gdzie jestem": jednorazowy `getCurrentLocation` (Fused, high accuracy) → jeden punkt śladu + sprawdzenie miasta (bez dwell — użytkownik świadomie kliknął; zapytać, czy to ma odkrywać całe miasto).
- Podpowiedź wyjazdu: bez śledzenia w tle tego nie wykryjemy ciągle. Opcje: (a) przy otwarciu apki porównać bieżącą lokalizację z „domem" (najczęstszy punkt), > 100 km → pytanie; (b) geofence wokół domu (tani, systemowy). Zacząć od (a).
- Transport: `ActivityRecognitionClient` (Play Services, uprawnienie `ACTIVITY_RECOGNITION`) → `mode` w `trail_point`. Fallback: prędkość (> 25 km/h = VEHICLE). Samolot: luka + > 300 km w < 6 h → zapis łuku (osobna tabela `flight_arc` z dwoma punktami). Prom: ruch po wodzie — na później.

#### E6 — instrukcja wykonawcza
**Model: Opus** — cykl życia usługi pierwszoplanowej, zgody i wznawianie po ubiciu procesu to najbardziej podstępna część Androida w tym projekcie.
**Pliki:**
- zmienić: `app/MainActivity.kt` (usunąć auto-start śledzenia i auto-prośbę o lokalizację w `onStart`), `domain/location/TrackingIntentRepository.kt` + `data/location/TrackingPreferences.kt` (`travelUntil: Long?`), `app/service/TrackingService.kt`, `app/service/TrackingNotification.kt`, `domain/location/LocationTracker.kt` + `data/location/FusedLocationTracker.kt` (`suspend fun currentLocation(): DomainLocation?` — `getCurrentLocation(PRIORITY_HIGH_ACCURACY)`, timeout 15 s), `ui/TripailApp.kt`, `ui/settings/SettingsScreen.kt`, `data/local/Migrations.kt` + `TripexPoseDatabase` (v8), stringi.
- utworzyć: `domain/usecase/DiscoverHereUseCase.kt`, `domain/location/TravelWindow.kt` (czysta funkcja wygaśnięcia), `domain/location/FlightDetector.kt`, `ui/travel/DiscoverButton.kt`, `ui/travel/DiscoverSheet.kt`, `ui/travel/TravelViewModel.kt`, `ui/travel/TripHintCard.kt`, `data/local/FlightArcEntity.kt`.
- usunąć z UI: `ui/map/components/BackgroundTrackingDialog.kt` (patrz niżej).

**Jedna migracja na resztę planu:** wersji 7 nikt nie ma na telefonie, więc **wszystkie** nowe tabele E6/E8/E10 dodaj w jednej migracji 7→8 już teraz: `flight_arc(id PK auto, fromLat, fromLng, toLat, toLng, at)`, `country_stamp(iso2 TEXT PK, firstAt INTEGER)`, `place_note(placeId TEXT PK, text TEXT, updatedAt INTEGER)`. SQL w migracji musi być znak w znak zgodny z tym, co Room wygeneruje w `schemas/8.json` — porównaj po buildzie.

**Decyzje techniczne:**
- **Jeden przycisk** `DiscoverButton`: pigułka, kolor `#4A8B80`, biały tekst „Odkrywaj”, dół-środek, 16 dp nad dolną kartą (kotwiczyć w tym samym `Box` co `AnimatedContent` panelu, żeby jechał razem z kartą). Podróż aktywna → ten sam przycisk z kropką i tekstem „Podróż trwa”. Niewidoczny przy otwartej wyszukiwarce i na piętrze Świat.
- **Arkusz** (`ModalBottomSheet`), dwa duże wiersze (min. 72 dp), każdy: ikona + tytuł + jedno zdanie: „Tylko tu” — „Odkryj miejsce, w którym jesteś. Bez śledzenia.” / „Cała podróż” — „Odkrywa w tle, aż wyłączysz. Sama się wyłączy po 7 dniach.” Gdy podróż trwa: „Tylko tu” + „Zakończ podróż”. Pod spodem cichy link tekstowy „W okolicy” (E10).
- **Zgody dopiero przy kliknięciu:** lokalizacja (dokładna) dla obu opcji; powiadomienia (API 33+) tylko dla „Cała podróż”. **Nie prosimy o `ACCESS_BACKGROUND_LOCATION`**: usługa pierwszoplanowa typu `location` uruchomiona, gdy apka jest na ekranie, zachowuje dostęp „podczas używania”. Skutek uboczny: po restarcie telefonu/ubiciu procesu system może nie pozwolić wznowić → w gałęzi „Revived without an Intent” przy `SecurityException`/braku fixów: zostaw intencję, pokaż zwykłe powiadomienie „Podróż wstrzymana. Otwórz Tripail, żeby wznowić.”, a `MainActivity.onStart` wznawia, jeśli intencja = true i nie wygasła. Uprawnienie zostaje w manifeście nieużywane → **usunąć z manifestu** razem z `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, jeśli nic innego ich nie woła (grep).
- **„Tylko tu”** (`DiscoverHereUseCase`): `currentLocation()` → `UnlockAreaUseCase.single()` → `reverseGeocode`; osada i `distance ≤ 0.5·R` → odkryj miasto (`Source.Auto`, z celebracją), inaczej samo kółko 1 km + krótki tekst na karcie „Odkryto okolicę”. Brak pozycji w 15 s → „Nie widzę Twojej pozycji. Spróbuj na zewnątrz.” Kamera: `flyTo` na pozycję (świadoma akcja użytkownika).
- **Auto-wyłączenie:** `travelUntil = now + dni` (domyślnie 7; ustawienia: 1 / 3 / 7 / 14 / bez limitu). Sprawdzane w `onFix` i w ticku watchdoga (`TravelWindow.isExpired(now, until)`); po wygaśnięciu: stop + jedno ciche powiadomienie „Podróż zakończona po 7 dniach.”
- **Powiadomienie:** kanał `IMPORTANCE_LOW` (jest). Tytuł „Podróż trwa”, treść „Dziś: N miast · X km” (liczba mnoga przez `plurals`). N = miasta `AUTO` z `unlockedAt ≥ północ`; X = suma odcinków między zaakceptowanymi fixami od północy, trzymana w pamięci usługi (reset przy zmianie dnia; utrata przy ubiciu procesu jest akceptowalna). Akcja „Zakończ”.
- **Podpowiedź wyjazdu (wariant a):** po wejściu na mapę, jeśli podróż wyłączona i jest zgoda na lokalizację: `lastLocation` vs „dom” = najčzęstszy `parentRes7` (`SELECT parentRes7 FROM trail_point GROUP BY parentRes7 ORDER BY COUNT(*) DESC LIMIT 1`). > 100 km i nie pytano w ostatnich 24 h → **karta** (nie dialog) „Wygląda na wyjazd. Włączyć podróż?” [Włącz] [Nie teraz]. Brak danych o śladzie → brak karty.
- **Samolot** (`FlightDetector`, czysta funkcja): poprzedni fix z sesji (dowolny wiek < 24 h) i nowy fix: dystans > 300 km oraz prędkość 150–1200 km/h (w DEBUG bez górnej granicy, żeby dało się testować skokiem) → wiersz `flight_arc` + oba końce jako `single()`. Rysowanie: `LineLayer` `line-dasharray [2,3]`, kolor tuszu, 1.5 px, łuk z 32 punktów (interpolacja + lekkie wygięcie), źródło `flights`, nad mgłą, widoczny do zoomu 8. Prom → E13.

**Pułapki:** `TrackingSession.bridgeableFix` ma limit 10 min — do lotów czytaj `session.lastFix` bez limitu. `startForeground` musi zostać pierwszą instrukcją `onStartCommand`. Nie ruszać czterech mechanizmów przetrwania usługi opisanych w KDoc `TrackingService`. `MapHostViewModel.playerLocation` subskrybuje lokalizację przy otwartej mapie — bez zgody strumień pada i ponawia co 15 s; to zostaje.

**Testy:** `TravelWindowTest`, `FlightDetectorTest` (299 km nie, 800 km/2 h tak, 800 km/20 h nie), `DiscoverHereUseCaseTest` (centrum → miasto; skraj → samo kółko; brak pozycji → błąd), aktualizacja `MapViewModelTest`/`AppShellViewModelTest`, jeśli zależały od auto-startu.

**Emulator:** czysta instalacja (`pm clear`) → **żadnego** okna zgód przy starcie. „Odkrywaj” → zrzut arkusza. „Tylko tu” w Krakowie (50.0617, 19.9373) → okno zgody → miasto z animacją. „Cała podróż” → zrzut powiadomienia (`adb shell cmd statusbar expand-notifications`), 10 fixów, przesuń apkę z ostatnich, 5 fixów, otwórz → ślad urósł. „Zakończ podróż” → powiadomienie znika, fixy nic nie dopisują. Lot: fix Warszawa, potem Rzym (41.80, 12.25) → przerywany łuk, dwa kółka.

### [ ] E7. Mapa jak winda
- Usunąć `ContinentMenuCanvas`, `WorldPan`, suwak; poziom „Świat" na MapLibre: styl uproszczony (kontynenty jako fill z `continents.geojson` w kolorach z VISION §4, bez etykiet), `minZoom=maxZoom` per piętro, gesty zoomu wyłączone poza ostatnim piętrem.
- Przejścia: `flyTo` z kadrowaniem jak dziś (`ContinentFocusPolicy`, kadrowanie głównego lądu kraju).
- Skoki: sąsiednie kraje klikalne na piętrze Kraj (przelot w bok); lupka z dowolnego piętra; 3 chipy „ostatnie"; pinch-out = piętro wyżej (`OnScaleListener`).
- Globus: sprawdzić, czy MapLibre Android 13.x obsługuje `projection: globe` w stylu (WebFetch changelog). Jeśli tak — przełącznik w ustawieniach + animacja. Jeśli nie — odłożyć.
- `BackResolver` / `TripailTopBar` — zachować semantykę „Wstecz = piętro wyżej".

#### E7 — instrukcja wykonawcza
**Model: Opus** — przebudowa nawigacji, gestów i największego pliku (`TripailMapSurface`) naraz; dużo zależności między plikami.
**Pliki:**
- zmienić: `ui/navigation/TripailNavHost.kt`, `ui/navigation/Routes.kt` (+`RoutesTest`), `ui/TripailApp.kt` (`isMapDestination` + `World`), `ui/map/host/MapScene.kt`, `ui/map/host/MapHostState.kt`, `ui/map/host/TripailMapSurface.kt`, `ui/shell/chrome/TripailTopBar.kt` + `ui/map/components/SearchSuggestions.kt` (ostatnie), `ui/shell/AppShellViewModel.kt` (cel startu), `ui/continent/ContinentMapViewModel.kt` (zostaje jako źródło granic/statystyk kontynentów).
- utworzyć: `ui/world/WorldRoute.kt`, `domain/usecase/ResolveStartLevelUseCase.kt`, `data/settings/RecentPlacesStore.kt` (+ interfejs w `domain/settings`).
- wycofać: `ui/continent/ContinentMapScreen.kt`, `ui/continent/components/ContinentMenuCanvas.kt`, `WorldPan.kt`, testy `WorldPanTest`, `WorldFitTest` (kasowanie — patrz 0.2a; minimum: nic ich nie woła).

**Decyzje techniczne:**
- **Piętro Świat na MapLibre** (`MapLevel.World`): `ADM0_FILL_HIT` bez filtra kontynentu, kolor = `match(continent → kolor z VISION §4)`, krycie 1; do tego `LineLayer` w tym samym kolorze (1 px), żeby zakleić włoskowate szczeliny między krajami; granice krajów (`ADM0_LINE`) ukryte. Wszystkie warstwy symboli ukryte, warstwy mgły (`FOG_LAYERS` + `night-shade`) ukryte. Pod spodem `BackgroundLayer` `world-ocean` `#BCE3F7` (nad mapą bazową, pod wypełnieniem), widoczna tylko na tym piętrze. Efekt: 7 kolorowych plam, zero napisów.
- **Tap w kontynent = od razu wjazd** (bez przycisku „Wybierz”, bez suwaka): `countryAt(box).continentId` → `flyTo(granice kontynentu, animate = true, fill = true)` + `navigate(Continent)`. Przesuwanie mapy w poziomie zostaje.
- **Gesty (zmiana 2):** zoom gestów **nie wyłączać** (wyłączony nie daje zdarzeń skali). Na piętrach Świat i Kontynent po ustawieniu kadru zablokować zakres: `setMinZoomPreference(z)` = `setMaxZoomPreference(z)` (z = zoom po `frame`), a intencję czytać z `addOnScaleListener`: skumulowany `scaleFactor` na `onScaleEnd` > 1.25 → piętro niżej w punkcie ogniskowym (kraj/kontynent pod palcami; brak → nic), < 0.8 → piętro wyżej (`chrome.onBack`). Podwójny tap: wyłączyć (`isDoubleTapGesturesEnabled = false`) na zablokowanych piętrach. Na piętrach Kraj i Region zoom swobodny, `minZoom = zoom kadru − 0.3`; ściśnięcie przy minimalnym zoomie (< 0.8) → piętro wyżej. Zakres zoomu przywracać przy **każdej** zmianie sceny, inaczej `flyTo` do nowego piętra zostanie przycięte — kolejność: odblokuj zakres → animacja kamery → `onFinish` → zablokuj.
- **Start (zmiana 1):** `ResolveStartLevelUseCase`: ostatnia pozycja (`TrackingSession.lastFix` → najnowszy `trail_point` → najnowsze `unlocked_place`) → kraj przez istniejący `BoundaryMatcher`/`BoundaryGeometrySource` (przeczytaj oba przed użyciem). Jest kraj → po ekranie startowym złóż stos `World → Continent → Country` trzema `navigate` z rzędu (bez strażnika `isResumed`) i ustaw kamerę `animate = false`. Brak danych → `World`.
- **Skoki w bok:** sąsiednie kraje są już klikalne na piętrze Kraj (zachować `boundaryAt`: region → kraj). Lupka działa z każdego piętra (jest).
- **Ostatnie (zmiana 3):** `RecentPlacesStore` (DataStore, max 3, najnowsze pierwsze: kraj/region/miasto otwarte z mapy lub wyszukiwarki). Pokazywane jako 3 chipy **tylko** w otwartej wyszukiwarce przy pustym polu.
- **Globus** → E13.
- `BackResolver` bez zmian; „Wstecz” na piętrze Świat = wyjście z apki (ekran startowy nie zostaje na stosie — `popUpTo(Loading){inclusive}`); zmienić opis w VISION poziom 1.

**Pułapki:**
- Mapa powstaje dopiero na „mapowych” celach (`mapCreated`). Świat jest teraz mapą → tworzy się zaraz po ekranie startowym; panele czekają na `mapHost.isReady`.
- `applyLabelPolicy` chowa na stałe etykiety obszarów (`AREA_LABEL_MARKERS`). Chowanie etykiet na Świecie zrób osobną funkcją i przy wyjściu przywracaj **tylko** te, których polityka nie chowa.
- `maxBounds`: Świat — `null`; kontynent — jak dziś. Czyścić przy wejściu na Świat.
- Kafelki granic na zoomie ~1.2: sprawdź na zrzucie, czy wszystkie kraje się rysują (brak → podnieś zoom Świata i pozwól przesuwać).
- **Gestów szczypania nie da się wysłać przez `adb input`.** Logikę progów wydziel do czystej klasy `FloorGesture` z testem jednostkowym, a w podsumowaniu napisz użytkownikowi: „szczypanie do sprawdzenia ręcznie na telefonie”.
- Zrzut z 2026-11-14 pokazał zły kadr Europy (Svalbard, kontynent mały) — `ContinentFocusPolicy` ma kadrować główny ląd; popraw granice Europy tak, by wypełniała ekran.

**Testy:** `RoutesTest`, `ResolveStartLevelUseCaseTest` (fix w Polsce → Country(PL); brak danych → World), `FloorGestureTest`, `MapHostStateTest` (scena World), `BackResolverTest` bez zmian.

**Emulator / zrzuty:** (1) świeża instalacja → Świat: 7 kolorów, brak napisów, brak suwaka; (2) tap Europa → płynny przelot, Europa wypełnia ekran; (3) tap Polska → Kraj, sąsiedzi przygaszeni; tap Czechy → przelot w bok; (4) Wstecz ×3 → Kontynent → Świat → wyjście; (5) z fixem w Warszawie i po restarcie → start od razu na Polsce; (6) lupka → „Italia” → przelot; ponowne otwarcie lupki → chip „Włochy/Italia”.

### [ ] E8. Statystyki + paszport
- Liczniki: miasta (odkryte/znane w kraju — mianownik z warstwy `place` lub paczki offline), regiony (ADM1 z PMTiles), kraje.
- % kraju = odwiedzone „okolice": niewidoczne jednostki = komórki H3 res 5 (~250 km²) lub 6 — decyzja po teście, czy liczby „czują się" sprawiedliwie (cel: ktoś, kto zjeździł Polskę → 70–90%).
- Paszport: ekran z pieczątkami krajów (data pierwszego wjazdu = pierwsza pozycja w ADM0). Pieczątki generowane (kształt + kolor kontynentu + nazwa), nie grafiki z sieci. Animacja przy przekroczeniu granicy.
- Wibracja: krótki `VibrationEffect` przy pierwszym punkcie w nowej „okolicy" (throttle, przełącznik).

#### E8 — instrukcja wykonawcza
**Model: Sonnet** — dobrze opisane, dość odrębne kawałki (liczniki, ekran paszportu, pieczątki na Canvas); Opus tylko, jeśli liczby „okolic” wyjdą dziwnie.
**Pliki:**
- zmienić: `domain/usecase/ObserveAreaCoverageUseCase.kt`, `domain/geo/CoverageResolutionPolicy.kt` (+test), `ui/explore/components/AreaStatsPanel.kt`, `ui/explore/{Country,Region,Continent}ViewModel.kt`, `ui/shell/chrome/TripailTopBar.kt` (`TripailSideActions`: ikona osoby → paszport), `ui/navigation/{Routes,TripailNavHost}.kt`, `app/service/TrackingService.kt`, stringi (`plurals`).
- utworzyć: `domain/usecase/ObserveCollectionStatsUseCase.kt`, `domain/usecase/StampCountryUseCase.kt`, `domain/repository/CountryStampRepository.kt` + `data/local/CountryStampEntity.kt` (tabela już w migracji 7→8) + impl, `ui/passport/PassportScreen.kt`, `ui/passport/PassportViewModel.kt`, `ui/passport/StampCanvas.kt`, `ui/celebration/Haptics.kt`.
- wycofać: `ui/map/components/CommunityDialog.kt` i `chrome.isCommunityOpen` (społeczność → E13).

**Decyzje techniczne:**
- **Karta kraju (jedna liczba, zmiana 3 i 6):** duże „12 miast” (bez mianownika), pod spodem cienki pasek i mały tekst „34% okolic · regiony 5/16”. Nic więcej. Karta regionu: „3 miasta” + pasek %. Karta kontynentu: „4 kraje”. Brak danych = 0 i zwykły pasek (VISION).
- **Miasta w kraju:** `unlocked_place` nie ma kodu kraju → licz przez punkt-w-wielokącie środka miasta względem granic kraju (`BoundaryGeometrySource.rings` + `GeometryOps.pointInPolygon`), wynik w pamięci per `placeId`.
- **% = „okolice”:** stała rozdzielczość H3 **5** (~250 km²) dla krajów, **6** dla regionów i krajów z < 40 komórkami res 5. Licznik: rodzice res 5/6 z `observeFar()` (res 7 → `parentOf`), środki miast, komórki ręcznie odkrytych regionów. Słowo w UI: „okolice”. Po implementacji policz na syntetycznym śladzie (Kraków→Gdańsk + Wrocław→Lublin) i zapisz wynik tutaj; jeśli < 5% → przejdź na res 4 dla krajów > 100 tys. km².
- **Regiony n/m:** m = liczba ADM1 kraju z kafli; n = regiony z ≥ 1 odkrytą komórką res 6 lub odkryte ręcznie. Mapowanie komórka→region licz leniwie i trzymaj w pamięci.
- **Kraje / pieczątki:** `StampCountryUseCase` wołany w `TrackingService.onFix`, w `DiscoverHereUseCase` i w imporcie (E9). Punkt-w-wielokącie jest drogi → sprawdzaj tylko, gdy zmieniła się komórka res 5 fixa; trzymaj ostatni kraj. Nowy kraj → `country_stamp(iso2, firstAt)` + `DiscoveryEvent.CountryEntered`. **Ręczne „Odkryj” z wyszukiwarki nie daje pieczątki** (paszport = fizyczna obecność).
- **Paszport** (ekran z bocznej ikony): siatka 2 kolumny, sortowanie po dacie. `StampCanvas`: kształt z `hash(iso2) % 3` (koło / zaokrąglony prostokąt / ośmiokąt), podwójny obrys w kolorze kontynentu, nazwa kraju wersalikami, data `MM.RRRR`, obrót ±6° z hasha, krycie 0.85 (wygląd tuszu), tło strony = papier `#E9DDC2`. Żadnych grafik z sieci, flaga-emoji mała w rogu (jest `CountryFlag.kt`). Pusty stan: „Tu pojawią się pieczątki z krajów, które odwiedzisz.”
- **Celebracja kraju:** przez `CelebrationQueue` z E5 — pieczątka wjeżdża ze skalą 1.4→1 i lekkim obrotem (0.4 s), trzymanie 1.2 s. Kraj przed miastem.
- **Wibracja (`Haptics`):** `VibrationEffect.createPredefined(EFFECT_TICK)` (API 29+, niżej `createOneShot(30)`), tylko miasto i kraj, tylko gdy ustawienie włączone. Uprawnienie `VIBRATE` do manifestu.

**Pułapki:** przeczytaj `BoundaryGeometrySource`, `BoundaryMatcher`, `AtlasRepository` zanim napiszesz wyszukiwanie kraju — część już istnieje. `ObserveAreaCoverageUseCase` ma pamięć podręczną mianowników zależną od rozdzielczości — po zmianie polityki stare wiersze `area_stats` same się nadpiszą (porównanie `cached.resolution`). Polskie liczby mnogie: `one` miasto, `few` miasta, `many` miast.

**Testy:** `ObserveCollectionStatsUseCaseTest`, `CoverageResolutionPolicyTest` (nowe progi), `StampCountryUseCaseTest` (ta sama komórka → brak ponownego sprawdzania; nowy kraj → jedno zdarzenie; ten sam kraj drugi raz → nic).

**Emulator / zrzuty:** fixy Cieszyn (49.7497, 18.6355) → Czeski Cieszyn (49.7461, 18.6261) → animacja pieczątki CZ; karta Polski z liczbami; paszport z 2–3 pieczątkami i pusty paszport; to samo po angielsku.

### [ ] E9. Import historii
- Ekran przy pierwszym uruchomieniu: „Wczytaj swoją historię": Google / zdjęcia / pomiń.
- Google: eksport Osi czasu z telefonu (Ustawienia → Lokalizacja → Oś czasu → Eksportuj) = JSON. Parser odporny na oba formaty (stary Takeout `Records.json`/semantic i nowy on-device `Timeline.json`) — sprawdzić aktualny format przez WebFetch. Import przez SAF (`OpenDocument`), wszystko lokalnie.
- Zdjęcia: `READ_MEDIA_IMAGES` + `ACCESS_MEDIA_LOCATION`, `ExifInterface.latLong`. Pokazać postęp, tylko lokalnie.
- Import → `trail_point` (mode UNKNOWN) + auto-miasta wg tych samych reguł (bez dwell — wystarczy punkt wewnątrz).

#### E9 — instrukcja wykonawcza
**Model: Sonnet** — parser z plikami testowymi i prosty ekran to praca według specyfikacji, łatwa do sprawdzenia testami.
**Pliki:**
- utworzyć: `domain/importer/HistoryImporter.kt` (interfejsy `PhotoLocationSource`, `TimelineFileParser` + `ImportedPoint(lat, lng, atMs)`), `domain/usecase/ImportHistoryUseCase.kt`, `data/importer/PhotoLocationReader.kt`, `data/importer/GoogleTimelineParser.kt`, `data/di/ImportModule.kt`, `ui/importer/ImportScreen.kt`, `ui/importer/ImportViewModel.kt`, pliki testowe `data/src/test/resources/timeline/*.json`.
- zmienić: `ui/navigation/{Routes,TripailNavHost}.kt`, `ui/settings/SettingsScreen.kt` (wiersze „Uzupełnij ze zdjęć”, „Wczytaj z Google”), `app/AndroidManifest.xml` (`READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` z `maxSdkVersion=32`, `ACCESS_MEDIA_LOCATION`), `gradle/libs.versions.toml` + `data/build.gradle.kts` (`androidx.exifinterface`), `domain/usecase/UnlockAreaUseCase.kt` (`importPath`).

**Decyzje techniczne:**
- **Wejścia (zmiana 4 i 10):** (a) po pierwszym pokazaniu mapy karta „Masz już za sobą podróże? Wczytaj je ze zdjęć.” [Wczytaj] [Nie teraz] — raz, nie blokuje; (b) na stałe w Ustawieniach. Ekran importu: dwa duże przyciski „Ze zdjęć” (główny) i „Z Google” (drugorzędny) + zdanie „Wszystko zostaje na Twoim telefonie.”
- **Zdjęcia:** `MediaStore.Images` (kolumny `_ID`, `DATE_TAKEN`), dla każdego `MediaStore.setRequireOriginal(uri)` → `ExifInterface(stream).latLong`. Bez `ACCESS_MEDIA_LOCATION` + `setRequireOriginal` Android **wycina GPS z EXIF** — to najczęstszy błąd. Android 14 „wybrane zdjęcia” → pracuj na tym, co dano. Pomiń (0,0) i brak daty (użyj `DATE_ADDED`). Zapamiętaj `lastPhotoImportAt` → „Uzupełnij” skanuje tylko nowsze. Postęp „1 240 / 5 300”, można przerwać.
- **Google — przed kodowaniem sprawdź aktualne formaty (WebFetch)**; parser strumieniowy (`android.util.JsonReader`, pliki bywają > 100 MB — nie ładować do pamięci), tolerancyjny, rozpoznaje po kluczach:
  1. nowy eksport z telefonu `Timeline.json`: `semanticSegments[]` → `timelinePath[].point` („50.06°, 19.93°”), `visit.topCandidate.placeLocation.latLng`, `activity.start/end.latLng`, czas `startTime` (ISO);
  2. stary Takeout `Records.json`: `locations[]` → `latitudeE7`, `longitudeE7`, `timestamp` lub `timestampMs`;
  3. miesięczne pliki Semantic Location History: `timelineObjects[]` → `placeVisit.location.latitudeE7/longitudeE7`, `activitySegment.startLocation/endLocation`, `waypointPath.waypoints[].latE7/lngE7`;
  4. eksport z iOS: tablica w korzeniu, współrzędne jako `"geo:50.06,19.93"`.
  Nieznany plik → „Nie rozpoznaję tego pliku. Wybierz plik eksportu Osi czasu Google (.json).”
- **Instrukcja Google** na ekranie (3 kroki, zwykły tekst): „1. Ustawienia telefonu → Lokalizacja → Usługi lokalizacyjne → Oś czasu. 2. Eksportuj dane osi czasu. 3. Wróć tu i wybierz zapisany plik.” + przycisk „Wybierz plik” (`OpenDocument`, `*/*`).
- **Zapis:** punkty posortowane po czasie; kolejne oddalone < 2 km i < 10 min → ten sam segment (`UnlockAreaUseCase.importPath`, tryb `UNKNOWN`, nowe `segmentId` z czasu) — wtedy import wygląda jak korytarze, nie rozsypane kropki; reszta jako pojedyncze (`SEGMENT_SINGLE`). Wstawianie paczkami po 500.
- **Miasta z importu:** kandydaci = komórki res 6 z ≥ 3 punktami lub wizytą (`visit`/`placeVisit`); max 200 na import; reverse geocode **1 zapytanie/s** (regulamin Photona), w tle, z postępem „Szukam miast… 12/80”, przerywalne; bez sieci → pomiń z informacją. Reguła „w środku” jak w E5, bez postoju. Bez celebracji — jedno podsumowanie.
- **Pieczątki:** import też stempluje kraje (`StampCountryUseCase`) z datą punktu, bez animacji.
- **Wynik:** „Odkryto 1 240 nowych miejsc i 18 miast.” + „Pokaż mapę”.

**Pułapki:** `kotlinx.serialization` z całym drzewem na 100 MB = OOM. Daty: ISO z offsetem (`OffsetDateTime.parse`, API 26 OK) i milisekundy jako string. `FogComposer` po dużym imporcie przeliczy wszystko raz — w tym czasie pokaż „Rysuję mapę…”. Nie proś o zgodę na zdjęcia przed kliknięciem „Ze zdjęć”.

**Testy:** `GoogleTimelineParserTest` — po jednym małym pliku na każdy z 4 formatów + plik-śmieć; `ImportHistoryUseCaseTest` (segmentacja 2 km/10 min, deduplikacja, limit kandydatów); `PhotoLocationReader` — logika filtrowania na atrapie kursora.

**Emulator:** `adb push` pliku testowego `Timeline.json` do `/sdcard/Download/`, przejście przez systemowy wybór pliku, zrzut wyniku i mapy. **Zdjęć z GPS nie da się wiarygodnie przygotować na emulatorze** — zaznacz w podsumowaniu „import ze zdjęć: tylko testy jednostkowe, do sprawdzenia na telefonie”. Prawdziwego eksportu Google też nie mamy — napisać to użytkownikowi wprost.

### [ ] E10. Dodatki
- Tryb nocny: ciemna wersja stylu + lampiony (`circle-blur`) nad odkrytymi miastami; auto wg godziny/zachodu słońca lub systemu.
- „Co jeszcze w okolicy?": Wikipedia GeoSearch (`/w/api.php?action=query&list=geosearch&gscoord=…&gsradius=10000`; dla większych promieni — kilka zapytań lub Wikidata SPARQL) + filtr „nieodkryte". Max 3 na ekranie.
- Panel „co tu zobaczyć": to samo źródło, 3 pozycje + „więcej".
- Notatki do miejsc: tabela `place_note` (placeId, text, updatedAt) + udostępnianie przez systemowy `ACTION_SEND` (bez serwera).
- Moja historia: suwak czasu filtrujący `discoveredAt` + ▶️ odtwarzanie. Wymaga `discoveredAt` w `trail_point`, `unlocked_place`, `unlocked_region` (jest/dodać).
- Tryb spokojny: jeden przełącznik → wyłącza wibracje/dźwięki, chowa liczniki poza jednym, wolniejsze animacje.

#### E10 — instrukcja wykonawcza (zakres po zmianach 7 i 12)
**Model: Sonnet** — kilka małych, niezależnych funkcji; wyjątek: „Moja historia” (wydajność kompozytora) — jeśli się tnie, oddać Opusowi.
Robimy: tryb nocny z lampionami, jeden panel „W okolicy / co tu zobaczyć”, notatki, „Moja historia” (▶), ustawienia. **Nie ma** przełącznika „tryb spokojny”.

**Pliki:**
- utworzyć: `domain/settings/AppSettingsRepository.kt` + `data/settings/AppSettingsDataStore.kt` (`sound`, `vibration`, `nightMode: AUTO|ON|OFF`, `autoOffDays`), `data/network/WikipediaApi.kt`, `data/repository/NearbyRepositoryImpl.kt` + `domain/repository/NearbyRepository.kt`, `domain/usecase/ObserveNearbyUseCase.kt`, `domain/repository/PlaceNoteRepository.kt` + `data/local/PlaceNoteEntity.kt` + impl, `ui/nearby/NearbySheet.kt`, `ui/history/HistoryPlayback.kt` (+ViewModel).
- zmienić: `ui/settings/SettingsScreen.kt`, `ui/map/host/TripailMapSurface.kt` (lampiony), `ui/TripailApp.kt` (`night` z ustawienia), `ui/theme/{Theme,Color,CartoonStyle}.kt` (ciemne kolory paneli), `ui/map/components/PlaceDetailSheet.kt`, `ui/travel/DiscoverSheet.kt` (link „W okolicy”), `ui/passport/PassportScreen.kt` (przycisk ▶), `domain/usecase/ObserveFogGeoJsonUseCase.kt`, `data/di/NetworkModule.kt`.

**Decyzje techniczne:**
- **Ustawienia** (cały ekran, 5 wierszy, nic więcej): Język · Wygląd mapy (Automatycznie / Dzień / Noc) · Dźwięk · Wibracja · Podróż wyłącza się po (1/3/7/14 dni/nigdy); pod kreską: Uzupełnij ze zdjęć · Wczytaj z Google.
- **Noc:** „Automatycznie” = systemowy tryb ciemny (nie liczymy zachodu słońca — prostsze i przewidywalne). Paleta mgły już jest (`applyFogPalette`). Karty i paski: ciemny granat `#1C2038`, tekst `#F3EBDD`, akcent złoty `#FFD38A`; sprawdź kontrast każdego tekstu.
- **Lampiony:** źródło `lanterns` = punkty odkrytych miast z właściwością `r` (promień w km / 40). `CircleLayer` poświata: `#FFB84D`, `circle-blur 1`, krycie 0.55, promień `interpolate(exponential 2, zoom, 6 → r·4, 12 → r·256)`; jądro: `#FFE3A3`, blur 0.6, promień 4→10 px. Tylko nocą, nad mgłą, pod etykietami. Prototyp miał je za słabe — jeśli na zrzucie ledwo widać, podnieś krycie poświaty do 0.7.
- **Jeden panel (zmiana 12)** `NearbySheet`, dwa wejścia: link „W okolicy” w arkuszu „Odkrywaj” (wokół gracza) i sekcja „Co tu zobaczyć” w `PlaceDetailSheet` (wokół miasta). Źródło: Wikipedia `action=query&generator=geosearch&ggscoord=lat|lng&ggsradius=10000&ggslimit=30&prop=coordinates|pageimages|description&pithumbsize=200&format=json`, host `{pl|en}.wikipedia.org` wg języka apki. „W okolicy”: 5 zapytań (środek + 4 punkty 20 km na N/E/S/W), scalone, **tylko miejsca poza odkrytym terenem** (`UnlockedAreaRepository.isKnown(komórka)` = fałsz), sortowanie po odległości, wiersz: miniatura · nazwa · „12 km stąd”. Na ekranie **max 3** + „Więcej” (rozwija do 10). Tap → kamera leci do miejsca. `User-Agent`: `Tripail/2.0 (Android)` — bez żadnych danych osobowych. Pamięć podręczna w RAM na sesję. Brak sieci → „Brak internetu — spróbuj później.”
- **Notatki:** pole tekstowe w `PlaceDetailSheet` tylko dla odkrytych miast („Dopisz, co tu było fajne”), zapis przy utracie fokusu do `place_note`. Ikona „Udostępnij” → `ACTION_SEND` tekstem „Kraków — moje notatki:\n…”. Bez serwera.
- **Moja historia:** przycisk ▶ w Paszporcie → mapa w kadrze kraju gracza, 60 kroków w ~12 s od pierwszego `discoveredAt` do dziś, u góry duży napis „marzec 2024”. Dotknięcie = stop i powrót do „teraz”. Technicznie: `ObserveFogGeoJsonUseCase.snapshot()` — **zmień je, by przyjmowało jeden `FogComposer` na całe odtwarzanie** (dziś tworzy nowy przy każdym wywołaniu → zero cache’u → za wolno); `MapHostState` dostaje `fogOverride`. Jeśli krok > 200 ms → zmniejsz liczbę kroków, nie blokuj wątku UI.

**Pułapki:** geosearch zwraca też ulice, szkoły, przystanki — sprawdź jakość w 3 miejscach (Kraków, wieś pod Łowiczem, Siena); jeśli śmieci, odfiltruj wyniki bez miniatury **i** bez opisu. Wikipedia wymaga niepustego `User-Agent`. `PlaceDetailSheet` nie może urosnąć ponad ~60% ekranu — sekcje zwijane.

**Testy:** `ObserveNearbyUseCaseTest` (filtr „nieodkryte”, limit 3, sortowanie), dekodowanie odpowiedzi Wikipedii (wzór: `PhotonResponseDecodingTest`), `PlaceNoteRepository` (DAO, Robolectric), `AppSettingsDataStore` wartości domyślne.

**Emulator / zrzuty:** `adb shell cmd uimode night yes` → mapa nocna z lampionami nad 2 miastami; ustawienia (dzień i noc); „W okolicy” z 3 pozycjami; karta miasta z notatką; klatka z „Mojej historii”.

### [-] E11. Społeczność — PRZENIESIONE do E13 (zmiana 11). Nie robić. Stary opis:
- Backend darmowy: Supabase (free tier). Najpierw zapytać, czy w ogóle.
- „Moja ulubiona rzecz w mieście" (1 na osobę na miasto), podgląd mapy znajomego. Żadnych tras innych osób — tylko agregaty.

### [ ] E12. Szlif
- Przegląd wszystkich stringów PL/EN (`ui/res/values*/strings.xml`, `app/res/values*/strings.xml`) — naturalny język, spójne nazwy.
- Nowy ekran ładowania, nowe logo — **pytać** o kierunek, pokazywać warianty w HTML.

#### E12 — instrukcja wykonawcza
**Model: Opus** — teksty i porządki zrobi Sonnet, ale nowy ekran startowy, logo i surowy przegląd wyglądu wymagają lepszego oka i szczerej oceny.
**Pliki:** `ui/src/main/res/values*/strings.xml`, `app/src/main/res/values*/strings.xml`, `ui/loading/TripLoadingScreen.kt`, `ui/loading/components/{TripailLogo,SlingshotState}.kt`, `ui/components/WavePatternBackground.kt`, `app/src/main/res/mipmap-*/ic_launcher*` + `drawable/ic_launcher_foreground.xml`, `app/AndroidManifest.xml`, nowy `app/src/main/res/xml/data_extraction_rules.xml`, `ui/TripailApp.kt` (baner offline), `README.md`, `docs/TRIPAIL_VISION.md`, `TODO.md`.

**1. Teksty PL/EN**
- Grep zakazanych słów w obu językach: `powiat|gmina|heks|hex|H3|komórk|cell|GeoJSON|indeks`.
- Jeden słownik, wszędzie te same słowa: Odkrywaj / Explore · Tylko tu / Just here · Cała podróż / Whole trip · Podróż trwa / Trip in progress · Zakończ podróż / End trip · Odkryj / Reveal · Zakryj / Cover · Paszport / Passport · W okolicy / Nearby · okolice / areas · Uzupełnij ze zdjęć / Fill in from photos · Moja historia / My story.
- Styl: zwykłe zdania, bez wykrzykników, bez żargonu, najwyżej 2 linie; przyciski 1–2 słowa. Każdy klucz w obu plikach (porównaj listy kluczy skryptem).

**2. Ekran startowy i logo (decyzja bez pytania — kierunek „papier i tusz”, spójny z mgłą)**
- Obecny jest słaby: białe „Trip” na jasnoniebieskim jest nieczytelne (zrzut 2026-11-14), fale i proca to inny styl niż reszta.
- Nowy: tło papier `#E9DDC2` z ziarnem; napis „Tripail” szeryfowy, tusz `#4E3B26`; pod nim okrągłe „okno” w papierze z kolorowym fragmentem mapy (kolory kontynentów) i postrzępionym brzegiem tuszu, które powoli rośnie podczas ładowania (to jest wskaźnik postępu — prawdziwa gotowość, nie timer). Bez procy, bez fal.
- **Bez przycisku „Wejdź”**: gdy gotowe (min. 0.8 s, żeby nie mignęło) → samo przejście na mapę. Świadoma zmiana VISION poziom 0 (mniej kliknięć, zmiana 1). Błąd ładowania → jedno zdanie + „Spróbuj ponownie”.
- Pierwsze uruchomienie: język → jeden ekran: „Twój świat jest zakryty mgłą. Znika tam, gdzie byłeś.” + „Wszystko zostaje na Twoim telefonie.” + przycisk „Zaczynamy” → mapa.
- Ikona (adaptacyjna, wektor): tło papier, na pierwszym planie okrągłe okno z kolorową mapą i kreską tuszu. Sprawdź maskę koła i kwadratu.

**3. Brakujące drobiazgi z 0.4**
- **Offline:** `MapView.addOnDidFailLoadingMapListener` → wąski, spokojny pasek u góry „Brak internetu — mapa wróci, gdy będzie sieć.” (znika sam). Wyszukiwarka: „Nie udało się wyszukać. Spróbuj za chwilę.”
- **Kopia zapasowa:** `android:allowBackup="true"` + `dataExtractionRules` obejmujące bazę `tripex_pose.db` i DataStore (cloud-backup i device-transfer). W ustawieniach jedna linia tekstu: „Twoja mapa jest w kopii zapasowej telefonu.”
- **Dostępność:** skala czcionki 1.3 (`adb shell settings put system font_scale 1.3`) — nic się nie ucina; cele dotyku ≥ 48 dp; `contentDescription` na ikonach.

**4. Przegląd wyglądu (surowo, jak projektant)**
- Macierz zrzutów: {PL, EN} × {dzień, noc} dla: Świat, Kraj z kartą, arkusz „Odkrywaj”, karta miasta, Paszport, Ustawienia. Do tego mały ekran (`wm size 720x1280`, `wm density 320`) i duży (`wm size 1440x3120`, `wm density 560`) dla mapy i paszportu; potem `wm size reset; wm density reset; settings put system font_scale 1.0`.
- Szukaj: nierówne marginesy (siatka 8 dp, karty 16/24 dp), tekst < 14 sp, ucięte napisy (zwłaszcza EN/PL długie nazwy), więcej niż 1 akcent kolorystyczny na ekran, elementy pod paskiem systemowym, różne promienie zaokrągleń i cienie między ekranami.
- Popraw to, co słabe, przed końcem; resztę wypisz uczciwie.

**5. Porządki:** `ktlintCheck detekt` (ostrzeżenia tylko w nowym kodzie), README (bez klucza MapTiler, nowy opis działania), VISION zaktualizowana do stanu faktycznego (lista świadomych zmian z 0.3 + 0.4), `TODO.md` — usunąć lub skrócić do odsyłacza do tego pliku, `versionName` → `2.0.0`.

**6. Raport końcowy dla użytkownika (po polsku, punkty):** zrzuty najważniejszych ekranów · co zrobione · decyzje podjęte samodzielnie (z §4) · co nie działa / nie było jak sprawdzić (szczypanie, zdjęcia z GPS, prawdziwy eksport Google, pliki do ręcznego usunięcia) · szczera ocena: co ładne, co dalej brzydkie.

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

#### E13 — rozpiska punktów (zarys; każdy punkt dopiero po osobnej zgodzie użytkownika)
Format: **Pliki** · **Decyzja** · **Pułapka** · **Sprawdzenie** · **Model**. Ścieżki nowych plików to propozycje. Zanim zaczniesz punkt, przeczytaj wskazane istniejące pliki — po E5–E12 mogły się zmienić. Każdy punkt z bodźcem (animacja, dźwięk, karta) musi mieć wyłącznik lub być cichy domyślnie (§0.1).

**Wygląd / klimat**
- **Słoje drzewa.** Pliki: `domain/geo/FogComposer.kt`, `ui/map/host/TripailMapSurface.kt`. Decyzja: nie ruszać mgły — dodać osobne źródło `age-wash` z 3 pasmami wieku (>1 rok, >3 lata, >6 lat) jako półprzezroczystą sepię **nad** odkrytym terenem (krycie 0.08/0.16/0.24), liczone z `discoveredAt` segmentów. Pułapka: łamie zasadę „odkryte zawsze żywe” — efekt ma być ledwo widoczny, a młodsze pasmo musi wycinać starsze (unia per pasmo, różnica). Sprawdzenie: test z punktami o 3 datach → 3 rozłączne kształty; zrzut ze sztucznie postarzonymi danymi. Model: Opus — kolejne operacje na geometrii w gorącej ścieżce kompozytora.
- **Pora roku w śladzie.** Pliki: jak wyżej. Decyzja: robić tylko razem ze słojami, tym samym mechanizmem pasm (miesiąc XII–II → chłodny odcień 0.08), bez wzoru szronu (wzór „pływa” przy zoomie — §3). Pułapka: półkula południowa (odwrócić miesiące po znaku szerokości). Sprawdzenie: punkty z grudnia i lipca na zrzucie. Model: Sonnet — po słojach to kopia gotowego wzorca.
- **Nocne odkrycia.** Pliki: nowy `domain/geo/SunTimes.kt`, `FogComposer.kt`, `data/local/TrailPointEntity.kt` (kolumna `night`, migracja). Decyzja: flagę liczyć przy zapisie punktu algorytmem NOAA offline; rysować jako kolejne pasmo (granat 0.1). Pułapka: strefy czasowe — liczyć w UTC z długości geograficznej, nie z zegara telefonu; noc polarna. Sprawdzenie: test `SunTimes` na 4 znanych datach/miejscach (±5 min). Model: Sonnet — czysta funkcja z testami.
- **Pogoda odkrycia.** Pliki: nowy `data/network/OpenMeteoApi.kt`, `data/local/UnlockedPlaceEntity.kt` (`weatherCode`, migracja), `domain/usecase/UnlockPlaceUseCase.kt`, `ui/map/components/PlaceDetailSheet.kt`. Decyzja: jedno zapytanie przy odkryciu miasta, brak sieci = brak ikonki, nigdy nie blokuje odkrycia. Pułapka: import historii — nie pytać o pogodę wstecz dla setek miast. Sprawdzenie: test dekodowania + karta miasta z ikoną. Model: Sonnet — małe API i jedna kolumna.
- **Mgła czuje pogodę.** Pliki: `OpenMeteoApi.kt`, nowy `ui/weather/WeatherOverlay.kt`, `ui/TripailApp.kt`, ustawienia. Decyzja: nakładka Compose `Canvas` (60–100 kropli), odświeżanie co 30 min tylko przy otwartej mapie, **domyślnie wyłączone** (dodatkowy bodziec). Pułapka: stała animacja = bateria i przebodźcowanie; zatrzymywać po 20 s i przy `ON_STOP`. Sprawdzenie: wstrzyknięty kod pogody → zrzut; profil GPU bez spadków klatek. Model: Sonnet — odrębna nakładka.
- **Przelot 3D nad miastem.** Pliki: `ui/map/host/TripailMapSurface.kt`, `MapHostState.kt`, `ui/celebration/*`. Decyzja: po celebracji `easeCamera(pitch 60, bearing +30, zoom 15)` 3 s i powrót do zapisanej kamery; budynki `FillExtrusionLayer` z warstwy `building`; opcja w ustawieniach, domyślnie wyłączona. Pułapka: blokady zoomu pięter (E7) i `maxBounds` utną lot — odblokować na czas animacji i **zawsze** przywrócić, także po przerwaniu dotykiem; łamie VISION „miasto nie rusza kamery”. Sprawdzenie: odkrycie miasta → kamera wraca dokładnie do punktu wyjścia (porównać `cameraPosition`). Model: Opus — splata się z gestami i kolejką celebracji.
- **Shader AGSL (płynące chmury, prawdziwy papier).** Pliki: nowy `ui/map/host/FogShaderOverlay.kt`, `TripailMapSurface.kt`. Decyzja: najpierw 1-dniowy spike na osobnej gałęzi: `RuntimeShader` (API 33+) jako nakładka z maską mgły renderowaną do bitmapy co ruch kamery; niższe API zostaje przy obecnych warstwach. Pułapka: synchronizacja maski z kamerą MapLibre (opóźnienie 1 klatki = mgła „pływa” względem mapy) — jeśli nie da się zejść do zera, porzucić. Sprawdzenie: nagranie przesuwania mapy, brak rozjazdu; 60 kl./s. Model: Opus — ryzykowny eksperyment graficzny.
- **Globus.** Pliki: `TripailMapSurface.kt`, ustawienia. Decyzja: najpierw sprawdzić w changelogu MapLibre Android, czy `projection: globe` jest stabilne w używanej wersji; jeśli nie — nie robić obejść. Pułapka: `LatLngBounds`, `fitBounds` i warstwy mgły z ramką świata mogą zachowywać się inaczej na kuli. Sprawdzenie: przełącznik → piętro Świat jako kula, wejście w kontynent bez skoku. Model: Opus — zależy od nieznanego stanu biblioteki.

**Ślady i miejsca**
- **Prom.** Pliki: `domain/geo/TravelMode` (+`FERRY`), `TrackingService`/`UnlockAreaUseCase`, `FogComposer.kt`, `TripailMapSurface.kt`. Decyzja: punkt jest „na wodzie”, gdy nie leży w żadnym kraju (ten sam test co pieczątki, E8); 3 takie fixy z rzędu → segment FERRY rysowany przerywaną linią, bez czyszczenia mgły. Pułapka: jeziora, szerokie rzeki i zatoki leżą wewnątrz granic kraju — tam promu nie wykryje (zaakceptować). Sprawdzenie: trasa Gdańsk→Karlskrona przez `geo fix`. Model: Sonnet — rozszerzenie gotowego mechanizmu trybów.
- **Drogi jako trasy.** Pliki: `TripailMapSurface.kt`. Decyzja: tylko wizualnie i tylko z bliska — `queryRenderedFeatures` warstwy `transportation` w obrębie śladu, podświetlenie klas `motorway|trunk|primary`; bez zapisu, bez map-matchingu. Pułapka: zapytanie zwraca kawałki pociete kafelkami i tylko to, co na ekranie — efekt może być poszarpany; zrobić spike i pokazać użytkownikowi przed dalszą pracą. Sprawdzenie: zrzut przejazdu autostradą. Model: Opus — spike z niepewnym wynikiem.
- **Wieżyczki (znane punkty widokowe).** Pliki: nowy skrypt w `tools/atlas/`, `app/src/main/assets/viewpoints.json`, `domain/usecase/` (odkrycie przy < 300 m), `TripailMapSurface.kt` (ikona). Decyzja: paczka offline budowana raz skryptem z Overpass (`tourism=viewpoint` + `wikidata`), nie zapytania z telefonu. Pułapka: rozmiar i jakość danych (tysiące błahych punktów mimo filtra) — limit np. 5 000 na świat wg liczby wersji językowych Wikipedii. Sprawdzenie: fix pod znanym punktem → większe kółko 5 km. Model: Sonnet — skrypt danych + prosty warunek odległości.
- **Dom.** Pliki: `data/local/TrailPointEntity.kt` (zapytanie o najčzęstszą komórkę — jest od E6), ustawienia (ręczne „Tu jest mój dom”), `TripailMapSurface.kt` (ikona), karta statystyk. Decyzja: dom ustawiany ręcznie, automat tylko jako propozycja; „najdalej od domu” liczone przyrostowo i trzymane w DataStore. Pułapka: prywatność — dom nigdy nie trafia do udostępnianych obrazków ani kopii poza telefon. Sprawdzenie: test dystansu; ikona na zrzucie. Model: Sonnet — mały, zamknięty zakres.
- **Kraj offline.** Pliki: nowy `data/map/OfflineMapRepository.kt`, ekran w ustawieniach. Decyzja: MapLibre `OfflineManager`, zoom 0–10 (nie 12), pokazać rozmiar przed pobraniem. Pułapka: **najpierw sprawdzić regulamin OpenFreeMap dot. masowego pobierania** (WebFetch); jeśli zabronione — nie robić. Sprawdzenie: pobrać Luksemburg, tryb samolotowy, mapa działa. Model: Sonnet — gotowe API, główna praca to UI postępu.
- **Moje zdjęcia w miejscu.** Pliki: tabela `place_photo` (migracja), `ui/map/components/PlaceDetailSheet.kt`, nowy `data/photos/PlacePhotoStore.kt`. Decyzja: Photo Picker (bez uprawnień), kopia pomniejszona do `filesDir`, max 3 na miasto. Pułapka: URI z pickera wygasa — kopiować od razu; pliki uwzględnić w kopii zapasowej (rozmiar!). Sprawdzenie: dodać zdjęcie, ubić apkę, dalej widoczne. Model: Sonnet — standardowy wzorzec Androida.

**Nagroda = wiedza / podsumowania**
- **Ciekawostka po odkryciu.** Pliki: `data/network/WikipediaApi.kt` (jest od E10), `ui/celebration/DiscoveryCelebration.kt`. Decyzja: REST `page/summary/{tytuł}`, pierwsze zdanie, max 140 znaków, pod nazwą miasta; cache w Room. Pułapka: tytuł artykułu ≠ nazwa z Photona (homónimy) — brać tytuł z geosearch najbliższego środkowi miasta, nie z nazwy. Sprawdzenie: Kraków, Łowicz, Siena — sensowne zdania w PL i EN. Model: Sonnet — dokładka do istniejącego klienta.
- **Dziennik miesiąca.** Pliki: nowy `domain/usecase/MonthlySummaryUseCase.kt`, `ui/summary/MonthlyCard.kt`. Decyzja: szablon lokalny bez AI („3 nowe miasta, najdalej: Kraków”), karta na mapie raz w miesiącu, nigdy powiadomienie. Pułapka: pusty miesiąc — nie pokazywać nic (nie „0 miast”). Sprawdzenie: test use case’u na danych z dwóch miesięcy. Model: Sonnet — prosta agregacja.
- **Pocztówka z odkrycia.** Pliki: nowy `ui/share/PostcardRenderer.kt`, `PlaceDetailSheet.kt`, `FileProvider` w manifeście. Decyzja: `MapSnapshotter` z kadrem miasta + ramka rysowana na `Canvas` (nazwa, data, kolor kontynentu) → PNG w `cacheDir` → `ACTION_SEND`. Pułapka: snapshotter nie ma naszych warstw mgły — trzeba dodać je do stylu snapshotu albo zrobić zrzut żywej mapy (`MapView.snapshot`); atrybucja OSM musi być na obrazku. Sprawdzenie: plik PNG otwarty w galerii emulatora. Model: Sonnet — jeśli warstwy w snapshocie sprawią kłopot, Opus.
- **Plakat.** Pliki: jak pocztówka + `PdfDocument`, zapis przez SAF. Decyzja: dopiero gdy użytkownik uzna mapę za ładną; A3 300 dpi składane z kafli snapshotów. Pułapka: pamięć (bitmapa 4961×3508 = ~70 MB) i limit tekstur — renderować pasami. Sprawdzenie: PDF otwarty na komputerze, ostry przy 100%. Model: Opus — zarządzanie pamięcią i składanie kafli.
- **Eksport / import mapy do pliku.** Pliki: nowy `data/backup/BackupFile.kt`, ustawienia. Decyzja: jeden plik JSON (punkty, miasta, regiony, pieczątki, notatki) przez SAF; import = scalenie, nie nadpisanie. Pułapka: wersjonowanie formatu od pierwszego dnia (`"version": 1`). Sprawdzenie: eksport → `pm clear` → import → identyczna mapa. Model: Sonnet — serializacja z testem w obie strony.
- **Podpowiedź wyjazdu przez geofence** (ulepszenie E6). Pliki: `app/` nowy `GeofenceReceiver`, `TravelViewModel`. Decyzja: jedna strefa 100 km wokół domu; wyjście → jedno powiadomienie „Włączyć podróż?”. Pułapka: wymaga zgody na lokalizację w tle, z której w E6 świadomie zrezygnowaliśmy — zapytać użytkownika, czy chce tę zgodę przywrócić. Sprawdzenie: `geo fix` 150 km od domu przy zamkniętej apce. Model: Opus — zgody w tle i wymogi Google Play.

**Społeczność (wszystko wymaga decyzji o serwerze i koncie Supabase użytkownika; przed publikacją: polityka prywatności, zgłaszanie/blokowanie treści)**
- **Mapa skarbów od znajomego — jedyny punkt bez serwera, robić pierwszy.** Pliki: `AndroidManifest.xml` (deeplink `tripail://treasure`), nowy `domain/usecase/TreasureUseCase.kt`, tabela `treasure`, `TripailMapSurface.kt` (kropka na mgle). Decyzja: link z `lat,lng,note` wysyłany przez `ACTION_SEND`; odkrycie przy < 300 m. Pułapka: link to dane z zewnątrz — walidować zakresy i długość notatki, tekst pokazywać jako zwykły tekst. Sprawdzenie: `adb shell am start -d "tripail://treasure?..."` → kropka; fix obok → odkrycie. Model: Sonnet — mały, lokalny zakres.
- **Moja ulubiona rzecz w mieście (dawne E11).** Pliki: nowy moduł/pakiet `data/community/` (klient Supabase), `PlaceDetailSheet.kt`. Decyzja: logowanie anonimowe, 1 wpis na osobę na miasto, limit 120 znaków, widać dopiero po odkryciu miasta. Pułapka: moderacja cudzych tekstów i reguły RLS w bazie (bez nich każdy nadpisze każdego); klucz `anon` jest publiczny z definicji — bezpieczeństwo tylko w RLS. Sprawdzenie: dwa emulatory, wpis z jednego widoczny na drugim; próba edycji cudzego wpisu odrzucona. Model: Opus — bezpieczeństwo i prywatność.
- **Mapa znajomego.** Pliki: `data/community/`, nowy ekran podglądu. Decyzja: użytkownik świadomie publikuje **uproszczony** kształt czystej warstwy (tolerancja ≥ 2 km, bez domu), kod zaproszenia; czat — nie budować, link do komunikatora. Pułapka: nawet uproszczony ślad zdradza miejsce zamieszkania — wyciąć okolicę domu i wymagać wyraźnej zgody przy każdej publikacji. Sprawdzenie: opublikowany kształt nie zawiera punktów w promieniu 5 km od domu (test). Model: Opus — ryzyko wycieku lokalizacji.
- **List w butelce.** Decyzja: robić tylko, jeśli powyższe dwa działają i jest pomysł na moderację. Pułapka: sprawdzanie „jesteś na miejscu” po stronie klienta da się oszukać — traktować jako zabawę, nie zabezpieczenie. Model: Opus.
- **Ślady innych (poświata popularności).** Decyzja: ostatnie albo wcale; agregaty H3 res 7 liczone na serwerze, pokazywane tylko komórki z ≥ 10 osobami. Pułapka: przy małej liczbie użytkowników agregat = pojedyncza osoba. Model: Opus.

**Platformy i porządki**
- **Widget.** Pliki: nowy `app/widget/TripWidget.kt` (Glance), manifest. Decyzja: miniatura mapy z zapisanego zrzutu + jeden przycisk podróży. Pułapka: start usługi lokalizacji z widgetu przy zamkniętej apce jest blokowany przez system — przycisk ma otwierać apkę z arkuszem, nie startować usługi sam. Sprawdzenie: widget na ekranie emulatora, tap otwiera arkusz „Odkrywaj”. Model: Sonnet — jest gotowa umiejętność/wzorzec Glance.
- **Usunięcie starej tabeli `unlocked_hex`.** Pliki: `data/local/*`, `Migrations.kt`, `H3Converter` (martwe metody). Decyzja: dopiero wersję po tym, jak v2 pobyła u użytkownika bez zgłoszeń o znikniętych śladach. Pułapka: migracja nieodwracalna — najpierw eksport do pliku (punkt wyżej). Sprawdzenie: test migracji; mapa identyczna przed i po. Model: Sonnet — mechaniczne, ale z testem migracji.
- **iOS.** Decyzja: osobny projekt, nie „punkt”; najpierw sprawdzić, czym zastąpić JTS (nie działa poza JVM) — od tego zależy cały `FogComposer`. Pułapka: wymaga Maca i przepisania śledzenia w tle. Sprawdzenie: testy `:domain` przechodzą na celu iOS. Model: Opus — decyzje architektoniczne.

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

## 4. Decyzje podjęte zamiast pytań (użytkownik może je zmienić w poprawkach)
- Przycisk: **„Odkrywaj”**; tryb: **„Podróż”** („Cała podróż”, „Podróż trwa”, „Zakończ podróż”).
- „Tylko tu” odkrywa całe miasto, jeśli stoisz w jego wewnętrznej połowie; inaczej samo kółko 1 km.
- Animacja: atramentowy wybuch jak w prototypie, spokojniejszy (40 kropli); dźwięk cichy, domyślnie włączony, milczy przy wyciszonym telefonie.
- Prototyp zostaje w repo (`prototype/`).
- Mgła z daleka: szeroki kształt 25 km zamiast heatmapy. Odcień kontynentu: 10% domieszki do papieru.
- Stare ślady (heksy) po migracji rysowane kropkami 230 m, nie korytarzem.
- Ikonki miast: tylko kolorowa kropka dla odkrytych; bez szarych sylwetek.
- Bez zgody na lokalizację w tle; podróż działa jako usługa pierwszoplanowa uruchamiana z ekranu.
- % = „okolice” na H3 res 5 (regiony res 6); pieczątka tylko za fizyczną obecność lub import, nie za ręczne „Odkryj”.
- Noc „automatycznie” = tryb ciemny systemu. Ekran startowy bez przycisku „Wejdź”.
- **Do ręcznego usunięcia przez użytkownika** (powłoka nie może kasować): dopisywać tu ścieżki martwych plików.
  - Po E3 (nieużywane w kodzie produkcyjnym; usunąć parami z testami): `domain/src/main/kotlin/com/tripex/pose/domain/geo/RingSmoothing.kt` + `domain/src/test/.../geo/RingSmoothingTest.kt`; `domain/.../geo/FogGeoJsonBuilder.kt` + `FogGeoJsonBuilderTest.kt`; `domain/.../geo/RevealUnion.kt` + `RevealUnionTest.kt`; `data/src/main/kotlin/com/tripex/pose/data/mapper/UnlockedHexMapper.kt` + `data/src/test/.../mapper/UnlockedHexMapperTest.kt` (uwaga: `UnlockedHexDaoTest` używa `toUnlockedHexEntity` — usunąć go razem z tabelą `unlocked_hex`).

### Stare pytania (rozstrzygnięte powyżej)
- Nazwa przycisku trybu podróży.
- Czy „odkryj, gdzie jestem" odkrywa całe miasto, czy tylko kółko.
- Styl animacji odkrycia (fajerwerki tuszem vs inne), dźwięk tak/nie.
- Gdzie trzymać prototyp HTML (repo `prototype/` vs poza repo).
