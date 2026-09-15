# Research: Wybór Dostawcy Mapy (Vector Tiles) dla MapLibre (Android)

W ramach **Fazy 2** projektu *Tripex Pose*, przeanalizowano dostępnych na rynku dostawców kafelków wektorowych (Vector Tiles) pod kątem integracji z silnikiem MapLibre na platformie Android. Poniższe zestawienie bazuje na aktualnych danych i limitach darmowych (stan na 2026 rok).

Kluczowe kryteria wyboru:
1. **Limity darmowego planu (Free Tier)** – wystarczające na rozwój MVP i do portfolio.
2. **Wsparcie dla kafelków wektorowych (Vector Tiles)** – niezbędne do płynnego i taniego renderowania warstwy "mgły wojny" w locie.
3. **Zgodność z MapLibre** – bezproblemowa obsługa plików stylów (Style JSON) oraz stabilność.

---

## Porównanie Dostawców

### 1. MapTiler
MapTiler to obecnie wiodący dostawca narzędzi open-source i jeden z głównych sponsorów fundacji MapLibre.
* **Darmowy Limit:** 100 000 żądań (API requests) miesięcznie, 5 000 sesji, 5 GB przestrzeni (dla 1 pliku).
* **Kafelki Wektorowe:** Pełne, natywne wsparcie dla 2D i 3D.
* **Licencja (Free Tier):** Użytek osobisty, testowy i niekomercyjny (wystarczające dla MVP).
* **Zalety:** Rewelacyjne narzędzie *MapTiler Cloud* do wizualnej edycji stylów, natywna obsługa przez SDK MapLibre, w pełni kompatybilne wektory.
* **Wady:** Wymagane przypisanie atrybucji (logo MapTiler).

### 2. Stadia Maps
Platforma znana z podejścia zorientowanego na prywatność i elastycznych stylów.
* **Darmowy Limit:** 200 000 "kredytów" miesięcznie (1 kafelek wektorowy = 1 kredyt, 1 zapytanie Geocoding = 20 kredytów).
* **Kafelki Wektorowe:** Pełne wsparcie, bardzo dobrze współpracuje z Mapbox GL / MapLibre.
* **Licencja (Free Tier):** Ściśle niekomercyjna (plany komercyjne zaczynają się od 20$/m-c).
* **Zalety:** Najwyższy czysty limit kafelków (200k), brak śledzenia (privacy-first).
* **Wady:** System kredytowy sprawia, że w przypadku używania innych API (np. wyszukiwania miejsc), pula darmowych kafelków szybko maleje.

### 3. Thunderforest
Dostawca specjalizujący się w unikalnych stylach map (m.in. mapy topograficzne, rowerowe, transportowe).
* **Darmowy Limit (Hobby Project):** 150 000 zapytań kafelkowych miesięcznie.
* **Kafelki Wektorowe:** Ograniczone. **Uwaga:** U Thunderforest 1 kafelek wektorowy zużywa równowartość 10 kafelków rastrowych. Realny limit to zatem tylko 15 000 wektorów na miesiąc!
* **Licencja (Free Tier):** Hobby Project (brak supportu, brak komercji).
* **Zalety:** Świetne, gotowe warstwy specjalistyczne (np. szlaki).
* **Wady:** Skrajnie nieopłacalny dla kafelków wektorowych (mnożnik x10 przy zużyciu limitu).

---

## Rekomendacja i Decyzja Architektoniczna

Zdecydowanym zwycięzcą na potrzeby tego projektu jest **MapTiler**.

### Uzasadnienie:
1. **Architektura:** W tym projekcie będziemy rysować dynamiczne dziury z logów GPS (geometria H3). Wektorowy format MapTilera idealnie współpracuje z MapLibre Android SDK, pozwalając na precyzyjne doczepienie własnej warstwy `FillLayer` ze źródłem GeoJSON ponad kafelkami bazowymi, nie wpływając na obciążenie sieci.
2. **Koszty (0 PLN):** Darmowy pakiet dostarcza 100 000 żądań, co jest całkowicie wystarczające do rozwoju prototypu, testów oraz późniejszej prezentacji w portfolio.
3. **Ekosystem:** Bezpośrednie połączenie MapTiler Cloud z MapLibre gwarantuje stabilność (w przeciwieństwie np. do starszych, zamkniętych wersji SDK od Mapboxa).

### Następne Kroki (Faza 3):
Wdrożenie w kodzie (`app/build.gradle.kts`):
```kotlin
// MapLibre native dependency
implementation("org.maplibre.gl:android-sdk:11.4.0") // użyj najnowszej stabilnej wersji
```
W samej aplikacji klucz API do MapTiler zostanie wstrzyknięty z pliku `local.properties` ze względów bezpieczeństwa (nie komitujemy go do repozytorium!).
