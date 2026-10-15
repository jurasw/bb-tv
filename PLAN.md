# Plan realizacji

1. Szkielet Gradle (`tv`, `phone`, `protocol`), jedna WebView, strona startowa, kursor i pilot TV. Build.
2. NanoHTTPD/WebSocket, NSD, PIN i token, wspólny protokół i komendy. Build.
3. Telefon: NSD/manualne IP, reconnect, touchpad/D-pad, głos, udostępnianie i zakładki. Build.
4. Adblock: HashSet domen, cache/offline, aktualizacja dzienna, CSS/ES5, przełączniki. Build.
5. Media: fullscreen wspólny dla obu pilotów, pauza w tle, lekki tryb mediów. Build.
6. README, testy protokołu i bezpieczeństwa, lint, test API 28, końcowy build.

Priorytet: Android 9 / 1 GB RAM, brak Compose, usług chmurowych i zewnętrznej telemetrii. Jedna WebView bez przeładowania przy zmianie fullscreen. Opcjonalny kafelek otwiera ekran wysyłania, ponieważ Android nie udostępnia URL aktywnej karty innej przeglądarki.

Wszystkie sześć etapów ukończone i zbudowane. Wyniki oraz zakres testów API 28: [VALIDATION.md](VALIDATION.md). Głos działa wyłącznie przez silnik on-device na API 31+; na starszym telefonie alternatywą jest tekst, ponieważ samo `EXTRA_PREFER_OFFLINE` nie gwarantuje lokalnego działania.
