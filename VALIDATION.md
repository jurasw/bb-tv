# Weryfikacja — 2026-10-15

Środowisko: macOS ARM64, JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, SDK 35 / build-tools 35.0.0. Budowanie wyłącznie z terminala.

## Buildy etapów

| Etap | Sprawdzenie | Wynik |
| --- | --- | --- |
| 1. Szkielet TV, WebView i pilot | `./gradlew assembleDebug` | OK |
| 2. Serwer, PIN, token, komendy | `./gradlew assembleDebug` | OK |
| 3. Telefon, NSD, głos, share, zakładki | `./gradlew assembleDebug` | OK po poprawieniu kolizji pola `socket` |
| 4. Adblock sieciowy i kosmetyczny | `./gradlew assembleDebug` | OK |
| 5. Media i fullscreen | `./gradlew assembleDebug` | OK |
| 6. README i testy | `./gradlew assembleDebug` | OK |

Końcowe wspólne sprawdzenie po wszystkich poprawkach:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain
node scripts/test-browser-js.cjs
```

Gradle: **BUILD SUCCESSFUL**, 30 s. Testy: **11 wykonanych, 0 błędów, 0 pominiętych**. Protokół: 3 testy; TV: 8 testów Robolectric na **API 28**, w tym rzeczywista komunikacja WebSocket przez localhost.

Zakres: serializacja, niedozwolone URL/komendy/wersje/rozmiary, wyszukiwanie i share URL (w tym nawiasy i tekst wielowierszowy), jednorazowy PIN, wygaśnięcie i blokada prób, skróty tokenów i odwołanie dostępu, odrzucanie komend przed autoryzacją i po odwołaniu tokenu, blokada Origin, trwałość/kolejność/escaping zakładek, granice dopasowania domen, ustawienia WebView, zachowanie tej samej WebView i jednokrotne zamknięcie natywnego fullscreen przez Back.

Node: debounce, reguły per domena, licznik bez powtórzeń, wyłączenie kosmetyki, pomijanie reklamy w trybie mediów oraz fallback fullscreen po odrzuceniu natywnego żądania, odtworzenie stylów, brak zmiany pozycji i audio. `browser.js` oraz wyodrębnione FORCE/RESTORE przeszły parser Acorn z `--ecma5`.

Lint: **0 błędów**, 8 ostrzeżeń TV i 29 telefonu. Pozostałe ostrzeżenia dotyczą m.in. wyglądu przycisków pilota, autofill/accessibility touchpada, rozmiaru wektora bannera, wymuszonego landscape, wymaganego JS, propozycji dodania AndroidX WebKit oraz singletonu trzymającego wyłącznie `applicationContext`. Nie dodano ciężkiej biblioteki dla samego odczytu wersji WebView. Wyłączony jest wyłącznie check polityki publikacji `ExpiredTargetSdkVersion` dla prywatnego TV target 28; alternatywa kafelka Intent dla API 24–33 ma lokalne wyciszenie fałszywego alarmu lintu i osłonę wersji. Reguły wyłączające backup/transfer danych oraz zasoby napisów i ikony zostały uwzględnione w końcowym sprawdzeniu.

Przerwane pobrania JDK/SDK i zależności testów zostały wznowione i zweryfikowane sumami kontrolnymi tam, gdzie były ręcznie umieszczane w cache. Przerwane uruchomienia infrastruktury nie są liczone jako zaliczone testy. Końcowe wyniki powyżej pochodzą z zakończonego pełnego polecenia.

## APK

`aapt2 dump badging` potwierdza minSdk **21** / target **28** dla TV, minSdk **24** / target **35** dla telefonu, launcher Leanback i banner aplikacji TV.

| APK | Rozmiar | SHA-256 |
| --- | ---: | --- |
| `tv/build/outputs/apk/debug/tv-debug.apk` | 909 570 B | `b0fbb4acb36f570f37d97b8ba51cac584e920edde0dbad122d3846e62b837e75` |
| `phone/build/outputs/apk/debug/phone-debug.apk` | 946 733 B | `6d0df8bbdc023c031b1566f0759e67d03681f46c54ec32e59eb05dfaccad8ce4` |

Sumy dotyczą lokalnych debug APK; kolejny build na innym komputerze może mieć inne podpisy i sumy.

## Niewykonane na urządzeniu

`adb devices` zwróciło pustą listę. **Nie wykonano instalacji ani testu na Xiaomi, telefonie lub emulatorze.** API 28 sprawdzono przez Robolectric, bez prawdziwego Chromium, GPU i dekoderów Androida. Testy JS sprawdzają logikę, nie renderowanie na starym WebView.

Do sprawdzenia na fizycznym TV pozostają: realny pilot, NSD przez router, sterowanie dotykiem z telefonu, lokalny silnik mowy API 31+, HLS/DASH i kodeki, DRM serwisów, fullscreen konkretnego WebView, pauza w obcych iframe, skuteczność selektorów reklam na aktualnych stronach oraz zużycie RAM przy dłuższym oglądaniu. Instrukcja adb i scenariusze są w README.
