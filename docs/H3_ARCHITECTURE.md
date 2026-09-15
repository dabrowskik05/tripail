# H3_ARCHITECTURE.md

**Projekt:** Tripex Pose — Fog of War Map
**Faza:** 3 (Architektura Hexagonów)
**Status:** Specyfikacja do implementacji (wejście dla Fazy 3.1 — Cursor)
**Zależy od:** M1 (moduły, Hilt, Version Catalogs)
**Wyjście:** `H3Utils` + `UnlockedHexEntity` + testy jednostkowe

---

## 1. Zakres dokumentu

Dokument definiuje:
- wybór rozdzielczości H3 dla pieszego i uzasadnienie liczbowe,
- kontrakt publiczny warstwy geometrii (`H3Utils` / `H3Converter`),
- algorytm zamiany strumienia fixów GPS na `Set<Long>`,
- model danych Room i strategię zapytań przestrzennych bez bazy przestrzennej,
- pipeline renderowania: `Set<Long>` → GeoJSON → MapLibre `FillLayer`,
- przypadki brzegowe, budżety wydajnościowe i listę testów jednostkowych.

Poza zakresem: implementacja `Foreground Service` (Faza 4), stylowanie mapy (Faza 5), geokodowanie (Faza 6).

---

## 2. Decyzje architektoniczne (skrót)

| # | Decyzja | Wartość |
| :- | :- | :- |
| D1 | Rozdzielczość bazowa (zapis) | **res 11** |
| D2 | Promień odkrycia | `gridDisk(k = 1)` (konfigurowalne 0–3) |
| D3 | Rozdzielczości LOD (render) | res 9 i res 7 (agregacja przez `cellToParent`) |
| D4 | Klucz w bazie | `Long` (natywny H3 index), `PRIMARY KEY` |
| D5 | Zapytanie o viewport | po kolumnie `parentRes7` (`IN (...)`), nie po lat/lng |
| D6 | Łączenie fixów | `gridPathCells` z limitem dystansu (anty-teleport) |
| D7 | Mgła | jeden poligon świata z dziurami z `cellsToMultiPolygon` |
| D8 | Biblioteka | `com.uber:h3-android:4.4.0` (AAR z natywnym JNI) |

---

## 3. Wybór rozdzielczości

### 3.1 Tabela referencyjna H3

| Res | Śr. długość krawędzi | Szerokość heksa (płaszczyzna–płaszczyzna) | Śr. powierzchnia |
| :-: | :-- | :-- | :-- |
| 8  | 461 m   | ~799 m | 0,737 km² |
| 9  | 174 m   | ~302 m | 0,105 km² |
| 10 | 65,9 m  | ~114 m | 0,0150 km² |
| **11** | **24,9 m** | **~43 m** | **0,00215 km² (~2 150 m²)** |
| 12 | 9,4 m   | ~16 m  | 0,000307 km² |

### 3.2 Uzasadnienie res 11

Kryterium nadrzędne: **promień odkrycia powinien odpowiadać temu, co pieszy realnie „widzi”** (ok. 40–60 m w mieście), przy jednoczesnym uwzględnieniu błędu GPS (5–20 m w gęstej zabudowie).

- `gridDisk(k = 1)` na res 11 = 7 heksów ≈ koło o promieniu **~43–50 m**. Idealne dopasowanie.
- Res 10 z `k = 1` dałoby promień ~114 m — odkrywanie całych kwartałów zabudowy, mapa wygląda „papkowato”, znika satysfakcja z eksploracji bocznych uliczek.
- Res 12 (16 m) jest poniżej progu błędu GPS — trasa wyglądałaby jak dziurawa, poszarpana nitka, a liczba rekordów rośnie ×7.

### 3.3 Konsekwencje pojemnościowe

Idąc 1 km z `k = 1` odblokowujesz korytarz ~100 m szerokości:
- ~23 heksy wzdłuż trasy × ~3 w poprzek ≈ **50–70 heksów / km**.
- 1 000 km chodzenia ≈ 60 tys. rekordów.
- 10 000 km (realistyczny sufit po latach) ≈ 600–700 tys. rekordów ≈ **~6 MB surowych danych**, ~15–25 MB bazy z indeksami.

Wniosek: res 11 jest bezpieczny pojemnościowo. Wąskim gardłem jest **render**, nie storage — stąd D3 (LOD).

### 3.4 LOD — dlaczego nie renderujemy res 11 zawsze

Przy oddaleniu mapy widok może obejmować dziesiątki tysięcy heksów res 11. Dlatego każdy rekord przechowuje zdenormalizowane ID rodziców:

| Zoom mapy | Rozdzielczość renderu | Źródło |
| :-- | :-- | :-- |
| ≥ 14 | res 11 | `h3Index` |
| 11–13 | res 9 | `parentRes9` (DISTINCT) |
| < 11 | res 7 | `parentRes7` (DISTINCT) |

Rodzic jest liczony raz, przy zapisie (`cellToParent`), a nie przy każdym renderze.

> **Uwaga wizualna:** rodzic res 9 jest „odkryty”, gdy odkryto choć jedno jego dziecko — przy oddaleniu mgła cofa się trochę zbyt hojnie. To jest akceptowalne i wręcz pożądane (czytelność), ale trzeba to świadomie zaakceptować, bo powrót do zoomu 15 „zawęża” odkryty obszar.

---

## 4. Zależności i konfiguracja

### 4.1 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
h3 = "4.4.0"

[libraries]
h3-android = { module = "com.uber:h3-android", version.ref = "h3" }
h3-jvm     = { module = "com.uber:h3",         version.ref = "h3" }
```

### 4.2 `build.gradle.kts` (moduł `:data` lub `:core:geo`)

```kotlin
dependencies {
    implementation(libs.h3.android)   // AAR z natywnymi .so
    testImplementation(libs.h3.jvm)   // wariant desktopowy — pozwala testować na JVM
}
```

**To jest kluczowy trik:** `h3-android` ładuje biblioteki natywne przez Android NDK i nie zadziała w zwykłych testach JVM (`src/test`). Wariant `com.uber:h3` ma w sobie `.so`/`.dylib`/`.dll` dla desktopa, dzięki czemu `H3Utils` testujesz **bez emulatora i bez Robolectrica**. Oba warianty dzielą identyczne API `com.uber.h3core.H3Core`.

### 4.3 Pułapki

- **Rozmiar APK:** natywne biblioteki dla 4 ABI dodają kilka MB. Dystrybuuj przez Android App Bundle (automatyczny split per ABI) albo ogranicz `abiFilters` do `arm64-v8a` + `armeabi-v7a`.
- **Konfiguracja NDK:** oficjalna próbka Ubera (`isaacbrodsky/h3-android-sample`) wymagała dodania sekcji `externalNativeBuild`, żeby przełączyć bibliotekę C++ na wariant współdzielony. Jeśli build wysypie się na `UnsatisfiedLinkError`, zajrzyj tam w pierwszej kolejności.
- **`H3Core.newInstance()` jest drogie** (ekstrakcja i `dlopen` biblioteki natywnej). Tworzymy **dokładnie jedną** instancję na proces, przez Hilt `@Singleton`. Instancja `H3Core` jest bezpieczna wątkowo.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object H3Module {
    @Provides @Singleton
    fun provideH3Core(): H3Core = H3Core.newInstance()

    @Provides @Singleton
    fun provideH3Utils(core: H3Core): H3Converter = H3Utils(core)
}
```

---

## 5. Umiejscowienie w Clean Architecture

`:domain` jest czystym modułem Kotlin/JVM — **nie może** zależeć od AAR-a. Dlatego:

```
:domain   →  interface H3Converter            (tylko typy prymitywne: Double, Long, Set<Long>)
:data     →  class H3Utils : H3Converter      (jedyne miejsce dotykające com.uber.h3core)
:ui       →  mapowanie FogGeometry → MapLibre FillLayer
```

Domena nie zna pojęcia „GeoJSON” ani „LatLng z Ubera”. Granicą jest model `FogGeometry` (patrz §8.1).

---

## 6. Kontrakt `H3Converter` / `H3Utils`

```kotlin
package pl.tripexposer.domain.geo

/**
 * Cała matematyka heksagonalna projektu. Bezstanowa, thread-safe,
 * wolna od typów Androida — implementacja w :data.
 */
interface H3Converter {

    /** Rozdzielczość, w której trwale zapisujemy odkrycia. */
    val baseResolution: Int          // = 11

    /** Lat/Lng → pojedyncza komórka w [baseResolution]. */
    fun cellAt(lat: Double, lng: Double): Long

    /**
     * Obszar odkryty z jednego fixu GPS: dysk o promieniu [k] komórek.
     * k = 1 → 7 komórek (6 przy pentagonie).
     */
    fun revealDisk(lat: Double, lng: Double, k: Int = DEFAULT_K): Set<Long>

    /**
     * Komórki wypełniające lukę między dwoma kolejnymi fixami.
     * Zwraca pusty zbiór, jeśli dystans przekracza [MAX_BRIDGE_CELLS]
     * (teleport = samochód/autobus/utrata sygnału — nie zaliczamy).
     */
    fun bridge(from: Long, to: Long): Set<Long>

    /** Rodzic w podanej (grubszej) rozdzielczości — do LOD. */
    fun parentOf(cell: Long, resolution: Int): Long

    /** Dystans w komórkach; -1 gdy nieobliczalny (zbyt daleko / pentagon). */
    fun gridDistance(from: Long, to: Long): Int

    /** Obrys zbioru komórek jako pierścienie [lng, lat] — gotowe pod GeoJSON. */
    fun outline(cells: Collection<Long>): FogGeometry

    /** Komórki pokrywające prostokąt widoku — do zapytań o viewport. */
    fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long>

    /** Debug/logowanie: 8a2a1072b59ffff zamiast 622054503267303423. */
    fun toDebugString(cell: Long): String

    companion object {
        const val BASE_RESOLUTION = 11
        const val LOD_MID_RESOLUTION = 9
        const val LOD_FAR_RESOLUTION = 7
        const val DEFAULT_K = 1
        const val MAX_BRIDGE_CELLS = 30      // ~1,3 km na res 11
    }
}
```

### 6.1 Szkic implementacji

```kotlin
package pl.tripexposer.data.geo

import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng

class H3Utils(private val h3: H3Core) : H3Converter {

    override val baseResolution = H3Converter.BASE_RESOLUTION

    override fun cellAt(lat: Double, lng: Double): Long =
        h3.latLngToCell(lat, lng, baseResolution)

    override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> {
        require(k in 0..3) { "k poza rozsądnym zakresem: $k" }
        return h3.gridDisk(cellAt(lat, lng), k).toSet()
    }

    override fun bridge(from: Long, to: Long): Set<Long> {
        if (from == to) return emptySet()
        val d = gridDistance(from, to)
        if (d < 0 || d > H3Converter.MAX_BRIDGE_CELLS) return emptySet()
        return runCatching { h3.gridPathCells(from, to).toSet() }
            .getOrDefault(emptySet())
    }

    override fun gridDistance(from: Long, to: Long): Int =
        runCatching { h3.gridDistance(from, to).toInt() }.getOrDefault(-1)

    override fun parentOf(cell: Long, resolution: Int): Long =
        h3.cellToParent(cell, resolution)

    override fun outline(cells: Collection<Long>): FogGeometry {
        if (cells.isEmpty()) return FogGeometry.EMPTY
        // geoJson = true → kolejność [lng, lat] i domknięte pierścienie
        val polygons: List<List<List<LatLng>>> =
            h3.cellsToMultiPolygon(cells.toSet(), true)
        return FogGeometry(
            polygons.map { poly -> poly.map { ring -> ring.map { it.lng to it.lat } } }
        )
    }

    override fun toDebugString(cell: Long): String = h3.h3ToString(cell)
}
```

**Ważne kontrakty `h3-java` v4, o które łatwo się potknąć:**
- `cellsToMultiPolygon` wymaga zbioru **unikalnych** komórek w **jednej** rozdzielczości. Mieszanka res 9 i res 11 da śmieci — stąd `toSet()` i osobne ścieżki LOD.
- Drugi parametr `true` = format GeoJSON (`lng, lat`, domknięta pętla). Zapomnienie o nim skutkuje mapą z Polską gdzieś koło Somalii.
- Metoda zwraca `List<List<List<LatLng>>>`: poligony → pierścienie → wierzchołki. **Pierwszy pierścień to obrys, kolejne to dziury** (niezwiedzone kieszenie wewnątrz odkrytego obszaru).

---

## 7. Algorytm odkrywania (fix GPS → baza)

```
fix GPS
  │
  ├─ 1. Filtr jakości:  accuracy > 50 m        → odrzuć
  │                     brak zmiany pozycji     → odrzuć (postój)
  │
  ├─ 2. cellAt(lat, lng)  →  currentCell
  │
  ├─ 3. if (currentCell == lastCell) → koniec (najczęstszy przypadek, zero I/O)
  │
  ├─ 4. cells = revealDisk(lat, lng, k = 1)
  │           + bridge(lastCell, currentCell)          // wypełnienie luki
  │
  ├─ 5. cells -= hotCache  (LRU ~2000 ostatnich ID)    // odsiew duplikatów
  │
  ├─ 6. if (cells.isEmpty()) → koniec
  │
  └─ 7. buffer += cells;  flush co 10 s lub co 100 komórek
             └─ DAO.insertAll(...)  @Insert(onConflict = IGNORE)
```

Kluczowe własności:
- **Krok 3 gasi 95% ruchu.** Pieszy przy fixie co 10 s przechodzi ~14 m, a heks res 11 ma 43 m — większość odczytów trafia w tę samą komórkę i nie dotyka bazy.
- **Krok 5** eliminuje zapisy przy chodzeniu tam i z powrotem tą samą ulicą.
- **Krok 7 (batching)** chroni baterię: jeden `INSERT` transakcyjny zamiast siedmiu na każdy fix.
- **Idempotencja** jest gwarantowana na dwóch poziomach: `Set` w pamięci i `PRIMARY KEY` + `IGNORE` w bazie. Ponowne przejście tej samej trasy nie zmienia rozmiaru bazy.

`bridge()` istnieje, bo przy fixie co 15 s i luce w sygnale (tunel, podwórko-studnia) pojawiłyby się dziury w ciągłości trasy. Limit `MAX_BRIDGE_CELLS = 30` sprawia, że wsiadając do samochodu **nie** rysujesz sobie prostej linii przez pół miasta.

---

## 8. Model danych (Room)

```kotlin
@Entity(
    tableName = "unlocked_hex",
    indices = [
        Index("parentRes9"),
        Index("parentRes7"),
        Index("discoveredAt")
    ]
)
data class UnlockedHexEntity(
    @PrimaryKey val h3Index: Long,   // res 11, natywny indeks H3
    val parentRes9: Long,            // zdenormalizowane — LOD + zapytania o viewport
    val parentRes7: Long,
    val discoveredAt: Long,          // epoch millis — pod "timeline" / statystyki
    val resolution: Int = 11         // bezpiecznik na wypadek zmiany D1 w przyszłości
)
```

### 8.1 Model domenowy geometrii

```kotlin
/** Multipoligon w kolejności GeoJSON: poligony → pierścienie → (lng, lat). */
@JvmInline
value class FogGeometry(val polygons: List<List<List<Pair<Double, Double>>>>) {
    companion object { val EMPTY = FogGeometry(emptyList()) }
}
```

### 8.2 DAO

```kotlin
@Dao
interface UnlockedHexDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(hexes: List<UnlockedHexEntity>)

    /** Render przy zoomie ≥ 14 — pełna rozdzielczość, tylko widoczny obszar. */
    @Query("SELECT h3Index FROM unlocked_hex WHERE parentRes7 IN (:viewportCells)")
    fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>>

    /** Render przy zoomie 11–13. */
    @Query("SELECT DISTINCT parentRes9 FROM unlocked_hex WHERE parentRes7 IN (:viewportCells)")
    fun observeMid(viewportCells: Set<Long>): Flow<List<Long>>

    /** Render przy zoomie < 11 — cała baza, ale garść ID. */
    @Query("SELECT DISTINCT parentRes7 FROM unlocked_hex")
    fun observeFar(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM unlocked_hex")
    fun observeCount(): Flow<Int>
}
```

### 8.3 Dlaczego zapytanie po `parentRes7`, a nie po lat/lng

Klasyczne podejście („`WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?`") wymagałoby przechowywania współrzędnych środka każdego heksa (dwa dodatkowe `REAL` na rekord) i indeksu złożonego, który w SQLite dla zapytań 2D działa przeciętnie.

Zamiast tego: `cellsForBounds(viewport, res = 7)` zwraca kilka–kilkadziesiąt dużych komórek (`polygonToCells` na prostokącie widoku), a `IN (...)` po zaindeksowanej kolumnie `INTEGER` jest praktycznie natychmiastowe. Zero trygonometrii, zero dodatkowych kolumn float, zero Spatialite.

**Uwaga:** przy bardzo oddalonej kamerze `cellsForBounds` na res 7 może zwrócić tysiące ID — dlatego dla zoomu < 11 używamy `observeFar()` bez filtra przestrzennego (i tak renderujemy wtedy tylko res 7).

---

## 9. Pipeline renderowania mgły

### 9.1 Model wizualny

MapLibre GL Android **nie udostępnia trybów mieszania typu `destination-out`**, więc mgły nie da się „wymazać" nakładką. Model, który działa:

> Jeden `Polygon`, którego **pierścień zewnętrzny to cały świat**, a **dziurami są obrysy odkrytych obszarów**.

```
FeatureCollection
 └── Feature (Polygon)
      ├── ring[0] = [[-180,-85.05],[180,-85.05],[180,85.05],[-180,85.05],[-180,-85.05]]
      ├── ring[1] = obrys odkrytego obszaru A     (dziura)
      ├── ring[2] = obrys odkrytego obszaru B     (dziura)
      └── ...
```

Warstwa: `FillLayer` z `fillColor` ciemnym i `fillOpacity ≈ 0.8`.

- Używamy granic **±85,05°** (limity Web Mercator), nie ±90° — biegun rzutuje się w nieskończoność i tessellator potrafi zwrócić puste płótno.
- `cellsToMultiPolygon` zwraca poligony, które **same mogą mieć dziury** (niezwiedzona kieszeń w środku odkrytego kwartału). Te dziury to wysepki mgły — muszą trafić do `FeatureCollection` jako **osobne `Feature`**, nałożone na warstwę świata. Zignorowanie ich = wyspy mgły znikają i eksploracja wygląda na kompletną, choć nie jest.
- W MapLibre o roli pierścienia decyduje **kolejność** (pierwszy = obrys, reszta = dziury), nie kierunek nawinięcia. Mimo to warto zachować zgodność z RFC 7946 (obrys CCW, dziury CW) — ułatwi eksport danych w przyszłości.

### 9.2 Przepływ

```
DAO Flow<List<Long>>
   │ (Dispatchers.Default)
   ├─ outline(cells)                → FogGeometry
   ├─ FogGeoJsonBuilder.build(...)  → String (GeoJSON)
   │ (Dispatchers.Main)
   └─ geoJsonSource.setGeoJson(json)
```

Wyzwalacze przeliczenia: **zmiana zbioru komórek** LUB **`onCameraIdle`** (nie `onCameraMove` — to 60 przeliczeń na sekundę). Debounce 300–500 ms.

### 9.3 Budżety i optymalizacje

| Metryka | Cel |
| :-- | :-- |
| Komórki przekazane do `cellsToMultiPolygon` | < 20 000 na przeliczenie |
| Czas `outline()` + budowa GeoJSON | < 100 ms (wątek tła) |
| Praca na głównym wątku | tylko `setGeoJson` |
| Częstotliwość przeliczeń | ≤ 2 / s |

- **`fillAntialias(false)`** na warstwie mgły — bez tego na styku sąsiadujących heksów widać włosowate szpary.
- `GeoJsonSource.Builder` przyjmuje `withTolerance()` — delikatne uproszczenie geometrii mocno tnie liczbę wierzchołków bez widocznej różnicy.
- Cache: trzymaj ostatnio wygenerowany GeoJSON per (LOD, viewport-key). Powrót kamery do poprzedniego kadru powinien być darmowy.

### 9.4 Wariant awaryjny (debug)

Jeśli renderowanie dziur sprawia problemy, na czas developmentu można odwrócić logikę: narysować **odkryte heksy** jako półprzezroczystą zieloną warstwę bez maski świata. Nie jest to docelowy efekt „fog of war", ale natychmiast pokazuje, czy warstwa danych działa poprawnie — i warto zostawić to jako przełącznik w ekranie debugowym.

---

## 10. Przypadki brzegowe

| Przypadek | Zachowanie |
| :-- | :-- |
| **Pentagony** (12 na rozdzielczość) | `gridDisk(k=1)` zwraca 6, nie 7 komórek. Nigdy nie zakładaj stałej 7. Używaj wyłącznie wariantów bezpiecznych (`gridDisk`, nie `gridDiskUnsafe`). |
| **Antymerydian (±180°)** | `cellsToMultiPolygon` potrafi zwrócić poligon przecinający linię zmiany daty, co renderuje się jako pas przez cały glob. Wykrywaj skoki > 180° między kolejnymi wierzchołkami i tnij poligon. Niski priorytet dla MVP. |
| **Teleport (samolot, mock location)** | `bridge()` zwraca pustkę powyżej 30 komórek — powstają dwie rozłączne wyspy odkrycia. Poprawne zachowanie. |
| **`accuracy` > 50 m** | Fix odrzucany przed konwersją. Bez tego jazda metrem „odkrywa" losowe kwartały. |
| **Zmiana `baseResolution` po premierze** | Kolumna `resolution` pozwala wykryć stare rekordy. Migracja w dół (11→9) przez `cellToParent` jest bezstratna; w górę przez `uncompactCells` — tworzy fałszywą precyzję. Traktuj res 11 jako decyzję nieodwracalną. |
| **Eksport / backup** | `compactCells()` przed serializacją potrafi zmniejszyć zbiór wielokrotnie (grupuje 7 dzieci w rodzica). Do bazy roboczej **nie** wprowadzamy skompaktowanych danych — komplikuje zapytania. |
| **`H3Core.newInstance()` rzuca `IOException`** | Awaria ekstrakcji biblioteki natywnej. Aplikacja bez H3 nie ma sensu — fail fast z czytelnym komunikatem w `Application.onCreate()`. |

---

## 11. Testy jednostkowe (`src/test`, czysty JVM)

Wymagane przypadki dla `H3UtilsTest`:

**Konwersja**
1. `cellAt` dla znanego punktu zwraca indeks o `getResolution() == 11`.
2. `cellAt` jest deterministyczne — dwa wywołania z tymi samymi współrzędnymi dają równe `Long`.
3. Dwa punkty odległe o 5 m dają **tę samą** komórkę; odległe o 200 m — **różne**.

**Dysk odkrycia**
4. `revealDisk(k = 0)` zwraca dokładnie 1 element.
5. `revealDisk(k = 1)` zwraca 7 elementów dla współrzędnych nad lądem (poza pentagonami).
6. `revealDisk(k = 2)` zwraca 19 elementów.
7. Wszystkie zwrócone komórki mają rozdzielczość 11 i przechodzą `isValidCell`.
8. `k` spoza zakresu 0..3 rzuca `IllegalArgumentException`.

**Mostkowanie**
9. `bridge(x, x)` → zbiór pusty.
10. Dwa punkty oddalone o ~100 m: wynik zawiera oba końce, a `gridDistance` między kolejnymi elementami ścieżki wynosi 1 (ciągłość).
11. Punkty oddalone o 5 km → zbiór pusty (zadziałał limit anty-teleport).
12. `gridDistance` dla nieobliczalnej pary zwraca -1, nie rzuca wyjątkiem.

**LOD**
13. `parentOf(cell, 9)` daje ten sam wynik dla wszystkich dzieci tego samego rodzica.
14. Rodzic ma `getResolution() == 9`.

**Geometria**
15. `outline(emptySet())` → `FogGeometry.EMPTY`.
16. `outline` dla 1 komórki → 1 poligon, 1 pierścień, 7 wierzchołków (pierwszy == ostatni, pętla domknięta).
17. `outline` dla 7 sąsiadujących komórek → **1** poligon (scalenie), nie 7.
18. Współrzędne są w kolejności `(lng, lat)` — test na punkcie, gdzie lat i lng są jednoznacznie rozróżnialne (np. lat ≈ 52, lng ≈ 21 dla Warszawy: pierwsza liczba musi być ~21).
19. Zbiór z „dziurą" (pierścień 6 komórek bez środka) → poligon z 2 pierścieniami.

**Idempotencja (integracyjny, z Room)**
20. Dwukrotne `insertAll` tej samej listy → `COUNT(*)` bez zmian.

---

## 12. Definition of Done — Faza 3

- [ ] `com.uber:h3-android` zintegrowane, aplikacja startuje na fizycznym urządzeniu bez `UnsatisfiedLinkError`.
- [ ] `H3Converter` w `:domain`, `H3Utils` w `:data`, dostarczane przez Hilt jako `@Singleton`.
- [ ] `UnlockedHexEntity` + `UnlockedHexDao` + migracje bazy.
- [ ] Wszystkie 20 przypadków testowych zielone na JVM (bez emulatora).
- [ ] Pusta mapa MapLibre renderuje się w `MapScreen` (Compose `AndroidView`).
- [ ] Ten dokument w repo jako `docs/H3_ARCHITECTURE.md`.

---

## 13. Prompt dla Fazy 3.1 (Cursor)

```
Użyj @.cursorrules oraz @docs/H3_ARCHITECTURE.md.

Zaimplementuj warstwę geometrii zgodnie z dokumentem:
1. Dodaj do Version Catalog h3-android 4.4.0 (implementation) oraz
   com.uber:h3 4.4.0 (testImplementation) — testy mają działać na JVM.
2. Interfejs H3Converter w module :domain (czysty Kotlin, sekcja 6).
3. Klasa H3Utils w :data implementująca kontrakt (sekcja 6.1).
4. Moduł Hilt H3Module dostarczający H3Core i H3Converter jako @Singleton.
5. UnlockedHexEntity, UnlockedHexDao i AppDatabase wg sekcji 8.
6. Testy jednostkowe pokrywające wszystkie 20 przypadków z sekcji 11.

Nie implementuj jeszcze serwisu lokalizacji ani renderowania mgły.
Trzymaj się dokładnie sygnatur z dokumentu — będą używane w fazach 4 i 5.
```
