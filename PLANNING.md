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

- [x] `WeatherViewModel`: espone lo stato (loading / dati / errore) e l'azione refresh — `ui/weather/WeatherViewModel.kt`, `StateFlow<WeatherUiState>`; il report resta a schermo se un refresh fallisce (errore mostrato sopra al JSON); città di default = New York (sample PRD) finché la Fase 5 non rende la città selezionabile; factory via `ServiceLocator`
- [x] Serializzare lo stato meteo nella struttura JSON del PRD (ordine sezioni: location, current_conditions, air_quality, pollen_report, astronomical, hourly_forecast, daily_forecast, system_info) — `ui/weather/WeatherJson.kt` `toDisplayJson()`: formati come il full sample (local_time `yyyy-MM-dd HH:mm`, orari `HH:mm`, daylight `10h 52m`, `last_sync` epoch string, numeri a 1 decimale); air_quality/pollen assenti → `null`; test in `WeatherJsonTest`
- [x] Renderizzare il JSON nel `CodeCanvas` con highlighting completo, numeri di riga e emoji inline — `ui/weather/WeatherScreen.kt`; ritocco a `JsonSyntax`: oggetti dentro array inline fino a 4 campi così le righe orarie stanno su una riga come nel sample
- [x] Top bar con `EditorTab` (`weather_data.json`) + `TerminalStatusBar` in basso (`⎇ città | api: 200 OK … Last Updated: HH:mm:ss`)
- [x] FAB refresh con glow: al tap ricarica i dati (bypass cache) e aggiorna `last_sync`; l'icona ruota durante il fetch
- [x] Stato di caricamento in stile terminale — commenti `// fetching weather_data.json …` / `// GET https://api.open-meteo.com/v1/forecast`; errori come `// ERROR: <terminalMessage>` + hint retry
- [x] Confronto con il mockup `weather_data.json_code_editor/screen.png` (ripristinato dopo un download fallito) — coerente al netto delle deviazioni già decise: colori dai token di CLAUDE.md (non quelli incoerenti del mockup), FAB circolare col glow (nel mockup è un quadrato arrotondato verde), gutter numeri di riga sempre visibile (nel mockup mobile è nascosto ma è requisito core del PRD), struttura JSON dal full sample e non dal JSON semplificato del mockup

## Fase 5 — Navigazione e struttura app

- [x] Bottom navigation con 4 tab: **Explorer** (browser città), **Search**, **Settings**, **Logs** — `ui/components/EditorNavBar.kt`: 56dp flat su surface-container-low, bordo top 1px, item attivo primary con indicatore 2px; icone del mockup (account_tree/search/code/terminal) via `material-icons-extended` (pinned 1.7.8, fuori BOM; R8 elimina il resto in release)
- [x] Navigation Compose: grafo con le 4 destinazioni + stato preservato per tab — `ui/navigation/TweatherApp.kt`: tab Explorer = grafo annidato con l'editor (`weather_data.json`) come start e il browser città un livello sotto (aperto da `[ files ]` nella top bar dell'editor, come da mockup dove l'editor vive sotto il tab Explorer); switch tab con `saveState`/`restoreState`; Search/Settings/Logs per ora `PlaceholderScreen` (finto file con `// TODO: module not yet compiled`, arrivano nelle Fasi 6–8)
- [x] Schermata Explorer: elenco città salvate in stile tree-view/file explorer, selezione città attiva, aggiunta/rimozione — `ui/explorer/ExplorerScreen.kt`: `TreeViewItem` radice `~/tweather/cities/`, città come foglie `milan.json` (attiva in primary + `// active`), rimozione col controllo testuale `[rm]` (l'ultima città non è rimovibile), `+ add_city…` naviga al tab Search (funzionale dalla Fase 6); tap città → attiva e torna all'editor
- [x] Persistere la lista città e la città attiva (DataStore o Room) — DataStore preferences in `data/CityStore.kt` (lista come JSON array — `City`/`Coordinates` ora `@Serializable` — attiva per id; seed New York al primo avvio); `WeatherViewModel` osserva `activeCity` e ricarica al cambio

## Fase 6 — Ricerca (`search_query.json`)

- [x] UI come oggetto JSON: la proprietà `"search_term"` è il campo di input (`TerminalInput` integrato nel rendering JSON) — `ui/search/SearchScreen.kt`; `CodeCanvas` esteso con `CanvasLine` sealed (`CodeLine` ora anche cliccabile con `onClick`, `WidgetLine` per righe composable): l'input vive tra le virgolette JSON con cursore `_` prima della quote di chiusura
- [x] Ricerca città con debounce → risultati dal geocoding renderizzati nella struttura JSON — `SearchViewModel`: debounce 400ms da 2 caratteri, IME Search per bypass; array `"results"` con oggetti inline tappabili `{ "city": …, "region": …, "country": … }`; ricerca in corso = `// GET /v1/search?name=…`, errori come `// ERROR: <terminalMessage>`
- [x] Array `recent_searches` mostrato nel JSON; persistenza delle ricerche recenti — `data/SearchHistoryStore.kt` (DataStore, JSON array, max 5, dedup case-insensitive, più recente in testa); tap su una recente ne rilancia la ricerca
- [x] Selezione risultato → imposta la città attiva e naviga alla schermata principale — `cityStore.add()` (aggiunge alla lista Explorer e attiva) + salvataggio in recenti + `navigateToTab(Explorer)`
- [x] Confronto con il mockup `search_search_query.json/screen.png` — fedele per struttura (search_term/recent_searches, numeri di riga, colori da CLAUDE.md); il blocco statico `"filters"` del mockup (radius_km 50, data_source "NOAA") è volutamente omesso: decorativo e riferito a feature/provider inesistenti

## Fase 6b — Interventi da feedback (spazio orizzontale, scroll, i18n)

- [x] Numeri di riga come impostazione, default **off** su mobile (il mockup mobile del main li nasconde; deciso col committente per recuperare spazio orizzontale) — `EditorOptions` + `LocalEditorOptions` forniti dalla shell, toggle `"line_numbers"` in `settings.config`; le guide di indentazione restano sempre; senza gutter il contenuto parte al margine 16px
- [x] Fix scroll orizzontale: le righe condividevano uno `ScrollState` ma ognuna scriveva il proprio `maxValue` — le righe corte lo clampavano a 0 (scroll bloccato, funzionante solo sulle righe lunghe, reset al passaggio di riciclo della LazyColumn). Ora ogni riga ha la stessa larghezza misurata sul monospace (`TextMeasurer` sulla riga più lunga), quindi range di scroll unico e coerente, gesto ovunque, gutter pinnato
- [x] Word wrap con hanging indent (20sp sulle righe di continuazione, stile `editor.wordWrap`) — toggle `"word_wrap"` in `settings.config`, default off come VS Code
- [x] `data/SettingsStore.kt` (DataStore) + schermata `settings.config` reale con la sola sezione `"editor"` (formato del mockup: JSON con commenti `//`, booleani `CodeToggle` tappabili); units/theme/notifications arrivano con la Fase 7
- [x] Localizzazione IT/EN: default `values/` = inglese (fallback automatico per lingue non supportate), `values-it/`; regola concordata: il "codice" resta inglese (chiavi JSON, nomi file, errori terminale, commenti `//`), localizzate chrome (nav, status bar, placeholder), accessibilità e i **valori** meteo (condizioni WMO, UV, AQI, pollini, fasi lunari — mappa `WeatherTranslations` inglese→risorsa; dominio e snapshot Room restano inglesi così i diff non cambiano con la lingua) + nomi giorni dal locale; `android:localeConfig` per la scelta lingua per-app da Android 13

## Fase 7 — Impostazioni (`settings.config`)

- [ ] UI in stile file `.config`: chiavi/valori con highlighting, booleani interattivi (es. `"severe_weather_alerts": true` come toggle) — base già in piedi dalla Fase 6b (`SettingsScreen` + `SettingsStore` + sezione `"editor"`); da estendere con le sezioni restanti
- [ ] Impostazioni: unità (°C/°F, km/h–mph), notifiche/allerte meteo, frequenza aggiornamento, tema
- [ ] Toggle `"show_details"` (default **false**, deciso col committente): nasconde dal `weather_data.json` i campi di dettaglio — `region`, `country`, `coordinates`, `timezone` (con `location` che collassa nella stringa compatta `"New York, NY"` come nel mockup), `dew_point_c`, `wind.degree`, `wind.gust_kph`; candidati extra da valutare: `pressure_mb`, `visibility_km`, `air_quality.pollutants` (tenendo visibili `aqi_index`/`status`)
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
