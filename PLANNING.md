# PLANNING.md — Piano di realizzazione tweather

Piano di sviluppo per **tweather**, app meteo Android (Kotlin + Jetpack Compose) con UI in stile code editor (tema "Obsidian Syntax"). Ogni passo è smarcabile: `[ ]` da fare → `[x]` completato.

Riferimenti: `tweather_comprehensive_project_prd_final.md` (requisiti), `obsidian_syntax/DESIGN.md` (design system), mockup HTML/PNG nelle cartelle `*_code_editor`, `search_*`, `settings_*`, `logs_*`, sample dati in `weather_data.json_full_sample.json`.

---

## Fase 0 — Setup progetto

- [x] Inizializzare il repository git (`git init`) e creare `.gitignore` per Android/Kotlin — remote: https://github.com/fiorenzobrioni/tweather.git, licenza GPL-3.0
- [x] Creare progetto Android (scheletro Compose), package/applicationId `com.callbackdev.tweather`
- [x] Configurare Gradle (Kotlin DSL): Gradle 9.1, AGP 8.13, Kotlin 2.2.20, Compose BOM 2025.08, Material 3, minSdk 26, target/compileSdk 36, version catalog `gradle/libs.versions.toml`
- [x] Aggiungere dipendenze base: Retrofit + OkHttp, Kotlinx.serialization (+ converter Retrofit), Navigation Compose, DataStore (Room rinviata alla Fase 3 quando serve lo storico; Hilt da valutare in Fase 3; Coil non necessario, icone = emoji Unicode)
- [x] Importare il font **JetBrains Mono** (pesi 400/500/600/700) in `res/font`
- [x] Configurare versioning e build variants (debug/release); keystore debug condiviso committato in `keystore/debug.keystore` (alias `tweather-debug`, password `android`) così gli APK debug di CI e macchine diverse si aggiornano senza reinstallare
- [x] CI GitHub Actions: `.github/workflows/android-debug-apk.yml` compila l'APK debug a ogni push e lo carica come artifact
- [x] Primo commit: progetto scheletro che compila e mostra una schermata vuota

## Fase 1 — Design system e tema

- [x] Definire la palette Material 3 dal frontmatter di `obsidian_syntax/DESIGN.md` (surface `#10141a`, container `#181c22`, primary `#b5d9ff`, ecc.) in `ui/theme/Color.kt` — oggetto `ObsidianColors` con tutti i token; i ruoli "fixed" restano costanti di riferimento (non esistono nel `ColorScheme` di material3 1.3.x)
- [x] Definire i colori di syntax highlighting come token dedicati: keys `#79c0ff`, strings `#a5d6ff`, numbers/booleans `#ffa657`, comments/braces `#8b949e`, diff add `#2ea043`, diff del `#f85149`, bordi `#30363d` — data class `SyntaxColors` esposta via `TweatherTheme.syntax` (CompositionLocal), un'istanza per profilo tema
- [x] Creare `Typography` con JetBrains Mono (headline-lg 32/24px, body-md 14px, code-block 13px, label-sm 11px, status-bar 12px) in `ui/theme/Type.kt` — anche gli stili Material senza spec esplicita sono rimappati su JetBrains Mono ("no exceptions")
- [x] Creare `Shapes`: raggio 4px per tutti i container; FAB circolare come unica eccezione (`ui/theme/Shape.kt`; il FAB userà `CircleShape` esplicito in Fase 2)
- [x] Implementare il tema scuro "Obsidian Syntax" come default (`ui/theme/Theme.kt`); predisporre la struttura per profili tema multipli (Obsidian, Dracula, Monokai) — enum `ThemeProfile` + `ThemeSpec` (colorScheme + syntax); Dracula/Monokai risolvono su Obsidian finché le palette non arrivano in Fase 7
- [x] Regola "no shadow": profondità solo con bordi 1px e tonal stacking; glow solo per il FAB (`0 0 15px #79c0ff88`) — modifier `editorBorder()`/`editorFocusBorder()`/`fabGlow()` in `ui/theme/Depth.kt`

## Fase 2 — Componenti UI riusabili ("editor kit")

- [x] `CodeCanvas`: container scrollabile con gutter dei numeri di riga a sinistra e contenuto monospaziato allineato — `ui/components/CodeCanvas.kt`, LazyColumn di `CodeLine(text, indent)`, gutter right-aligned con divisore 1px, guide di indentazione, scroll orizzontale sincronizzato per righe lunghe
- [x] `SyntaxText` / renderer JSON: trasforma una struttura dati in `AnnotatedString` con highlighting (chiavi blu, stringhe azzurre, numeri arancio, punteggiatura grigia) e indentazione 20px per livello — `ui/components/JsonSyntax.kt`: `buildJsonLines(JsonElement, SyntaxColors)` + `commentLine()` + `SyntaxText`; oggetti/array piccoli tutti-primitivi resi inline come nei mockup
- [x] `EditorTab` (top bar): mostra il nome del file attivo (es. `terminal tweather.json`) in stile tab di editor — 48dp, glifo `>_`, bordo inferiore 1px, slot `actions`
- [x] `TerminalStatusBar`: barra fissa 28px per metadati secondari (es. "Last Updated: 12:01:04", icona branch per la località) — slot unico a Row + `StatusBarDivider()`; colori surface-container-high di default, override per la variante primary del mockup desktop
- [x] `CodeBlockContainer`: box con bordo 1px `#30363d`, header con filename e icona collapse — header 32dp tappabile con `▾`/`▸`, stato expanded in `rememberSaveable`
- [x] `TreeViewItem`: elemento con simboli `▸`/`▾` (o `+`/`-`) e guide verticali 1px per i figli annidati — `·` per le foglie, guida a 6px + indent figli 20px come da mockup
- [x] `TerminalInput`: input a riga singola stile prompt (`> Search Location _`) con cursore underscore lampeggiante — BasicTextField con caret nativo trasparente, underscore blink 500ms, placeholder in comment-gray
- [x] `CodeCheckbox` / `CodeToggle`: rendering testuale `[x]` / `[ ]` interattivo — `ui/components/CodeControls.kt`, `Modifier.toggleable` con Role.Checkbox/Switch; `CodeToggle` rende `true`/`false` tappabile (stile settings mockup)
- [x] `GlowFab`: FAB circolare con glow, icona stroke 2pt (refresh/"Run") — 56dp, `Modifier.fabGlow()`, nessuna elevation Material; icona Refresh (material-icons-core)
- [x] Anteprime `@Preview` per ogni componente per validarli contro i mockup PNG — una `@Preview` in ogni file componente
- Nota: i mockup HTML usano colori token incoerenti tra loro (es. stringhe verdi nel main, numeri rosa); fanno fede i valori non negoziabili di `CLAUDE.md`/`DESIGN.md` già codificati in `SyntaxColors`

## Fase 3 — Layer dati (dominio e rete)

- [x] Definire i modelli di dominio dal sample `weather_data.json_full_sample.json`: `Location`, `CurrentConditions` (temp, feels like, umidità, vento, precipitazioni, UV), `AirQuality` (AQI + inquinanti PM2.5/PM10/O3/NO2/SO2/CO), `PollenReport`, `Astronomical` (alba/tramonto, fase lunare, durata giorno), `HourlyForecast` (24h), `DailyForecast` (7 giorni), `SystemInfo` (source, last_sync, cache status) — `domain/model/WeatherModels.kt`; tempi come java.time, formattazione a render time; moonrise/moonset omessi (Open-Meteo non li fornisce)
- [x] Integrare **Open-Meteo** come provider (gratuito, nessuna API key): Forecast API (`api.open-meteo.com/v1/forecast`) per condizioni correnti, orarie e giornaliere + dati astronomici (sunrise/sunset); Air Quality API (`air-quality-api.open-meteo.com/v1/air-quality`) per AQI, inquinanti (PM2.5/PM10/O3/NO2/SO2/CO) e pollini (disponibili solo in Europa — gestire l'assenza del dato altrove) — verificato con chiamate reali che `current` accetta anche dew_point/visibility/uv_index; air quality best-effort (il suo fallimento non affonda il report); AQI = scala US (il sample "42 Good" è US AQI); CO convertito µg→mg/m³
- [x] Implementare il client Retrofit + OkHttp con Kotlinx.serialization; DTO separati dai modelli di dominio + mapper — `data/remote/dto/*` + `data/mapper/WeatherReportMapper.kt`; 3 istanze Retrofit (host diversi) su un unico OkHttp; logging BASIC solo in debug
- [x] Implementare la ricerca città con la Geocoding API di Open-Meteo (`geocoding-api.open-meteo.com/v1/search`) per la schermata Search — `searchCities()` nel repository, mappa su `City` di dominio
- [x] Nota dati non forniti da Open-Meteo: fase lunare da calcolare localmente (algoritmo astronomico) o omettere in v1; `source` in `system_info` = `"Open-Meteo API"` — `MoonPhase.at(Instant)`: ciclo sinodico medio da new moon di riferimento (±1 giorno, sufficiente per l'emoji), 6 test su date astronomiche note
- [x] Mappare le condizioni meteo alle emoji Unicode (`☀️`, `🌧️`, `⛅`, `☁️`, `🌙`, fasi lunari `🌔`…) in un'unica utility — `domain/WeatherCodes.kt`: codici WMO→condizione+emoji (varianti day/night), descrizione UV, status AQI, bussola vento 16 punte, livelli pollini
- [x] Repository con cache locale: ultimo dato per città, stato cache HIT/MISS, timestamp `last_sync` per la sezione `system_info` — cache in-memory per `cacheKey` città, TTL 15 min, `forceRefresh` per il FAB; forecast+air quality in parallelo
- [x] Persistenza dello storico aggiornamenti (Room): ogni fetch salvato come "commit" (hash generato, autore `sys@tweather.app`, timestamp, snapshot valori) per la schermata Logs — Room 2.8.4 + KSP; `WeatherHistoryEntry` con hash SHA-1 a 7 char e snapshot JSON flatten (chiavi stabili per il diff di Fase 8); retention 100 entry; `observeLatest` come Flow
- [x] Gestione errori: assenza rete, città non trovata, errore API — con messaggi in stile terminale — sealed `WeatherException` con `terminalMessage` (`net::ERR_INTERNET_DISCONNECTED`, `404: location ... not found`, `http::<code>`, `panic: ...`)
- Decisione: niente Hilt — DI manuale con `data/ServiceLocator.kt` (l'app è piccola); Room aggiunta ora come previsto

## Fase 4 — Schermata principale (`weather_data.json`)

- [ ] `WeatherViewModel`: espone lo stato (loading / dati / errore) e l'azione refresh
- [ ] Serializzare lo stato meteo nella struttura JSON del PRD (ordine sezioni: location, current_conditions, air_quality, pollen_report, astronomical, hourly_forecast, daily_forecast, system_info)
- [ ] Renderizzare il JSON nel `CodeCanvas` con highlighting completo, numeri di riga e emoji inline
- [ ] Top bar con `EditorTab` (`weather_data.json` / `terminal tweather.json`)
- [ ] FAB refresh con glow: al tap ricarica i dati e aggiorna `last_sync`
- [ ] Stato di caricamento in stile terminale (es. commento `// fetching...` o spinner testuale)
- [ ] Confronto pixel-level con il mockup `weather_data.json_code_editor/screen.png`

## Fase 5 — Navigazione e struttura app

- [ ] Bottom navigation con 4 tab: **Explorer** (browser città), **Search**, **Settings**, **Logs**
- [ ] Navigation Compose: grafo con le 4 destinazioni + stato preservato per tab
- [ ] Schermata Explorer: elenco città salvate in stile tree-view/file explorer, selezione città attiva, aggiunta/rimozione
- [ ] Persistere la lista città e la città attiva (DataStore o Room)

## Fase 6 — Ricerca (`search_query.json`)

- [ ] UI come oggetto JSON: la proprietà `"search_term"` è il campo di input (`TerminalInput` integrato nel rendering JSON)
- [ ] Ricerca città con debounce → risultati dal geocoding renderizzati nella struttura JSON
- [ ] Array `recent_searches` mostrato nel JSON; persistenza delle ricerche recenti
- [ ] Selezione risultato → imposta la città attiva e naviga alla schermata principale
- [ ] Confronto con il mockup `search_search_query.json/screen.png`

## Fase 7 — Impostazioni (`settings.config`)

- [ ] UI in stile file `.config`: chiavi/valori con highlighting, booleani interattivi (es. `"severe_weather_alerts": true` come toggle)
- [ ] Impostazioni: unità (°C/°F, km/h–mph), notifiche/allerte meteo, frequenza aggiornamento, tema
- [ ] Selezione tema da `"available_profiles"`: Obsidian, Dracula, Monokai — implementare le palette Dracula e Monokai e lo switch runtime
- [ ] Persistenza con DataStore; le modifiche si riflettono immediatamente nell'app
- [ ] Confronto con il mockup `settings_settings.config/screen.png`

## Fase 8 — Logs / Storico (`weather_history.diff`)

- [ ] UI in formato git diff: header commit (hash, autore `sys@tweather.app`, timestamp relativo "2 hours ago")
- [ ] Calcolo diff tra fetch consecutivi: valori nuovi come `+` (verde `#2ea043`), superati come `-` (rosso `#f85149`)
- [ ] Lista commit scrollabile dal più recente; lettura dallo storico Room
- [ ] Politica di retention dello storico (es. ultimi N commit o ultimi 7 giorni)
- [ ] Confronto con il mockup `logs_weather_history.diff/screen.png`

## Fase 9 — Rifiniture e qualità

- [ ] Test unitari: mapper DTO→dominio, serializzazione JSON per il rendering, generazione diff, utility emoji
- [ ] Test UI (Compose): rendering syntax highlighting, interazione toggle/checkbox testuali, navigazione
- [ ] Verifica accessibilità: contentDescription sui controlli testuali, contrasto, dimensioni touch target
- [ ] Gestione configurazioni: rotazione, split screen, font scale di sistema (il layout monospaziato deve reggere)
- [ ] Performance: lazy rendering del JSON lungo (LazyColumn per righe), evitare ricomposizioni inutili
- [ ] Icona app dal logo brand `{ ☁️ }` (`tweather_brand_logo/screen.png`) + splash screen a tema
- [ ] Revisione finale di tutte le schermate contro i mockup PNG

## Fase 10 — Release

- [ ] Configurare signing config e build release (R8/ProGuard, regole per Retrofit/serialization)
- [ ] Screenshot e testi per lo store
- [ ] Versione 1.0.0, tag git e changelog
- [ ] (Opzionale) CI: build + test su push

---

## Note trasversali

- **Vincoli di design non negoziabili** (vedi `CLAUDE.md` e `DESIGN.md`): solo JetBrains Mono, griglia 4px, indent 20px, niente ombre (solo bordi 1px + glow del FAB), raggio 4px, controlli renderizzati come testo.
- **Ordine consigliato**: le fasi 1–2 sono il fondamento di tutte le schermate; la fase 3 può procedere in parallelo alla 2. Le fasi 4–8 dipendono da 1–3.
- Aggiornare questo file smarcando i passi completati e annotando eventuali deviazioni dal PRD.
