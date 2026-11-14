# Tripail — pomysły

> Krótko. Jedna rzecz = jeden blok. Czytaj tylko nagłówki, jeśli chcesz.

---

## 🎯 Wniosek

- **Nie przepisujemy od zera.** Szkielet jest dobry. Wymieniamy „skórę".
- **Zero złotówek.** Wszystko niżej jest darmowe.
- **Cel:** mapa jak w grze (AC-style) — świat jest zakryty, a ty go „zamalowujesz", idąc.

---

## 1. ⭕ Kółka zamiast sześciokątów

**Problem:** ślad z chodzenia jest zbudowany z plastrów miodu (biblioteka H3). Obrys plastrów = zawsze poszarpany.

**Rozwiązanie:**
- apka zapamiętuje **punkty**, gdzie byłeś (zaokrąglone do ~150 m, prywatność OK)
- każdy punkt = **kółko 1 km**
- spacer = **gładki tunel** z okrągłymi końcami
- tunel **rośnie na żywo za tobą** — jedziesz autem po Włoszech i widzisz, jak za tobą ciągnie się odkryty pas

**Ile roboty:** mało. 2–3 pliki. Miasta już są kółkami.
**Stare dane:** nie przepadają, da się przeliczyć.

---

## 2. 🎨 Odsłanianie jak w grze

Teraz: beżowa folia z wyciętymi dziurami. Wygląda jak Paint.

Jak w grach typu AC — **świat to rysunek na papierze, który ożywa tam, gdzie byłeś:**

- **Zakryte** = stara, ręcznie rysowana mapa: papier, tusz, kontury, plamy
- **Odkryte** = kolorowa, żywa mapa
- **Brzeg** = nie ostra kreska, tylko **atrament rozlewający się po papierze** (postrzępiony, miękki)
- **Odkrywanie** = animacja: tusz rozlewa się od środka na zewnątrz, ~1 sekunda

**Jak technicznie (w skrócie):**
- poziom 1 → rozmyty brzeg (`line-blur`) — 1 wieczór
- poziom 2 → tekstura papieru zamiast koloru (`fill-pattern`)
- poziom 3 → **shader** (mały program na grafice telefonu) — płynące chmury/tusz, animacje. To jest ten „wow".

⚠️ Robimy *własny* styl inspirowany grami, nie kopiujemy grafik z AC.

---

## 3. 🛗 Mapa jak winda (zamiast globusa)

**Twój powód dla menu kontynentów był dobry:** za dużo danych naraz = przebodźcowanie.
**Problem był gdzie indziej:** menu i mapa to dwa różne ekrany → czuć „sklejkę".

**Rozwiązanie: jedna mapa, ale kamera jeździ jak winda — tylko po piętrach.**
- 🏢 piętra: **Świat → Kontynent → Kraj → Region**
- na każdym piętrze widać **tylko to piętro** (na Świecie: 7 kolorowych plam, zero napisów)
- zoom palcem **zablokowany** na danym piętrze, poza ostatnim
- tap = zjazd piętro niżej, płynny przelot
- „Wstecz" = piętro wyżej

**Sztuczka:** mgła sama ukrywa nadmiar. Nieodkryty świat to pusty papier, a nie tysiące nazw. Mało bodźców z założenia.

**Szybki skok do innego kraju** (żeby winda nie była męcząca):
- ↔️ **sąsiedzi widoczni** — na piętrze Kraj sąsiednie kraje są przygaszone, ale klikalne. Tap = przelot w bok, bez jeżdżenia w górę
- 🔍 **lupka** — wpisujesz „Włochy" i lecisz prosto tam, z dowolnego piętra
- 🕘 **ostatnie** — u góry 3 małe przyciski z ostatnio oglądanymi miejscami
- 🤏 **gest** — zsunięcie palców = piętro wyżej

**Przełącznik globus ↔ piętra:** da się, i bez „sklejki", bo to **ta sama mapa**, zmienia się tylko sposób wyświetlania + krótka animacja (kula rozwija się w płaską mapę).
- domyślnie: piętra (spokojnie)
- globus: dla chętnych, w ustawieniach albo jednym guzikiem
- ⚠️ do sprawdzenia: czy wersja MapLibre na Androida ma już globus tak dobry jak wersja w przeglądarce

---

## 4. 🗺️ Darmowa mapa

| Co | Teraz | Zamiana | Koszt |
|---|---|---|---|
| Mapa | MapTiler (darmowy limit, potem płatny) | **OpenFreeMap** | 0 zł, bez klucza, bez limitu |
| Wyszukiwarka | MapTiler | **Photon** / **Nominatim** (OpenStreetMap) | 0 zł |
| Granice | własne pliki w apce | zostaje | 0 zł |

Bonus: na OpenFreeMap sami ustawiamy kolory → mapa w kolorach kontynentów, bajkowy styl.

---

## 5. ✨ Rzeczy, których nie mają inne apki

- **Słoje drzewa** — stare odkrycia lekko wyblakłe, nowe soczyste. Widać historię.
- **Kompas ciekawości** — „1 km stąd jest wioska, której nie odkryłeś".
- **Pocztówka** — odkryłeś region → ładny obrazek do wrzucenia na Insta.
- **Samolot** — odkrywa tylko lotniska + przerywana linia (bo nad chmurami nic nie widać).

---

## 7. 🧩 Podział na regiony i statystyki

**Podział na regiony = dobry pomysł.** Zostaje. Propozycja układu:

| Piętro | Czym jest | Jak się odkrywa |
|---|---|---|
| Kraj | obrys | sumą regionów |
| Region | obrys (województwo itp.) | ręcznie albo chodząc |
| Miasto | kółko | wejście / wyszukanie |
| Ślad | tunel z kółek | chodzenie |

**Wejście do miasta = całe miasto naraz** (jak w grach) ✅
- chodzenie odkrywa mało, więc miasto to główna nagroda
- wjeżdżasz → tusz rozlewa się od miejsca, którym wjechałeś, na całe miasto (~2 s)
- na środku ekranu na chwilę pojawia się nazwa: **„Kraków"** + mały dźwięk
- ⚠️ **obwodnica ≠ wizyta.** Autostrada koło miasta NIE odkrywa miasta. Warunek: musisz być **w środku** — np. w wewnętrznej połowie kółka miasta i przez kilka minut (apka ma już wykrywacz postojów, `DwellDetector`). Sam przejazd skrajem = tylko zwykły tunel.
- regiony: **nie** odkrywają się same, tylko ręcznie albo chodzeniem

**Ikonki miast na mapie:**
- nieodkryte = szara sylwetka (widać, że coś tam jest)
- odkryte = kolorowa ikonka

**Statystyki — mała zmiana, duża różnica:**
- samo „odkryłeś 0,3% Polski" **dołuje** (powierzchnia rośnie strasznie wolno)
- dodać **liczniki-kolekcje**: „Miasta 12/954", „Regiony 5/16", „Kraje 3/44"
- jak w albumie z naklejkami: każda nowa = małe „ding" ✅
- procent powierzchni zostaje, ale jako drugi numerek

---

## 8. 👥 Społeczność (darmowo)

Serwer za 0 zł: **Supabase** albo **Firebase** (darmowe plany wystarczą na start).

Pomysły, od najbardziej nietypowych:

- ❤️ **Moja ulubiona rzecz w mieście** — każdy wpisuje jedną, swoją ulubioną rzecz z danego miasta (knajpa, widok, ławka). Tylko jedną, więc to musi być ta najlepsza. Po odkryciu miasta widzisz, co polecają inni.
- 📜 **List w butelce** *(może, w jakiejś formie)* — wiadomość w miejscu, czytelna dopiero na miejscu.
- 🗝️ **Mapa skarbów od znajomego** — znajomy wysyła ci zakrytą mapę z jedną kropką: „idź tu". Odkrywasz, idąc.
- 👣 **Ślady innych** — delikatna poświata: „tędy chodzi dużo ludzi". Bez imion, bez tras.

⚠️ Prywatność: nikt nigdy nie widzi czyjejś trasy. Tylko zbiorcze liczby.

---

## 9. 🧘 Tryb spokojny (ADHD-friendly)

- jeden przełącznik: **mniej wszystkiego**
- zero powiadomień poza „odkryłeś nowe miasto"
- ekran główny = mapa + jedna liczba
- animacje wolniejsze i miększe
- podsumowanie raz w tygodniu zamiast ciągłych komunikatów

Inne drobne:
- 📔 **Dziennik** — apka sama pisze krótkie podsumowanie miesiąca: „3 nowe miasta, najdalej: Kraków"
- 🌦️ **Pogoda odkrycia** — miejsce pamięta, czy odkryłeś je w deszczu czy w słońcu (ikonka)
- 🌙 **Nocne odkrycia** — odkryte po zmroku mają lekko granatowy odcień

---

## 11. ✈️ Tryb podróży (zamiast ciągłej lokalizacji)

**Problem:** śledzenie w tle cały czas ma mało sensu, kiedy siedzisz w jednym mieście albo jeździsz w kółko tą samą trasą (np. dom ↔ uczelnia). Bateria idzie, a nic nowego się nie odkrywa.

**Rozwiązanie:** duży guzik do włączania podróży (nazwa do wymyślenia, „Ruszam w podróż" to roboczo).
- włączasz → apka śledzi w tle i odkrywa
- wyłączasz → zero śledzenia, zero baterii
- w powiadomieniu na bieżąco: „dziś odkryto: 2 miasta, 34 km"

**Dodatki (opcjonalnie):**
- ⏰ **auto-wyłączenie** po wybranym czasie (np. 7 dni), żeby nie zapomnieć
- 💡 **podpowiedź:** jesteś daleko od zwykłych miejsc? Apka pyta raz: „Wygląda na wyjazd. Włączyć tryb podróży?"
- 📍 **„Odkryj, gdzie jestem"** — jedno kliknięcie, jedno kółko, bez śledzenia (na krótkie wypady)

**To irytujące powiadomienie „apka używa lokalizacji":**
- Android **każe** je pokazywać, gdy apka śledzi w tle. Bez niego system ubija śledzenie po kilku minutach.
- ✅ ale z trybem podróży pojawia się **tylko w podróży**, a nie cały czas
- ✅ można je zrobić ciche, małe i przydatne („dziś: 2 miasta, 34 km")
- „Odkryj, gdzie jestem" nie potrzebuje go wcale

Widget: później, jeśli w ogóle.

---

## 13. 🏛️ Panel „co tu zobaczyć"

**Google Maps:** najlepsze dane, ale wymaga podpięcia karty i po przekroczeniu darmowego limitu płacisz. ❌ Odpada.

**Darmowe zamienniki:**
- **Wikipedia** — „artykuły w pobliżu" (opis + zdjęcie). Za darmo, bez klucza. ⭐ najlepszy start
- **OpenStreetMap** — muzea, punkty widokowe, zabytki
- **OpenTripMap** — gotowe atrakcje z obu powyższych, darmowy klucz

**Żeby nie było za dużo:** max **3 rzeczy** na miasto. Reszta pod „więcej".

---

## 14. 💡 Burza mózgów (do rozwijania)

Rzucone na szybko. Bierz, co cię ruszy.

**Jak w grach:**
- 🗼 **Wyjątkowe punkty widokowe** ✅ — tylko te naprawdę znane (np. mają własny artykuł na Wikipedii), nie każda górka. Na mapie jako ikonka-wieżyczka. Dojdziesz → większe kółko.
- 🛂 **Paszport z pieczątkami** ✅ — przekraczasz granicę → pieczątka z animacją. Statystyki jako paszport zamiast tabelek.
- 📳 **Wibracja na granicy mgły** ✅ — telefon w kieszeni lekko pyka, gdy wchodzisz na nowy teren.

**Jak odkrywasz:**
- 🚶 vs 🚗 **Pieszo czyściej** ✅ — autem mgła tylko się rozrzedza, pieszo znika całkiem. Uczciwe: z auta mniej widzisz. (szczegóły: punkt 15)
- 🧭 **„Co jeszcze w okolicy?"** ✅ — jesteś np. na wakacjach we Włoszech i nie wiesz, gdzie jeszcze pojechać → apka podpowiada: „42 km stąd: Siena". Najlepiej rzeczy, których jeszcze nie odkryłeś. (dane: punkt 13)

**Z innymi:**
- 👀 **Mapa znajomego** *(później)* — podglądasz, jak wygląda mapa kumpla, możesz do niego napisać.
- 📝 **Notatki do miejsc** ✅ — do każdego miasta możesz dopisać „tu było fajne to i to". Ktoś znajomy tam jedzie → wchodzisz w notatkę i jednym guzikiem mu ją wysyłasz.

**Do pokazania:**
- ⏪ **Moja historia** ✅ — w menu suwak czasu (np. ostatnie 3 lata). Przesuwasz → mgła wraca albo znika, tak jak było wtedy. Dodatkowo guzik ▶️, który sam to odtwarza.
- 🖼️ **Plakat** *(tylko jeśli mapa będzie naprawdę ładna)* — eksport mapy do druku.

**Animacja odkrycia miasta:**
- 🎆 nad miastem krótkie fajerwerki / wybuch kolorów + nazwa miasta
- w stylu papieru i tuszu, żeby pasowało do mgły (np. atramentowe fajerwerki)
- 🎵 dźwięk: tak, ale do dobrego przemyślenia — krótki, przyjemny, łatwy do wyłączenia

---

## 14b. 💡 Burza mózgów — runda 2

**Wygląd / klimat:**
- 🎥 **Przelot nad miastem** *(na później)* — po odkryciu miasta kamera przez 3 s przelatuje nisko nad nim, budynki w 3D.
- 🏮 **Tryb nocny z lampionami** ✅ — wieczorem mapa ciemnieje, a nad odkrytymi miastami świeci ciepłe światło.
- 🌧️ **Mgła czuje pogodę** — pada u ciebie naprawdę → na mapie lekki deszcz. (darmowe dane pogodowe: Open-Meteo)
- ❄️ **Pora roku w śladzie** — teren odkryty zimą ma delikatny szron, latem jest cieplejszy w kolorze.

**Ślady:**
- ⛴️ **Każdy transport inaczej** *(tylko automatycznie, nikt nic nie wybiera ręcznie)*
  - pieszo / rower / pojazd → Android sam to rozpoznaje (darmowe „rozpoznawanie aktywności" od Google)
  - samolot → GPS działa w trybie samolotowym (nie potrzebuje internetu), ale w kabinie łapie słabo. Wystarczy wykryć: brak śladu + skok o setki km w kilka godzin = lot → łuk przerywany
  - prom → ruch po wodzie = falista linia
  - pociąg vs auto → nie da się pewnie odróżnić, więc oba wyglądają tak samo
- 🛣️ **Drogi jako trasy** — jedziesz znaną drogą (np. wzdłuż wybrzeża) → podświetla się cała przejechana część.

**Przeszłość bez Google:**
- 📷 **Odkryj ze zdjęć** ✅ — apka (za zgodą) czyta, gdzie były robione zdjęcia w galerii, i odkrywa te miejsca. Nic nie wysyła do internetu.
- 🔄 **„Wczytaj swoją historię"** — przy pierwszym uruchomieniu jeden ekran z wyborem: **z Google** / **ze zdjęć** / **pomiń**.

**Podróże:**
- 🖼️ **Moje zdjęcia w miejscu** *(na później)* — 2–3 ulubione zdjęcia przypięte do miasta, tylko dla siebie.
- 📦 **Kraj offline** — przed wyjazdem pobierasz mapę kraju, żeby działała bez internetu za granicą.
- 🏠 **Dom** — twoje miejsce ma swoją ikonkę, a apka pokazuje „najdalej od domu: 1 840 km".

**Nagroda = wiedza:**
- 💬 **Ciekawostka po odkryciu** — odkrywasz miasto → jedno krótkie, dziwne zdanie o nim (z Wikipedii). Zamiast punktów.

---

## 15. 🍝 Mapa-spaghetti (najważniejszy problem wyglądu)

**Problem:** nawet ktoś, kto zjeździł cały kraj, ma mapę **pociętą cienkimi ścieżkami**. To brzydkie. Świat jest za duży, żeby go „wyczyścić" jak w grze, a nikt nie pojedzie na każdą wieś.

**Rozwiązanie: 3 stopnie mgły zamiast 2**
| Stan | Wygląd | Kiedy |
|---|---|---|
| ☁️ gęsta | papier, nic nie widać | nigdy tu nie byłeś |
| 🌫️ lekka | mapa prześwituje, kolory przygaszone | byłeś **w tej okolicy** |
| ☀️ czysto | pełne kolory | byłeś **dokładnie tu** |

- „okolica" = mały kawałek kraju (pod spodem np. gmina, ale **tego słowa w apce nie ma**)
- przejechałeś przez okolicę → **cała** okolica robi się lekko prześwitująca
- efekt: z daleka Polska wygląda na **ładnie, miękko odkrytą płachtami**, a nie spaghetti
- z bliska widać dokładne tunele

**Plus dwie małe sztuczki:**
- 🕳️ **Łatanie dziur** — mały kawałek mgły otoczony ze wszystkich stron odkrytym terenem → sam się czyści. Znika „sito".
- 🌁 **Miękkie z daleka** — przy oddaleniu ścieżki się rozmywają w chmurkę (jak mapa ciepła), zamiast być cienkimi kreskami.

**Procenty:** liczone po „okolicach", ale w apce bez słów urzędowych typu powiat/gmina.

**Szerokość tunelu (propozycja):**
| Jak | Szerokość | Mgła |
|---|---|---|
| 🚶 pieszo / rower | 2 km | znika całkiem |
| 🚗 auto / pociąg | 3–4 km | tylko rzednie |
| 🏙️ miasto | całe | znika całkiem |

**Bonus: odkryj przeszłość** 🕰️ ✅
- import historii lokalizacji z Google (Oś czasu / Timeline — da się ją wyeksportować z telefonu)
- odpalasz apkę pierwszy raz → od razu widzisz lata swoich podróży, a nie pustą mgłę
- bez Google: ręczne odhaczanie „byłem tu" (już jest przez wyszukiwarkę)

---

## 16. 🍎 iOS (na kiedyś)

- da się bez pisania od nowa: **Kotlin Multiplatform** — logika (`domain`) jest już w czystym Kotlinie, więc przejdzie prawie bez zmian
- MapLibre działa też na iPhonie
- do przerobienia: śledzenie w tle i ekrany
- ⚠️ do budowania na iOS potrzebny jest Mac

---

## 12. 🧪 Testowanie na Windowsie

1. **Prototyp w przeglądarce** ⭐ — robię mapę z mgłą jako stronę, oglądamy tutaj, dopracowujemy, potem przenoszę do apki. Zero instalacji.
2. **Telefon na kablu** — mały zip `platform-tools` (~10 MB), ja wgrywam apkę i robię zrzuty ekranu.
3. **Testy** — `gradlew test`, potrzebna tylko Java 17.

---

## 📋 Kolejność

1. Prototyp w przeglądarce (kółka + odsłanianie w stylu gry + OpenFreeMap)
2. Kółka w apce
3. Nowy wygląd mgły w apce
4. Zamiana na OpenFreeMap + Photon
5. Mapa jak winda (pkt 3)
6. Liczniki-kolekcje (pkt 7)
7. Tryb spokojny (pkt 9)
8. Społeczność (pkt 8) — na końcu, bo potrzebuje serwera
9. Teksty PL / EN, nowy ekran ładowania, nowe logo

> Przy realizacji: Claude Code pyta na bieżąco o decyzje (wygląd, nazwy, kolory), zamiast zgadywać.
