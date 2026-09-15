# 🗺️ Geocoding API Research (Faza 6)

## 1. Wybór API dla Tripex Pose
Celem fazy 6 jest wdrożenie paska wyszukiwania, który pozwoli na manualne "odkrywanie" miast (Geocoding) i generowanie odblokowanych stref (mgła wojny). Główne założenia to **brak kosztów** przy rozsądnym użyciu oraz łatwość integracji z aplikacją Android (Retrofit).

### Główny kandydat: Nominatim API (OpenStreetMap)
Nominatim to darmowe API wyszukiwania i geocodowania opierające się na danych OpenStreetMap. Jest idealne dla projektów typu MVP, ale posiada rygorystyczną politykę użytkowania.

---

## 2. Przykłady Zapytań REST API (Nominatim)

Aby pobrać bounding box dla **Warszawy**, użyjemy endpointu `/search`.

**Endpoint:**
```http
GET https://nominatim.openstreetmap.org/search?q=Warszawa&format=json&limit=1
```

**Kluczowe parametry:**
*   `q=Warszawa` - nazwa szukanego miejsca.
*   `format=json` - wymagany format odpowiedzi.
*   `limit=1` - ograniczenie do najlepszego dopasowania.

**Przykładowy fragment odpowiedzi JSON:**
```json
[
  {
    "place_id": 257529881,
    "licence": "Data © OpenStreetMap contributors, ODbL 1.0. http://osm.org/copyright",
    "osm_type": "relation",
    "osm_id": 336074,
    "lat": "52.2319581",
    "lon": "21.0067249",
    "class": "boundary",
    "type": "administrative",
    "place_rank": 15,
    "importance": 0.6865231908902534,
    "addresstype": "city",
    "name": "Warszawa",
    "display_name": "Warszawa, województwo mazowieckie, Polska",
    "boundingbox": [
      "52.0978496",
      "52.3681534",
      "20.8516882",
      "21.2711512"
    ]
  }
]
```
*(Uwaga: Tablica `boundingbox` zawiera współrzędne w formacie `[lat_min, lat_max, lon_min, lon_max]`, co pozwoli na odblokowanie odpowiedniego promienia wokół miasta za pomocą indeksów H3).*

---

## 3. Aktualne Limity i Restrykcje Nominatim (Usage Policy 2026)

Korzystanie z publicznej instancji Nominatim wiąże się z surowymi zasadami [cite: 1, 3]:
1.  **Rate Limiting:** Absolutne maksimum to **1 zapytanie na sekundę** (1 req/s) [cite: 3].
2.  **Obowiązkowy nagłówek `User-Agent`:** Nie wolno używać domyślnych nagłówków bibliotek (np. `okhttp/4.10.0`) [cite: 1, 2]. Twoja aplikacja MUSI identyfikować się unikalnym nagłówkiem [cite: 1, 2, 3], najlepiej zawierającym kontaktowy adres e-mail [cite: 2], np.:
    `User-Agent: TripexPoseApp/1.0 (moj.email@example.com)` [cite: 1, 2, 3]
3.  **Brak Bulk Geocodingu:** Systematyczne odpytywanie w pętli (np. dla siatki punktów) skutkuje banem [cite: 1]. API można używać tylko do zapytań bezpośrednio wyzwalanych przez akcję użytkownika (wpisanie nazwy miasta w `SearchBar`) [cite: 1].
4.  **Zabroniony Auto-complete:** API nie służy do budowania wyszukiwarek podpowiadających w trakcie pisania ("as you type") [cite: 1]. Należy wysyłać zapytanie dopiero po wciśnięciu "Szukaj" lub używać opóźnień (debounce) [cite: 1].
5.  **Obowiązkowe Cachowanie:** Wyniki wyszukiwania powinny być zapisywane po stronie klienta (np. w bazie Room), aby unikać wielokrotnego odpytywania o to samo miasto [cite: 1].

---

## 4. Alternatywne darmowe API (Backup)

Jeśli restrykcje Nominatim okażą się zbyt surowe w fazie testów, możemy gładko przejść na alternatywne usługi oferujące warstwę darmową (Free Tier):

1.  **LocationIQ:** Oparte na danych OSM, ale z luźniejszymi limitami. Darmowy próg to **5 000 zapytań dziennie** [cite: 4], wspiera autocomplete. Idealne jako plan B.
2.  **Positionstack:** Szybkie, niezawodne API oparte na infrastrukturze chmurowej. Próg darmowy: **25 000 zapytań miesięcznie** (wystarczające do MVP) [cite: 4].
3.  **Geocode.maps.co (Oparte na OSM):** Alternatywna publiczna instancja oferująca darmowe geocodowanie z mniejszymi rygorami, choć wymaga klucza API i limitowana do 1 req/s.

## 5. Rekomendacja dla Tripex Pose (Implementacja `SearchBar`)

Dla modułu `M6 / Faza 6`:
*   Użyjemy **Nominatim API** ze względu na brak potrzeby autoryzacji (brak kluczy API do ukrywania) [cite: 1].
*   Skonfigurujemy klienta `Retrofit` z niestandardowym interceptorem, który wymusi dodanie nagłówka `User-Agent: TripexPose/1.0 (kontakt@twojadomena.pl)` [cite: 1, 2, 3].
*   Stworzymy mechanizm "Click to Search" (bez autocomplete'a) [cite: 1]. Po znalezieniu centrum miasta, przekształcimy jego `Lat/Lng` na indeks H3 i zapiszemy w lokalnej bazie `Room` jako nową, odblokowaną strefę [cite: 1].

---
**Źródła:**
* [1] Nominatim Usage Policy (aka Geocoding Policy)
* [2] User_Agent argument in Nominatim in GeoPy - GIS StackExchange
* [3] How to run OpenStreetMap locally? Reverse geocoding without
* [4] The best free geocoding APIs available: Ultimate list - Ambee
