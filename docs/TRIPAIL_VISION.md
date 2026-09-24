# TRIPAIL — WIZJA PRODUKTU I TWARDE ZASADY (GDD)

Ten dokument zawiera nienaruszalne zasady wizualne i logiczne aplikacji Tripail.
Każda zmiana w kodzie musi być z nimi zgodna. Jeśli implementacja łamie
którąkolwiek z tych zasad, jest traktowana jako błąd (bug), nie jako „inna
interpretacja".

Kolejność etapów prac mieszka w `TODO.md`. Ten plik mówi
**jak ma działać gotowy produkt**, tamten — co jeszcze zostało do zrobienia.

---

## 1. CORE FLOW — cztery poziomy

### Poziom 0: Ekran startowy (Splash)
- Logo, animowany napis „Przygotowuję Twój świat", potem przycisk „Wejdź".
- Dalej przechodzi się wyłącznie kliknięciem — żadnego auto-przejścia po timerze.
- Gotowość liczy się z realnych sygnałów startowych (baza, H3, styl, atlas,
  kafle granic), nigdy z fałszywego paska postępu.
- **Pierwsze uruchomienie** *(aktualizacja 2026-09-22)*: zanim pokaże się świat,
  pojawia się prosty ekran wyboru języka. Raz wybrany, nie wraca.

### Poziom 1: Menu kontynentów (tryb wyboru)
- Widok **wypełniony maksymalnie** kolorowymi kontynentami — żadnych pasów
  pustego oceanu nad i pod lądami.
- **Zoom zablokowany.** Cały zakres szerokości geograficznych jest zawsze
  widoczny, żeby Antarktyda dała się kliknąć bez przewijania.
- **Nawigacja suwakiem** *(aktualizacja 2026-09-22)*: na dolnym panelu jest
  poziomy suwak sterujący długością geograficzną kamery — w prawo na wschód,
  w lewo na zachód. Zastępuje przesuwanie mapy palcem we wszystkich kierunkach
  na ekranie, gdzie zoom i tak jest zablokowany.
- To jest **osobny byt wizualny** od mapy: Compose Canvas, zero MapLibre.
  (MapLibre rysuje w `SurfaceView`, który zamalowałby ten canvas.)
- Na dole karta/menu z interakcją. Przycisk potwierdzenia wyboru kontynentu
  ma napis **„Wybierz"** *(aktualizacja 2026-09-22)*.
- **Pasek systemowy:** panel ma własne tło sięgające dolnej krawędzi ekranu
  (biel Antarktydy `#F2F6F9`), a treść jest podniesiona ponad pasek. Żaden
  przycisk aplikacji nie może siedzieć pod systemowymi gestami.
- **Wstecz** (zarówno w UI, jak i systemowe) cofa o jeden krok: najpierw
  czyści zaznaczenie kontynentu, a przy braku zaznaczenia wraca na ekran
  startowy. **Nigdy nie zamyka aplikacji.**

### Poziom 2: Wybrany kontynent (zunifikowana mapa)
- Potwierdzenie wyboru kontynentu przenosi do WŁAŚCIWEJ mapy (MapLibre).
- **Zasada jednej mapy:** jedna instancja MapLibre na całą aplikację, jeden
  styl „pergaminowy". Żadnego skakania między mapami ani stylami.
- Przejście Menu → Mapa jest **płynne** *(aktualizacja 2026-09-22)*: subtelna
  animacja (crossfade / scale), żadnych twardych cięć.
- **Skalowanie kontynentu** *(aktualizacja 2026-09-22)*: od razu po załadowaniu,
  w czasie 0 ms i bez animacji zjeżdżania, kontynent maksymalnie wypełnia ekran
  w pionie lub poziomie i jest idealnie wyśrodkowany.
- **Auto-przybliżenie do gracza** *(aktualizacja 2026-09-22)*: gdy kontynent już
  wypełni ekran, aplikacja sprawdza obecną lokalizację GPS. Jeśli gracz jest na
  tym kontynencie, kamera płynnie i miękko przybliża się do jego lokalizacji, na
  poziom zoomu odpowiadający wielkości kraju.
- **Granice kamery** *(aktualizacja 2026-09-22)*: `maxBounds` wokół bounding boxa
  obecnego kontynentu, z szerokim marginesem. Użytkownik nie może swobodnie
  przewijać ekranu w nieskończoność na inne, wygaszone kontynenty.
- Widoczne granice państw na kontynencie.

### Poziom 3: Wybrany kraj
- Akcja: kliknięcie w kraj.
- **Kamera: bardzo płynne, powolne przybliżenie i wyśrodkowanie na tym kraju.**
  To jedyne miejsce w aplikacji, gdzie kamera rusza się sama.
  - Kadrowanie liczone jest **jednakowo dla każdego kraju**: margines to stały
    ułamek krótszego boku ekranu, więc Luksemburg i Brazylia wypełniają tyle
    samo widoku.
  - Kadruje się **główny ląd** kraju, nie pełny zasięg. Pełny bbox to pułapka:
    Francja sięga Gujany i Nowej Kaledonii, Norwegia ma Jan Mayen — kamera
    lądowała wtedy na środku oceanu.
- Wizualia: pozostałe kraje są wygaszone (gęstszy pergamin), pojawiają się
  granice regionów wybranego kraju i największe/turystyczne miasta. Ręczne
  przybliżanie palcem odsłania kolejne, mniejsze miasta.
- **Mapa jest klikalna bez guzików** *(aktualizacja 2026-09-22)*:
  - nie ma przycisku „Regiony" — regiony są klikalne od razu, będąc na
    poziomie kraju,
  - **miasta muszą być klikalne z poziomu mapy** (hit-test na ikony i etykiety
    miast),
  - zaznaczenie regionu (np. mazowieckiego) **nie odbiera możliwości** kliknięcia
    w inny kraj obok (np. Czechy).
- UI: panel z flagą kraju i statystyką odblokowania.

### Poziom 4: Wybrany region / miasto
- **Kamera stoi.** Kliknięcie regionu albo miasta nie wywołuje żadnego
  `flyTo`, `easeTo` ani zmiany zoomu. Użytkownik sam przybliża, jeśli chce.
  Dotyczy to także wejścia w tryb eksploracji mapy z panelu.
- **Odkrycie miasta** odblokowuje cały jego obszar miejski **+ 1 km zapasu**
  wokół granic. Obszar mierzy się z bboxa geokodera i **padduje**, nigdy nie
  przycina.
- **Odkrycie regionu** odblokowuje dokładny wektorowy obrys regionu.
- **Wyszukanie obiektu go NIE ODBLOKOWUJE** *(aktualizacja 2026-09-22)*. Ma
  jedynie wyśrodkować kamerę, otworzyć dolny panel z informacjami i pokazać
  przyciski: **„Odkryj"** (jeśli nieodkryte) / **„Zakryj"** (jeśli odkryte).
  Panel nie ma przycisku „Zamknij" *(aktualizacja 2026-09-24)*: kliknięcie
  regionu lub kraju na mapie po prostu podmienia wybór, a panel zamyka też
  systemowe „Wstecz".
- Wydajność: odkryte obszary są widoczne zawsze, ale liczone oszczędnie —
  jedno źródło geometrii na aplikację, cache obrysów, żadnych równoległych
  przebudów tego samego GeoJSON-u na kilku poziomach naraz.

---

## 2. Filozofia odkrywania (core loop)

- **„Byłem w Rzymie = mam zaliczony cały Rzym".** Gra daje szybką satysfakcję.
  Odrzucamy grindowanie pojedynczych uliczek.
- **Miasta:** odkrycie/wyszukanie odblokowuje od razu cały obszar — zapisywany
  jako **środek i promień** w tabeli `unlocked_place` i wycinany ze mgły jako
  okrąg *(aktualizacja 2026-09-21)*.
- **Regiony:** odkrycie zapisuje **ID** w tabeli `unlocked_region`, nie miliony
  punktów H3. Obrys czytany jest z kafli na żądanie.
- **Spacer GPS (tło):** promień odsłaniania wokół gracza to sztywno 1 km.
  To jedyne miejsce, w którym nadal powstają komórki H3.
- **Zakaz rasteryzacji dużych obszarów.** Warszawa przy res 11 to ~770 000
  komórek; stary limit `AreaTooLargeException` leczył objaw i **został
  usunięty**. Duży obszar nie jest błędem — jest sygnałem, że ma być odkryciem
  makro *(aktualizacja 2026-09-21)*.
- **Baza nie przechowuje geometrii.** Wiersz regionu to identyfikator, wiersz
  miasta to trzy liczby.
- **Nie zapisujemy surowego śladu trasy** *(aktualizacja 2026-09-22)*. W bazie
  zostają wyłącznie komórki H3 wycierające mgłę — żadnego logowania dokładnych
  punktów lat/lng.
- **Największą jednostką odkrywaną ręcznie jest region**
  *(aktualizacja 2026-09-22)*. Nie ma przycisku „Odkryj cały kraj" — odkrycie
  całego kraju na raz psuje sens gry. Odkrywamy maksymalnie pojedyncze regiony
  i miasta.
- **Kształt miasta to dynamiczny okrąg** *(aktualizacja 2026-09-22)*: promień
  liczony z punktu centralnego. Wektory ADM2 to przyszłość, poza obecnym etapem.
- **„Zakryj" działa wyłącznie dla odkryć zrobionych ręcznie z UI**
  *(aktualizacja 2026-09-22)*: regionów odblokowanych z poziomu interfejsu
  i miast z wyszukiwarki. **Ślad z GPS (spacer) zostaje na zawsze.**

---

## 3. Architektura wizualna mapy

- **Koncepcja pergaminu i dziur:**
  - Nieodkryty świat przykryty warstwą wypłowiałego pergaminu.
  - Na pergaminie widać granice państw i nazwy głównych miast (żeby wiedzieć,
    gdzie się klika), ale nie ma ulic, budynków ani ukształtowania terenu.
  - Odkryte obszary są dziurami w masce i pokazują pod spodem żywą, kolorową
    mapę bazową.
  - **Dziury są widoczne zawsze** — niezależnie od poziomu, na którym jest
    użytkownik. Odkrycie w Czechach zostaje odkryte, gdy patrzysz na Polskę.
- **Raz odkryty teren = ZAWSZE odkryty** *(aktualizacja 2026-09-22)*. Nakładające
  się odkrycia nie mogą się wzajemnie znosić: odkrycie np. Warszawy, a potem woj.
  mazowieckiego, nie ma prawa zakryć Warszawy z powrotem. Działa to w obie
  strony — **zakrywanie również nie psuje innych odkryć**.
- **Sumowanie wszystkich warstw naraz** *(aktualizacja 2026-09-22)*: przed
  wysłaniem GeoJSON-a do mapy łączy się (union) i spłaszcza **wszystkie** warstwy
  jednocześnie — komórki H3 ze śladu GPS, okręgi miast (z auto-odblokowania
  i manualne) oraz wektory regionów. Żadne obiekty nie mogą się wzajemnie wycinać.
- **Stabilna geometria** *(aktualizacja 2026-09-22)*: obiekty nie zmieniają losowo
  swojej geometrii — ani w bazie, ani w rendererze — pomiędzy zapisem a odczytem.
- Warstwy od dołu: mapa bazowa → pergamin z dziurami → półprzezroczyste
  wypełnienia i obrysy krajów/regionów → etykiety miejscowości ze stylu
  bazowego (pergamin wchodzi **pod** pierwszą warstwę symboli).
- Wypełnienia obszarów muszą być realnie renderowane, nie „prawie niewidoczne" —
  `queryRenderedFeatures` nie zwraca cech z warstwy o zerowej widoczności,
  a wtedy mapa przestaje reagować na kliknięcia.
- **Kolorystyka pochodna od kontynentu** *(aktualizacja 2026-09-21)*: po wejściu
  w kontynent **wszystkie** państwa na nim oraz **woda** wokół przyjmują odcienie
  pochodne od koloru tego kontynentu — pergamin rozjaśniony o 62 %, ląd o 35 %,
  woda o 78 % i odbarwiona, granice przyciemnione o 22 %. Kontynent ma być
  rozpoznawalny bez czytania etykiet. Zaznaczenie *(aktualizacja 2026-09-24,
  zastępuje jasnożółty `#F2DF8E`)*: wybrany kraj jest o **20 % jaśniejszy** od
  koloru kontynentu; wybrany region jest o 20 % jaśniejszy, a **reszta jego
  kraju o 20 % ciemniejsza**. Tylko to, co kliknięte, ma efekt 3D (twardy,
  przesunięty cień pod obszarem i miękki pod jego granicą): wybrany kraj, a przy
  wybranym regionie — region i jego kraj. Pozostałe granice są płaskie.
- **Granularność regionów** *(aktualizacja 2026-09-21)*: kafle ADM1 są rozbijane
  (`-explode`) na pojedyncze poligony. Każda fizyczna wyspa to osobny, klikalny
  obiekt z własnym `adm1_code` — jeden region egejski w Natural Earth potrafi
  zawierać 36 wysp w jednym `MultiPolygon`.

---

## 4. Estetyka, kolory i UI

- **Kolory kontynentów (sztywne HEXy):**
  - Ameryka Północna: `#4A8B80`
  - Ameryka Południowa: `#85C25F`
  - Afryka: `#E39B3B`
  - Azja: `#D95B5B` (Rosja należy do Azji!)
  - Europa: `#8F75AD`
  - Australia i Oceania: `#C76345`
  - Antarktyda: `#F2F6F9`
  - Woda/tło: `#BCE3F7`
- **Granice:** zakaz czerni i szarości. Albo o ~20 % ciemniejszy odcień
  samego kontynentu, albo półprzezroczysta biel.
- **Przyciski:** nowoczesne, gładkie, dopasowane kolorystycznie (morski
  `#4A8B80` z białym tekstem). Żadnych domyślnych czerwono-zielonych potworków.
- **Paski systemowe (window insets):** edge-to-edge, ale dolny pasek nawigacji
  zawsze ma pod sobą tło panelu i nigdy nie zasłania treści aplikacji.
- **Czcionki:** czytelne, w 100 % wspierające polskie znaki (brak artefaktów
  w słowie „Wejdź").
- **Uniwersalny Top Bar** *(aktualizacja 2026-09-22)*: na każdym poziomie mapy
  (Świat, Kontynent, Kraj) ten sam pasek górny:
  - lewa strona: `[Ikona Wstecz]` oraz `[Nazwa obecnego zakresu (np. Polska)]`,
  - prawa strona: `[Ikona Szukaj (Lupka)]`, `[Ikona Ustawienia (Śrubka)]`,
    `[Ikona Społeczność (Osoba)]`.
  - Wyszukiwarka otwiera się dopiero po kliknięciu Lupki, a w trybie
    wyszukiwania ma własny powrót.
  - Nie ma przycisków „Odkrywaj mapę".
- **Systemowe „Wstecz" robi dokładnie to samo, co strzałka w Top Barze**
  *(aktualizacja 2026-09-22)*. Gest i przycisk są zapięte pod tę samą funkcję —
  żadnego „zapadania się" ekranu ani psucia stanu nawigacji.
- **Dwujęzyczność PL / EN** *(aktualizacja 2026-09-22)*: wsparcie dla polskiego
  i angielskiego obejmuje UI **oraz dane MapTilera** — dynamiczna zmiana tagów
  `name:pl` / `name:en`. Wybór dostępny przy pierwszym uruchomieniu i w
  Ustawieniach. Kafle granic niosą `name_pl` z Natural Earth dla ADM0 i ADM1.
- **Pasek postępu zawsze** *(aktualizacja 2026-09-22)*: panel postępu nigdy nie
  pokazuje komunikatu o braku danych. Brakujące dane dają **0 %** i normalny
  pasek postępu.
- **Ikona aplikacji** *(aktualizacja 2026-09-22)*: własna, wektorowa
  `ic_launcher` pasująca do tematyki Tripail (podróże, mapa, kontynenty) —
  nie domyślna, systemowa ikona Androida.
- **Żargon techniczny nie wycieka do UI:** „heks", „H3", „komórka", „GeoJSON",
  „indeks" nie mają prawa pojawić się w tekstach widocznych dla użytkownika.

---

## 4a. Śledzenie w tle *(aktualizacja 2026-09-22)*

- **System śledzenia lokalizacji i auto-odblokowywania miast musi działać
  CAŁKOWICIE w tle.**
- Aplikacja rejestruje ślad i wylicza postoje (`DwellDetector`) nawet wtedy, gdy
  ekran telefonu jest zablokowany, aplikacja jest zminimalizowana, a co
  najważniejsze: **nawet gdy aplikacja zostanie usunięta z kart** („ostatnich
  aplikacji").
- Architektura pod to: odporny Foreground Service z flagą `START_STICKY`, stała
  notyfikacja, poprawne uprawnienia `ACCESS_BACKGROUND_LOCATION`.
- Kryterium akceptacji: wyjazd do innego miasta, telefon w kieszeni przez całą
  drogę, bez konieczności odpalania apki na ekranie.

---

## 5. Ekran startowy — szczegóły

- **Fale na oceanie:** ułożone deterministycznie (konkretny seed), spójne.
- **Animacja ładowania:** napis „Przygotowuję Twój świat" wisi na tyle długo,
  by dało się go przeczytać (i nie mruga, gdy dane są gotowe od razu).
- **Brak błysków:** między wystrzeleniem procy a kolejnym ekranem nie ma
  martwych klatek ani białych rozbłysków.

---

## 6. Historia decyzji

- **2026-09-22 — przejście Menu → Mapa.** Poprzednia wersja wymagała **twardego
  cięcia**. Zasada została odwrócona na polecenie autora: przejście ma być
  płynne (crossfade / scale), bez twardych cięć. Kamera nadal jest *ustawiana*
  natychmiast (kontynent wypełnia ekran w 0 ms) — animowana jest sama zmiana
  ekranu, nie przelot między światami.
- **2026-09-22 — język.** Poprzednia wersja mówiła „język polski wszędzie".
  Zastąpione dwujęzycznością PL / EN z wyborem przy pierwszym uruchomieniu
  i w Ustawieniach.

- **2026-09-21 — kamera.** Wcześniejsza wersja tego dokumentu zakazywała
  automatycznych ruchów kamery przy klikaniu **państw i regionów**. Zasada
  została zawężona: **kraj przybliża się płynnie** (poziom 3), **region
  i miasto nie ruszają kamery w ogóle** (poziom 4). Powód zakazu pozostaje
  aktualny dla poziomu 4 — mapa szarpiąca się przy każdym tapnięciu była
  najbardziej dezorientującą rzeczą w kaskadzie.
