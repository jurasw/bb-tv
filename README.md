# BB TV — prywatna przeglądarka Android TV sterowana telefonem

Kotlin, Gradle multi-module: `tv` (minSdk 21, target 28), `phone` (minSdk 24, target 35), `protocol` (minSdk 21). Jedna WebView, widoki XML, NanoHTTPD + nanohttpd-websocket 2.3.1. Telefon używa Java-WebSocket 1.5.7. Bez konta, backendu chmurowego i telemetrii. Budowanie nie wymaga Android Studio. Komunikacja pilota odbywa się w lokalnej sieci IPv4.

Priorytetem jest Xiaomi Mi TV 4A 43″ Full HD / Android 9 / 1 GB RAM. Emulacja i testy JVM nie potwierdzają wydajności, kodeków ani zgodności serwisów na tym telewizorze. To prywatny projekt do instalacji APK, bez konfiguracji publikacji w Google Play.

## Narzędzia z terminala

Wymagane: JDK **17**, Android SDK Command-line Tools, `platform-tools`, platforma 35 i build-tools 35.0.0. Wrapper pobiera Gradle **8.9** i weryfikuje SHA-256; Android Gradle Plugin 8.7.3, Kotlin 2.0.21.

macOS (Apple Silicon):

```sh
brew install openjdk@17
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Linux: zainstaluj JDK 17 (np. `sudo apt install openjdk-17-jdk unzip`), ustaw `JAVA_HOME` na ten JDK i `ANDROID_HOME` np. na `$HOME/Android/Sdk`.

Pobierz [Command-line Tools ze strony Android SDK](https://developer.android.com/studio#command-tools). Rozpakuj katalog `cmdline-tools` tak, żeby plik `sdkmanager` znalazł się pod `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`, a `lib` obok `bin`. Nie twórz zagnieżdżenia `latest/cmdline-tools/bin`.

```sh
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
# Opcjonalne: API 28 do narzędzi/testów urządzenia
sdkmanager 'platforms;android-28'
```

Na Windows użyj JDK 17, `sdkmanager.bat`, `gradlew.bat` i analogicznych zmiennych środowiskowych. `ANDROID_HOME` wystarcza; alternatywnie wpisz `sdk.dir=/pełna/ścieżka/do/sdk` do nieśledzonego `local.properties`.

W tym workspace narzędzia mogą być w ignorowanym `.tools/`; skrypt `scripts/android-env.sh` wykrywa je automatycznie. Na zwykłym checkout używa ustawionych `JAVA_HOME` i `ANDROID_HOME`. Wymusza IPv4 dla Javy, ponieważ w środowisku budowania połączenia Javy przez IPv6 zawieszały pobieranie SDK i Mavena.

## Budowanie

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

APK:

* `tv/build/outputs/apk/debug/tv-debug.apk`
* `phone/build/outputs/apk/debug/phone-debug.apk`

Pierwszy build wymaga internetu do pobrania SDK/zależności. Kolejne po zapełnieniu cache można uruchamiać z `--offline`. Aplikacje nie zawierają serwera internetowego do sterowania; same odwiedzane strony i pobieranie list adblocka korzystają z internetu.

## Instalacja przez adb

Na TV włącz opcje programistyczne (w ustawieniach informacji o urządzeniu kilkukrotnie wybierz numer kompilacji), potem debugowanie USB/sieciowe. Dostępność debugowania przez TCP zależy od firmware Xiaomi. Zaakceptuj klucz RSA komputera na TV. Dla Androida 9 zwykle używa się `adb connect IP_TV:5555`; na części firmware najpierw potrzebne jest USB i `adb tcpip 5555`. Nie otwieraj portu adb na routerze.

```sh
adb connect 192.168.1.10:5555
adb devices
adb -s 192.168.1.10:5555 install -r tv/build/outputs/apk/debug/tv-debug.apk
adb -s 192.168.1.10:5555 shell am start -n pl.bbtv.tv/.TvActivity

# Telefon przez USB; zastąp SERIAL_TELEFONU wartością z adb devices
adb -s SERIAL_TELEFONU install -r phone/build/outputs/apk/debug/phone-debug.apk
adb -s SERIAL_TELEFONU shell am start -n pl.bbtv.phone/.PhoneActivity
```

Przy lokalnych narzędziach zamiast `adb` użyj `scripts/android-env.sh adb`. Debug APK jest podpisany lokalnym kluczem debug, nie kluczem wydania. Przed instalacją APK z innego komputera może być potrzebne odinstalowanie poprzedniej wersji, co usuwa tokeny/zakładki.

## Parowanie i sterowanie

1. Uruchom BB TV. Na pasku TV są adres IPv4, port **8765** i PIN. PIN działa 2 minuty i jest jednorazowy. Menu / Ustawienia → Opcje → Nowy PIN odnawia parowanie.
2. Telefon i TV podłącz do tej samej sieci bez izolacji klientów. Telefon wykrywa usługę NSD `_tvbrowser._tcp.`. Wybierz **Znajdź**; gdy mDNS jest blokowany, wpisz IP TV ręcznie (opcjonalnie `IP:8765`).
3. Wpisz PIN i wybierz **Sparuj**. Telefon zapisuje token i adres TV. Następne uruchomienia łączą się automatycznie. Telefon sprawdza identyfikator TV przed wysłaniem zapisanego tokenu; inne urządzenie pod tym samym IP wymaga ponownego parowania. **Połącz** używa zapisanego tokenu. Przy odrzuceniu tokenu odnów PIN i sparuj ponownie.
4. **Wyślij na TV** otwiera adres albo wyszukuje tekst. Szablon wyszukiwarki zawiera `{query}` (np. `https://www.google.com/search?q={query}`). Wpisywanie działa do aktywnego pola: najpierw kliknij pole na TV.

Touchpad przesuwa kursor; dotknięcie = klik, przytrzymanie = fullscreen. D-pad na telefonie i pilocie przyspiesza przy przytrzymaniu. Pilot TV: OK = klik, długie OK = fullscreen, Back = wyjście z fullscreen / historia / start, play/pause = media, Menu = ustawienia. W trybie linków D-pad przenosi fokus po widocznych linkach i polach (maksymalnie 300 kandydatów); OK aktywuje element. Nie wszystkie widgety stron wspierają fokus.

Zakładki są przechowywane na TV. Początkowo tylko **YouTube, TVP VOD, Pluto TV**. Na telefonie: Zakładki → dodaj bieżącą stronę TV; wybór pozycji pozwala ją otworzyć, usunąć lub przesunąć w górę/dół. Maksymalnie 100 zakładek. Pusta lista po usunięciu ostatniej zakładki pozostaje pusta.

## Udostępnianie, kafelek i głos

W dowolnej przeglądarce telefonu wybierz **Udostępnij → Wyślij na TV**. Z tekstu wyodrębniany jest pierwszy URL HTTP/HTTPS. Jeżeli token jest zapisany, aplikacja łączy się i wysyła link raz; w przeciwnym razie kończysz parowanie na tym samym ekranie. Nie odczytuje schowka ani historii przeglądarek.

Opcjonalny kafelek Quick Settings **Wyślij na TV** otwiera ekran pilota. Nie zna aktywnej karty innej aplikacji — Android nie udostępnia takiego API. Do wysłania aktualnej strony służy Share Target. Zrezygnowano z mini-przeglądarki telefonu, by ograniczyć pamięć i utrzymać jeden jasny sposób wysyłania.

Głos: `SpeechRecognizer`, domyślnie **pl-PL**, język można zmienić obok mikrofonu. Aplikacja prosi o mikrofon dopiero po wybraniu tej funkcji. **Wymaga Androida 12+ (API 31) i dostępnego silnika on-device**; używa wyłącznie `createOnDeviceSpeechRecognizer`, bez przełączania na silnik chmurowy. Pobierz pakiet języka offline. Na API 24–30 i przy braku silnika lokalnego alternatywą jest wpisanie tekstu. To celowy wybór dla prywatności: [EXTRA_PREFER_OFFLINE](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE) w starszym systemie jest tylko sugestią i nie gwarantuje braku połączeń z serwerem. Rozpoznany tekst trafia na TV jako URL lub wyszukiwanie. Zobacz [API rozpoznawania na urządzeniu](https://developer.android.com/reference/android/speech/SpeechRecognizer#createOnDeviceSpeechRecognizer(android.content.Context)).

## Media i stary WebView

Ustawienia TV pokazują pakiet/wersję WebView (API 26+) i wersję Chrome odczytaną z oryginalnego user agenta. Chrome poniżej **90** powoduje ostrzeżenie: zaktualizuj **Android System WebView ze Sklepu Play** (zależnie od firmware dostawcą może być Chrome). Desktopowy UA zachowuje rzeczywistą wersję silnika. Zmiana UA nie dodaje brakujących funkcji.

JS, DOM storage, hardware acceleration i autoplay są włączone. HTML5 video/audio oraz HLS/DASH działają tylko w zakresie wspieranym przez konkretny WebView, MSE i kodeki urządzenia. Wiele współczesnych stron nie działa już na Chrome 50–80. Nie ma osobnego odtwarzacza ExoPlayer, transkodowania ani emulacji kodeków. Nie zaimplementowano pobierania plików, wysyłania plików, wielu kart, kamery/mikrofonu stron ani otwierania zewnętrznych schematów intent/file.

Fullscreen używa `WebChromeClient.onShowCustomView/onHideCustomView`, wspólnego dla telefonu i pilota. Wymuszenie na pierwszym `<video>` ma awaryjne style CSS, gdy stare WebView odrzuca żądanie fullscreen bez gestu. Element wideo nie jest przenoszony w DOM, WebView nie jest odtwarzana ani przeładowywana; zachowuje to pozycję i dźwięk. Back zawsze wychodzi z trybu aplikacji. Wideo w obcym iframe lub zamkniętym shadow DOM może być niedostępne dla wymuszenia i play/pause. Zmiana układu przez samą stronę może przerwać odtwarzanie.

Przejście aplikacji TV w tło pauzuje media i timery WebView; powrót nie uruchamia mediów automatycznie. Przy presji pamięci w tle zwalniany jest cache RAM WebView. Play/pause steruje pierwszym video/audio. TV pozostaje aktywny podczas wykrytego odtwarzania. Jedna karta, bez animacji, bez Compose, bez ciągłego obserwowania DOM; WebView jest niszczona przy zamknięciu aktywności. Android może zabić proces na sprzęcie z 1 GB RAM; nie obiecujemy zachowania odtwarzania po zabiciu procesu. YouTube i inne ciężkie SPA nadal mogą przekraczać pamięć TV.

**DRM/Widevine w WebView zwykle nie działa w sposób wymagany przez serwisy VOD.** Dostępność CDM, licencji, poziomu zabezpieczeń i dopuszczenie przeglądarki zależą od urządzenia i serwisu. Nazwy domyślnych zakładek nie oznaczają gwarancji odtwarzania. Użyj oficjalnej aplikacji serwisu, jeśli wymaga DRM.

## Adblock

* `shouldInterceptRequest` sprawdza domenę i jej domeny nadrzędne w `HashSet`. Nie blokuje przypadkowych sufiksów typu `notads.example.org` dla reguły `ads.example.org`.
* Mała lista offline jest w `tv/src/main/assets/ad-domains.txt`. Aktualizacja [listy Peter Lowe](https://pgl.yoyo.org/as/) odbywa się najwyżej raz na dobę po udanym pobraniu, na pojedynczym wątku poza UI. `JobScheduler` aktualizuje ją także w tle, gdy system pozwoli i jest sieć. Doze / wyłączony TV mogą opóźnić zadanie. Ostatni poprawny cache jest zachowywany, a nieudane pobranie nie usuwa reguł offline. Limit pliku: 2 MB, limit domen: 50 000.
* Parser przyjmuje hosts, surowe domeny oraz proste reguły `||domena^`. To **nie jest pełny silnik EasyList/uBlock**: reguły ścieżek, wyjątki, skrypty i opcje filtrów nie są interpretowane.
* Kosmetyka w ES5: generyczne selektory reklam i kilka reguł per domena. CSS działa na nowe pasujące elementy automatycznie. Zliczanie i pomijanie reklam mają debounce; skany są ograniczone do pasujących elementów, uruchamiane przy zdarzeniach i kilkukrotnie po załadowaniu. Brak stałego `MutationObserver` i `setInterval`.
* Tryb mediów po zdarzeniu play/playing dokłada selektory reklamowych overlayów i próbuje kliknąć znane przyciski „Pomiń reklamę”. Nie usuwa dowolnych dialogów ani zgód. To heurystyki, selektory serwisów mogą się zmieniać.
* Przełączniki globalny i dla bieżącej domeny są na telefonie i TV. Zmiana kosmetyki działa na bieżącej stronie; już zablokowane żądania odzyskasz po ręcznym odświeżeniu (przełącznik nie przerywa filmu). Wyjątek dotyczy dokładnego hosta, nie całej domeny rejestrowej.
* Licznik sieci jest sumą zablokowanych żądań w bieżącym procesie TV, kosmetyczny liczy pasujące elementy bieżącego dokumentu; nie jest liczbą unikalnych reklam. CSS/iframe/shadow DOM i dynamiczne SPA ograniczają dokładność.

Popupy są odrzucane w `onCreateWindow`. Główna nawigacja bez gestu jest blokowana (API 24+: `hasGesture`, API 21–23: krótkie okno po kliknięciu). Celowo proste i restrykcyjne: może psuć przekierowania HTTP, logowanie, OAuth, płatności i automatyczne przejścia SPA. Link z telefonu / zakładka otwiera się jawnie przez natywny kod. JS nie dostaje `addJavascriptInterface`. Skrypty wstrzykiwane do strony są w ES5 z wykrywaniem funkcji i try/catch.

**Reklam wstrzykniętych po stronie serwera (SSAI), z tej samej domeny co film lub będących częścią strumienia nie da się w pełni zablokować w WebView.** Nie ma obietnicy odtwarzania VOD bez reklam. Jeśli filtr psuje stronę, wyłącz go dla niej i odśwież.

## Protokół i bezpieczeństwo LAN

TV nasłuchuje na porcie 8765 tylko podczas otwartej aplikacji. `GET /health` ujawnia jedynie nazwę usługi, nie stronę ani PIN. WebSocket: `/ws`. Wszystkie ramki JSON mają `v:1` i `type`. Przykłady:

```json
{"v":1,"type":"pair","pin":"123456"}
{"v":1,"type":"auth","token":"64-znakowy-token-hex"}
{"v":1,"type":"command","name":"open","data":{"url":"https://example.org/"}}
{"v":1,"type":"command","name":"move","data":{"dx":16,"dy":-8}}
```

`pair` zwraca `paired` z losowym 256-bitowym tokenem; `auth` zwraca `authorized`. Następnie dostępne są: `open`, `search`, `back`, `forward`, `reload`, `home`, `scroll`, `move`, `click`, `type`, `volume`, `media`, `fullscreen`, `link_mode`, `bookmark_add`, `bookmark_delete`, `bookmark_move`, `adblock`, `config`, `state`. `state` zawiera URL, tytuł, zakładki, wersję WebView i liczniki. Nazwy i limity w module `protocol`.

Token jest w prywatnych preferencjach telefonu, na TV tylko skrót SHA-256; backup Androida jest wyłączony. PIN jednorazowy, 2 minuty, po 5 błędach minuta blokady. Ustawienia TV pozwalają odwołać wszystkie tokeny. Do 8 zapisanych tokenów; parowanie dziewiątego telefonu unieważnia starsze. Do 4 równoległych połączeń. Brak autoryzacji = odrzucenie komendy. Wiadomości sprawdzane po dekodowaniu: do 16 KB, do 80/s na połączenie, dozwolone komendy i zakresy liczb. Odrzucane są nagłówki `Origin`, więc natywna aplikacja telefonu łączy się, a strony WWW nie dostają dostępu do pilota.

HTTP/WS w LAN **nie szyfruje** PIN-u, tokenu ani komend. Używaj zaufanej domowej sieci. Nie przekierowuj 8765 na internet, nie wystawiaj usługi przez Cloudflare Tunnel. Mały NanoHTTPD nie stanowi ochrony przed atakiem DoS; limit JSON jest sprawdzany po odebraniu ramki, nie zapobiega alokacji dużej ramki przez bibliotekę. Prywatny projekt nie ma certyfikatów, kont użytkowników ani zdalnego dostępu. Hosting i domena Cloudflare nie są potrzebne do działania aplikacji.

Reconnect telefonu ma opóźnienie 1–15 s i heartbeat WebSocket. Po utracie połączenia nie odtwarza kolejki gestów ani kliknięć. TV musi być otwarty; zamknięcie/tło wyłącza serwer. NSD jest wygodą, ręczne IP pozostaje podstawową alternatywą. Zmiana adresu DHCP może wymagać ponownego wyboru TV/parowania; warto przypisać TV stały adres w routerze.

## Testowanie

### Test w Android Studio

Otwórz katalog repozytorium w Android Studio. Użyj SDK wskazanego przez `local.properties` i **JDK 17** jako Gradle JDK. Na tym Macu ustawione jest `GRADLE_LOCAL_JAVA_HOME`, które odczytuje `java.home` z ignorowanego `.gradle/config.properties`; nowy JBR 25 w Studio nie zastępuje JDK 17 projektu. Zobacz [konfigurację JDK w Studio](https://developer.android.com/build/jdks).

Do szybkiego testu przygotowano dwa AVD: `BB_TV_API30` i `BB_Phone_API30`. Korzystają z tego samego obrazu AOSP Android 11 / ARM64, ale mają osobne dane i procesy. TV ma ekran 1920×1080 w poziomie; telefon jest w pionie. **To test aplikacji TV na zwykłym Androidzie, bez systemowego launchera Android TV. Nie emuluje firmware Xiaomi ani Androida 9.** Emulator 37.2.12 podnosi żądane 1024 MB RAM do 2048 MB, więc ten test nie potwierdza działania na sprzęcie z 1 GB RAM. Pełny obraz Android TV na Apple Silicon wymaga większego pobrania i nowszego API.

Po utworzeniu tych AVD można uruchomić oba okna i wgrać aplikacje jednym poleceniem (wymagany Python 3):

```sh
./scripts/run-demo.sh
```

Skrypt buduje APK, uruchamia emulatory, instaluje aplikacje i tworzy przekierowanie do serwera TV. Na ekranie telefonu wpisz **`10.0.2.2:18765`**, PIN z okna TV i wybierz **Sparuj**. Dane AVD i sparowanie są zachowywane. Okna emulatorów pozostają otwarte po zakończeniu skryptu. W Android Studio znajdziesz urządzenia w **Device Manager**. Moduł `phone` można uruchamiać przyciskiem Run; dla `tv` użyj skryptu, jeśli Studio odrzuca profil AOSP z powodu braku systemowej funkcji Leanback.

Adres `10.0.2.2` prowadzi do komputera hosta, a `adb forward` łączy port 18765 hosta z 8765 emulatora TV. Jest to lokalny fallback, który nie wymaga działania NSD pomiędzy emulatorami. Zobacz [sieć emulatora](https://developer.android.com/studio/run/emulator-networking-address).

Pierwsza konfiguracja emulatorów na nowym komputerze:

```sh
scripts/android-env.sh sdkmanager 'emulator' 'system-images;android-30;default;arm64-v8a'
scripts/android-env.sh avdmanager create avd -n BB_TV_API30 -k 'system-images;android-30;default;arm64-v8a' -d pixel_5
scripts/android-env.sh avdmanager create avd -n BB_Phone_API30 -k 'system-images;android-30;default;arm64-v8a' -d pixel_5
```

Dla TV ustaw w Device Manager rozmiar wyświetlacza 1920×1080, density 160 i orientację poziomą (albo odpowiednie `hw.lcd.width`, `hw.lcd.height`, `hw.lcd.density`, `hw.initialOrientation` w jego `config.ini`). Na hoście Intel/AMD wybierz obraz `x86_64`. Istniejący stary AVD Pixel użytkownika nie jest zmieniany. Jeśli porty 5554/5556 są zajęte, skrypt przyjmuje `BBTV_TV_PORT` i `BBTV_PHONE_PORT` z wolnymi parzystymi portami.

Opcjonalna strona testowa bez dostępu do internetu:

```sh
python3 -m http.server 18766 --bind 127.0.0.1 --directory tests/browser-fixture
```

Wyślij z pilota telefonu URL **`http://10.0.2.2:18766/index.html`**. Strona ma pole tekstowe, link do testowania historii, baner do sprawdzenia adblocka i własny 6-sekundowy film H.264/AAC, domyślnie wyciszony. Film jest zasobem testowym poza APK. Głos na emulatorze API 30 korzysta z alternatywy tekstowej; lokalne rozpoznawanie głosu sprawdzaj na telefonie API 31+ z silnikiem on-device.

### Testy automatyczne

```sh
./gradlew assembleDebug
./gradlew :protocol:testDebugUnitTest :tv:testDebugUnitTest lintDebug
```

Testy TV używają Robolectric z `@Config(sdk = [28])`. Sprawdzają inicjalizację WebView na API 28, PIN/limity/tokeny, rzeczywiste lokalne połączenie WebSocket, odrzucenie komend bez autoryzacji i po odwołaniu tokenu, zakładki oraz granice domen. Robolectric to test środowiska Androida na JVM, nie test silnika Chromium ani fizycznego telewizora. Zależności testowe nie trafiają do APK.

Opcjonalne testy skryptów bez dodatkowych paczek: `node scripts/test-browser-js.cjs`. Sprawdzają debounce, kosmetykę, pomijanie reklamy i przywracanie stylów fullscreen; nie zastępują testu renderowania w WebView. Pierwszy test Robolectric pobiera duże pliki Androida do cache Mavena, także zasoby SDK kompilacji; następne uruchomienia korzystają z cache.

Test urządzenia/emulatora API 28 (CLI):

```sh
sdkmanager 'emulator' 'system-images;android-28;default;arm64-v8a'
avdmanager create avd -n bbtv28 -k 'system-images;android-28;default;arm64-v8a'
emulator -avd bbtv28 -memory 1024 -no-snapshot
# Na hostach Intel/AMD wybierz zamiast arm64-v8a obraz x86_64.
adb -s emulator-5554 install -r tv/build/outputs/apk/debug/tv-debug.apk
adb -s emulator-5554 shell am start -n pl.bbtv.tv/.TvActivity
```

Na Apple Silicon starszy obraz API 28 może nie uruchamiać się z konkretną wersją emulatora. Wtedy testuj na fizycznym Androidzie 9 albo na hoście obsługującym obraz x86_64. Emulator nie odwzorowuje kodeków, pilota i pamięci firmware Xiaomi.

Do ręcznej weryfikacji na TV: parowanie poprawnym/błędnym/wygasłym PIN-em; reconnect po wyłączeniu Wi-Fi; odwołanie tokenów; touchpad i długi OK; linki i formularze; fullscreen i Back z obu pilotów; pozycja/dźwięk przed i po fullscreen; pauza po Home systemowym; dodanie/usunięcie/kolejność zakładek po restarcie; przełączniki adblocka; aktualizacja list przy braku sieci; ostrzeżenie na starym WebView; 20–30 min oglądania przy 1 GB RAM. Wyniki wykonanych sprawdzeń są w `VALIDATION.md`.

```sh
adb -s IP_TV:5555 shell dumpsys meminfo pl.bbtv.tv
adb -s IP_TV:5555 logcat -s AndroidRuntime chromium
```

## Decyzje i licencje zależności

Zwykłe `Activity` i XML zmniejszają zależności. NanoHTTPD zamiast Ktor, Java-WebSocket zamiast całego klienta HTTP, jedna karta zamiast menedżera kart, `SharedPreferences` zamiast bazy, `JobScheduler` zamiast WorkManager, skrypty i selektory zamiast ciężkiego silnika filtrów. TV targetSdk 28 utrzymuje zachowanie starego urządzenia; ten profil nie jest przygotowany do obecnych wymagań publikacji w Google Play. Telefon targetuje 35 i obsługuje nowszy sposób otwierania aktywności z kafelka (API 34+) oraz insets (API 30+) z alternatywami dla starszych systemów.

Biblioteki: [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) — BSD-3-Clause; [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) — MIT; [Kotlin](https://github.com/JetBrains/kotlin) — Apache-2.0. Lista online: [Peter Lowe / warunki użycia](https://pgl.yoyo.org/as/). Mała lista offline i selektory są utrzymywane w tym repo. Projekt nie pobiera pełnej EasyList ani nie dołącza jej treści.
