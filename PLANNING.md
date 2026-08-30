# PLANNING.md — Piano di realizzazione tweather

Piano di sviluppo per **tweather**, app meteo Android (Kotlin + Jetpack Compose) con UI in stile code editor (tema "Obsidian Syntax"). Ogni passo è smarcabile: `[ ]` da fare → `[x]` completato.

Riferimenti: `tweather_comprehensive_project_prd_final.md` (requisiti), `obsidian_syntax/DESIGN.md` (design system), mockup HTML/PNG nelle cartelle `*_code_editor`, `search_*`, `settings_*`, `logs_*`, sample dati in `weather_data.json_full_sample.json`.

---

## Fase 0 — Setup progetto

- [x] Inizializzare il repository git (`git init`) e creare `.gitignore` per Android/Kotlin — remote: https://github.com/fiorenzobrioni/tweather.git, licenza GPL-3.0
- [x] Creare progetto Android (scheletro Compose), package/applicationId `com.callbackdev.tweather`
- [x] Configurare Gradle (Kotlin DSL): Gradle 9.1, AGP 8.13, Kotlin 2.2.20, Compose BOM 2025.08, Material 3, minSdk 26 (→ **33** da fine Fase 9), target/compileSdk 36, version catalog `gradle/libs.versions.toml`
- [x] Aggiungere dipendenze base: Retrofit + OkHttp, Kotlinx.serialization (+ converter Retrofit), Navigation Compose, DataStore (Room rinviata alla Fase 3 quando serve lo storico; Hilt da valutare in Fase 3; Coil non necessario, icone = emoji Unicode)
- [x] Importare il font **JetBrains Mono** (pesi 400/500/600/700) in `res/font`
- [x] Configurare versioning e build variants (debug/release); keystore debug condiviso committato in `keystore/debug.keystore` (alias `tweather-debug`, password `android`) così gli APK debug di CI e macchine diverse si aggiornano senza reinstallare
- [x] CI GitHub Actions: `.github/workflows/android-debug-apk.yml` compila l'APK debug a ogni push e lo carica come artifact — *da ago 2026 sostituito da `android-ci.yml`, vedi Note trasversali*
- [x] Primo commit: progetto scheletro che compila e mostra una schermata vuota

## Fase 1 — Design system e tema

- [x] Definire la palette Material 3 dal frontmatter di `obsidian_syntax/DESIGN.md` (surface `#10141a`, container `#181c22`, primary `#b5d9ff`, ecc.) in `ui/theme/Color.kt` — oggetto `ObsidianColors` con tutti i token; i ruoli "fixed" restano costanti di riferimento (non esistono nel `ColorScheme` di material3 1.3.x)
- [x] Definire i colori di syntax highlighting come token dedicati: keys `#79c0ff`, strings `#a5d6ff`, numbers/booleans `#ffa657`, comments/braces `#8b949e`, diff add `#2ea043`, diff del `#f85149`, bordi `#30363d` — data class `SyntaxColors` esposta via `TweatherTheme.syntax` (CompositionLocal), un'istanza per profilo tema
- [x] Creare `Typography` con JetBrains Mono (headline-lg 32/24px, body-md 14px, code-block 13px, label-sm 11px, status-bar 12px) in `ui/theme/Type.kt` — anche gli stili Material senza spec esplicita sono rimappati su JetBrains Mono ("no exceptions")
- [x] Creare `Shapes`: raggio 4px per tutti i container; FAB circolare come unica eccezione (`ui/theme/Shape.kt`; il FAB userà `CircleShape` esplicito in Fase 2) — *da ago 2026 il FAB è rettangolare come tutto il resto (review di coerenza pre-release): nessuna eccezione di forma, a distinguerlo resta solo il glow*
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
- [x] `CodeCheckbox` / `CodeToggle`: rendering testuale `[x]` / `[ ]` interattivo — `ui/components/CodeControls.kt`, `Modifier.toggleable` con Role.Checkbox/Switch; `CodeToggle` rende `true`/`false` tappabile (stile settings mockup) — *da ago 2026 rimosso: nessuna schermata l'ha mai usato (i booleani di settings sono righe `true`/`false` tappabili via `boolLine`)*
- [x] `GlowFab`: FAB circolare con glow, icona stroke 2pt (refresh/"Run") — 56dp, `Modifier.fabGlow()`, nessuna elevation Material; icona Refresh (material-icons-core) — *da ago 2026 rettangolare, radius 4px come tutto il resto; glow reso con BlurMaskFilter attorno al rounded-rect*
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
- [x] Confronto con il mockup `weather_data.json_code_editor/screen.png` (ripristinato dopo un download fallito) — coerente al netto delle deviazioni già decise: colori dai token di CLAUDE.md (non quelli incoerenti del mockup), FAB circolare col glow (nel mockup è un quadrato arrotondato verde) — *da ago 2026 il FAB è tornato rettangolare: della deviazione resta solo il glow primary al posto del verde del mockup* —, gutter numeri di riga sempre visibile (nel mockup mobile è nascosto ma è requisito core del PRD), struttura JSON dal full sample e non dal JSON semplificato del mockup

## Fase 5 — Navigazione e struttura app

- [x] Bottom navigation con 4 tab: **Explorer** (browser città), **Search**, **Settings**, **Logs** — `ui/components/EditorNavBar.kt`: 56dp flat su surface-container-low, bordo top 1px, item attivo primary con indicatore 2px; icone del mockup (account_tree/search/code/terminal) via `material-icons-extended` (pinned 1.7.8, fuori BOM; R8 elimina il resto in release)
- [x] Navigation Compose: grafo con le 4 destinazioni + stato preservato per tab — `ui/navigation/TweatherApp.kt`: tab Explorer = grafo annidato con l'editor (`weather_data.json`) come start e il browser città un livello sotto (aperto da `[ files ]` nella top bar dell'editor, come da mockup dove l'editor vive sotto il tab Explorer); switch tab con `saveState`/`restoreState`; Search/Settings/Logs per ora `PlaceholderScreen` (finto file con `// TODO: module not yet compiled`, arrivano nelle Fasi 6–8)
- [x] Schermata Explorer: elenco città salvate in stile tree-view/file explorer, selezione città attiva, aggiunta/rimozione — `ui/explorer/ExplorerScreen.kt`: `TreeViewItem` radice `~/tweather/cities/`, città come foglie `milan.json` (attiva in primary + `// active`), rimozione col controllo testuale `[rm]` (l'ultima città non è rimovibile), `+ add_city…` naviga al tab Search (funzionale dalla Fase 6); tap città → attiva e torna all'editor
- [x] Persistere la lista città e la città attiva (DataStore o Room) — DataStore preferences in `data/CityStore.kt` (lista come JSON array — `City`/`Coordinates` ora `@Serializable` — attiva per id; seed New York al primo avvio, → **Milan** da post-9b: città di sviluppo dell'app, deviazione dal sample PRD decisa col committente); `WeatherViewModel` osserva `activeCity` e ricarica al cambio

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
- [x] Rifiniture da review (post Fase 8): commento header `// Tweather Search Query` anche in `search_query.json` (Explorer invariato: è un pannello, non un file); cursore dell'input di ricerca — l'underscore fisso a fine testo nascondeva la posizione reale di editing, ora caret nativo color accent durante la digitazione e underscore lampeggiante solo a campo vuoto (deviazione motivata dalla spec "blinking underscore cursor"); i toggle booleani di `settings.config` da `WidgetLine` (Row, non wrappabile) a `CodeLine` tappabili sull'intera riga → il word wrap funziona anche sulle sezioni `editor`/`data` e il touch target cresce

## Fase 7 — Impostazioni (`settings.config`)

- [x] **Bugfix trovato durante la fase**: `air_quality` e `pollen_report` risultavano sempre `null` nel JSON — non era la rete (verificata: US AQI 66 per NY) ma `?.let { putJsonObject(…) } ?: put(key, JsonNull)` in `WeatherJson`: i `put*` del builder kotlinx ritornano il valore *precedente* della chiave (null), quindi l'elvis sovrascriveva sempre la sezione appena scritta; test di regressione aggiunto
- [x] UI in stile file `.config`: chiavi/valori con highlighting, booleani interattivi — `SettingsScreen` completa nel formato del mockup (JSON + commenti `//`): sezioni `editor`, `data`, `units`, `theme`, `notifications`, `sync`; booleani `CodeToggle`, valori stringa/numero che commutano o ciclano al tap
- [x] Impostazioni: unità (°C/°F, km/h–mph — cambiano valori E chiavi: `temp_f`, `speed_mph`, un file JSON non mente sulle unità), notifiche (3 preferenze persistite; il motore di allerta arriverà più avanti, annotato nel file stesso), frequenza aggiornamento (`update_frequency_min` 15/30/60 → TTL della cache del repository), tema
- [x] Toggle `"show_details"` (default **false**): nasconde `region`/`country`/`coordinates`/`timezone` (location tiene solo `city` + `local_time` — deviazione dalla stringa compatta: `local_time` non era tra i campi da nascondere), `dew_point`, `wind.degree`, `wind.gust` + gli extra accettati `pressure_mb`, `visibility_km`, `pollutants` (restano `aqi_index`/`status`)
- [x] Selezione tema da `"available_profiles"`: Obsidian, Dracula, Monokai — palette complete (ColorScheme + SyntaxColors da spec ufficiali: Dracula keys cyan/strings yellow/numbers purple, Monokai keys green/strings yellow/numbers purple), switch runtime da `active_profile` (cicla al tap) o tap diretto sul profilo nell'array (marcato `// active`)
- [x] Persistenza con DataStore (`SettingsStore` esteso); le modifiche si riflettono immediatamente (tema via `MainActivity`, editor via `LocalEditorOptions`, unità/dettagli via `WeatherViewModel.displayOptions`)
- [x] Confronto con il mockup `settings_settings.config/screen.png` — struttura e formato fedeli; deviazioni: niente sezione `security`/`api_key` (Open-Meteo non usa chiavi), niente unità `precipitation` (fuori scope PLANNING), valori unità espliciti (`"celsius"`/`"fahrenheit"` invece di `"metric"`), in più le sezioni `editor`/`data`/`sync`
- Decisione (post Fase 9b, valutata col committente): `update_frequency_min` resta default **15** con valori 15/30/60 — oggi è solo il TTL della cache foreground (nessun lavoro in background: la fetch parte solo ad app aperta), quindi zero impatto batteria e 15 min = freschezza percepita giusta. **Rivalutare default 60 + opzione 120** quando arriverà il motore di allerta in background, che trasformerà il setting in un vero intervallo di polling *(→ applicata in Fase 9c: valori 15/30/60/120, default 60)*

## Fase 8 — Logs / Storico (`weather_history.diff`)

- [x] UI in formato git diff: header commit (hash, autore `sys@tweather.app`, timestamp relativo "2 hours ago") — `ui/logs/LogsScreen.kt`: `commit <hash> [città]` + `Author:` + `Date:` + `diff --git a/weather_data.json b/weather_data.json`; primo fetch di una città = "new file mode 100644" con tutte righe `+`; l'output git è codice → resta inglese
- [x] Calcolo diff tra fetch consecutivi: valori nuovi come `+` (verde), superati come `-` (rosso, prima del `+` come in git), chiavi invariate come context colorate JSON — `data/local/SnapshotDiff.kt` (funzione pura, 4 test); righe ± con tinta di sfondo al 12% come nel mockup; il "precedente" è il fetch più recente della **stessa città** (lo storico interleaved di più città non si mescola)
- [x] Lista commit scrollabile dal più recente; lettura dallo storico Room — `LogsViewModel` su `repository.observeHistory()` (Flow reattivo: un refresh aggiunge il commit in cima in tempo reale), rendering nel `CodeCanvas` (numeri di riga/wrap gratis)
- [x] Politica di retention dello storico — già dalla Fase 3: 100 entry, pruning a ogni insert; `observeLatest` limitato a 100
- [x] Confronto con il mockup `logs_weather_history.diff/screen.png` — righe ±/context e tinte fedeli; deviazione: header commit come righe git-log testuali nel canvas (col gutter) invece delle due card bordate del mockup, coerente con l'impianto "file finto" delle altre schermate; snapshot inglesi by design (Fase 6b) quindi i diff non churn-ano col cambio lingua

## Fase 9 — Rifiniture e qualità

- [x] Test unitari: mapper DTO→dominio, serializzazione JSON per il rendering, generazione diff, utility emoji — `WeatherReportMapperTest` (13 test: finestre hourly/daily, conversioni unità, degradazione air quality/pollini, CO µg→mg); gli altri tre ambiti erano già coperti da `WeatherJsonTest`/`SnapshotDiffTest`/`WeatherCodesTest`. Totale suite: 52 test
- [x] Test UI (Compose): rendering syntax highlighting, interazione toggle/checkbox testuali, navigazione — via **Robolectric** (niente emulatore: girano in `testDebugUnitTest`, SDK 35, graphics NATIVE): `JsonSyntaxTest` (colori token/inline/indent come pure function), `CodeControlsTest` (`[x]`/`true` toggling + semantics), `CodeCanvasTest` (gutter on/off, righe cliccabili), `TweatherNavigationTest` (shell reale, switch dei 4 file dalla bottom bar)
- [x] Verifica accessibilità: contentDescription sui controlli testuali, contrasto, dimensioni touch target — FAB ora con `contentDescription` vera (prima solo onClickLabel), nav bar con stato `selected` (Role.Tab), `CodeLine.onClickLabel` per le righe interattive (risultati/recenti in Search, tutte le impostazioni), `TerminalInput` annuncia il placeholder. Contrasto: tutti i token ≥ ~5.5:1 su `#10141a` (comment gray ~6:1); sotto soglia solo gli hint `// …` al 60% di alpha (decorativi). Touch target: righe-codice ~22dp di altezza (sotto i 48dp raccomandati) — allargarle stravolgerebbe la densità da editor, lasciato così deliberatamente
- [x] Gestione configurazioni: rotazione, split screen, font scale di sistema — stato in ViewModel + `rememberSaveable` (scroll/nav sopravvivono); tipografia tutta in sp e larghezza righe misurata via `LocalDensity` (fontScale-aware); barre fisse (EditorTab 48, status bar 28, nav 56) portate a `heightIn(min=…)`: identiche a scala 1x, crescono senza clippare a font scale grandi
- [x] Performance: lazy rendering del JSON lungo, evitare ricomposizioni inutili — già a posto LazyColumn/`@Immutable`/`remember` sui builder e diff calcolati sul dispatcher di Room; ottimizzato `CodeCanvas`: la larghezza condivisa ora misura solo le ~12 righe candidate più lunghe (monospace: larghezza ∝ caratteri) invece dell'intero documento a ogni cambio dati
- [x] Icona app dal logo brand `{ ☁️ }` + splash screen a tema — adaptive icon vettoriale (nuvola bordata tra graffe con tick arancio/viola/verde come nel PNG, safe zone 66dp; adaptive-only, niente fallback legacy) + splash `androidx.core:core-splashscreen` su sfondo Obsidian con lo stesso marchio (`Theme.Tweather.Starting` → `installSplashScreen()`)
- [x] **Bugfix da lint durante la fase**: `Duration.toMinutesPart()` richiede API 31 con minSdk 26 → crash del rendering `daylight_duration` su Android 8–11; sostituito con `toMinutes() % 60` (poi ripristinato con l'innalzamento a minSdk 33, vedi sotto). Più `app_name` marcato `translatable="false"`. `lintDebug` ora pulito
- [x] **Decisione post-fase: minSdk 26 → 33** (Android 13) — concordato col committente per non scrivere fallback per versioni vecchie: language picker di sistema per la l10n IT/EN (il `localeConfig` della Fase 6b sotto il 33 non ha UI), un solo code path runtime per `POST_NOTIFICATIONS` quando arriverà il motore di allerta, themed icons attive, `java.time` completo (`toMinutesPart()` ripristinato). Costo: esclusi i dispositivi Android 8–12 (~15-20% del parco attivo)
- [x] Revisione finale di tutte le schermate contro i mockup PNG — fedeltà confermata al netto delle deviazioni già decise nelle fasi 4–8; 4 residui proposti al committente, 2 approvati e applicati: riga `// Last modified: <ISO-8601>` in settings.config (timestamp reale in DataStore, appare dalla prima modifica) e gutter tinto verde/rosso sulle righe ± dei Logs (visibile con line_numbers attivo); respinti (schermate attuali preferite): righe vuote tra sezioni top-level del main, header commit a card bordate nei Logs

## Fase 9b — Posizione GPS (`current_location.json`)

- [x] Definire il modello GPS nel dominio: sentinella `GpsCityId = -1L` (gli id GeoNames sono positivi, nessuna collisione), `GeoFix` + `toGpsCity()` con coordinate a 2 decimali (~1,1 km: `cacheKey` esatto, cache e storico si frammentano solo per spostamenti reali) e label fallback `45.46N 9.19E`, nuove `WeatherException.Location*` con messaggi terminale `gps::…` — `domain/model/GpsLocation.kt`, 4 test puri
- [x] Implementare `data/LocationProvider.kt` (interfaccia + `AndroidLocationProvider`): one-shot `LocationManager.getCurrentLocation` (fused→network→gps) con `CancellationSignal`, timeout 15 s e fallback last-known; reverse geocode `Geocoder` async best-effort (5 s, mai fatale) — niente play-services (app GPL, zero dipendenze nuove) e **solo `ACCESS_COARSE_LOCATION`**: per il meteo basta la precisione a livello città e il prompt è meno invasivo
- [x] Estendere `CityStore`: prefs `use_gps` + `gps_city_json`, flow `activeSource` (`Gps|Saved`) sul sentinel `active_city_id = -1` (nessuna pref dedicata alla selezione), `setUseGps` (on = GPS subito attivo, off = fallback alla prima città salvata, transizioni atomiche in un solo `edit`), `setActiveGps`, `updateGpsCity`; la pseudo-città GPS non entra mai in `cities_json` — `CityStoreTest`, 8 test
- [x] Integrare l'acquisizione in `WeatherViewModel`: guardia di reload su `id:cacheKey` (fix del confronto solo-id che ignorava i cambi di coordinate), stale-while-revalidate al cold start (ultimo fix persistito subito, ri-acquisizione in background), FAB con GPS attivo = ri-acquisizione + forceRefresh, errori `gps::` nel canale `// ERROR:` esistente, riga `// gps: acquiring position …` durante il fix — `WeatherViewModelTest` con `FakeLocationProvider` (Retrofit su porta chiusa come rete finta); aggiunta `kotlinx-coroutines-test` ai test dep
- [x] Sezione `location` in settings.config: riga `"use_gps"` col **primo flusso di permessi runtime dell'app** (`rememberLauncherForActivityResult`; deny transitorio 4 s in rosso diff, permanently denied → il tap apre le impostazioni di sistema, revoca rilevata al resume → il tap ri-chiede il permesso), attivazione immediata della sorgente GPS al toggle on (deciso col committente), `git restore settings.config` riporta a off; stringhe a11y IT/EN — `SettingsGpsLineTest`, 5 test Compose
- [x] Voce Explorer fissata `· current_location.json` in **tertiary** (DESIGN.md riserva quel colore proprio a "Current Location"): visibile solo con `use_gps`, non rimovibile, commento `// gps` / `// active`, selezione via `setActiveGps` senza spegnere il toggle; status bar col nome del fix e conteggio file incluso — `ExplorerGpsRowTest`, 3 test Compose
- [x] Verifica manuale su device/emulatore: grant/deny/deny permanente, GPS di sistema off, cold start con GPS attivo, FAB con posizione cambiata (nuovo commit nei Logs), revoca permesso a toggle on — verificata dal committente su device: attivazione con grant, fix e meteo della posizione, passaggio a città cercata e rientro al GPS da `current_location.json`; dal feedback è nato l'hint `// tap to add — cities are saved in [ files ]` nei risultati di Search (discoverability del file tree)

## Fase 9c — Notifiche (motore di allerta)

- [x] Aggiungere WorkManager al progetto: `work-runtime-ktx` 2.10.5 + `work-testing` (solo test) nel version catalog — init di default via androidx.startup (niente Application class né modifiche manifest); consumer rules R8 della libreria sufficienti (verificato con `assembleRelease`)
- [x] Estendere `update_frequency_min`: valori **15/30/60/120**, default nominato `DefaultUpdateFrequencyMin = 60` (il setting ora è TTL foreground E intervallo di polling background), hint `// 15 | 30 | 60 | 120`; chi non aveva mai toccato il setting migra silenziosamente 15→60 (accettato: cold start fetcha comunque, FAB esiste)
- [x] Implementare `domain/AlertEngine.kt` puro (niente clock/Android/I-O): bucket severi THUNDER/ICE/RAIN/SNOW su wmo {56,57,65,66,67,75,82,86,95,96,99} con lookahead 12 h e fingerprint `cityKey:sev:bucket:data`, pioggia `precip_chance ≥ 70%` su 6 h con dedup per mezza giornata (AM/PM), daily summary in finestra 06–12 locale con dedup per data, un severe sopprime il precip della stessa valutazione — `AlertEngineTest` table-driven (12 test)
- [x] Creare `data/AlertStateStore.kt` (DataStore `alerts`, 3 chiavi fingerprint) + registrazione nel `ServiceLocator` — store separato da SettingsStore per non bumpare il `// Last modified:` visibile; fingerprint registrato solo dopo notify riuscito
- [x] Implementare `notifications/AlertNotifier.kt`: 3 canali (severe HIGH / precip DEFAULT / summary LOW), id fissi 1001-1003 (stessa kind sovrascrive, mai stack), titolo localizzato IT/EN ("⛈️ Allerta meteo — Milan"), temperature nell'unità dell'utente; nuovo vettore monocromo `ic_stat_tweather` (graffe + nuvola); tap → MainActivity FLAG_IMMUTABLE — `AlertNotifierTest` (Robolectric)
  - **Revisione post-9d del corpo notifica** (il "corpo terminale inglese" originale violava la regola l10n della Fase 6b: i *valori* meteo si localizzano, come già fanno schermata principale e widget — su device IT si leggeva `Overcast`, e la riga lunga andava a capo male). Ora il corpo è un **oggetto JSON**: chiavi e riga di comando inglesi (`$ tweather --daily`), valori localizzati via `WeatherTranslations` come nel widget; **collassato** = oggetto foldato su una riga (`{ "status": "Coperto ☁️", "high_c": 35, … }`, il sistema tronca da destra e l'ordine dei campi è l'ordine di importanza), **espanso** = pretty-print un campo per riga sotto il comando. Chiavi allineate a `weather_data.json` (`status`, `wmo_code`, `precip_chance`, `precip_pct`, `high_c`/`high_f` col suffisso unità). Il precip warning e il severe ora mostrano anche `status` (il tipo di pioggia era già nel dominio, non veniva reso)
- [x] Implementare `WeatherSyncWorker` + `AlertScheduler`: periodic unico `weather-sync` con intervallo = `update_frequency_min`, constraint solo CONNECTED (niente battery-not-low: sopprimerebbe le allerte severe proprio quando servono), nessuna flex window (batching massimo all'OS), backoff esponenziale 15 min solo su NoNetwork, `ExistingPeriodicWorkPolicy.UPDATE`, GPS = solo lastFix persistito (mai background location), self-cancel se permesso revocato o toggle tutti off; riconciliazione da un unico collector in `MainActivity` (copre avvio/toggle/frequenza/reset); **le fetch del worker creano commit nei Logs come da metafora PRD** (deciso col committente, ~24/die a 60 min) — `WeatherSyncWorkerTest` (TestListenableWorkerBuilder) + `AlertSchedulerTest`
- [x] Flusso `POST_NOTIFICATIONS` in settings.config: uses-permission nel manifest, secondo launcher sul pattern GPS della 9b (grant/deny/permanently denied → impostazioni di sistema, ri-check al resume), accendere un toggle senza permesso chiede prima il permesso e poi persiste; il commento placeholder "alert engine ships later" sostituito da **riga di stato dinamica** (`// polling every N min` / `// alerts disabled` / `// ERROR: … tap to grant`) — `SettingsNotificationsLineTest` (4 test Compose); deviazione dal pattern GPS: niente riga flash transitoria, la riga di stato è già persistente
- [x] Verifica manuale su device: grant/deny/deny permanente, revoca da impostazioni di sistema (self-cancel del worker), cambio frequenza, notifiche reali, riavvio device (persistenza del periodic work), release build minificata — tutto verificato dal committente su device in italiano; le notifiche riviste provate cambiando location per far scattare anche i temporali (tutti e tre i tipi corretti: valori localizzati, corpo JSON leggibile collassato ed espanso, nessun ritorno a capo forzato), periodic work vivo dopo riavvio, build minificata in uso continuativo

## Fase 9d — Widget home screen (`tweather --now`)

Mockup di riferimento: `tweather_widget/tweather_widget.png` (finestra terminale). Decisioni di inizio fase col committente: **RemoteViews classiche con layout XML, niente Glance**; valori meteo localizzati come nella schermata principale (chiavi/prompt/chrome del terminale restano inglesi); job periodico attivo se widget installato **o** notifiche attive — niente widget e notifiche off → job cancellato come oggi.

- [x] Setting `widget.bg_opacity_pct` in settings.config: valori 100/85/70/50 ciclabili al tap (pattern di `update_frequency_min`), chiave DataStore `widget_bg_opacity_pct`, default 100 — `SettingsWidgetLineTest` (3 test Compose)
- [x] Content builder puro `widget/WidgetContent.kt`: snapshot Room (`snapshotJson` flatten) → righe token-izzate per 3 tier (SMALL emoji+temp+città, MEDIUM = mockup Location/Temp/Status/Humidity, LARGE + Feels/Wind/AQI/Sun + `# last_sync: HH:mm`), conversione unità via gli helper internal di `WeatherJson` (resi internal anche quelli del vento), `translate` applicato alla sola descrizione dello Status, empty state `# no data yet — open tweather`
- [x] Palette per profilo tema `widget/WidgetPalette.kt` dai token pubblici (`*Colors` + `*Syntax`, prompt = secondary, alert = diffDel)
- [x] Layout XML dei 3 tier + preview del picker (testi/colori hardcoded), drawables fill/border con colori Obsidian *nel drawable* e ri-tintati a runtime (il layout iniziale viene inflazionato dall'host prima del primo bind: con drawable bianchi si vedeva un rettangolo bianco), opacità solo sul fill, bordo sempre opaco, corner radius di sistema; `xml/widget_tweather_info.xml` con `updatePeriodMillis=0`, `previewLayout`, `targetCellHeight=3`
- [x] `WidgetRenderer`: mappa `RemoteViews(Map<SizeF, …>)` API 31+ (il launcher sceglie il tier al resize senza round-trip), colori per token via `ForegroundColorSpan` (ParcelableSpan, sopravvive all'IPC), tap sul corpo → MainActivity, tap ↻ → broadcast al provider. **Breakpoint calcolati dai layout** (`chrome 68dp + righe × 20dp + 12dp`), non stimati: una chiave della mappa è la promessa che il layout ci *stia*, e con i valori iniziali il launcher sceglieva MEDIUM/LARGE ad altezze dove le righe venivano tagliate in silenzio — test che misura ogni tier al proprio breakpoint
- [x] `TweatherWidgetUpdater.updateAll` (settings + città dell'istanza + ultima entry Room, render raggruppato per città) e `TweatherWidgetProvider`: tutto il lavoro asincrono in **un solo** `goAsync` in `onReceive` (è consumabile una volta sola e `ACTION_APPWIDGET_ENABLE_AND_UPDATE` colpisce due hook: la seconda chiamata restituiva `null`), `ACTION_REFRESH` → one-time work `weather-sync-manual` expedited con `force_refresh`; receiver `exported=false` nel manifest
- [x] Batteria: nessun polling proprio del widget — `AlertScheduler.shouldRun = alertsWanted || hasWidgets`, worker con blocco alert gated da `alertsWanted` (un sync solo-widget fetcha ma non notifica mai), re-render agganciato a `WeatherRepository.onHistoryCommitted` (un solo choke point per fetch foreground e background; `catch` esplicito della `CancellationException`, che `runCatching` mangiava facendo pubblicare alla schermata principale il report di una città già abbandonata) + collector in `MainActivity` per i re-render senza fetch (tema/unità/opacità/città)
- [x] **Deviazione dal vincolo "JetBrains Mono ovunque"** (registrata anche in `CLAUDE.md`, decisa col committente): i layout del widget usano il `monospace` di sistema. Dalla patch di giugno 2021 (CVE-2021-0567, backportata fino ad Android 11 QPR3) il launcher inflaziona i layout dei widget in un context ristretto che **scarta senza errore** i font resource — un `@font/` diventerebbe Roboto, cioè proporzionale. Glance ha lo stesso limite; l'unica alternativa sarebbe rasterizzare il testo in bitmap, perdendo scala testo di sistema e TalkBack
- [x] Indicatore dati vecchi: oltre 2× `update_frequency_min` senza commit il marcatore `# stale` (colore diff-delete) compare sulla riga Temp (MEDIUM/SMALL) e la riga `# last_sync` diventa rossa (LARGE). Il worker ridisegna il widget **anche quando il fetch fallisce**: senza quello il marcatore non sarebbe mai comparso proprio nello scenario che deve segnalare (rete giù = nessun commit = nessun re-render)
- [x] Città fissa per widget: `data/WidgetCityStore.kt` (DataStore `widget_cities`, una chiave per appWidgetId) + `WidgetConfigActivity` in stile file explorer (`widget.config`), `widgetFeatures="configuration_optional|reconfigurable"` così il widget si aggiunge anche senza configurare e resta modificabile dopo; senza pin il widget segue la sorgente attiva dell'app, `onDeleted` ripulisce le chiavi e `onRestored` le ri-mappa (dopo un restore gli appWidgetId cambiano: senza il remap un widget erediterebbe il pin di un altro). **Il worker fetcha anche le città fissate** — un widget fissato non è la sorgente attiva di nessuno, quindi senza il suo fetch si sarebbe congelato, avrebbe latchato `# stale` per sempre e il ↻ non avrebbe potuto sbloccarlo; costo: una GET in più per periodo per ogni città fissata distinta, e solo finché quel widget è piazzato
- [x] Bug trovato dai test: `goAsync()` restituisce `null` quando `onReceive` non arriva da un broadcast reale, e la `finally` andava in NPE dentro una coroutine di background (crash silenzioso del processo). `PendingResult` reso nullable
- [x] **Bugfix da verifica su device del committente: l'opacità non aveva alcun effetto.** `GradientDrawable.draw()` sostituisce un riempimento **opaco** (`mFillPaint.setColor(mAlpha << 24)`) quando c'è un color filter su una shape che non dichiara `<solid>`: il layer del bordo — solo `<stroke>`, tintato a runtime — disegnava quindi un rettangolo pieno sopra il riempimento, rendendo l'alpha invisibile qualunque valore avesse. Risolto con un `<solid>` trasparente in `widget_bg_border.xml`. Le asserzioni sui setter non potevano vederlo (`imageAlpha` era corretto): aggiunti due test che **disegnano i due layer su bitmap** e controllano i pixel — centro del bordo trasparente, cornice comunque presente e nel colore del tema, riempimento a 50% davvero semitrasparente
- [x] Rifiniture da review: `SINGLE_TOP` sui PendingIntent di widget e notifiche (senza, il tap distruggeva e ricreava MainActivity), `layout_marginEnd` che riserva la colonna emoji/↻ (altrimenti `ellipsize` misurava sulla larghezza piena e i valori lunghi finivano disegnati *sotto* i glifi), target del ↻ a 48dp, header con `minHeight` invece di altezza fissa (scala testo), nome città del tier piccolo in colore testo invece che grigio-commento (~3:1 su Dracula/Monokai)
- [x] Test: builder puro, renderer e provider (Robolectric con `ShadowAppWidgetManager`), risoluzione città per istanza, `WidgetCityStore`, schermata di configurazione (Compose), truth-table scheduler a 3 argomenti, worker "toggle off + widget presente non si auto-cancella", riga opacità in settings
- [x] Freccia ↻ spostata **in alto a sinistra**, speculare all'emoji: entrambe in box da 48dp, titolo pesato e centrato tra le due. La barra del titolo diventa un `LinearLayout` (non più `FrameLayout`) così il titolo si accorcia invece di finire *sotto* i glifi su un widget stretto. Effetto collaterale che vale più dell'estetica: **niente più nulla che fluttua sopra il corpo**, quindi via il margine riservato e via la logica `setViewLayoutMargin` per riga — tutte le righe a larghezza piena, compresa l'ultima. Costo misurato: **+14dp di chrome** (162dp per 4 righe contro 148), cioè circa due terzi di riga
- [x] **Correzioni dopo la prova su device (screenshot del committente).** La barra del titolo con il box tocco da 48dp **in altezza** era troppo alta: mangiava una riga ed era brutta. Ora il box è 48dp **largo** ma alto quanto la barra (`match_parent` su `minHeight=34dp`), glifi a 18sp — il target resta comodo, la barra torna a ~34dp. Padding del prompt limato di 4dp
- [x] **Margine di sicurezza sulla scala.** Sul device l'ultima riga si vedeva per pochi pixel: il test misura con il font monospace della JVM, il device usa quello dell'OEM, e il trascritto reale veniva un filo più alto. Le costanti ora tengono ~4dp di chrome e ~2dp per riga sopra il minimo misurato — una riga in meno è un guasto molto meno grave di una riga tagliata. Il test accetta il margine ma continua a bocciare gli scostamenti grossi (il bug originale valeva 30dp)
- [x] Tentato e **ritirato**: `-current` come view separata con `layout_weight` per farlo sparire quando non ci sta. Con poco spazio residuo si riduceva a `-…` invece di sparire, che si legge peggio di un comando troncato — nessun meccanismo RemoteViews dà il tutto-o-niente senza misurare. Prompt tornato a una sola view con ellissi
- [x] Rimosso il cursore `_` in basso a destra (era nel mockup, ma lì stava **dopo il prompt**, dove un cursore di shell ha senso; spostato nell'angolo era solo un trattino) e freccia ↻ portata a 20sp nell'angolo, con il target di 48dp invariato. Terzo glifo decorativo del mockup che non sopravvive allo spazio reale di un widget, dopo `⋯` e l'emoji fluttuante. Il margine riservato sull'ultima riga scende da 56 a 48dp: quei caratteri tornano al testo
- [x] **Rifiniture da prova su device (3ª tornata).** `Location` mostra **sempre e solo la città**, a qualunque dimensione (la regione è ciò che l'utente già sa, ed è ciò che spingeva il nome della città nell'ellissi). Aggiunte **Rain** (probabilità di pioggia — era assente ed è la domanda per cui si guarda un meteo) e **UV**. La scala dei tier è diventata **un gradino per riga** (`WidgetTier.Terminal(lines)`, 4→11) invece di quattro taglie con nomi: la sizes-map sceglie solo un gradino che *ci sta*, quindi un buco nella scala lasciava un widget con spazio per sette righe fermo a cinque — è esattamente il sintomo riportato dal committente. Le altezze ora sono **misurate, non stimate**: un test fa una ricerca binaria sull'altezza minima reale di ogni gradino e fallisce se le costanti si discostano — la stima precedente chiedeva ~30dp di troppo per gradino, cioè una riga di meteo buttata via. Il ridisegno su `onAppWidgetOptionsChanged` è stato **rimosso**: la sizes-map esiste perché sia l'host a ri-scegliere il tier, e il nostro push in più veniva applicato contro la dimensione ancora vecchia (da lì l'asimmetria per cui rimpicciolendo restava il trascritto lungo, tagliato)
- [x] **Rifiniture da prova su device (2ª tornata).** Testo a **15sp** (era 13): un widget si legge a distanza di braccio, non come il canvas dell'app. **Emoji spostata nella barra del titolo**: in basso a destra costringeva a riservarle larghezza su *ogni* riga, e i valori valgono più della decorazione — ora solo l'ultima riga lascia spazio al ↻, via `setViewLayoutMargin` (API 31+) invece che sulla colonna. Aggiunta la riga **Feels** accanto a Temp e nuovo tier **EXTENDED** (5 righe) tra MEDIUM e LARGE: con una sola scala grossolana un widget con spazio in più si sarebbe preso comunque il trascritto corto. `Location` mostra solo la città sui tier stretti (la regione mangiava il nome), tutto sui tier larghi
- [x] Verifica manuale su device: aggiunta dal picker (con e senza configurazione), resize sui 3 tier, tap ↻ con e senza rete, opacità, cambio tema/unità/città, widget fissato su una città diversa da quella attiva, marcatore `# stale` con rete disattivata, notifiche off + widget → job vivo, rimozione ultimo widget con notifiche off → job cancellato, riavvio, release minificata — completata dal committente (le tre tornate di rifiniture qui sopra nascono da questo giro di prove)

## Fase 9e — Rifiniture da feedback (`search_query.json`)

- [x] **La casella di ricerca non si svuotava dopo la selezione** (svista della Fase 6, non una scelta): `select()` salvava città e recente ma lasciava query e risultati, e il ViewModel sopravvive al cambio tab — tornando in Cerca si trovava il testo vecchio da cancellare a mano. Ora la selezione azzera query e risultati: la ricerca è conclusa, la città è diventata un file in Esplora
- [x] Cancellazione dei recenti in stile terminale: riga `$ history -c` in coda al file con conferma a due tap, come `$ git restore settings.config` in settings.config — ma col verbo della shell, non una metafora git, perché è esattamente ciò che fa. Compare solo se c'è qualcosa da cancellare. `SearchHistoryStore.clear()` — `SearchClearHistoryTest` (4 test Compose) + `SearchHistoryStoreTest` (5 test)
- [x] **Decisione: cancellare i recenti NON tocca i file in Esplora** (valutata col committente). I recenti sono la cronologia di cosa si è cercato, i file sono i dati salvati: una cronologia non deve mai cancellare dati. Le città si rimuovono una alla volta col `[rm]` che l'Esplora ha già
- [x] Valutata e scartata la cancellazione per singola voce: servirebbero delle `Row`, e nella Fase 6b i toggle erano stati portati da `WidgetLine` a `CodeLine` proprio per far funzionare il word wrap. Con massimo 5 voci che si auto-espellono il comando unico basta
- [x] Verifica manuale su device: selezione di un risultato (casella e risultati vuoti al rientro), `$ history -c` con conferma, recenti che restano dopo un tap singolo, città in Esplora intatte dopo la cancellazione — completata dal committente

## Fase 9f — Rifiniture da feedback (`widget.config`)

- [x] **La configurazione del widget non si leggeva come le altre schermate** (segnalata dal committente su screenshot). Non era fuori tema — usava gli stessi componenti dell'Esplora — ma usava la *metafora sbagliata*: il tab prometteva un file `.config` e il corpo era un file tree. Riscritta con `CodeCanvas` nel formato di `settings.config`: header a commenti, oggetto `widget` con `instance_id` e `source`, e l'array `available_sources` in cui **ogni riga è tappabile** e fissa quella sorgente, esattamente come `available_profiles` nel blocco tema
- [x] `"source"` è di sola lettura: il tap su una sorgente fissa e chiude l'Activity, quindi un valore che cicla non avrebbe mai potuto compiere un giro. Il controllo è la lista, il valore è il riepilogo. Conservate le due scelte cromatiche dell'Esplora: sorgente selezionata in `primary`, `current_location.json` in `tertiary` (design system: "riservato alle costanti globali come Current Location")
- [x] **Status bar in prosa localizzata** (`tocca un file per fissare questo widget`): unico punto dell'app in cui la barra di stato conteneva una frase in linguaggio naturale, contro la regola scritta in cima a `strings.xml` ("i commenti `//` restano inglesi"; le altre barre sono token tipo `⎇ config | rw | UTF-8`). Ora è `⎇ widget | rw | N sources` e l'affordance vive dov'è di casa, come commento inline: `"available_sources": [  // tap to pin`. Rimossa `widget_config_hint`, aggiunte `status_sources` e `cd_widget_pin` (era riusata `cd_open_city`, cioè "Apri Milano" per un'azione che fissa)
- [x] L'Activity vive fuori dal guscio `TweatherApp`, quindi non riceveva `LocalEditorOptions`: ora lo fornisce da sé leggendo `settings.config`, e `widget.config` rispetta numeri di riga e word wrap come ogni altro canvas (prima li avrebbe ignorati in silenzio)
- [x] `punctLine`/`keyOpenLine`/`stringValueLine` promossi da privati di `SettingsScreen` a `ui/components/JsonSyntax.kt` (più `numberValueLine`), così i due file di config si costruiscono con gli stessi mattoni invece di divergere
- [x] **Sfoltita dopo la prova su device (screenshot del committente).** Via `instance_id`: era l'id reale dell'istanza, ma è un numero assegnato dal launcher che non compare da nessuna parte sulla home — non aiuta a capire *quale* widget si sta configurando e genera solo la domanda "19 cosa?". Rimasto senza, il blocco `"widget": { … }` conteneva due chiavi dentro un file che si chiama `widget.config`: wrapper tautologico, appiattito, e ogni riga guadagna un livello di indent
- [x] **Commenti da 7 a 3, uno per mestiere**: l'affordance (`// tap to pin`), lo stato (`// selected`) e l'unico nome file che non può spiegarsi da solo (`// follows the app`). Cancellati `// pinned` (lo dice già il valore), `// gps` (lo dice già il nome del file, e il colore `tertiary` è il marcatore previsto dal design system) e la seconda riga di intestazione — che era anche **l'unica riga a sforare lo schermo**, quindi andava pannata orizzontalmente per leggerla
- [x] Test: `WidgetConfigScreenTest` riscritto sulla nuova resa (una riga = un nodo di testo, quindi match sulla riga JSON esatta) — 7 test, incluso il caso di una città fissata e poi rimossa dall'elenco, che ricade su `active_file`
- [x] Verifica su device del committente — completata ad ago 2026 durante i test della Fase 9g (che ha toccato anche widget.config: selezione per id)

## Fase 9g — Review completa pre-release (bug, battery drain, coerenza design)

- [x] Tripla review dell'intera app prima della release — caccia ai bug, analisi battery drain, coerenza con la filosofia "a weather app that thinks it is a code editor" — con seconda passata di verifica dopo i fix (PR #5, mergiata ago 2026). Esito: 0 bug di severità alta; battery già ben progettata (location single-shot, worker unico UPDATE/no-flex, widget passivo confermati); nessuna violazione dura del design system
- [x] Fix bug: dedup degli alert per città (set di fingerprint recenti, max 16, chiave stabile id/`"gps"` — prima uno slot singolo ri-notificava lo stesso evento alternando città o spostando il fix GPS); doppia esecuzione della ricerca da `searchNow` eliminata (il debounce cancellava la richiesta in volo); date relative dei Logs ri-clockate al minuto; pending del toggle notifiche applicato/azzerato al rientro dai settings di sistema; nomi file univoci per città omonime (`springfield_illinois.json`, selezione per id in Esplora e widget.config); query di ricerca in `SavedStateHandle` (sopravvive alla process death)
- [x] Fix battery: `ReportDiskCache` — l'ultimo fetch persiste su disco come DTO grezzi, un cold start dentro il TTL rimappa da disco (0 GET) invece di rifare forecast+air quality; pruning a 4h (2× il TTL massimo, il GPS non accumula un file per cella); il ↻ del widget forza il refresh solo della città che mostra (cacheKey nel data URI del PendingIntent — gli extra non contano nell'identità); cursore lampeggiante creato solo a campo vuoto; ticker dei Logs dentro `repeatOnLifecycle(STARTED)`
- [x] **Decisione design: FAB rettangolare** (radius 4px come tutto il resto, valutata col committente): il cerchio era l'ultimo elemento Material rimasto e in un editor niente è circolare; a distinguerlo resta solo il glow, ora un vero blur (`BlurMaskFilter`) attorno al rounded-rect. DESIGN.md, PRD e CLAUDE.md aggiornati; sanato anche il conflitto tra fonti su on-surface (`#dfe2eb` ovunque, il PRD diceva `#e6edf3`)
- [x] Derive uniformate: bordo `EditorTab` su `syntax.border` come nav e status bar; tab dell'Esplora rinominato `cities/` (unico titolo fuori metafora); rimosso `CodeControls.kt` mai usato dalle schermate; helper JSON di SearchScreen deduplicati su `JsonSyntax.kt`
- [x] **User-Agent identificabile** (`tweather/<version> (+github.com/fiorenzobrioni/tweather)`) su tutte le chiamate Open-Meteo. Verificati i termini del free tier per la distribuzione: uso non-commerciale (niente ads/abbonamenti) in regola per il Play Store, limiti 600/min–5.000/ora–10.000/giorno applicati **per IP del client** (dichiarazione del creatore), quindi il modello device-diretto regge anche a grandi volumi; attribuzione CC BY 4.0 già presente in app e README (da ripetere nella scheda store). Se un domani l'app monetizza serve il piano commerciale con API key
- [x] Verifica manuale su device e merge della PR #5 — completata dal committente

## Fase 9h — Confronto previsioni (`weather_forecast.diff`)

Idea del committente (ago 2026): un secondo file nei Logs che risponde a "quanto è cambiata la previsione?" — diff tra previsioni successive **per la stessa data target**, nel linguaggio git dell'app (weather_history.diff confronta osservazioni, questo confronta predizioni dello stesso momento futuro). Decisioni di inizio fase col committente: campi `status`/`high_c`/`low_c`/`precip_pct` (niente vento: nella maggior parte dei casi è rumore), orizzonte **domani + dopodomani** (il giorno 6 cambia sempre e non interessa), soglie anti-rumore (1 °C, 10 punti di probabilità, status sempre), switch tra i due file con una vera tab bar da editor.

- [x] Colonna `forecast_json` su `weather_history` (Room v1→v2, `ALTER TABLE` con migrazione testata — l'installazione del committente non deve perdere lo storico): snapshot forecast flatten con chiavi per data target assoluta (`2026-08-18.high_c`), orizzonte d+1/d+2 calcolato nel fuso della città (`location.localTime`), valori metrici inglesi come lo snapshot esistente (i diff non cambiano con lingua o unità) — `WeatherSnapshots.flattenForecast`, colonna nullable senza default così le righe pre-migrazione restano distinguibili e vengono saltate (non trattate come previsione vuota)
- [x] `ForecastDiff` puro (`data/local`): baseline per (città, data target) = ultima previsione *mostrata*, non l'ultimo fetch — una deriva sotto soglia si accumula finché non la supera invece di sparire per sempre (testato: 30.0→30.6→31.2 emerge al terzo fetch contro la baseline 30.0); prima apparizione di una data = "new file" (`--- /dev/null`, tutte righe `+`); data uscita dall'orizzonte = silenzio (non è una revisione); fetch senza cambi sopra soglia = nessun commit nel file; etichetta del giorno senza fuso a render time (l'orizzonte contiene solo d+1/d+2, la data minore del fetch è "tomorrow")
- [x] Rendering nei Logs: stesso hash del commit del fetch (un fetch = un commit che tocca due file, come in git), header `--- a/forecast_<date>.json (<ora baseline>)` / `+++ b/forecast_<date>.json (<ora fetch>)` (ora `HH:mm` se stesso giorno locale, `MMM d HH:mm` se più vecchia — due previsioni a ore di distanza si leggono diversamente da due a un giorno), hunk header `@@ tomorrow @@` / `@@ in 2 days @@` in key-blue (git colora gli hunk header a parte), righe ± e context con gli stili esistenti dei Logs
- [x] `EditorTabs`: la barra del titolo dei Logs diventa una vera tab bar a due file (attivo primary + indicatore 2px come la nav bar, inattivo comment-gray, semantics Role.Tab con selected, scroll orizzontale se due nomi file non ci stanno, testo bodyMedium — due tab a headlineMedium sforerebbero i 360dp); status bar che segue la tab: `⎇ history` / `⎇ forecast` + `status_revisions` IT/EN
- [x] Test (+15, suite a 210): flatten forecast (orizzonte/fuso/chiavi), `ForecastDiff` (soglie inclusive, deriva accumulata, new-day, giorno uscito, status sempre, commit vuoti saltati), migrazione Room 1→2 (db v1 costruito a mano con lo schema esatto — il test lo *pinna*: se diverge da quello generato da Room la validazione fallisce — righe vecchie con forecast null, insert nuove ok), `buildForecastRevisions` (città interleaved indipendenti, righe pre-migrazione saltate, ordinamento), switch tab Compose (selezione, contenuti, status bar, placeholder); lint pulito, release minificata compilata
- [x] **Rifinitura post-prova: valori meteo localizzati anche nei diff** (decisa col committente, che ha rilevato l'asimmetria: i Logs erano l'unica superficie con i valori in inglese). La regola l10n dell'app distingue chiavi (inglesi) da valori meteo (localizzati), non "schermate di codice" da "schermate di dati": hash, `Author:`/`Date:`, header `diff`/`---`/`@@` sono le *chiavi* del formato git e restano inglesi, `Overcast` dentro una riga `±` è un valore come negli altri render. Traduzione a render time via `WeatherTranslations.valueTranslator` (tollera la forma `"descrizione emoji"` degli snapshot, stesso split difensivo del widget), gate per chiave (`*.status`, `*.moon_phase`) così un futuro valore che collidesse con una parola tradotta non viene toccato; gli snapshot Room restano inglesi (i diff non churn-ano mai col cambio lingua). Test con locale IT: valori tradotti su entrambi i file, chrome git/città/hunk header invariati
- [x] **Rifiniture da prova su device (2ª tornata, decise col committente).** (1) Nomi file nelle tab a **bodyMedium bold su tutte le schermate** (main, search, settings, cities/, widget.config, Logs): il committente notava i nomi dei Logs poco evidenti e lo stacco col 24sp delle altre schermate a ogni switch — la tab bar è chrome, non contenuto, e ora tutto il chrome tab è a 14sp bold (il glifo `>_` era già bodyMedium bold). (2) `[ files ]` → **`$ ls cities/`** nella top bar del main: il comando shell che farebbe esattamente quello, `$ ` in comment-gray come i comandi nel corpo di settings/search; valutata e scartata la status bar (target tocco minuscoli su una barra già piena, e la posizione top-right è quella già imparata); aggiornato l'hint di Search (`cities are saved in cities/`). (3) **`↑ top` flottante nei due file diff**: chip di testo bordato (niente glow — resta esclusivo del FAB refresh) in basso a destra, compare oltre ~una schermata di scroll, tap → scroll animato in cima; scroll **separato per tab** (prima cambiando file si atterrava a metà dell'altro diff). Test: back-to-top (assente in cima, appare dopo scroll, riporta al primo commit)
- [x] Verifica manuale su device — completata dal committente in tre giri sugli APK di CI (funzionalità base, poi l10n dei valori, poi le rifiniture); PR #6 mergiata ad ago 2026

## Fase 10 — README della città (`README.md`)

Idea del committente (ago 2026): un secondo file accanto a `weather_data.json` nella tab bar del main — il `README.md` della città. La metafora regge in profondità: in un repo vero il README è il riassunto *umano* del contenuto macchina, e qui è la vista a colpo d'occhio del meteo mentre il JSON resta la sorgente dati completa. Decisioni di inizio fase col committente: markdown **sorgente** con syntax highlighting (vista "Code" di GitHub, non Preview — il rendering proporzionale romperebbe JetBrains Mono, griglia 4px e gutter), pagina **interamente localizzata** (è prosa: anche gli heading, a differenza delle chiavi JSON), vista sommario ma completa (tutti i gruppi di dati principali; fuori solo il dettaglio orario, che resta l'identità del JSON), tab attiva **persistita in DataStore** come workspace state di un editor (silenziosa, niente voce in `settings.config` — il "last open file" negli editor non sta nelle impostazioni), `weather_data.json` default al primo avvio.

- [x] Tab bar del main con `EditorTabs` (riuso dalla Fase 9h, esteso con uno slot `actions` per tenere `$ ls cities/` fissato a destra fuori dalla strip scrollabile): `weather_data.json` + `README.md`, scroll separato per tab, FAB refresh su entrambe le viste (stessa sorgente dati, due render). Stato attivo in un **`WorkspaceStore` dedicato** (DataStore `workspace`, non una chiave di `SettingsStore`): è workspace state da editor, e `$ git restore settings.config` non deve chiudere il tab; `stateIn` Eagerly così il ripristino atterra prima del primo frame
- [x] Renderer markdown sorgente sui token esistenti (`MarkdownSyntax.kt`): heading in key-blue bold con `#` in comment-gray, pipe e righe separatore delle tabelle in comment-gray, blockquote in diff-red (li usa solo `## Status`), `**bold**` con asterischi a vista (è la vista sorgente), footer `*…*` corsivo-grigio, numeri/orari/percentuali in orange, testo libero su on-surface — zero nuovi colori, zero nuova tipografia. Loading/errori sul tab README come commenti **`<!-- -->`** (un file markdown non commenta con `//`)
- [x] Contenuto (`WeatherReadme.kt`): `# <Città>` + riga regione/paese, Current (temp bold, percepita, stato), Today (max/min, precipitazioni, UV), Conditions (righe emoji: vento, umidità, pressione, visibilità), Air quality (AQI + riga pollini), Astronomy (alba/tramonto, luce diurna, luna), Forecast come **tabella markdown** di tutti i giorni del forecast, footer `*Last updated HH:mm · data by Open-Meteo*` nel fuso della città; fuori solo il dettaglio orario (resta l'identità del JSON) e **indipendente da `show_details`** (il README è un sommario curato, il toggle governa i campi tecnici del JSON). Sezioni che le API non riempiono (AQ giù, pollini fuori Europa) **assenti**, non `null`: un README documenta ciò che esiste. Funziona anche per la pseudo-città GPS
- [x] `## Status` collegata all'AlertEngine: regole severe/precipitazioni valutate **stateless** (`AlertState()` vuoto, senza fingerprint di dedup — il README mostra ciò che È, le notifiche decidono cosa è nuovo); nessun alert → "Everything looks good.", alert → blockquote `> ⚠️/🌧️ …` in diff-red (il "build badge" del repo)
- [x] Localizzazione completa IT/EN: heading e prosa da ~30 nuove stringhe risorsa, valori meteo via `WeatherTranslations` come ovunque; unità che seguono le Settings come il JSON (°C/°F, km/h/mph); nome file e sintassi markdown invariati
- [x] Test (+25, suite a 239): `MarkdownSyntaxTest` (heading/blockquote/tabelle/bold/corsivo/commenti HTML/numeri), `WeatherReadmeTest` (sezioni e ordine EN, documento IT interamente localizzato con `@Config(qualifiers="it")`, Status calmo/severe/pioggia, sezioni assenti, °F, footer nel fuso città), `WeatherTabsTest` (default JSON, switch, `$ ls cities/` su entrambe, commenti `<!--`), `WorkspaceStoreTest` (default, selezione, sopravvivenza al riavvio su nuovo store); lint pulito, release minificata compilata
- [x] Verifica manuale su device — completata dal committente sull'APK di CI del branch (PR #7, ago 2026)

## Fase 10b — Città e ricerca unificate (`cities.json`)

Decisione del committente (ago 2026), da prova su device della Fase 10: con due tab veri nel main (`weather_data.json` + `README.md`) l'azione `$ ls cities/` fissata a destra ruba larghezza alla strip e tronca `README.md`, e visivamente si mischia ai nomi file. La schermata `cities/` era inoltre quasi vuota (una lista di pochi file) e il flusso era già mezzo unificato: un tap su un risultato di ricerca aggiungeva *e attivava* la città. Decisione: la schermata Search assorbe la lista città e diventa il file **`cities.json`** — la ricerca è il modo per aggiungere voci al file, pattern standard delle app meteo (una schermata: lista salvate + campo di ricerca). Questo **supera la valutazione della Fase 9h** che aveva scartato la status bar come target: ora `⎇ <città>` nella status bar del main è tappabile (il branch switcher di VS Code) e porta a `cities.json` — accettato il target piccolo perché è la scorciatoia secondaria, la via primaria è il tab Cerca.

- [x] `SearchViewModel` assorbe lo stato dell'Explorer: `CitiesUiState` (città, attiva, GPS) da `combine` su `CityStore` + azioni `activate`/`activateGps`/`remove`; `ExplorerViewModel` eliminato
- [x] `SearchScreen` → `cities.json`: `"search_term"` (input) in cima, `"results"` mentre si digita, poi `"saved_cities"` come array JSON — `current_location.json` pinnata in tertiary (`// gps`/`// active`, mai `[rm]`), città salvate come stringhe-filename (attiva in primary + `// active`, `[rm]` in diff-red solo con >1 città), poi `"recent_searches"` e `$ history -c` invariati; tap su salvata = attiva + torna all'editor (stesso ritorno del tap su risultato); status bar `⎇ <attiva> | N files` + risultati/open-meteo.com
- [x] Helper dei filename finti (`fileNames`/`fileSlug`, condivisi col widget.config) spostati da `ui/explorer` a `ui/search/CityFileNames.kt`; `ExplorerScreen` eliminata, import del widget aggiornato
- [x] Main: via `$ ls cities/` dalla tab bar (lo slot `actions` di `EditorTabs` resta, generico); `⎇ <città>` nella status bar tappabile → tab Cerca (`cd_open_cities` IT/EN)
- [x] Navigazione appiattita: via il nested graph `explorer` e `Routes.Cities` — l'editor è la destinazione `explorer` (la route dell'item della nav bar non cambia)
- [x] Test aggiornati (suite a 244: navigazione su `cities.json`, tab del main senza `ls cities/` + tap sulla status bar, `SearchCitiesSectionTest` porta i casi GPS/active/rm dell'Explorer, filename test spostato); stringhe orfane rimosse (`cd_open_explorer`, `cd_add_city`); lint pulito; `CLAUDE.md` allineato
- [x] Verifica manuale su device — completata dal committente sull'APK di CI (ago 2026; fase committata direttamente su main, senza PR)

## Fase 11 — Weather CI (`alerts.rules`)

Idea del committente (ago 2026): notifiche definite dall'utente come regole scritte in stile configurazione — "Weather CI", un mini sistema di automazione meteo per developer. La metafora è già tutta in casa: ogni fetch è già un commit in `weather_history.diff`, le regole sono la pipeline che gira su ogni commit, una notifica scattata è un check fallito. Decisioni di progettazione (ago 2026, col committente):

- **Niente DSL testuale libero in v1.** Una regola *sembra* codice ma *è* una struttura: 1–2 condizioni (variabile, operatore, soglia) + un messaggio. Editing **per token** sul pattern "controls rendered as text": tap sulla variabile → picker stile autocomplete IDE (lista finita), tap sull'operatore → cicla (`> >= < <= == !=`), soglia → input numerico, messaggio → input testo. Un errore di sintassi non è fisicamente scrivibile: niente parser, niente diagnostica, niente gestione errori.
- **Variabili curate con namespace temporale esplicito**, stessi nomi di `weather_data.json` (la reference è la schermata principale stessa): `current.*` specchio di current_conditions + air_quality (`current.temp_c`, `current.uv_index`, `current.wind.gust_kph`, `current.aqi_index`, …); `next_6h.*` / `next_12h.*` aggregati **precalcolati** sull'hourly (`precip_chance_max`, `temp_c_min`, `temp_c_max`, `wmo_severe` boolean) — niente funzioni nel linguaggio, l'utente sceglie la finestra scegliendo il nome; `today.*` da daily[0] (`high`, `low`, `precip_pct`). `rain_probability` nudo sarebbe ambiguo (adesso? stasera?), e l'ambiguità in un sistema di notifiche produce o rumore o silenzi inspiegabili: la scelta "attuale o previsione" sta nel nome, non in sintassi extra.
- **Massimo un `and` per regola** (due condizioni), niente `or` né parentesi. Le regole vere sono quasi sempre congiunzioni (`uv >= 7 and today.high > 25`): senza `and` la feature si rivela un giocattolo alla seconda regola. Col token editing il costo è una seconda riga-condizione opzionale (`+ and` / `[rm]`), senza parser né precedenze; `or` = due regole, forma già disponibile.
- **Zero costo batteria**: valutazione in-process nel worker `weather-sync` esistente, sul report appena fetchato, dopo `AlertEngine.evaluate`. Nessun job nuovo, nessuna rete extra, nessun risveglio extra.
- **Collocazione**: secondo file della schermata Settings via `EditorTabs` (il pattern a due file di main e Logs): `settings.config` + `alerts.rules`. Il messaggio della notifica è contenuto dell'utente, nella sua lingua per definizione — nessuna l10n; localizzato solo il chrome, come in 9c.
- **Gli alert builtin restano toggle in `settings.config`** (deciso col committente, ago 2026): accesso rapido a un tap per chi non vuole entrare nelle regole. Valutata e scartata la loro presentazione come regole di sistema in `alerts.rules`: due superfici da sincronizzare per un beneficio solo estetico, e pseudo-regole non fedeli al motore (bucket WMO, lookahead 12h, finestra 06–12, soppressione precip-sotto-severe non sono esprimibili nel modello) — codice di sola lettura che non dice quello che fa. Resta solo il commento header di rimando (step UI).
- **Fuori, dichiaratamente — non un backlog**: editing testuale libero, funzioni, `or`/parentesi (`or` = due regole), regole per-città (si valuta la città attiva, come gli alert builtin), finestre temporali custom. Deciso col committente (ago 2026) di **non** pianificare una fase v2: se un'estensione servirà davvero, nascerà come fase da feedback dopo l'uso reale, come 9e/9f/9h/10b — la forza del v1 è il confine.

- [x] Modello `NotificationRule` (id stabile, enabled, 1–2 condizioni, messaggio) + registry delle variabili: risolutore puro `WeatherReport` → valore (Double/Boolean), testabile a tabella; soglie **memorizzate in unità metriche canoniche** come il dominio, nomi e valori resi nell'unità delle Settings a render time (`current.temp_c` ↔ `current.temp_f` — "un file non mente sulle unità", Fase 7) — `domain/rules/`: 22 variabili curate (11 `current.*` incluso `aqi_index` che risolve null quando l'AQ manca, aggregati `next_6h/12h` con l'ora che li ha prodotti, 3 `today.*`); deviazione minima: `today.high_c`/`low_c` col suffisso unità anche se il daily JSON usa `high` nudo — il principio unità-nel-nome vince (stesse chiavi delle notifiche 9c)
- [x] `RuleEngine` puro sul modello di `AlertEngine` (niente clock/Android/I-O): confronti + `and` singolo; anti-rumore a due semantiche — variabili `current.*` **edge-triggered** (notifica al passaggio falso→vero, riarmo quando torna falso), aggregati forecast con **fingerprint a bucket temporale** (`cityKey:rule_id:data:AM|PM`, come il precip della 9c); stato in DataStore accanto ad `alerts`, fingerprint registrato solo dopo notify riuscito — in più `RuleEngine.check` stateless (il motore del dry run); regola mista current+aggregato → fingerprint; dato mancante ≠ falso (non riarma il latch); l'unlatch si applica comunque, il latch/fingerprint solo a notify riuscito
- [x] Interpolazione `{…}` nel messaggio: stesse variabili + namespace `trigger.*` (valore e ora che hanno fatto scattare la regola — è ciò che rende il messaggio utile invece che generico); placeholder ignoto reso letterale, mai un crash — `RuleMessages`: risolve sia il nome canonico che quello nell'unità corrente (`{current.temp_f}`), valori formattati nell'unità delle Settings
- [x] Persistenza regole: DataStore `rules` (JSON array, pattern di `cities_json`), tetto basso (~10 regole) — `RuleStore` con contatore id monotono **mai riciclato** (gli id chiavano notification id, latch e fingerprint: una regola nuova non deve ereditare lo stato di una rimossa); `+ add rule` crea il template ombrello già funzionante; `RuleStateStore` separato (non bumpa `// Last modified:`) con `clearRule` chiamato a ogni edit delle condizioni — un latch vecchio con una soglia nuova resterebbe muto
- [x] UI `alerts.rules`: `EditorTabs` in Settings con scroll separato per tab (pattern Logs), rendering come sorgente con i token esistenti (zero nuovi colori), editing a token, `+ add rule` in stile diff, `[rm]` per regola e per condizione `and`, enabled per regola come boolean tappabile (`boolLine`); commento header che rimanda agli alert builtin (`// builtin alerts (severe, precip, daily) live in settings.config` — restano toggle lì, vedi decisione in cima alla fase) — `RulesScreen`+`RulesViewModel`: variabile → picker inline (l'autocomplete IDE disegnato come righe, voce attiva `// selected`), operatore cicla al tap (i boolean limitati a `==`/`!=` e la soglia `true/false` commuta invece di aprire l'editor), soglia/nome/messaggio → `TerminalInput` in place con commit su IME Done; nome sanitizzato a slug (nomina il comando e il fingerprint); regole disattivate da `user_rules` off → riga `// WARN:` in cima al file
- [x] Notifica: canale dedicato (importanza DEFAULT), id derivato dall'id regola (stessa regola sovrascrive, regole diverse coesistono), corpo sul pattern 9c — riga di comando `$ tweather run <rule_id>` + messaggio utente interpolato — `RuleNotifier`: id 2000+ (mai in collisione con i 1001–1003 builtin), titolo `🔔 <nome> — <città>` (translatable=false: non contiene parole); a differenza della 9c il corpo NON è JSON: il messaggio è contenuto dell'utente, nella sua lingua, e si mostra com'è
- [x] **Dry run**: comando `$ tweather run rules` in fondo al file (conferma a due tap come `$ git restore settings.config`): valuta tutte le regole sui dati correnti e mostra l'esito inline per regola (`✓ pass` / `✗ notify: "<messaggio interpolato>"`), senza toccare fingerprint né inviare notifiche vere — senza feedback immediato un sistema di automazione non viene adottato — valuta anche le regole disabilitate (si sta testando il file, non lo scheduler) e le non risolvibili escono come `// ? unavailable: <variabile>`; passa dalla cache/TTL normale (di solito zero GET), errori nel canale `// ERROR:` esistente; ogni edit invalida i risultati
- [x] Integrazione worker e scheduler: valutazione dopo il blocco AlertEngine nello stesso fetch; regole utente attive contano in `alertsWanted` (`AlertScheduler`); le regole scattate compaiono nel commit dei Logs come righe di check (`✓ rule "umbrella" fired`) — **solo quelle scattate**, i pass sarebbero rumore — un `alerts.rules` vuoto non tiene in vita il polling (conta `hasEnabledRules`); il collector di reconcile in MainActivity osserva anche il rule store (la prima regola arma il job, l'ultima rimossa lo lascia auto-cancellare); check lines via **Room v3** (`fired_rules`, migrazione 2→3 testata): UPDATE sull'ultimo commit della città dopo i notify riusciti — corretto anche su cache HIT, quei dati SONO l'ultimo commit
- [x] Toggle master `user_rules` nella sezione `notifications` di `settings.config`, dietro il flusso `POST_NOTIFICATIONS` esistente — default **true** (scrivere una regola basta: il toggle pesa solo quando esistono regole), hint `// alerts.rules`; la riga di stato `// polling every N min` e il gating considerano anche le regole
- [x] Test (registry variabili, engine table-driven con edge/fingerprint, interpolazione, UI Compose del token editing e del dry run, worker) + lint pulito + release minificata — suite a **301** (+57: `RuleVariablesTest`, `RuleEngineTest`, `RuleMessagesTest`, `RuleStoreTest`, `RuleStateStoreTest`, `RuleNotifierTest`, `RulesScreenTest`, migrazione v2→v3, check lines nei Logs, scheduler e worker estesi); lint senza errori, `assembleRelease` minificata compilata
- [x] **Bugfix da verifica su device (1ª tornata)**: il doppio tap su `$ tweather run rules` crashava appena una regola produceva un `Fires` — la regex dei placeholder aveva la `}` di chiusura non escapata, legale sul JVM dei test ma respinta dal motore ICU di Android: `RuleMessages` moriva alla prima inizializzazione (che avviene pigramente proprio alla prima regola che scatta) con `ExceptionInInitializerError`, un `Error` fuori dal catch. Circoscritto con un test end-to-end su JVM (`RulesDryRunEndToEndTest`: repository su disk cache primata, zero rete) risultato verde → colpa Android-only. Regex escapata; in più il dry run ora cattura `Throwable` e rende `// ERROR: panic: <eccezione>` invece di crashare — la prossima anomalia si diagnostica dal file
- [x] **Bugfix pre-esistente trovato durante la verifica** (meccanica della 6b, visibile dalla 10b): la larghezza condivisa delle righe di `CodeCanvas` misurava solo le `CodeLine` — una `WidgetLine` più larga di ogni riga di testo veniva strizzata e troncata in silenzio (`Cerca località` → `Cerc`, `[rm]` → `[r` in cities.json; colpiva anche le righe-condizione di alerts.rules). Ora ogni `WidgetLine` dichiara il proprio equivalente testuale monospace (`measureText`, con margine per i padding dei token) e partecipa alla misura; regression test che fallisce senza il fix (verificato). Suite a 304
- [x] Verifica manuale su device — completata dal committente sugli APK di CI (ago 2026, tre giri: funzionalità base, poi il fix del crash regex-ICU sulle regole che scattano, poi il fix del troncamento delle WidgetLine): token editing, unità, dry run, notifiche reali con check line nei Logs, migrazione Room senza perdita dello storico, toggle `user_rules`

## Fase 11b — Restyling pre-v1

Passata di rifinitura chiesta dal committente (ago 2026) a feature complete: nessuna funzionalità nuova, solo i residui visivi che si sono accumulati nelle fasi 9–11. Decisioni prese in apertura:

- **L'ordine dei tab della schermata principale resta `weather_data.json` + `README.md`, con il JSON come default all'installazione.** Sull'usabilità pura vincerebbe il README (la sintesi sta in una schermata, il JSON richiede pan e scroll), ma il JSON evidenziato *è* la tesi del prodotto ed è quello che mostrano store, widget e mockup: aprire su un markdown al primo avvio fa sembrare tweather un visualizzatore di markdown. Il costo per chi preferisce il README è un tap una volta sola — `WorkspaceStore` persiste il file attivo, quindi il default conta solo al primissimo avvio. Se un domani la scelta si ribalta, **ordine e default vanno cambiati insieme**: primo tab e tab attivo devono coincidere.
- **Fold delle sezioni JSON: archiviato, non rinviato.** Proposto per accorciare le ~250 righe del documento completo, respinto dal committente con l'argomento giusto: le previsioni orarie sono tra le prime cose che si guardano in un meteo, e un fold chiuso per default mette un tap tra l'utente e il dato principale. Un fold aperto per default non risolverebbe nulla (lo scroll resta identico). Il difetto vero non era la lunghezza ma la **posizione** (`hourly_forecast` arriva dopo air quality, pollen e astronomical): riguarda l'ordine delle sezioni, non il collasso. Esigenza coperta meglio dall'idea del committente di portare le orarie anche nel `README.md`, da pianificare a parte.

- [x] Spazio per il FAB a fondo documento (`WeatherScreen`): il `CodeCanvas` usava il `contentPadding` di default (8dp) mentre il FAB occupa 56dp + 24dp di margine, quindi le ultime righe si fermavano *sotto* il pulsante senza modo di liberarle — la coda di `system_info` con la graffa di chiusura sul JSON, il footer del last sync sul README. `contentPadding` con bottom 96dp sulla sola schermata principale: il FAB è l'unico elemento che si sovrappone al canvas, la costante sta lì e non in `CodeCanvas`
- [x] Nav bar: primo tab da "Explorer"/"Esplora" con glifo ad albero (`AccountTree`) a **"Editor" con `DataObject` (`{ }`)** — dalla 10b lì non c'è più nessun albero di file (`cities/` è dentro Cerca), l'icona prometteva una cosa che non esiste. Deviazione consapevole dal mockup, approvata dal committente. La rotta resta `"explorer"`: è chiave di selezione e di stack salvato, non una stringa visibile
- [x] Indicatore del file attivo su **tutte** le schermate: `cities.json` e `widget.config` erano gli unici due file senza la barra 2px sotto il nome (usavano il vecchio `EditorTab` a file singolo invece di `EditorTabs`), e lo stacco si notava a ogni ingresso. Ora passano una lista di un elemento; `EditorTab` rimosso, resta un solo componente di chrome. Su richiesta del committente esteso anche a `widget.config` così un eventuale secondo file entra senza toccare il layout
- [x] Test + lint + release minificata: suite a **309** (+5: `EditorTabsTest` sulla striscia a un elemento, più un'asserzione di tab selezionato per `cities.json` e `widget.config`), lint senza errori, `assembleRelease` minificata compilata. **Lo spazio per il FAB non è coperto da test** e non lo sarà: verificarlo richiede di raggiungere il fondo di una `LazyColumn` di ~250 righe da un test, e ogni riga del canvas ha il proprio `horizontalScroll` (quindi nessuno scrollabile identificabile né nodo componibile su cui fare `performScrollTo`). È una costante di layout con effetto puramente visivo: va sull'occhio, come i fix visivi della 11
- [x] Verifica manuale su device — completata dal committente sull'APK di CI (ago 2026, un giro solo): coda del documento libera dal FAB su JSON e README (il punto senza test), etichetta e icona del primo tab **approvate viste sullo schermo**, indicatore attivo su `cities.json` e `widget.config`. Con questo la passata è chiusa e **il lavoro sulle feature è finito**: resta solo la Fase 12
- [x] **Test instabile smascherato dalla CI rossa sul commit di sola documentazione** (ago 2026): `RulesDryRunEndToEndTest > a firing rule produces an interpolated notify line` è caduto con `TimeoutCancellationException` su un commit che tocca solo `PLANNING.md`, cioè a codice identico al run verde precedente — quindi flake, non regressione. Dal report della CI (l'artifact `tweather-reports` sui fallimenti) il timeout non era sul dry run — il test gemello faceva l'intero dry run in 0.105s — ma sul **setup**: `viewModel.cycleOp()` è fire-and-forget su `viewModelScope` e il test aspettava il round-trip su DataStore con `first { op == LT }` a budget 10s, un'attesa guidata da nessuno che sotto contesa di CPU si è piantata. Alzare il timeout avrebbe solo nascosto il problema (non era lentezza: era bloccata). Ora il setup flippa l'operatore con `ruleStore.update(...)`, una `suspend` che il test **attende**, più una rilettura di verifica; il ciclo dell'operatore resta coperto da `RulesScreenTest`. Il `withTimeout` su `dryRun` resta: quello aspetta l'output del soggetto sotto test, che è legittimo

## Fase 11c — Tabelle formattate e previsioni orarie nel `README.md`

Richiesta del committente (ago 2026) a valle della 11b: le tabelle del `README.md` devono essere **incolonnate** come le scriverebbe chiunque a mano, e mancano le **previsioni orarie**, il dato più consultato di un'app meteo. La seconda chiude il punto lasciato aperto dalla 11b ("esigenza coperta meglio dall'idea del committente di portare le orarie anche nel `README.md`, da pianificare a parte") e **supera la decisione della Fase 10** che teneva fuori il dettaglio orario. Decisioni prese in apertura:

- **Allineare le tabelle non è un vezzo, è la vista sorgente.** Il README è reso come *source* (Fase 10, la vista "Code" di GitHub, non la Preview): lì una tabella pipe non allineata è semplicemente formattata male, e la allineano Prettier, il table editor di Obsidian e chiunque scriva markdown a mano. Il file lo faceva già a metà, ed è il sintomo: la riga separatore si dimensionava sull'intestazione (`"-".repeat(header.length)`) mentre le celle no. Le colonne numeriche prendono anche il marker `---:` vero, così padding e sintassi dicono la stessa cosa. L'allineamento vive solo nella vista sorgente (un renderer markdown collasserebbe gli spazi): è esattamente giusto per un'app che una Preview non ce l'ha, ed è lo stesso mestiere dell'allineare gli `=` in un file di config.
- **L'emoji sta sul bordo destro della cella, esattamente una per cella.** Nessuna delle 12 emoji meteo esiste in JetBrains Mono (verificato sulla cmap del font): vengono tutte dal font emoji di sistema, ~1.25em contro 0.6em, cioè ~2 celle di carattere. Da qui le due regole che fanno tornare le colonne: (a) una cella si misura sul **testo**, mai sulla stringa che porta l'emoji (`"🌧️".length` sono 3 unità UTF-16 per un glifo solo, e quel glifo non è largo un carattere), (b) con una sola emoji in fondo a ogni cella la larghezza sconosciuta del glifo è la **stessa costante su ogni riga**, quindi la pipe successiva resta in colonna comunque venga disegnata sul device. La costante `EmojiCells = 2` decide solo il padding di intestazione e separatore, che emoji non ne hanno.
- **Orarie sì, ma 12 righe, non 24.** Riversare le 24 ore nel README ne farebbe un secondo dump completo e cancellerebbe la distinzione tra i due tab (sommario curato vs sorgente dati), che è l'unica cosa che giustifica due file. Dodici ore è l'orizzonte dell'app — l'AlertEngine guarda 12h avanti per il severe e le regole hanno il namespace `next_12h.*` — quindi la tabella e il badge `## Status` sotto descrivono la stessa finestra; oltre subentra la tabella giornaliera, senza sovrapposizione.
- **Risoluzione oraria, non campionata.** Valutato e scartato il campionamento a 3 ore su 24 (8 righe, più copertura): chi apre le orarie vuole sapere *a che ora* piove, e un campione ogni 3 ore quella risposta la perde. Scartata anche la sparkline monospace, che pure sarebbe stata esatta (i Block Elements U+2581–2588 in JetBrains Mono ci sono, verificato): dà la forma, non l'ora. Semmai un complemento, mai un sostituto, e due rappresentazioni della stessa cosa nella stessa sezione sono ridondanza, non ricchezza.
- **Niente colonna descrizione nelle orarie**: a risoluzione oraria si ripete per righe intere, ed è la sola emoji a tenere la tabella entro ~33 caratteri, leggibile senza pan in entrambe le lingue. La giornaliera in italiano il pan lo chiede ("Parzialmente nuvoloso" porta la colonna Stato a 24 celle): accettato, il pan è nativo della metafora e il JSON lo fa da sempre.
- **Posizione: subito dopo `## Today`**, prima di tutte le sezioni di dettaglio. È letteralmente il difetto diagnosticato dalla 11b sul JSON (`hourly_forecast` sepolto dopo air quality, pollini e astronomical): ripeterlo nel README sarebbe stato assurdo. **Deciso in corsa dal committente**: sale anche `## Forecast`, subito dopo le orarie. Le due previsioni si leggono di seguito (ore → giorni) e il dettaglio scende sotto; ordine finale del documento: Current, Today, Next hours, Forecast, Conditions, Air quality, Astronomy, Status.
- **`Giorno` → `Gg` nell'intestazione italiana della tabella giornaliera** (richiesta del committente): in italiano la colonna Stato arriva a 25 celle nel caso peggiore ("Temporale con grandine"), e i tre caratteri risparmiati *prima* di essa sono esattamente la differenza tra vederla intera sullo schermo e doverla inseguire in pan. `gg` è l'abbreviazione italiana standard di giorno — quella delle maschere di data `gg/mm/aaaa` — e non collide con nessun nome di giorno, a differenza di `Gio` che è giovedì. Col minimo di 3 caratteri del separatore markdown la colonna resta larga 3, quanto i `Lun`/`Mar` che contiene. L'inglese resta `Day`: è già di tre.

- [x] `MarkdownTable.kt`: formattatore puro e riusabile (colonne con allineamento, celle testo + emoji), usato da entrambe le tabelle del README — larghezza misurata sul testo, emoji sul bordo destro, separatore `---:` sulle colonne numeriche, minimo `---` perché una colonna stretta resti markdown legale
- [x] `## Next hours` in `WeatherReadme.kt`: ora, temperatura, emoji del cielo, probabilità di pioggia sulle prossime 12 ore, subito dopo `## Today`. Sezione **assente** se l'API non ha restituito orarie, come per qualità dell'aria e pollini (un README documenta ciò che esiste)
- [x] Stringhe nuove EN+IT: `readme_h_hourly`, `readme_t_hour`, `readme_t_temp`; `readme_t_status` e `readme_t_rain` riusate dalla tabella giornaliera, così le due tabelle parlano lo stesso vocabolario
- [x] `@Preview` di `MarkdownSyntax` aggiornata alla forma allineata (era l'unico posto dove restava una tabella ragged) e nota nel KDoc: le righe arrivano già paddate
- [x] `## Forecast` spostata subito dopo `## Next hours` e intestazione italiana del giorno abbreviata (seconda tornata, su richiesta del committente a documento visto)
- [x] Test + lint + release minificata: suite a **317** (+8: `MarkdownTableTest` sul formattatore — padding, marker `---:`, cella sola-icona, e la prova che tre emoji di 2, 1 e 3 unità UTF-16 finiscono tutte allo stesso offset; nella `WeatherReadmeTest` la tabella oraria completa, il taglio a 12 ore e la sezione assente senza dati orari, più i golden aggiornati delle tabelle EN/IT/°F). Lint senza errori, `assembleRelease` minificata compilata
- [x] Verifica manuale su device — completata dal committente (ago 2026): screenshot dal device in apertura della richiesta 11d, tabella oraria perfettamente incolonnata con emoji miste (🌦/☁️/⛈) sul font emoji del device. Le colonne tornano sullo schermo, la riserva sul glifo è chiusa

## Fase 11d — Descrizione nello stato orario, colonna Stato in coda, emoji davanti

Richiesta del committente (ago 2026) a valle dell'uso su device della 11c: nella tabella oraria la sola emoji è ambigua (🌦 e ☁️ si alternano senza dire cosa siano di preciso) e vuole la descrizione come nella giornaliera. **Supera la decisione della 11c** ("Niente colonna descrizione nelle orarie") allo stesso modo in cui la 11c aveva superato la Fase 10: a documento visto. Decisioni prese in apertura:

- **La descrizione entra nelle orarie.** L'argomento della 11c ("a risoluzione oraria si ripete per righe intere") si ribalta: la ripetizione è essa stessa informazione — a colpo d'occhio si vede *a che ora* gira il tempo — e il costo in larghezza (~53 celle nel caso peggiore italiano) è lo stesso ordine già accettato dalla giornaliera (~60), su un canvas che il pan orizzontale ce l'ha nativo.
- **Colonna Stato in coda in ENTRAMBE le tabelle.** Le colonne numeriche (ora/temp/pioggia; gg/max/min/pioggia) restano sempre sullo schermo; a sforare è solo la descrizione, che tronca senza nascondere dati ("Temporale con grand" si legge lo stesso). Nella giornaliera questo ripara anche un difetto della 11c: la Pioggia stava *dopo* la descrizione lunga, cioè fuori schermo proprio nei giorni brutti, quando serve di più. E le due tabelle diventano simmetriche: stesso vocabolario (11c), ora stesso ordine.
- **Emoji sul bordo SINISTRO della cella, descrizione dopo** (richiesta esplicita del committente): il cielo si legge a colpo d'occhio anche quando la descrizione tronca al bordo dello schermo. L'invariante di allineamento regge a specchio: già la garanzia della 11c si fondava sull'assunzione che le *diverse* emoji meteo condividano lo stesso avanzamento nel font di sistema (le righe portano emoji diverse tra loro); col glifo in testa ogni descrizione parte allo stesso offset su ogni riga e la pipe di chiusura resta in colonna per lo stesso motivo di prima. Con Stato ultima colonna, dopo la cella non resta nulla da disallineare se non la pipe finale.
- **Le righe in prosa restano "descrizione emoji"** (`## Attuale`/`## Current`, la luna in Astronomy): in un testo scorrevole l'emoji in coda è la posizione naturale; l'inversione serve solo dove c'è una colonna da tenere leggibile. Proposto e condiviso col committente.

- [x] `MarkdownTable.kt`: `pad()` emette `emoji + spazio + testo + padding` (il padding segue, mai precede il glifo); `TableCell.icon()` rimossa — senza celle solo-emoji non ha più usi
- [x] `WeatherReadme.kt`: entrambe le tabelle riordinate a `… | Pioggia | Stato`, cella oraria con `translate(description)` + emoji come la giornaliera
- [x] `@Preview` di `MarkdownSyntax` allineata alla nuova forma (emoji in testa)
- [x] Test: golden EN/IT/°F rifatti nelle `WeatherReadmeTest`; in `MarkdownTableTest` il test della cella sola-icona sostituito 1:1 da emoji-in-testa-anche-su-colonna-right-aligned, e il test UTF-16 ora fissa che i suffissi dopo il glifo siano identici riga per riga (con l'emoji a sinistra è il lato destro a dipendere dalla misura). Suite a **317** come la 11c — il test rimosso è sostituito 1:1 — tutta verde, lint senza errori
- [x] Verifica manuale su device — completata dal committente sull'APK di CI (ago 2026): "provate e tutto ok", colonne vere con l'emoji in testa sul font emoji del device

## Fase 11e — Le orarie partono dall'ora successiva, 14 righe, pioggia in `## Attuale`

Richiesta del committente (ago 2026) sull'app in uso: la prima riga di `## Prossime ore` è l'ora corrente — verificato: il mapper fa partire `hourly` dalla prima ora non precedente a `now` troncato all'ora (`WeatherReportMapper`, `currentHourIndex`) — quindi duplica la sezione `## Attuale` appena sopra, e alle 08:44 la riga "08:00" descrive per giunta un'ora già quasi trascorsa. Decisioni prese in apertura:

- **La tabella parte dall'ora successiva** (`hourly.drop(1)` nel solo compositore del README): l'ora corrente la racconta `## Attuale`, la tabella legge `+1h..+14h` senza buchi né doppioni. Il dominio resta intatto — `report.hourly` continua a partire dall'ora corrente, così AlertEngine, regole (`RuleVariables` filtra già `!isBefore(now)`, che esclude da sé lo slot corrente a ora iniziata) e diff dei Logs non cambiano di una virgola.
- **La probabilità di pioggia dell'ora corrente sale in `## Attuale`**, sulla riga della Percepita (`Percepita: 26.4°C · Pioggia: 45%`), non su una riga nuova: la sezione resta un colpo d'occhio da due righe. Nessun dato "spostato" dalla tabella: il dominio ce l'aveva già come `current.precipitation.chancePct`, che il mapper riempie esattamente dalla stessa cella oraria della riga eliminata. Etichetta riusata dalla tabella (`readme_t_rain`), zero stringhe nuove; "Precipitazioni" resta il vocabolo di `## Oggi` per il dato giornaliero.
- **14 righe invece di 12** (richiesta esplicita): al mattino la tabella arriva alla sera — alle 08:00 si legge fino alle 22:00. **Supera la simmetria della 11c** con l'orizzonte a 12h dell'AlertEngine/`next_12h.*`; il contro-argomento del committente ha chiuso la questione: il tab gemello `weather_data.json` mostra 24 ore e quella simmetria non l'ha mai avuta. Il badge `## Status` continua a guardare 12h avanti, per suo conto. Copertura garantita: il dominio porta 24 ore dall'ora corrente, `+1..+14` c'è sempre.

- [x] `WeatherReadme.kt`: `drop(1).take(HourlyRows)`, `HourlyRows = 14` con KDoc riscritto, `Pioggia: n%` sulla riga della Percepita
- [x] Test: golden EN/IT/°F sull'ora di partenza spostata e sulla riga Percepita; il test del taglio passa a 14 righe (16:00→05:00 su 24 generate); nuovo test: con la SOLA ora corrente in `hourly` la sezione è assente (è compito di `## Attuale`)
- [x] Docs: CLAUDE.md, README di root (paragrafo "Twelve hours" riscritto a 14), questa sezione
- [x] Verifica manuale su device — completata dal committente sull'APK di CI (ago 2026): prima riga = ora successiva a quella corrente, `Pioggia: n%` in `## Attuale` coerente con l'ex prima riga, 14 righe

## Fase 11f — Anche `hourly_forecast` nel JSON parte dall'ora successiva (24 righe piene)

Richiesta del committente (ago 2026), estensione naturale della 11e al tab gemello: anche in `weather_data.json` la prima riga di `hourly_forecast` è lo slot dell'ora corrente, ridondante con `current_conditions` appena sopra (non *identica* — `current` è il blocco istantaneo di Open-Meteo, lo slot è la previsione dell'ora — ma stessa ora e stessa informazione, e `chance_pct` sta già in `current_conditions.precipitation`). Decisioni:

- **`drop(1)` nel solo renderer JSON** (`WeatherJson.kt`), come nel README: il dominio resta ancorato all'ora corrente — slot 0 riempie `current_conditions.precipitation.chance_pct` nel mapper ed è il riferimento di AlertEngine e regole — quindi motori, dedup e Logs invariati.
- **Variante a 24 righe piene** (scelta del committente tra 23 e 24): `HOURLY_WINDOW` nel mapper passa da 24 a **25** — l'ora corrente più il giorno intero che le viste mostrano da `+1h` — così `hourly_forecast` legge `+1h..+24h` senza accorciarsi. Room non persiste le orarie: nessun impatto su snapshot e diff.

- [x] `WeatherReportMapper.kt`: `HOURLY_WINDOW = 25` con KDoc sul perché dello slot in più
- [x] `WeatherJson.kt`: `hourly.drop(1)` in `hourly_forecast`
- [x] Test: mapper a 25 slot (ultimo = stessa ora del giorno dopo), prima riga JSON = ora successiva (EN e imperiale); il test del clipping rinominato (il taglio non è più "sotto 24")
- [x] Docs: CLAUDE.md e questa sezione (il README di root non quantifica le righe del JSON, nulla da toccare)
- [x] Verifica manuale su device — completata dal committente sull'APK di CI (ago 2026): prima riga di `hourly_forecast` = ora successiva, 24 righe, `chance_pct` in `current_conditions` al posto della riga eliminata

## Fase 12 — Release

Decisioni di apertura (ago 2026), condivise col committente: **pubblicazione solo su GitHub** per ora (niente account sviluppatore Play; il Play Store resta un capitolo futuro — stessa chiave come upload key, si caricherà un AAB), nella release va **il solo APK release firmato con la chiave vera** più il `mapping.txt` R8 (gli artifact di CI scadono a 30 giorni, la mappa di una versione pubblicata serve per sempre; col sorgente pubblico non nasconde nulla). Keystore reale generato dal committente con `keytool` (RSA 4096, 30 anni, alias `tweather`), custodito FUORI dal repo in `C:\Fiorenzo\keys\` con backup nel password manager; stessa operazione fatta in parallelo per tsteps (e chiavi già pronte per saldo e snake).

- [x] Signing config reale: la `release` signingConfig nasce solo se le 4 proprietà `TWEATHER_*` sono valorizzate (da `~/.gradle/gradle.properties` in locale, da `ORG_GRADLE_PROJECT_*`/GitHub Secrets in CI) e vince sempre quando c'è; il flag `-PsignReleaseWithDebugKey` resta per la CI per-push, il checkout non configurato resta non firmato. `*.jks` in `.gitignore` per cintura di sicurezza. R8/ProGuard erano già a posto dalla fase della CI.
- [x] Workflow `release.yml` su tag `v*`: test + lint (una suite rossa non pubblica), APK firmato con la chiave vera, GitHub Release automatica con APK e mapping rinominati sul tag (`tweather-v1.0.0.apk`)
- [x] Secrets sul repo GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — caricati dal committente via `gh secret set` (ago 2026), stessi nomi su tsteps
- [x] Verifica firma in locale: password compilate in `~/.gradle/gradle.properties`, `assembleRelease` produce `app-release.apk` firmato con la chiave vera — certificato controllato con apksigner (`CN=callbackdev`, chiave distinta da tsteps). Nota: il primo APK con la firma nuova richiede disinstallazione di quello debug-signed sul device
- [x] README curato al posto di "screenshot e testi per lo store": sezione Install che punta alla latest release (con l'avviso di Android spiegato), link al changelog, paragrafo sul workflow di release; la pagina release la compone il workflow (APK + mapping + note generate)
- [x] Versione 1.0.0 (`versionName`; `versionCode` resta 1) e `CHANGELOG.md`; tag `v1.0.0` su main → **prima GitHub Release pubblicata dal workflow** (20 ago 2026): `tweather-v1.0.0.apk` + `mapping-v1.0.0.txt` allegati, APK riscaricato e firma verificata con apksigner — stesso SHA-256 del certificato della chiave vera. **Fase 12 chiusa, v1.0.0 fuori.**
- [x] CI build + test su push — già chiusa dalla fase che ha introdotto `android-ci.yml`

## Fase 13 — Verifica dati Open-Meteo e UV giornaliero nel README (post-1.0)

Segnalazione del committente (21 ago 2026, screenshot del `README.md` di Cavenago di Brianza alle 23:52): la tabella `## Prossime ore` segna **nebbia** alle 03, 05, 06, 07, 08 e 09 di una notte d'agosto, e in mezzo due ore serene — "impossibile". Richiesta: verifica completa della lettura dei dati da Open-Meteo e della loro resa nell'app.

**Verifica: l'app è fedele, la nebbia è del provider.** API interrogata dal vivo sulle stesse coordinate (45.589 / 9.419) e confrontata riga per riga con lo screenshot: `weather_code` = 45 in tutte e sei le ore contestate, 1 alle 02:00 e 0 alle 04:00 (le due ore serene), 2 alle 10-11, 0 alle 12-13; temperature identiche al decimale dopo l'arrotondamento (17.7→18°, 16.2→16°, 22.5→23°). Nessun disallineamento di indici tra `temperature_2m`, `weather_code`, `precipitation_probability` e `is_day` (il mapper li legge dallo stesso `i`), `currentHourIndex` correttamente ancorato all'ora troncata, mapping WMO conforme alla tabella pubblicata da Open-Meteo. Diagnostica del modello (`best_match` = ICON per l'Italia): alle 07-09 umidità 100%, temperatura **uguale** al punto di rugiada, nubi basse 84-100%, visibilità 60-260 m — nebbia da irraggiamento fisicamente coerente dopo una giornata con 100% di precipitazione e suolo saturo, anche ad agosto; alle 03, 05 e 06 invece il codice 45 arriva con visibilità 8-11 km e cielo quasi sereno, cioè è un difetto della derivazione del codice in ICON (ECMWF dà 3 su tutta la notte, GFS 3/0). **Decisione del committente: sulla nebbia non si interviene** — l'app resta lo specchio fedele del provider, niente riclassificazione dei codici 45/48 sulla visibilità oraria.

**Perché sul sito open-meteo.com l'icona non è una nebbia** (domanda del committente, indagata sul repo `open-meteo/open-meteo-website`): non viene da un altro parametro. `src/lib/components/response/highcharts/json-to-chart.ts` legge lo **stesso** `data.hourly.weather_code` e lo passa a `getWeatherIconName` di `src/lib/utils/weather-codes.ts`, la cui tabella però non è la WMO 4677 (ww) che il sito stesso documenta in `wmo-codes-table.svelte` ("45, 48: Fog and depositing rime fog"): mappa `45 → hail`, `48 → snow`, `65 → hail`, `99 → tornado`, e manda a `fog` codici (4, 5, 10, 11, 20, 30-35) che Open-Meteo non emette nemmeno. È la mappatura **wmo4680** (wawa, stazioni automatiche) della libreria weather-icons applicata a un valore ww — provenienza verificata sul CSS della libreria, dove `wi-wmo4680-45/46` puntano a `015` = `wi-hail` e `47/48` a `01b` = `wi-snow`, esattamente le voci della tabella del sito: sul sito il codice 45 (nebbia) diventa un'icona di grandine. Il difetto è dell'icona del sito, non del dato né del nostro mapping — nessuna azione da parte nostra, ma vale come conferma che `weather_code` è l'unica fonte dell'aspetto meteo.

**Bug vero emerso dalla verifica: l'indice UV del README.** Sotto `## Oggi`, accanto a max/min e precipitazione (tutti valori giornalieri), il file stampava `current.uvIndex`, cioè la lettura **istantanea**: alle 23:52 "Indice UV: 0 (Basso)" in un giorno il cui massimo era 2.1, e ogni sera lo stesso. Causa a monte: `uv_index_max` era già richiesto all'API e deserializzato in `DailyDto`, ma `mapDaily` non lo copiava nel dominio, quindi il dato non arrivava mai alle viste.

- [x] `WeatherModels.kt`: `DailyForecast` porta `uvIndexMax` + `uvDescription` (specularmente a `CurrentConditions`), con KDoc sul perché non è la lettura istantanea
- [x] `WeatherReportMapper.kt`: `mapDaily` mappa `uv_index_max` (arrotondato) e ne deriva l'etichetta con `WeatherCodes.uvDescription`
- [x] `WeatherReadme.kt`: la riga UV entra nel blocco `daily.firstOrNull()` e usa il massimo di oggi — è una riga di `## Oggi`, non di `## Attuale`
- [x] `WeatherJson.kt`: `uv_index_max` nelle righe di `daily_forecast`, dietro `show_details` (la riga compatta resta giorno/max/min/stato/pioggia); chiave con `_max` esplicito per non confondersi con l'`uv_index` istantaneo di `current_conditions`
- [x] `RuleVariables.kt`: nuova variabile `today.uv_max` per `alerts.rules` — una regola "metti la crema" deve poter scattare al mattino, quando l'indice istantaneo è ancora basso
- [x] Test: mapper (6 → `"High ☀️"`), README golden (5 = massimo di oggi, **non** 4 = istantaneo del campione), `today.uv_max`, JSON con e senza dettagli; fixture del campione e degli snapshot aggiornate
- [x] Nessun impatto su `WeatherSnapshots.flattenForecast`: il diff dei Logs continua a seguire stato/max/min/pioggia, l'UV non entra nei commit (scelta, non dimenticanza)
- [x] Verifica manuale su device (committente): `## Oggi` mostra il massimo del giorno anche di sera — **fatta** (29 ago 2026, giro di verifica pre-2.0.0)

---

## Fase 13b — Il codice del giorno lo aggrega l'app, non il provider (post-1.0)

Riapertura del capitolo nebbia (22 ago 2026): il committente porta un documento che propone di scartare i codici 45/48 quando lo scarto fra temperatura e punto di rugiada supera i 2 °C ("aria non satura, quindi è caligine, non nebbia"). **Verificato e scartato: sui dati veri il filtro non scatta mai.** Interrogate 8 città padane (Cavenago, Milano, Pavia, Bologna, Mantova, Torino, Verona, Ferrara), 1344 ore: 18 con codice 45/48 e **zero** con scarto > 2 °C — le sei di Cavenago stanno fra 0.0 e 0.5 °C con umidità 97-100%. Il motivo è strutturale: il punto di rugiada è derivato dallo stesso campo di umidità che genera le nubi basse, quindi il modello è sempre coerente con sé stesso sulla termodinamica. Cade anche la diagnosi del documento ("visibilità < 1000 m forza il 45"): la regola esiste davvero (`Sources/App/Helper/WeatherCode.swift:99`, dopo i controlli su temporali/neve/precipitazione, non prima della nuvolosità), ma alle 01 e alle 03 il 45 arriva con **10 km** di visibilità mentre alle 06-07 la nebbia a 160 m è etichettata `3` (coperto). Anche a modello singolo (`icon_d2`) il `weather_code` servito non è ricostruibile dalle altre variabili servite alla stessa ora in 12 casi su 24, nuvolosità compresa: è categoriale e interpolato diversamente dai campi continui. **Confermata la decisione di Fase 13 sui codici orari: nessuna riclassificazione, `## Prossime ore` resta lo specchio del provider.**

**Il difetto che vale la pena correggere è un altro: l'aggregazione giornaliera.** `daily.weather_code` è il `max()` dei 24 codici orari (la doc lo chiama "the most severe weather condition on a given day", il maintainer conferma "maximum of the weather code when aggregating daily" in [discussion #1292](https://github.com/open-meteo/open-meteo/discussions/1292), e la misura lo verifica: `daily == max(hourly)` in 56 giorni su 56). È l'unica sintesi che sceglie *garantito* l'ora meno rappresentativa: 18 ore di nebbia su 1344 sono l'1.3% delle ore ma riscrivono l'etichetta di **5 giorni su 56**, e lo stesso meccanismo stampa "temporale con grandine" su una settimana intera per un picco di CAPE alle 3 di notte. Il 22 ago il giorno di Cavenago era `Foggy 🌫️` con la moda delle 14 ore diurne a "prevalentemente sereno". La nebbia era il sintomo, il `max()` è la malattia. Nota: nemmeno il documento sbagliava su questo punto (il suo Step 4), ed è l'unico che abbiamo tenuto.

**Precedente di settore**: nessuno dei provider grandi dà un codice unico per 24 ore. Apple WeatherKit espone `daytimeForecast`/`overnightForecast` (`DayPartForecast`, notte 19-07), la Weather API di Google `daytimeForecast` (07-19)/`nighttimeForecast`, ognuno con la propria `weatherCondition`; MET Norway/Yr dà i simboli solo per periodi (`next_1_hours`, `next_6_hours`, `next_12_hours`) e dichiara l'algoritmo di sintesi troppo complesso da documentare. L'icona di un giorno risponde a "come sarà la giornata", quindi guarda le ore di luce.

**Regola adottata** (in `dailyCode`, `WeatherReportMapper.kt`): prima la pioggia — se una qualunque delle 24 ore ha un codice ≥ 51 (da lì in su la scala WMO è tutta precipitazione) vince il massimo fra quelle; altrimenti il cielo delle sole ore diurne (`is_day == 1`), codice più frequente, pareggio al codice più alto. Le date che l'array orario non copre tengono il codice del provider come fallback.

**La pioggia guarda tutte e 24 le ore: prima versione sbagliata, corretta in corso d'opera.** Il primo taglio restringeva alle ore diurne anche la precipitazione, con l'argomento che la colonna Pioggia porta già il segnale dell'ombrello. Misurato sulle stesse 8 città: **17 giorni su 56 perdevano del tutto la precipitazione**, e in 8 di questi un temporale notturno (95/96) diventava "Coperto". Sotto-avvisare di un temporale è un difetto peggiore della nebbia che stiamo togliendo; tenendo le 24 ore la regola può solo rimuovere una distorsione, mai un avviso. Effetto finale sulle 8 città: 23 giorni su 56 cambiano etichetta, **tutti nella famiglia del cielo** (5 false nebbie rimosse, il resto "Coperto" → "Parzialmente nuvoloso"/"Sereno" dove una singola ora chiusa oscurava una giornata di sole), e nessun giorno con precipitazione cambia.

Compromesso accettato: dentro la famiglia della precipitazione resta il `max()` dei codici WMO, dove 80 (rovesci deboli) supera 65 (pioggia forte). È l'ordinamento del provider, impreciso sull'intensità ma mai sbagliato sul *se* piove; una tabella di severità su misura non vale la manutenzione. Scartata anche la versione completa alla Apple/Google (due colonne Giorno/Notte): allargherebbe una tabella che la Fase 11d ha già faticato a tenere su una riga.

- [x] `WeatherReportMapper.kt`: `mapDaily` riceve gli orari già parsati, raggruppa le ore per data e deriva la condizione con `dailyCode`; `daily.weather_code` resta richiesto perché è il fallback delle date scoperte
- [x] Costante `FIRST_PRECIP_CODE = 51` con il perché della soglia (sotto ci sono solo cielo e nebbia)
- [x] Ricadute automatiche: `## Prossimi giorni` nel README, `daily_forecast` nel JSON e la **notifica di riepilogo del mattino** (`AlertEngine` legge `report.daily.first().condition`, che il 22 ago avrebbe annunciato "Nebbia" su una giornata serena)
- [x] Non toccati: regole utente (`today.*` non espone il codice meteo) e alert severi (45/48 non sono in `SevereCodes`); max, min e probabilità di pioggia restano gli aggregati del provider, che sono corretti
- [x] Test del mapper: nebbia notturna su giornata serena, pioggia diurna che batte il cielo, pioggia notturna che etichetta comunque il giorno, giornata davvero nebbiosa che resta nebbiosa, pareggio al codice più alto, date fuori dall'orizzonte orario che tengono il codice del provider


## Fase 13c — La nebbia oraria riparata con la visibilità del provider (post-1.0)

Richiesta del committente (22 ago 2026, dopo la 13b): correggere la nebbia anche in `## Prossime ore` e, se serve, in `## Attuale`. La Fase 13 aveva deciso di non intervenire perché non esisteva un discriminante affidabile; la 13b ha mostrato che il `weather_code` servito non è ricostruibile dalle altre variabili servite. **Rivalutato con i numeri: un discriminante c'è, ed è la regola di Open-Meteo stessa.** `WeatherCode.swift:99` deriva la nebbia da `visibility <= 1000` una volta escluse le precipitazioni, e altrimenti ricade sulla nuvolosità. Non stiamo inventando meteorologia: stiamo applicando la definizione del provider al campo `visibility` del provider, nell'ora in cui i due si contraddicono. Il codice perde perché è categoriale e interpolato diversamente dai campi continui.

**Misura sulle 8 città padane (1344 ore).** La regola sposta **15 ore, l'1.1%**, in entrambe le direzioni: 8 false nebbie rimosse (a Cavenago il 45 delle 01:00 e delle 03:00 arriva con 9.8 e 10.0 km di visibilità) e 7 nebbie vere aggiunte, dove 120-640 m di visibilità erano serviti come `3` (coperto) o `2`. La seconda direzione è quella pericolosa: alle 07:00 a Cavenago il modello vede 160 m e l'app scriveva "coperto". Verificata anche fuori dall'Italia: `visibility` non è mai nullo in 8 località su 3 continenti (1344 ore), e le ore corrette sono coerenti — nebbia marina a Reykjavík (blocchi di 6-9 ore a 120-400 m) e nebbia da irraggiamento all'alba a Sydney (520 e 820 m con cielo quasi sereno).

**Perché solo la nebbia e non anche il cielo.** Ri-derivare pure i codici 0-3 dal `cloud_cover`, che è il passo successivo ovvio e userebbe la stessa tabella del provider (`WeatherCode.swift:103`), sposterebbe **308 ore su 1344, il 23%**: a quel punto non si ripara un difetto, si sostituisce all'ingrosso la classificazione del provider, e per giunta su un campo che l'app non mostra da nessuna parte (quindi l'utente non può nemmeno verificare la contraddizione). La visibilità invece l'app la stampa, in `## Attuale` e in `current_conditions`: "Nebbia" sopra "Visibilità: 9.8 km" era una contraddizione visibile nella stessa schermata. Scartato quindi il ri-calcolo del cielo; la nebbia si tocca perché è l'unico verdetto che il provider stesso definisce con un numero che l'app possiede.

**Le precipitazioni non si toccano mai** (codici >= 51): non sono derivate dalla visibilità, può benissimo piovere dentro la nebbia, e i temporali dipendono da CAPE/lifted index che l'app non scarica. Una `visibility` nulla lascia il codice esattamente com'era.

Raggio d'azione: `## Prossime ore` e `hourly_forecast`, `## Attuale` e `current_conditions`, il widget, le righe del Logs e — a cascata — l'aggregazione giornaliera della 13b, che ora conta ore già riparate. **Non toccati**: alert severi (`SevereCodes` sono tutti >= 51) e regole utente (nessuna variabile espone il codice meteo; `current.visibility_km` continua a leggere il dato grezzo).

- [x] `OpenMeteoApis.kt`: `visibility,cloud_cover` fra le variabili orarie, `cloud_cover` fra quelle correnti (la visibilità corrente c'era già). Nessuna delle due viene mostrata: servono solo a riparare il codice
- [x] `ForecastDto.kt`: `HourlyDto.visibilityM` (`List<Double?>`, difensivo — il campo dipende dal modello) e `cloudCoverPct`; `CurrentDto.cloudCoverPct`
- [x] `WeatherReportMapper.kt`: `repairFog` con le costanti della soglia (`FOG_VISIBILITY_M`, `WMO_FOG`, `FogCodes`) e `skyCode` con i bucket di nuvolosità del provider; `repairedCodes()` calcolato una volta e passato sia a `mapHourly` sia a `dailyCode`, così le ore e i giorni non possono divergere
- [x] Test: nebbia con chilometri di visibilità che ricade sul cielo, nebbia fitta servita come coperto che diventa nebbia, nebbia vera lasciata stare, precipitazione mai riscritta, visibilità nulla che lascia il codice del provider, stessa riparazione su `## Attuale`; fixture dei test della 13b rese coerenti (visibilità bassa dove il codice dice nebbia)


## Fase 13d — `## Stato` promosso a terza sezione del README (post-1.0)

Domanda del committente (22 ago 2026): `## Stato` è in fondo alla schermata `README.md`, è il posto giusto? **No.** Contate le righe che il documento produce con dati reali (14 ore + 7 giorni), la sezione cadeva alla riga **57 su 58**: `⚠️ Temporale in arrivo verso le 18:00` — l'unica riga azionabile dell'intera pagina — stava due schermate sotto la piega, con la fase lunare sopra di sé. C'era anche un'incoerenza interna: la KDoc del compositore dichiara che `## Status` è il *build badge* del repo, e in un README vero il badge sta sotto l'H1, prima di tutto.

**Scartata però la promozione in cima.** Un badge di build si consulta a colpo d'occhio perché è minuscolo; qui la sezione costa un heading più una riga, lo stesso peso di ogni altra. Sopra `## Attuale` farebbe rispondere all'app "c'è un allerta?" prima di "quanti gradi ci sono?", che è la priorità sbagliata per un'app aperta venti volte al giorno per la temperatura.

**Adottato: terza sezione, subito dopo `## Oggi` e prima di `## Prossime ore`** — dalla riga 57 alla riga ~13, sempre nella prima schermata, senza spostare in basso la temperatura di una sola riga. Completa il blocco del colpo d'occhio (adesso → oggi → c'è qualcosa di cui preoccuparsi) prima che si debba scorrere le due tabelle. La regola della Fase 11c ("le previsioni subito dopo Oggi, prima di ogni sezione di dettaglio") non è violata nello spirito: era scritta per impedire che una pagina di condizioni e pollini separasse le ore dai giorni, e due righe di avviso non sono quella pagina; il commento in `WeatherReadme.kt` è stato aggiornato per dirlo.

**Posizione fissa, non condizionale.** Valutata e scartata la variante "in alto se c'è un avviso, in fondo se non c'è": una sezione che si sposta col contenuto è più difficile da imparare di una anticipata, e `Tutto regolare.` guadagna le sue due righe come le guadagna un badge verde — dice che il controllo è stato fatto. Resta invece invariata la regola della Fase 10 per cui una sezione senza dati sparisce del tutto (`## Qualità dell'aria` fuori dalla copertura pollini): assente non è lo stesso che mobile.

**Secondo scambio, approvato dal committente nella stessa sessione: `## Qualità dell'aria` prima di `## Condizioni`.** AQI e pollini sono azionabili (esco a correre? prendo l'antistaminico?), mentre il blocco sotto è consultazione e la pressione in mb è il valore meno azionabile della pagina. Le due sezioni restano separate e non si fondono: fuori dalla copertura pollini l'aria deve poter sparire per intero (regola della Fase 10), e una sezione che sparisce non può portarsi via il vento e l'umidità.

Ordine finale: Attuale → Oggi → **Stato** → ore → giorni → **Aria** → Condizioni → Astronomia. Gradiente pulito, con l'astronomia in coda perché la fase lunare è vezzo, non utilità.

- [x] `WeatherReadme.kt`: blocco `## Stato` spostato fra `## Oggi` e le due tabelle, con il commento del perché (riga 57 su 58, badge sotto la piega) e la nota che la regola 11c non ne è toccata; KDoc aggiornata
- [x] `WeatherReadme.kt`: blocco `## Qualità dell'aria` spostato sopra `## Condizioni`, con il commento del perché e della ragione per cui non si fondono
- [x] `WeatherReadmeTest.kt`: ordine degli heading aggiornato nel golden; l'asserzione posizionale delle ore non usa più un offset fisso (`## Today` + 5, che il contenuto variabile dello Stato avrebbe reso fragile) ma la sequenza degli heading; nuovo test che lo Stato precede previsioni e astronomia; il test della sezione assente verifica anche che togliere l'aria non sposti la coda dell'ordine


## Fase 13e — Il tap sul ↻ del widget si vede (post-1.0)

Richiesta del committente (25 ago 2026): la freccia di refresh del widget deve cambiare disegno al tap, come quella di tsteps. Portata da lì (tsteps Fase 16), stessa forma e stessi nomi di risorsa.

**Il difetto.** Il tap accodava il sync e ridisegnava lo stesso identico frame: il fetch è un job WorkManager e atterra secondi dopo, quindi per secondi non cambia un pixel; e quando il meteo non è cambiato — o il fetch fallisce — il frame resta identico anche *dopo*. L'unica riga che si muove è `# last_sync`, che è l'ultima del transcript: le taglie che la gente piazza davvero la tagliano. Il risultato è un tasto che sembra morto.

**La soluzione: il glifo indossa il tap.** `↻` diventa `…` in colore commento (con la sua `contentDescription`) mentre il fetch è in volo, e torna al primo repaint utile. È l'unico elemento presente su tutte e tre le tier, small compresa, quindi l'unico riscontro che raggiunge ogni taglia.

**Chi lo fa tornare.** Il primo repaint che arriva: il commit della history quando il fetch riesce (il gancio del repository), il repaint che il worker fa da sé quando il sync fallisce, altrimenti la finestra `BusyWindowMs` (5s) nel provider. La finestra è un **tetto, non un'attesa**: serve al tap che non serve nessuno. tsteps legge il contapassi dentro il broadcast e sa quando ha finito; qui no — il vincolo CONNECTED tiene il job accodato finché la rete non torna, e un widget che resta con `…` per ore sarebbe una bugia peggiore di numeri che non si muovono. Cinque secondi stanno larghi su un fetch normale (due GET dietro un job expedited) e stretti sul budget che un broadcast di background può tenere con `goAsync`.

**Scartato: osservare il lavoro con `getWorkInfosForUniqueWorkFlow`.** In teoria più preciso (il glifo tornerebbe esattamente a job finito), in pratica fragile: `enqueueUniqueWork` registra il lavoro in modo asincrono, e un `WorkInfo` terminale del tap precedente può soddisfare il predicato prima che il nuovo esista — il glifo tornerebbe subito, proprio nel caso che questa fase esiste per riparare. La finestra è deterministica e non dipende dagli interni di WorkManager.

**L'enqueue resta sincrono in `onReceive`**, prima della coroutine: il fetch è quello che il tap sta davvero chiedendo e non deve mettersi in coda dietro a un render.

- [x] `strings.xml` (EN/IT): `widget_refresh_glyph_busy` (`…`, non traducibile come l'altro glifo) e `cd_widget_refresh_busy` per il TalkBack
- [x] `WidgetRenderer`: parametro `syncing` su `render` e `sizeMap`; testo, colore e `contentDescription` impostati su **entrambi** i rami — un glifo scritto solo quando è occupato non tornerebbe più
- [x] `TweatherWidgetUpdater.updateAll(syncing)`: stesso stato persistito, glifo diverso
- [x] `TweatherWidgetProvider.acknowledgeTap`: repaint occupato, finestra, repaint normale; il ramo del tap sostituisce il vecchio `needsRender = true`
- [x] Test: il glifo indossa il tap su tutte le tier (testo, colore e accessibilità, occupato e a riposo)


## Fase 13f — La ricerca città parla la lingua del telefono (post-1.0)

Segnalazione del committente (25 ago 2026): su un dispositivo italiano la maggior parte delle città italiane si trova solo scrivendola in inglese — Firenze è "Florence".

**La causa è una riga nostra, non un limite del provider**: `OpenMeteoApis.kt` fissava `language = "en"` nella query di geocoding. Su Open-Meteo `language` non è un'impostazione di visualizzazione: sceglie anche **l'indice su cui la query fa match**, quindi decide che cosa l'utente può trovare. Misurato sull'API il 25 ago 2026:

| query | `language=en` | `language=it` |
|---|---|---|
| `Firenze` | solo `Firenze Nova`, una frazione | `Firenze`, Toscana, Italia |
| `Napoli` | Napoli (Gambia), Napoli (USA), Nāpoli (India), Napoli (Messico), Napoligu (Ghana) | `Napoli`, Campania, Italia |
| `Roma` | Roma (Romania) in testa | `Roma`, Lazio, in testa |
| `Genova` | Génova (Guatemala) | `Genova`, Liguria |
| `Florence`, `Milan` | Florence, Milan | Firenze, Milano |

Il cambio è quindi **additivo**: con `it` la grafia inglese continua a trovare la città (ultima riga), e in più funziona quella italiana. Un codice non supportato ricade sull'inglese lato server (verificato con `xx` e `zz`), quindi la lingua del dispositivo si passa com'è: nessuna lista di lingue supportate da mantenere qui, che daterebbe l'app il giorno in cui Open-Meteo ne aggiunge una decima. Risolta **a ogni chiamata**, non alla costruzione del repository: il language picker di sistema può cambiare la lingua mentre il processo vive.

**Ambito deciso col committente: solo le nuove ricerche.** Le città già salvate tengono il nome con cui sono state salvate e la Milano seminata resta `Milan`: nessuna migrazione, nessuna riga `location` di diff nei Logs, e la `cacheKey` è sulle coordinate quindi cache e history non sentono nulla. Scartata (per ora) la variante che riallinea anche `cities.json` via `/v1/get?id=&language=` — l'endpoint funziona, verificato, e la Milano di default ha già l'id GeoNames giusto — perché rinominerebbe file che l'utente ha già imparato a riconoscere.

Ricaduta accettata: nome, `admin1` e `country` arrivano localizzati (Toscana, Italia), quindi le città salvate d'ora in poi avranno nomi-file italiani (`firenze.json`) e il `⎇` della status bar il nome italiano. È la regola di l10n del progetto applicata: i nomi di città sono valori, non chiavi.

- [x] `OpenMeteoApis.kt`: `language` senza default sulla `search` (un caller non può più ricadere in inglese per distrazione), `DEFAULT_LANGUAGE` e `languageOf(locale)` nel companion con il perché nella KDoc
- [x] `WeatherRepository.searchCities`: passa `languageOf(Locale.getDefault())`, letto a ogni chiamata
- [x] Non toccati: forecast e air quality (non restituiscono nomi), il `Geocoder` di piattaforma del GPS (già nella lingua del dispositivo), le città salvate e la Milano seminata
- [x] Test: la query va all'indice della lingua del dispositivo, un cambio di lingua raggiunge la ricerca successiva, `languageOf` su locale con regione, lingua non supportata e `Locale.ROOT`


## Fase 14a — Ri-aggiungere una città ne aggiorna il record (post-1.0)

Segnalazione del committente (25 ago 2026, dopo la 13f): cercata "Milano" in italiano, il file resta `milan.json`.

Non era la ricerca: era `CityStore.add()`, che su una città già salvata **saltava la scrittura** (`if (cities.none { it.id == city.id })`) e teneva il record vecchio. Milano cercata in italiano ha lo stesso id GeoNames (3173435) della "Milan" seminata, quindi l'add era un no-op e sopravviveva il nome inglese. Vale per chiunque cambi lingua al telefono e ri-aggiunga una città, non solo per il seed.

Adesso è un upsert: stesso id, record fresco, **stessa posizione nella lista** — ri-aggiungere una città non è un riordino.

- [x] `CityStore.add()`: sostituisce in posizione invece di saltare
- [x] Test: ri-aggiungere una città salvata ne aggiorna nome, regione e paese senza spostarla

## Fase 14b — "Nessuna città" diventa uno stato rappresentabile (post-1.0)

Proposta del committente (25 ago 2026): togliere la città di default all'installazione, la sceglie l'utente.

**D'accordo nel merito**: `cities.json` che elenca una città mai scelta è la bugia più grossa rimasta in un prodotto la cui regola dichiarata è che il file non deve mentire. E Milano non è un default neutro: è l'impronta del laboratorio.

**Il prezzo vero non era togliere una costante.** Prima della 14b lo stato "zero città" non era rappresentabile: `decode()` rimetteva Milano ogni volta che la lista era vuota, `activeCity` e `activeSource` facevano `cities.first()` (che su lista vuota lancia), `remove()` rifiutava di cancellare l'ultima e la UI nascondeva `[rm]` sull'ultima riga. Quattro guardie che esistevano solo perché la schermata principale non sopravviveva senza un soggetto.

**Il grosso era già pagato dalla Fase 9b.** Il GPS senza fix è già un "nessuna città": il widget stampa già `# no data yet — open tweather`, `resolveCity` restituisce già `City?`, il worker esce già con `lastFix ?: return`. È bastato aggiungere `ActiveSource.None` e lasciare che il `when` esaustivo elencasse i consumatori da sistemare (worker, widget, regole, editor).

**La migrazione è la parte che meritava attenzione.** Chi ha installato prima della 14b deve tenere la città che sta guardando, e il caso scomodo è chi non ha *mai* toccato `cities.json`: non ha niente nello store, ma guarda la Milano seminata da mesi. Il discriminante è **la history dei Logs**: qualunque installazione usata ha almeno un commit. `migrateFirstRun(hasHistory)` gira una volta sola, prima che la shell decida cosa disegnare, e a un'installazione usata scrive il seed **per davvero** (era un fallback, non un valore salvato) marcando l'init come già risposto. Un'installazione davvero nuova scrive solo il marcatore. Da qui `FirstRun.Unknown`: finché il controllo non è passato la shell non disegna, o lampeggerebbe `init` in faccia a chi usa l'app da mesi.

**Non spedibile da sola**: chi installa dallo store ha visto screenshot pieni di dati, e un primo avvio su un file vuoto si legge come "rotta", non come "onesta". All'editor vuoto ci si deve arrivare *scegliendo* di saltare, il che è la Fase 14c: le due viaggiano insieme.

- [x] `ActiveSource.None` + `FirstRun` (Unknown/Pending/Done) in `CityStore`; `decode()` senza fallback, `remove()` senza guardia sull'ultima, `setUseGps(false)` che può non avere dove ricadere
- [x] Rimosso `CityStore.activeCity`: codice morto, e l'unico altro punto che faceva `cities.first()`
- [x] `migrateFirstRun(hasHistory)` una volta per installazione + `WeatherRepository.hasAnyHistory()`, chiamata da `MainActivity`
- [x] Consumatori: worker ed engine escono senza città, il widget ricade sul suo stato vuoto, la dry run delle regole dice `no location configured`
- [x] Editor: `// no location configured` + `// hint: open cities.json and search a city` (output di terminale, quindi inglese) su entrambi i tab, e **il FAB sparisce** — un refresh che non ha niente da aggiornare è la stessa bugia di una metrica senza il suo dato
- [x] `[rm]` offerto anche sull'ultima città
- [x] Test: store vuoto, ultima città rimossa, GPS spento senza dove ricadere, le quattro combinazioni della migrazione (fresca, usata senza lista, usata con lista, controllo che gira una volta sola), l'editor che dichiara l'assenza e la toglie appena arriva una città. Le fixture che ereditavano il seed adesso dichiarano la propria precondizione


## Fase 14c — `$ tweather init` al primo avvio (post-1.0)

Conseguenza diretta della 14b: tolta la città seminata, l'app deve chiederne una. Ed è quello che rende l'editor vuoto un posto dove l'utente ha **scelto** di essere, invece della prima cosa che vede chi ha appena installato: chi arriva dallo store ha visto screenshot pieni di dati, e un file vuoto al primo avvio si legge come "rotta", non come "onesta".

**Non è un carosello, ed è una scelta.** Le schermate di onboarding sono la superficie più skippata del mobile, e una definizione offerta *prima* di aver visto la cosa che definisce non attecchisce: nessuno ricorda cos'è un "commit" se non ha ancora visto un commit. Quindi questa schermata fa **solo** il lavoro senza cui l'app non parte — una posizione — e il vocabolario vive in `HELP.md` (14d), che c'è quando la domanda arriva davvero.

Tre risposte, e sono tutte risposte: `usa la mia posizione` (chiede `ACCESS_COARSE_LOCATION`, e questo è il posto dove la motivazione di un permesso ha davvero senso, non in un dialogo a freddo), `cerca una città` (marca l'init come risposto e apre `cities.json`, riusando la ricerca che esiste già), `salta` (marca e basta, si atterra sull'editor vuoto). Permesso negato: la schermata lo dice in rosso e lascia in piedi le altre due strade, non è un vicolo cieco.

**Localizzata, a differenza dell'output di terminale del resto dell'app.** È la stessa eccezione che fa già `README.md`: la finzione la porta la *forma* — il prompt, le scelte `>`, le note `#` — non la lingua, e questa è l'unica schermata il cui scopo è farsi capire da chi non legge `git` per mestiere. Il comando `$ tweather init` resta com'è, perché è un comando.

- [x] `ui/init/InitScreen.kt`: `CodeCanvas` con le righe tappabili, tab bar a un file (`tweather.sh` — è una sessione, non un documento), status bar `⎇ setup`
- [x] `TweatherApp` divisa in `FirstRunSetup` e `Workspace`, con il ramo `FirstRun.Unknown` che disegna una superficie vuota: la 14b decide un attimo dopo, e indovinare "pending" per quel frame significa sbattere una schermata di setup in faccia a chi usa l'app da mesi
- [x] Il flag `openCitiesOnStart` è `rememberSaveable`: il dialogo di sistema del permesso può ricreare l'Activity
- [x] Test: le tre risposte, il permesso negato che non chiude le strade, l'installazione fresca che atterra su `init`, `salta` che apre comunque il workspace sull'editor vuoto

## Fase 14d — `HELP.md` e la hint una tantum (post-1.0)

Richiesta del committente: spiegare l'app, la sua filosofia e i termini che usa, a chi non è uno sviluppatore.

**Il posto giusto non è un'intro, è un file.** Un carosello lo si vede una volta, prima di avere il contesto per capirlo, e non lo si può riconsultare il giorno in cui la domanda arriva. Uno sviluppatore impara uno strumento dal suo `--help`, non da delle slide. Quindi la spiegazione è **il quinto file dell'app**, `HELP.md`, terzo tab dietro la barra di Impostazioni: lì si va quando l'app ti ha confuso, e i due tab dell'editor appartengono alla città, non all'app.

Contenuto: le quattro schede, le parole prese in prestito (commit, diff, branch, CI) spiegate in una riga ciascuna, da dove arrivano i dati (con la verità sulla posizione: se la attivi, le coordinate *escono* per chiedere le previsioni di quel punto) e un paragrafo solo sul perché ha questo aspetto. **La filosofia in quella dose e non di più**: una schermata che spiega che l'app è affascinante è l'unica cosa capace di romperne il fascino. Prosa, quindi interamente localizzata, heading compresi; le parole fra backtick sono nomi di file e di chiavi dell'app e restano com'erano. Una `<item>` per riga renderizzata, perché un a-capo vero dentro una risorsa Android viene schiacciato a spazio.

**La hint: attiva di default, e NON un toggle in `settings.config`.** Domanda esplicita del committente, risposta ragionata: una riga `// prima volta? apri HELP.md` in cima al documento, tappabile, che sparisce quando l'aiuto è stato visto — **per qualunque strada**, anche aprendolo dalle Impostazioni. Un interruttore per una cosa che succede una volta passerebbe il resto della vita dell'app appoggiato su `false` dentro un file che l'utente legge, e `$ tweather reset settings` la rifarebbe comparire a chi usa tweather da un anno. Lo stato sta quindi nel DataStore `workspace`, accanto al tab attivo, che è esattamente il tipo di cosa che quel file esiste per contenere ("workspace state has no line in the settings.config file"). Il modo per rivedere l'aiuto non è un flag: è il file, che non va da nessuna parte.

La hint appare anche a chi aggiorna, non solo alle installazioni nuove: `HELP.md` è nuovo anche per loro, ed è una riga che se ne va al primo tocco.

- [x] `ui/settings/HelpScreen.kt` + `SettingsFiles` a tre voci (`HelpFileIndex`), status bar `ro` — l'unico file dell'app che non si può modificare
- [x] `help_md` come `<string-array>` EN/IT; `help_hint` localizzata come la 14c
- [x] `WorkspaceStore.helpHintDismissed` + `dismissHelpHint()`; `WeatherViewModel.showHelpHint` (Eagerly: una hint che compare un frame dopo sembra un glitch) e `SettingsViewModel.markHelpSeen()`
- [x] La hint viaggia fra i tab: `openHelp` in `Workspace`, consumato da `SettingsScreen` — il grafo di navigazione ripristina l'ultimo file aperto, quindi non basta cambiare tab
- [x] Test: il documento con i suoi heading, `HELP.md` come file aperto della striscia, la hint in testa al documento che apre il file, la sua assenza quando è già stata vista, e il flag che sopravvive nel workspace store

**Rifinitura della chiusura (26 ago, rilievo del committente).** L'ultima riga era «Se sei arrivato fin qui, sai già se fa per te»: non è imperativa in senso grammaticale, ma **emette un verdetto sul lettore**, e lo fa nell'ultima riga della pagina che esiste apposta per chi si è sentito perso. Divide chi legge in due categorie proprio dove il documento dovrebbe lasciare la porta aperta; sarebbe stata giusta nel README del repo o nella scheda dello store, dove il lettore sta ancora decidendo se installare, non qui. Sostituita con una che **restituisce** invece di giudicare — «E se qualche parola qui sopra resta oscura, non importa: il meteo te lo dice lo stesso» — che ha anche il pregio di essere vera: la metafora è decorazione, i numeri funzionano senza. La prima frase resta intatta: il registro secco è la voce dell'app, il difetto non era il tono. Scartata la variante ammiccante (emoji, «benvenuto a bordo»): in un file che si presenta come sola lettura in un editor, un guizzo di calore fuori registro si nota più di una frase secca.

**Corretto insieme un refuso ortografico**: nel testo italiano `perché` era finito con l'accento grave (`perchè`), nell'heading e due volte nella riga finale. Colpa di una sostituzione automatica scritta male in fase 14d (`e' ` → `è ` applicata prima della regola specifica, che ha morso dentro `perche' `). tsteps non era affetto, perché lì gli accenti erano stati scritti direttamente.


## Fase 15 — Allineamento della nav bar alla serie (post-1.0)

Rilievo del committente confrontando le tre app: tweather era l'unica con **Impostazioni non in ultima posizione** e l'unica col glifo **terminal** sui Log. Entrambe le differenze sono eredità del mockup, non decisioni prese contro i gemelli: quando tweather è stata disegnata non c'era ancora una serie con cui essere coerenti.

**Impostazioni va in fondo.** L'ordine diventa Editor / Cerca / Log / Impostazioni. È la convenzione della piattaforma (l'ultimo slot di una bottom bar è il cassetto delle opzioni, non un contenuto) ed è già l'ordine di tsteps e thabit: i tab che contengono *il meteo* stanno insieme, e i Log — che sono la storia di quei dati — smettono di essere separati dall'editor da una schermata di opzioni. Costo zero: `EditorNavItems.All` è l'unica sede dell'ordine, la rotta `"explorer"` e gli stack salvati non cambiano.

**I Log prendono il glifo `commit`.** Il file è `weather_history.diff`, un log git con hash, autore e hunk `+`/`-`: il punto sulla linea di branch *è* quel file, mentre `terminal` nominava la pelle dell'app e non il suo contenuto. Doppiamente fuori posto perché i veri terminali di tweather stanno altrove (i comandi `$`, la status bar, il widget "terminal window"), e chi ha due app della serie installate vedeva lo stesso file dietro due icone diverse. tsteps e thabit avevano già scelto `commit`: qui vince il gemello, come previsto dalla regola per cui il kit si allinea sull'implementazione più recente.

**Non toccato: il glifo Editor di thabit.** Nella stessa revisione è emerso che thabit usa `checklist` dove i gemelli usano `{ }` (`DataObject`). Resta com'è: `habits.test` è una lista di checkbox, `{ }` prometterebbe JSON, e "il file non deve mentire" batte l'uniformità del glifo. L'uniformità della serie sta sul sistema (forma della barra, indicatore 2px, tint, label-sm, Impostazioni in fondo, `commit` sui log), non sull'obbligare ogni app a indossare l'icona sbagliata per il proprio file di identità.

- [x] `EditorNavItems`: ordine `Editor, Search, Logs, Settings`, `Logs` da `Icons.Filled.Terminal` a `Icons.Filled.Commit`; kdoc riscritta con le tre deviazioni (Explorer→Editor, terminal→commit, Impostazioni in fondo)
- [x] `Routes` e le `composable()` del NavHost riordinate a seguire — non cambia comportamento (la start destination è esplicita), ma la shell si legge nell'ordine in cui i tab appaiono
- [x] `HELP.md` EN/IT: l'elenco "le quattro schede" segue il nuovo ordine — è una guida alla barra, e una guida che elenca in un ordine diverso da quello che si vede è una guida sbagliata
- [x] Suite verde (360 test) e lint pulito. Nessun test asserisce l'ordine: `bottomBarSwitchesBetweenTheFourFiles` clicca per etichetta

**Nota su una flakiness pre-esistente**: durante la verifica `TweatherNavigationTest` è fallito due volte su quattro esecuzioni della suite completa, con test diversi ogni volta (`aFreshInstallLandsOnTweatherInit`, poi `skippingInitOpensTheWorkspaceAnyway`) e sempre sull'attesa della schermata di init. È una corsa fra la scrittura DataStore della migrazione (su `Dispatchers.IO`) e la prima composizione, non una conseguenza di questa fase: la classe passa isolata e la suite completa passa pulita nelle altre esecuzioni. Annotata qui perché prima o poi tingerà di rosso una CI senza colpa del commit che la fa girare.


## Fase 16 — Il modulo cielo (`sky.crontab`, `sky_runs.log`) — in corso

Proposta del committente (`VISION_SKY.md`, revisione 2 in repo): il cielo sopra la città attiva come **crontab**. `weather_data.json` dice cosa sta facendo l'atmosfera adesso; `sky.crontab` dice **cosa ha in programma il cielo**, e se le nuvole lasceranno passare il job. Ogni riga è un lavoro schedulato (alba, tramonto, ora d'oro, finestra di buio, sciame meteorico) e porta un **verdetto di build** calcolato sulle previsioni che l'app già scarica: `✓ pass`, `~ unstable`, `✗ fail`, `? unknown`, `∅ not scheduled`.

**Perché dentro tweather e non in una quarta app.** La feature è impossibile senza le previsioni: una `tsky` autonoma che voglia dire `✗ fail: cloud 92%` dovrebbe scaricare la nuvolosità oraria per città, gestire le città salvate, persistere un file di impostazioni, far girare un job in background e disegnare un widget — cioè reimplementare tweather e diventare un'app meteo peggiore con una scheda di astronomia. E `weather_data.json` ha già un blocco `astronomical`: il modulo non è un soggetto nuovo che arriva, è un blocco esistente che finalmente ha spazio.

**Niente quinto tab in basso — decisione del committente, ed è quella giusta.** La bozza originale spendeva il quinto slot della `NavigationBar` e si dichiarava "l'ultimo modulo assorbibile in questa forma". tweather ha già due livelli di navigazione che significano cose diverse: la barra in basso risponde a *cosa sto guardando* (i dati della città, l'elenco città, la storia, le opzioni), la striscia in alto risponde a *quale file di quella cosa*. Un modulo che aggiunge file non aggiunge una destinazione. Quindi:

| File | Dove | Perché lì |
|---|---|---|
| `sky.crontab` | **Editor**, terzo tab dopo `weather_data.json` e `README.md` | È un documento *sulla città attiva*: la sua latitudine produce ogni istante che contiene. I tab dell'editor appartengono alla città, e questo pure — compreso il cambio città dal `⎇` della status bar. Ed è la prima schermata dell'app, cioè l'unico posto dove una feature si fa trovare. |
| `sky_runs.log` | **Log**, terzo tab dopo `weather_history.diff` e `weather_forecast.diff` | È storia. Il tab Log è lo scaffale di ciò che è già successo. |
| blocco `[sky]` | `settings.config` | Tre chiavi. Nessun file di impostazioni nuovo. |

Il costo che resta, dichiarato: la striscia dell'editor passa a tre nomi (39 caratteri in bodyMedium bold — su un 5" scrolla), il sole e la luna compaiono in tre posti (JSON, README, crontab) e **un solo motore** deve alimentarli tutti e tre, e prima di qualunque verdetto serve lavoro sul layer dati (16a).

**Il problema di onestà della metafora.** Una riga di crontab dichiara una schedule fissa; l'alba non lo è (deriva di circa un minuto al giorno e salta di un'ora al cambio ora legale). Scrivere `29 6 * * *  sunrise` sarebbe un file che dichiara una grammatica che non rispetta. La soluzione è che **i crontab veri hanno già questa forma**: chi deve far partire qualcosa a un istante calcolato non scrive un campo minuti finto, scrive una ricorrenza e lascia che sia il job a calcolare il momento, documentando il valore risolto in un commento. Quindi il campo schedule dichiara la **ricorrenza** (`@daily`, `@yearly`), che è vera; l'istante esatto vive nel **canale dei commenti**, dove tweather mette già tutto ciò che sa e la struttura dati non può contenere; gli eventi senza regola di ricorrenza (fasi lunari) diventano **job di polling** con un `*/30 * * * *` onesto, che è letteralmente quello che l'app fa — valuta a ogni fetch. Criterio di accettazione: **ogni espressione cron che il renderer emette deve passare un parser cron vero** (`cron-utils`, solo `testImplementation`).

**La riga che tiene tutto insieme è la stessa delle altre fasi**: *il file può non sapere una cosa, non può inventarla*. Un verdetto che le previsioni non reggono è `? unknown`; una luna che quel giorno non sorge è `∅`, non `00:00`; una run che l'app non ha osservato è `– skipped` e non conta in nessuna statistica; un promemoria che l'app non sa consegnare in tempo non viene offerto come lead più corto.

Spec completa e motivata in `VISION_SKY.md`. Le sei sottofasi sotto sono verdi e spedibili una per una, e sono **tutte fatte** (ago 2026): la 16f, che aveva un go/no-go suo, è passata.


## Fase 16a — Il layer dati smette di buttare via ciò che ha già scaricato

Prerequisito di tutto il resto, e utile **anche da solo**. Niente di quanto sta in `VISION_SKY.md` §7 è calcolabile sul modello di dominio attuale: `HourlyForecast` non porta la nuvolosità e la finestra oraria si ferma a 25 slot. Entrambe le cose costano **zero rete**.

- `cloud_cover` è in `HOURLY_VARIABLES` dalla Fase 13c (ripara la nebbia di `weather_code`), è già deserializzato in `HourlyDto` ed è già sul disco dentro `ReportDiskCache` come DTO grezzo. Il mapper semplicemente smette di scartarlo.
- `forecast_days = 7` restituisce **168** valori orari, già parsati: l'app ne mappa 25 e ne getta 143. L'orizzonte di verdetto a 3 giorni non è una capacità nuova, è roba che l'app paga e butta.

La metà rischiosa è allargare la finestra, perché tre renderer leggono `report.hourly`: `weather_data.json` fa `hourly.drop(1)` **senza cap** e stamperebbe 167 righe; il `README.md` fa già `take(HourlyRows)`; `RuleVariables` e `AlertEngine` sono già limitati nel tempo (`next_6h`/`next_12h`, `now.plusHours(...)`). Quindi il cap esplicito nel JSON, e il test che lo blocca prima di tutto il resto.

- [x] Test di regressione: `hourly forecast renders one day whatever the report carries` — costruisce una settimana di slot e pretende 24 righe. Verificato che **fallisce senza il cap** (167 righe) e passa con: una guardia che non si è vista fallire non è una guardia
- [x] `HourlyForecast.cloudCoverPct` + mapping in `WeatherReportMapper.mapHourly`
- [x] `HOURLY_WINDOW` 25 → 168; `.take(HourlyJsonRows)` esplicito in `WeatherJson.hourly_forecast` (che è ciò che la sua KDoc già dichiarava)
- [x] Verificato che `RuleVariables`, `AlertEngine` e `WeatherSnapshots` non cambino comportamento: i primi due filtrano per **tempo** (`!isBefore(now) && !isAfter(end)`, `firstOrNull` con lo stesso predicato) e il terzo non tocca le orarie. Nessun consumatore di `report.hourly` indicizza per posizione — l'unico `hourly[...]` in `main/` sta dentro il mapper, sul DTO
- [x] Suite verde (363 test, +3) e lint pulito. La guardia è stata vista fallire: senza il cap, `expected:<24> but was:<167>`

**`FORECAST_DAYS` diventa una costante di `OpenMeteoForecastApi`**, e sia `HOURLY_WINDOW` (`× 24`) sia `DAILY_WINDOW` ne derivano. Il difetto che questa fase ripara è esattamente un 7 di qua e un 25 di là che non si parlavano: lasciare 168 scritto a mano nel mapper avrebbe ricreato lo stesso disallineamento un giro più in là. Ora i giorni di previsione si cambiano in un posto e la finestra segue.

**La costante del JSON si chiama `HourlyJsonRows`, non `HourlyRows`.** `WeatherReadme.kt` ha già un `HourlyRows` privato che vale **14**, nello stesso package: due costanti private con un nome e due valori sono una trappola per chi legge, non un riuso.

**Deviazione: `cloudCoverPct` era nullable, è finito non-null — e il test lo ha dimostrato.** La prima stesura lo aveva fatto `Int? = null` con un argomento che suonava bene: il verdetto del cielo ha uno stato `? unknown`, quindi serve un input capace di produrlo, e una nuvolosità mancante **non è un cielo sereno**. Il test scritto per coprire quel caso (array parallelo corto) è fallito con `IndexOutOfBoundsException` **prima di arrivare al mapping**: `repairedCodes()` legge la stessa colonna un passo prima, su tutti gli indici, e sarebbe esplosa comunque. Cioè il null era uno stato che il tipo permetteva e che il dato non può assumere.

E un campo così non è neutro: ogni lettore in 16d avrebbe scritto `?: 0`, e 0% di nuvole significa "sereno". La versione nullable era la strada più breve verso esattamente la bugia che voleva impedire. Quindi campo **non-null, senza default**, letto come i suoi fratelli (`hourly.cloudCoverPct[i]`), e i quattro punti di costruzione aggiornati a mano — nel `SampleWeatherReport` con valori **coerenti con la condizione di ogni riga** (l'ora `sunny` è al 5%, non al 90%), perché il primo verdetto scritto contro quel sample lo leggerà davvero. L'assenza che la 16d dovrà gestire non è una colonna mancante, è un'**ora** mancante: un evento oltre la fine di `hourly`, che la lista esprime da sé.

**La memoria, contata invece che stimata.** `hourlyTimes` e `hourlyCodes` erano già costruiti su **tutti** i 168 slot prima che la finestra si applicasse — quindi i `LocalDateTime` e i codici riparati non sono un costo nuovo. Il delta vero sono i soli oggetti mappati: 143 `HourlyForecast` (header + ref + `Double` + ref + due `Int` ≈ 40 B allineati) più 143 `WeatherCondition` (`WeatherCodes.condition` ne alloca uno per ora; le stringhe sono costanti condivise) ≈ 32 B → **~10 KB per report**. Per confronto, il `ForecastResponseDto` che il mapper ha in mano mentre lavora porta 168 stringhe di timestamp da ~56 B, cioè ~9,4 KB per la sola colonna `time`, più altre sei liste parallele: l'input era già più grande dell'aggiunta. La cache in RAM di `WeatherRepository` è però una `ConcurrentHashMap` per `cacheKey` **senza sfratto** (pre-esistente, e per la pseudo-città GPS c'è una chiave per cella da ~1,1 km): venti città in un processo sono ~200 KB in più. Nessun intervento qui, ma è la voce da guardare per prima se quella mappa diventerà un problema, perché adesso pesa quasi sei volte tanto per entrata.

**Niente riga nel `CHANGELOG.md`.** Questa fase non ha nessun effetto visibile: la tabella oraria del JSON mostra le stesse 24 righe, il README le stesse 14, le notifiche le stesse cose. È infrastruttura per la 16d, e un changelog che annuncia un campo di dominio che nessuno vede è rumore. La riga arriva con la fase che si vede.


## Fase 16b — `AstronomyEngine`: il motore, il catalogo, gli sciami (nessuna UI)

Tutto puro, tutto JVM-testabile, come `AlertEngine` e `RuleEngine`: niente Android, niente clock implicito. `domain/sky/`: `AstronomyMath.kt` (le serie di Meeus), `AstronomyEngine.kt` (lat/lon/zona in, istanti fuori), `SkyJob.kt`, `SkyJobCatalog.kt` (insieme fisso, versionato, nessuna registrazione a runtime — stessa disciplina delle 22 variabili di `alerts.rules`), `SkyScheduler.kt`, `MeteorShowerTable.kt`.

**Una primitiva, tutti gli eventi.** Invece di una formula chiusa per evento, il motore cerca l'istante in cui l'astro **attraversa una soglia di altezza**: l'alba è quel passaggio a −0,833°, il crepuscolo civile a −6°, l'ora d'oro fra +6° e −0,833°, il sorgere della luna a una soglia che segue la sua parallasse. Per questo l'elenco degli eventi può crescere senza che cresca la matematica, e per questo giorno polare, giorno senza luna e crepuscolo equatoriale di dieci minuti sono lo **stesso** ramo di codice invece di tre casi speciali.

**Il catalogo**: `sun.rise`, `sun.set`, `solar.noon`, i tre crepuscoli (civile/nautico/astronomico, am e pm), `golden_hour.*`, `blue_hour.*`, `darkness.window`, `moon.rise`/`moon.set`/`moon.today`/`moon.phase`, solstizi ed equinozi, `meteor.<sciame>.peak`. **32 job**, quattro abilitati di default (`sun.rise`, `sun.set`, `golden_hour.pm`, `moon.today`) — chi apre il tab e ne trova trentadue lo richiude.

**`darkness.window`** è l'aggiunta della revisione 2 e l'unica riga *derivata* del catalogo: l'intersezione fra buio astronomico e luna tramontata. Ogni altro job è un'ora in cui il sole o la luna attraversa un angolo, cosa che qualunque sito di effemeridi sa dire; questa è la domanda che si fa davvero un astrofilo, ed è calcolo puro sui due motori che il modulo ha già.

**Sciami meteorici**: dieci maggiori, picchi calcolati dalla **longitudine solare** e non hardcoded per anno. Il test verifica i picchi nel 2026, 2027, 2030 e **2041**: la tabella non scade e non ha bisogno di un aggiornamento spedito con l'app. Il picco si risolve come la **notte** in cui cade (dal crepuscolo astronomico all'alba), non come un timestamp nudo.

**`MoonPhase` riconciliato.** Era una media a otto bucket del mese sinodico medio con un novilunio hardcoded, accurata a circa un giorno — la sua stessa KDoc diceva "plenty for an emoji". Ora è un **classificatore sopra l'elongazione del motore**: enum, etichette ed emoji intatti, sostituita solo l'aritmetica. I sei test che l'enum aveva già passano **immutati**, compreso quello sulle date precedenti al vecchio riferimento: il valore reso non è cambiato di natura, solo di precisione.

- [x] `AstronomyEngine`: alba/tramonto (lembo superiore, rifrazione standard −0,833°), i tre crepuscoli, ora d'oro (+6° → −0,833°) e ora blu (−4° → −6°), transito solare, sorgere/tramontare della luna, illuminazione, istante dei quarti, solstizi ed equinozi
- [x] `SkyJobCatalog` + `SkyScheduler` (prossime N occorrenze, `nextToFire` per l'header) + `MeteorShowerTable`
- [x] `MoonPhase` riconciliato: nessuna seconda implementazione in giro
- [x] Test di correttezza: **9 siti da −67,6° a +69,6° × 3 giorni = 54 confronti** di alba e tramonto, tolleranza **120 s** (vedi sotto perché non 60); solstizi ed equinozi entro **56 s** su 7 istanti pubblicati fra il 2024 e il 2030; quarti lunari entro **5 min** su 5 istanti
- [x] **Contract test contro Open-Meteo**: i 54 confronti *sono* quel test — i valori sono catturati dall'API vera il 26 ago 2026 e congelati nel file
- [x] Test parser cron: ogni espressione del catalogo passa `cron-utils` (`testImplementation`, mai a runtime)
- [x] Suite verde (403 test, +40) e lint pulito

**Perché la tolleranza del contract test è 120 s e non 60.** Il limite non sta dalla nostra parte del filo. Open-Meteo **tronca al minuto**: su tutti i siti a media latitudine il nostro valore cade fra 0 e +60 s dopo il loro, mai prima, che è esattamente la firma di un troncamento. Oltre i |lat| 60 un minuto di orologio vale appena ~0,07° di altezza, quindi lì affiora anche la loro approssimazione (a Tromsø 85 s, a Ushuaia 106 s). Quello che il test può dimostrare è che i due valori coincidono ben dentro la larghezza di un `HH:mm` renderizzato. A fissare il motore all'angolo richiesto ci pensa il **secondo** test: per ogni evento solare, il sole *è davvero* all'altezza che lo definisce, entro 0,005° — cioè entro **un secondo del suo moto**, che è la risoluzione a cui il motore risponde e non un epsilon di comodo. I due insieme sono l'argomento completo: il primo prova la **posizione del sole** (una sbagliata non potrebbe accordarsi con un provider vero a nove latitudini), il secondo prova il **solutore** a qualunque soglia. Crepuscoli, ora d'oro e ora blu sono allora corretti per costruzione — che è il motivo per cui esiste una primitiva sola invece di sei formule.

**Deviazione misurata: i solstizi NON usano il root-find.** `VISION_SKY.md` prevedeva di trovarli cercando l'istante in cui la longitudine apparente del sole tocca 0/90/180/270, con l'argomento che un solo modello solare non può contraddirsi. La misura ha smentito il piano: la serie solare a bassa accuratezza vale ~0,01°, il sole copre 0,01° in **un quarto d'ora**, e l'equinozio di marzo 2026 trovato così cadeva **otto minuti** dopo l'istante pubblicato — dentro la barra d'errore del modello e fuori da tutto ciò che una riga `HH:mm` può permettersi di affermare. I solstizi vengono quindi dalla serie *fittata sugli istanti di stagione* (Meeus 27), che su sette istanti pubblicati fra 2024 e 2030 sbaglia al massimo di 56 s. Il root-find resta, e serve **solo** agli sciami, dove la risposta è una notte larga nove ore e un quarto d'ora non sposta nulla. I due metodi non sono lasciati a divergere di nascosto: **un test misura la loro distanza e la fissa** (≤ 15 minuti, e ≤ 0,01° di scarto della serie all'istante vero), così il giorno in cui uno dei due cambia la discordanza è un test rosso e non una sorpresa.

**Due scale di tempo, e non sono intercambiabili.** Le serie di posizione sono serie in Tempo Terrestre; il tempo siderale — e quindi ogni angolo orario — è funzione del Tempo Universale. ΔT fra i due vale ~75 s nel 2026. Passare UT a una serie di posizione è un errore piccolo (la luna si sposta di 0,015° in 75 s), passare TT al tempo siderale è un errore grande (il cielo gira 0,3° in 75 s). La prima stesura li confondeva; ora `centuriesTT` alimenta le posizioni e il giorno giuliano UT alimenta il siderale.

**Due bug veri, trovati dai test e non dalla rilettura.**

1. **La bisezione andava in una direzione sola.** Bracketing e bisezione assumevano una funzione crescente: giusto per un'alba, sbagliato per un tramonto. Non è un errore "un po'": la ricerca si allontanava dalla radice e restituiva **il bordo del bracket**, cioè un orario plausibile appoggiato su un multiplo di dieci minuti (`20:19:59` invece di `20:12:06`, otto minuti di errore che *sembrano* un orario). Riparato piegando il verso nel **segno** della funzione testata, così la caccia è una sola, `negativo → non negativo`, e non esiste più un ramo "discesa" da sbagliare.
2. **`nextMoonQuarter` non era strettamente successivo.** Chiedendo "il quarto dopo questo quarto" — cioè esattamente quello che fa scorrere una serie — l'elongazione all'istante di partenza sta un capello *sotto* il proprio bersaglio, la ricerca inquadrava l'istante da cui era partita e lo restituiva tale e quale: quattro copie dello stesso istante invece di quattro quarti. La guardia sta nel motore e non nel chiamante, dove il secondo a servirsene avrebbe dovuto ricordarsela.

**Costo misurato, per la 16c.** `solarDay` ≈ **1,7 ms** e `lunarDay` ≈ **0,9 ms** per giorno-città sulla JVM di sviluppo (griglia da 10 minuti, ~15 attraversamenti più il transito). Su ART sono realisticamente 3–5 volte tanto, cioè **un frame perso** se il file lungo li ricalcola a ogni ricomposizione. La memoizzazione per `(città, data locale)` prevista in 16c non è quindi una precauzione: è la misura che la chiede. Già ridotto qui il grosso spreco evidente — giorno polare e notte polare vengono da **una** scansione del giorno invece che da due.

**Niente riga nel `CHANGELOG.md`.** Come la 16a: nessuna UI, nessun effetto visibile. L'unica cosa che *si vedrà* è la fase lunare più precisa nel JSON e nel README, ma quella arriva insieme al resto in 16e, che è la fase che si vede.


## Fase 16c — `sky.crontab`, terzo tab dell'editor (ancora senza verdetti)

Il file si legge e si modifica **token per token**, come `alerts.rules`: nessun campo di testo, nessun parser, un errore di sintassi non è scrivibile. Tap sul nome del job = commenta/decommenta (che è **come si disabilita davvero** un cron job: la riga resta nel file, grigia, in colore commento, e non viene valutata); `[rm]` toglie la riga dal file con conferma a due tap e il job torna nel catalogo; `+ add job` apre il catalogo come una lista di autocomplete in stile IDE. `#` e `[rm]` sono diversi apposta e la differenza è reale: un job commentato si riaccende con un tap, uno rimosso va ripescato dal catalogo. Nessuno dei due manda mai una notifica.

Ecco il file, com'è uscito (Milano, 26 ago 2026, 18:30 locali):

```
# sky.crontab — Milan, Lombardy (Europe/Rome)
# 10 jobs · 1 disabled · next: golden_hour.pm in 1h 2m
# times are computed per occurrence, not fixed; see each line

@daily         sun.rise               [rm]  # Aug 27 06:38   +1m15s vs yesterday
@daily         sun.set                [rm]  # 20:12   −1m46s vs yesterday
#@daily        golden_hour.am         [rm]
@daily         golden_hour.pm         [rm]  # 19:32..20:12
@daily         blue_hour.pm           [rm]  # 20:30..20:43
@daily         darkness.window        [rm]  # 22:01..04:49   moon up all night
@daily         moon.today             [rm]  # 🌕 full moon, 96% lit
*/30 * * * *   moon.phase             [rm]  # Aug 28 06:18   🌕 full moon
@yearly        solstice.winter        [rm]  # 2026-12-21 21:50   in 117d
@yearly        meteor.perseids.peak   [rm]  # 2027-08-13 00:32..04:22   in 351d

+ add job

// light pollution is not modelled: the app does not know your sky
// this file is the schedule; whether the clouds allow it comes next
```

**Inglese, come ogni superficie di codice dell'app**: nomi dei job, espressioni cron e canale dei commenti sono codice. Il registro localizzato del cielo vive nel `README.md` (16e); qui sono localizzate solo le etichette di accessibilità. *(Corretto dalla **Fase 18**: "canale dei commenti" era la formulazione sbagliata, il canale non è una categoria. Restano codice i job, il cron, i verdetti, gli istanti e i numeri — cioè tutto quello che la colonna commento di una riga contiene davvero; le righe intere che sono frasi passano alla lingua del lettore.)*

**Le soglie non ci sono ancora** perché non ci sono ancora i verdetti (16d). Il piede del file lo dice invece di tacerlo: *this file is the schedule; whether the clouds allow it comes next*.

**Stato "nessuna città" (Fase 14b)**: senza latitudine non è calcolabile niente, quindi il tab rende `# no location configured` + il suggerimento, come gli altri due file dell'editor — ma nel canale commenti che *questo* file usa, cioè `#`, perché è con quello che commenta un crontab.

- [x] `ui/sky/SkyCrontabScreen.kt` + `SkyScreen.kt` + `SkyDocument.kt`: gutter, striscia tab, renderer a token e canvas riusati di peso; **`SkyDocument` è puro**, così l'intero file si asserisce in un test JVM senza comporre niente
- [x] `data/SkySubscriptionStore.kt` (DataStore `sky`): job sottoscritti + lead `--notify` (scritti ora, letti in 16f)
- [x] Sottoscrizioni **globali, non per città**; per città è lo *schedule* che risolvono, che è calcolato e mai memorizzato
- [x] `sky.enabled` in `settings.config` (`false` toglie il tab; le righe cielo del README arrivano in 16e)
- [x] `EditorTabs`: il tab attivo entra in vista
- [x] `SkyAlmanac`: memoizzazione per `(città, data locale)`, LRU limitata
- [x] Test: fuso della città, giorno/notte polare `∅` con la causa, luna che non sorge `∅`, DST, crepuscolo equatoriale, due job allo stesso istante in ordine stabile, "nessuna città", ordine di catalogo, riga commentata senza commento
- [x] Suite verde (445 test, +42) e lint pulito

**Il `--notify` non si vede, e non è una dimenticanza.** Lo store porta già `notifyLeadMinutes` e c'è un test che lo verifica, ma il file non rende nessun token `--notify=30m`: un token che promette un promemoria che l'app non sa ancora mandare sarebbe **la prima cosa che questo modulo mente**. Stessa ragione per cui il blocco `[sky]` di `settings.config` ha **una** chiave e non le tre della spec: `notify_default` e `notify_on_fail` governano promemoria che spedisce la 16f.

**Un bug vero, trovato dal test dello store.** `edit()` scriveva `Seeded = true` **prima** di leggere la lista corrente. Sembra equivalente e non lo è: `decode` restituisce i quattro default solo finché il flag è falso, quindi la **prima modifica di un'installazione nuova** decodificava un file vuoto e ci salvava sopra — un tap su una riga e le altre tre sparivano. Ora si legge prima e si marca dopo, e c'è un test che si chiama esattamente così.

**Tre correzioni di resa, ognuna trovata renderizzando il file e guardandolo.**

1. **`moon.today` nominava domani.** Risolto come "prossima occorrenza", alle sei di sera stampava `Aug 27 12:00`: una riga che si chiama `today` che nomina il giorno dopo. Non è un evento che il cielo ha in programma, è un'affermazione sul giorno in cui sei: adesso risponde per oggi e risponde **senza orologio**, perché una fase non "succede" a mezzogiorno.
2. **La causa polare era scritta per il job sbagliato.** «polar day: the sun does not set here today» compariva anche sotto `sun.rise`, dove non vuol dire niente. Riscritta in modo che valga per chiunque la chieda: «the sun stays above the horizon here».
3. **Il segno del DST era ambiguo.** La spec suggeriva `# DST +1h on Oct 25`; quel giorno l'offset **scende** di un'ora e la giornata **si allunga** di un'ora, quindi un `+` è giusto o sbagliato a seconda di quale delle due cose intendeva chi legge. Adesso il file dice cosa fa l'orologio: `# DST: the clock falls back 1h on Oct 25`.

**Due decisioni di layout prese contro la spec, e la ragione è la stessa della Fase 11d.**

`VISION_SKY.md` §4 metteva `[rm]` **in fondo alla riga**. In una riga di crontab risolta finiva **oltre il bordo destro di uno schermo da 360dp**, raggiungibile solo pannando: che non è un controllo, è la diceria di un controllo. La 11d aveva già chiuso questa discussione per le tabelle del README — il commento è a lunghezza variabile e si può permettere di clippare, un bersaglio da toccare no — quindi `[rm]` sta **prima** del commento.

E la conferma **è il token stesso**, `[rm]` → `[rm?]` in rosso, non un `// tap again to confirm` appeso in coda: cambia sotto il dito che l'ha appena toccato, non costa larghezza alla riga, e le parole stanno nella click label dove le legge lo screen reader. Un `// tap again` in fondo a una riga larga è una conferma che nessuno vede.

**La colonna dei nomi si allinea al file, non al catalogo.** Prima era paddata all'id più lungo del catalogo (`meteor.eta_aquariids.peak`, 25 caratteri): un file di due righe spingeva così il proprio `[rm]` fuori schermo. Un crontab vero si allinea a sé stesso — è quello che fa `column -t` — e qui è anche l'unica versione usabile.

**`EditorTabs` scrollava ma non riportava in vista il tab attivo.** Bug latente da sempre, che la terza voce dell'editor rendeva visibile: il tab selezionato poteva stare fuori schermo col suo indicatore da 2px, e la barra sembrava non avere nessun file attivo. Riparato **nel componente** e non nella sola chiamata che lo ha esposto, perché le strisce di Impostazioni e Log hanno la stessa forma a tre nomi e lo stesso difetto.

**La memoizzazione era obbligatoria, non prudente.** `SkyScheduler.resolve` chiama `solarDay` per ogni job: con 32 job sulla stessa data sono ~55 ms di matematica per ricomposizione, misurati in 16b. `SkyAlmanac` è la memoria di `AstronomyEngine`, che resta un calcolatore senza stato — referenzialmente trasparente, LRU limitata a 400 voci (la chiave contiene una data e il file può chiederne un anno: una mappa che cresce e basta sarebbe una perdita lenta che non si annuncia mai), e le coordinate arrotondate a ~10 m così stare fermi è **una** chiave.

**Niente riga nel `CHANGELOG.md` nemmeno qui.** Il tab si vede, ma senza verdetti è metà della cosa promessa: la voce di changelog arriva con la 16d/16e, quando `sky.crontab` dice anche se vale la pena uscire.


## Fase 16d — `SkyVerdictEngine`: le nuvole danno il verdetto

`SkyVerdictEngine.kt`: evento risolto + previsione oraria + luna → verdetto, nel canale dei commenti di ogni riga. Puro come tutti gli altri motori: niente clock, niente Android, niente repository.

Ecco il file con e senza previsioni utili (Milano, 26 ago 2026):

```
# sky.crontab — Milan, Lombardy (Europe/Rome)
# 7 jobs · next: golden_hour.pm in 1h 2m ✓

@daily   sun.set              [rm]  # 20:12   ✓ pass  cloud 8%   −1m46s vs yesterday
@daily   golden_hour.pm       [rm]  # 19:32..20:12   ✓ pass  cloud 8%
@daily   darkness.window      [rm]  # 22:01..04:49   ~ unstable  moon 99% and up
@yearly  meteor.perseids.peak [rm]  # 2027-08-13 00:32..04:22   in 351d   ? unknown (past the forecast horizon)

$ tweather run sky
// sun.set              20:12         ✓ pass  cloud 8%
// golden_hour.pm       19:32..20:12  ✓ pass  cloud 8%
// darkness.window      22:01..04:49  ~ unstable  moon 99% and up
// moon.today           🌕 full moon, 96% lit
// meteor.perseids.peak 2027-08-13    ? unknown (past the forecast horizon)

// pass ≤ 25% cloud · fail above 65% · rain ≥ 70% fails it whatever the sky does
// a bright moon (≥ 60%) unsettles a dark-sky job under a clear sky
// light pollution is not modelled: the app does not know your sky
// a verdict is the forecast's opinion, not an observation — it will change
```

- La **finestra, non l'istante**: per un job a intervallo si media sui bucket orari che attraversa, per uno puntuale si prende il bucket che lo contiene. La pioggia però si prende al **massimo** e non in media: un'ora di pioggia dentro una finestra di due non si media via, è la cosa che rovina l'evento.
- **Il numero usato si stampa.** Un verdetto la cui prova è invisibile è un'opinione, e questa app non renderizza opinioni.
- I job di buio hanno **in più la condizione lunare**: sopra il 60% di illuminazione con la luna alta, un cielo sereno resta `~ unstable`, e il commento nomina **la luna, non le nuvole**. La luna può solo peggiorare un verdetto, mai migliorarlo.
- **Oltre l'orizzonte non c'è verdetto**: `? unknown` con la ragione fra parentesi, mai una stima.
- **I dati stantii non hanno diritto a un'opinione.** Sopra la soglia esistente (due volte l'intervallo di polling) il verdetto è `? unknown (no recent data)`, non l'ultimo noto: stampare quello sarebbe rispondere a una domanda su stasera con quello che l'app pensava ieri, con le stesse parole che usa quando sa.

- [x] `SkyVerdictEngine` + tabella verdetti con tutti i confini di soglia, l'override precipitazioni e l'override luna
- [x] `$ tweather run sky` (conferma a due tap), verdetti nella riga e nell'header (`# next: golden_hour.pm in 1h 2m ✓`)
- [x] Dati stantii: sopra la soglia di staleness il verdetto è `? unknown`, non l'ultimo noto
- [x] Fetch fallito: **lo schedule si rende lo stesso** — non gli serve la rete, ed è testato che ogni riga e ogni finestra compaiono anche senza nessun report. La *causa* del fallimento resta della scheda che ha provato a fetchare: questo tab non fetcha, quindi riporta la conseguenza (`? unknown (no recent data)`) e non un errore che non è suo
- [x] Test: confini di soglia, evento esattamente sul bordo dell'orizzonte (`? unknown`, mai estrapolato), copertura mancante, buco *dentro* la previsione (che è una cosa diversa dalla sua fine, e il file dice quale)
- [x] Suite verde (483 test, +38) e lint pulito

**Il bug più grosso della fase: `sun.set` non aveva verdetto.** Avevo agganciato il verdetto a `visibilityDependent`, il flag introdotto in 16b — e quel flag risponde a un'**altra** domanda. `visibilityDependent` governa se un *promemoria* va soppresso quando l'evento non si vedrà (16f): un tramonto **avviene comunque** e magari alle 20:12 hai un appuntamento, quindi il suo promemoria parte lo stesso; il picco delle Perseidi senza cielo sereno non è un evento, quindi il suo si sopprime. Ma se le nuvole *contano* per guardarlo è un'altra cosa ancora — e vale per il tramonto più che per qualunque altra riga, visto che **"stasera vale la pena uscire?" è la domanda per cui esiste tutto il modulo**. Aggiunto `SkyJob.observable`, vero per tutto tranne i momenti di pura geometria (solstizio, istante del quarto, mezzogiorno solare): quelli avvengono a un orario calcolato che nessuno va a guardare, e un `✗ fail` su un primo quarto sarebbe il file che si inventa una posta in gioco che nessuno ha.

**La soglia di pioggia che fa fallire un evento è quella che l'app già aveva** (`AlertEngine.PRECIP_THRESHOLD_PCT`, 70%). «Piove» deve voler dire una cosa sola dentro l'app: un job del cielo in disaccordo con una notifica sulla stessa ora sarebbe l'app che litiga con sé stessa. C'è un test che tiene le due costanti legate.

**La regola di staleness ha cambiato casa.** Viveva dentro `WidgetContent`, che è dove era stata scritta ma non dove appartiene: quanto vecchio è troppo vecchio è un fatto sui **dati**, non su una delle superfici che li disegna. Ora è `domain/WeatherFreshness`, letta dal widget e dal cielo. Due volte l'intervallo di polling: un sync saltato è ordinario, due di fila vogliono dire che i numeri a schermo non sono più un'affermazione sull'adesso.

**`WeatherRepository.cachedReport()`: legge, non fetcha.** Aprire un tab non è un motivo per spendere due GET, e lo schedule che questo file rende non ha bisogno di rete. Nessun cancello TTL, a differenza di `getWeather`: restituisce quello che c'è e lascia al chiamante il giudizio sull'età — che è esattamente il giudizio che il verdetto deve dare ad alta voce. Il tab si aggiorna sul **feed dei commit** che già esiste (ogni fetch che atterra committa nello storico), lo stesso segnale su cui si ridisegna il widget.

**Tre correzioni di resa, di nuovo trovate guardando il file.**

1. **La luna veniva nominata due volte.** `# 22:01..04:49   moon up all night   ~ unstable  moon 99% and up`: il suffisso dice *quando* la luna se ne va, il verdetto *quanto* è luminosa, ma insieme sono la stessa frase due volte. Quando il verdetto ha già nominato la luna, il suffisso si fa da parte.
2. **Il verdetto arrivava terzo.** Sulla riga dell'alba stava dopo la deriva in secondi: `# 06:38   +1m15s vs yesterday   ✓ pass`. La deriva è curiosità, il verdetto è la risposta alla domanda con cui si apre il file. Adesso viene subito dopo il *quando*.
3. **`// no fetch yet` con altre parole dopo.** Un `//` apre una spiegazione e quindi vive a fine riga; ma su una riga il verdetto può essere seguito dalle curiosità del job, e `? unknown // no fetch yet −1m46s vs yesterday` è un commento che non è riuscito a commentare. Le ragioni del verdetto stanno fra **parentesi**; il `//` resta sulle righe `∅`, dove non segue mai niente.

**`$ tweather run sky` è una seconda vista degli stessi fatti, e va bene così.** Senza registro delle run e senza notifiche (16e e 16f), non c'è niente che il dry run *eviti* di fare — quindi la sua ragione d'essere qui è un'altra: una riga di crontab risolta è larga abbastanza da doverla pannare, quindi il blocco è **l'unico posto in cui i verdetti stanno incolonnati uno sotto l'altro**, con la finestra su cui ognuno è stato calcolato scritta per intero invece che abbreviata per stare in colonna. Un job senza verdetto ci stampa il **fatto** a cui si è risolto invece di una finestra e un trattino (`moon.today` stampava `12:00`, che è l'istante a cui si misura la sua fase e non vuol dire niente per chi legge). Ogni modifica al file azzera il blocco: un dry run è un'istantanea, e una lasciata lì sopra un file cambiato è una risposta vecchia coi vestiti di una nuova.

**Questa volta il `CHANGELOG.md` la riga ce l'ha.** Le fasi 16a–16c non avevano niente che un utente potesse vedere e la voce è stata rimandata due volte dicendolo; adesso il tab risponde alla domanda per cui esiste, quindi la voce copre 16a–16d insieme — che è anche come le leggerà chi la legge, cioè come una cosa sola.


## Fase 16e — La storia delle run, e il cielo nel `README.md`

**`sky_runs.log` non si porta dietro una tabella nuova.** La bozza prevedeva `SkyRunEntity`, `SkyRunDao`, `SkyRunRecorder` e un pruning proprio a 200. Qui le run vanno dove vanno già le regole scattate: **una colonna `sky_runs` TEXT nullable su `weather_history`** (Room v3 → v4), scritta dallo stesso `UPDATE` post-fetch di `setFiredRulesOnLatest`. Tre motivi, e il primo non è il costo:

1. **È più onesto.** Una run del cielo non è un evento indipendente, è *qualcosa che un fetch ha notato*. `obs +12m` è la bozza che lo ammette dentro una stringa; attaccare la run al commit che l'ha osservata lo dice nello schema: il timestamp della riga meno l'istante dell'evento **è** `obs`.
2. **La schermata Log sa già renderlo.** Le run rendono come check line `✓ sun.set ran clear` sul commit, gratis, e `sky_runs.log` è una **seconda vista** sulle stesse righe, raggruppata per giorno invece che per commit. Due file, una verità, nessun test di riconciliazione da scrivere.
3. **Il pruning si risolve da sé.** Le run invecchiano con i 200 commit della storia, invece di avere una seconda politica di retention che può dissentire dalla prima.

```
# Aug 26
20:12  sun.set          ✓ pass       cloud   8%  obs +12m
20:43  blue_hour.pm     ✗ fail       cloud  92%  obs +6m
06:37  sun.rise         – skipped                obs +95m
# 1 passed · 1 failed · 1 skipped
```

Le run si registrano **solo per i job osservabili e abilitati**, nella finestra `(commit precedente, adesso]` — che è precisamente l'ultimo momento in cui l'app ha guardato, quindi il vuoto fra i due è tutto quello che è successo mentre non guardava. Un job a intervallo conta come eseguito **quando la finestra si chiude**: l'ora d'oro non è successa alle 19:32, è successa fra le 19:32 e le 20:12. Sul primissimo commit non c'è un precedente e non c'è niente che si sia perso: un'installazione non acquisisce una storia di tramonti per cui non era installata.

**`– skipped` è lo stato di copertura, e viene da sé.** Se la previsione in mano non porta più l'ora dell'evento, `SkyVerdictEngine` risponde già `? unknown (no forecast hour covers it)`: la run si registra senza verdetto e non conta in nessuna statistica. Non è stato necessario inventare una regola dei ±90 minuti — il motore della 16d produceva esattamente la risposta giusta.

**Il README: `## Astronomia` cresce, `## Stato` prende l'avviso — nessuna sezione nuova.** La bozza aggiungeva un `## Stanotte` dopo `## Prossime ore`, che avrebbe messo tramonto, fase e tramonto della luna lì e, sei righe sotto, `## Astronomia` avrebbe ristampato alba, tramonto, luce diurna e fase: un documento, due sezioni, stesso soggetto. Quindi `## Astronomia` resta dove l'ha messa la 13d e diventa la casa del cielo:

```
## Astronomia
Alba: 06:37 · Tramonto: 20:12
Luce diurna: 13h 34m
Ora d'oro: 19:32–20:12 · Ora blu: 20:30–20:43
Buio astronomico: 22:01–04:49, la luna resta alta tutta la notte
Luna: Gibbosa crescente 🌔 · illuminata al 96% · sorge 19:34 · tramonta 05:36
```

Sempre presente, perché è *oggi* e non la pubblicità di un modulo; le due righe in mezzo compaiono solo con `sky.enabled`. L'**avviso** va in `## Stato`, l'unica sezione del documento che parla già in blockquote, e solo per un job **abilitato dall'utente**, nelle prossime 12 ore, con verdetto diverso da pass: la sezione riporta **le tue** sottoscrizioni e non fa pubblicità a chi `sky.crontab` non l'ha mai aperto. Uno solo, o `## Stato` smette di essere un badge.

**Un solo motore, tre render — JSON compreso.** `astronomical.sunrise`/`sunset` non sono più i valori `daily` di Open-Meteo: li calcola il motore, e `daylight_duration` è la loro differenza invece di un terzo numero che deve essere d'accordo con gli altri due. È il **test di accordo** a tenere insieme il tutto: i tempi che il README stampa sono un sottoinsieme di quelli che `sky.crontab` risolve, per la stessa città allo stesso istante.

- [x] Room v3 → v4: colonna `sky_runs` + `MIGRATION_3_4` + `setSkyRunsOnLatest`
- [x] Check line sul commit in `weather_history.diff` + `ui/logs/SkyRunsLog.kt` come terzo tab della striscia Log
- [x] `## Astronomia` alimentata dal motore; avviso in `## Stato`; stringhe EN/IT
- [x] `astronomical` del JSON e `MoonPhase` dal motore; `daylight_duration` coerente
- [x] Riga widget opzionale, default off, ultima nel budget (opzione per-widget in `widget.config`)
- [x] **Test di accordo**: i tempi di `## Astronomia` sono quelli di `sky.crontab`, con le due divergenze *volute* fissate a loro volta (vedi sotto)
- [x] Accessibilità: ogni riga del crontab e del log annuncia il proprio stato a parole, EN e IT
- [x] `README.md` (sezioni `### sky.crontab` e `### sky_runs.log`, riga nella tabella dati, **senza trattini**), `HELP.md` (la parola *crontab*, il calcolo locale, quale città riceve le notifiche), `CHANGELOG.md`, `CLAUDE.md`, `DESIGN.md` (tre ruoli token, nessuna tinta nuova)
- [x] Suite verde (521 test, +38) e lint pulito

**Il modello non sapeva dire "non sorge".** `Astronomical.sunrise` era un `LocalTime` non-null perché i valori venivano dal provider; con il motore, sopra il circolo polare a giugno **non c'è un'alba**, e il vecchio tipo poteva solo metterci un altro orario e lasciare che il lettore lo prendesse per buono. Ora `sunrise`, `sunset` e `daylightDuration` sono nullable: il JSON stampa `null` (che è già quello che fa per una sezione che i provider non hanno riempito, quindi è in carattere e non un'eccezione), il README stampa `∅`, lo stesso glifo che `sky.crontab` usa per lo stesso fatto.

**Un bug vero, e la sua onda.** Il motore risponde al secondo, e `WeatherSnapshots.flatten` scrive `sunrise.toString()` nello storico: senza troncare, **ogni singolo fetch** avrebbe messo una riga `astronomical.sunrise` fresca in `weather_history.diff`, perché la risposta si sposta di frazioni di secondo fra un fetch e l'altro. I valori del provider erano precisi al minuto e nessuno se n'era accorto finché non hanno smesso di essere la sorgente. Troncati al minuto — che è anche l'unica precisione che l'app renderizza. Nella `SkyRun`, invece, l'istante tiene i secondi: quel valore si scrive una volta e non si ridiffa mai, quindi non c'è niente che possa fare rumore.

**`obs` si arrotonda, non si tronca.** È una distanza: 11m53s è più vicino a `+12m` che a `+11m`, e troncare avrebbe sotto-riportato in silenzio proprio la debolezza che quel numero esiste per dichiarare.

**Due divergenze fra i due file, entrambe volute, entrambe fissate da un test.** `## Astronomia` descrive **oggi**, `sky.crontab` descrive **il prossimo**: alle sei di sera il README dice giustamente l'alba di oggi (06:37, già passata) e il crontab dice giustamente quella di domani (06:38). Lo stesso, amplificato, per la luna, che sorge ~50 minuti più tardi ogni giorno. Non sono in disaccordo: sono due file che rispondono a due domande. Fissato con due test apposta, così nessuno "ripara" l'uno nell'altro.

**Il test di accordo ha trovato un bug nel test di accordo.** Il primo tentativo raccoglieva anche `*Last updated 18:30*`: `## Astronomia` è l'ultimo heading del documento, quindi la sezione "arrivava fino in fondo" e il piè di pagina veniva letto come uno degli orari del cielo. Riparato l'helper, non il codice — ma valeva la pena scriverlo, perché la stessa svista in un test più permissivo sarebbe passata.

**La striscia dei Log a tre nomi era più larga di un telefono, e i nomi si sono accorciati.** `weather_history.diff weather_forecast.diff sky_runs.log` sono 53 caratteri monospace: oltre il bordo di uno schermo da 360dp. La barra scorre da sempre e dalla 16c porta in vista il tab **attivo**, quindi il file si raggiungeva con uno swipe — ma a riposo il terzo nome non era sullo schermo, e un file che non si intravede è un file che non esiste per chi non sa già che c'è.

Il prefisso `weather_` era **l'unica cosa in tutto il progetto a portarselo dietro**: nessun altro file dell'app dice di che parla nel proprio nome, perché è un'app meteo e lo dicono tutti. `history.diff` e `forecast.diff` non perdono niente e restituiscono 16 caratteri che quella striscia non aveva. Misurato con una sonda Robolectric a tre larghezze prima e dopo, invece di stimarlo: con i nomi lunghi `sky_runs.log` è `displayed=false` a **320, 360 e 411dp**; con quelli corti i tre nomi sono `displayed=true` a tutte e tre. Il test ora lo pretende con `assertIsDisplayed` su tutti e tre, più un caso a `w320dp` — l'asserzione più dura, che è quella che si può permettere solo un layout che ci sta davvero.

Costo dichiarato: gli screenshot in `docs/screenshots/` mostrano ancora i nomi vecchi, e non si rigenerano da qui. La scoperta si appoggia su `HELP.md`, che nomina `sky_runs.log` e usa il nome nuovo.

**La regola dei trattini del README ha costretto due migliorie nell'app.** Il `README.md` non ha **mai** avuto un trattino lungo, nemmeno dentro un blocco di codice, e i miei blocchi citano output vero. Invece di piegare la regola o di far dire al README una cosa che l'app non dice, ho cambiato l'app: l'header del crontab separa con `·` (che è già il separatore della riga successiva — il trattino era l'unico segno tipografico che quel file prendeva in prestito per un uso solo) e la riga di piede usa un punto e virgola. Il glifo `–` di `skipped` resta nell'app, perché accanto a ✓ ✗ ~ ∅ un `-` ASCII sembrerebbe un refuso, e il README lo descrive a parole invece di citarlo.

**La riga del widget è per-widget, non globale.** Due widget possono fissare due città, e uno dei due può essere quello che qualcuno ha messo su uno schermo per guardare il cielo. Spenta di default (un widget già piazzato non deve cambiare forma perché l'app si è aggiornata) e **ultima nel budget di righe**, cioè la prima a cadere quando il launcher dà meno spazio: la temperatura è il motivo per cui il widget esiste, questa riga no. Niente numero delle nuvole sulla riga: alla larghezza di un widget viene ellissata da destra, e il numero se ne porterebbe via la parola del verdetto.

**La regola di staleness ha trovato casa in `domain/WeatherFreshness`** già in 16d; qui il modulo cielo è il suo secondo lettore, come previsto.


## Fase 16f — I promemoria (`--notify`)

L'unica parte del modulo che **aggiunge una primitiva di scheduling all'app**, e per questo è l'ultima: tutto il resto spedisce senza.

Oggi tweather schedula esattamente una cosa: un job periodico WorkManager a 15/30/60/120 minuti, default 60. Nient'altro. Un promemoria "30 minuti prima del tramonto" su quel tick non ci sta — al default l'app si sveglia due volte l'ora in momenti che col tramonto non c'entrano — quindi serve `AlarmManager`, e questo porta con sé:

- **`setAndAllowWhileIdle`**, mai `setExact*`, mai `SCHEDULE_EXACT_ALARM` — la batteria è una feature e un tramonto non è una sveglia. `setWindow` non basta: in Doze un allarme a finestra semplice viene rimandato alla maintenance window successiva, che può essere fra ore, e il promemoria arriva a buio fatto.
- **Un receiver `RECEIVE_BOOT_COMPLETED` e un percorso di riarmo**, perché gli allarmi non sopravvivono a un riavvio e la persistenza di WorkManager non si estende a loro. Un permesso nuovo (normale, non runtime), un receiver nuovo in manifest, e una cosa in più che può smettere di funzionare in silenzio.
- **Una deriva che l'app deve dichiarare.** Inesatto vuol dire ±10 minuti in pratica: per questo il lead minimo selezionabile è **15 minuti** e per questo `5m` **non è nel ciclo** (`off · 15m · 30m · 1h · 3h · 1d`). La bozza offriva `5m` al §4 e lo vietava al §10: aveva ragione il §10. Un lead che l'app non sa onorare non è un lead più corto, è una bugia.

Comportamento: la notifica **porta il verdetto** (`golden hour in 30 min — ✓ clear (8%)`); con `notify_on_fail = false` (default) un `✗ fail` la sopprime, perché ricordare qualcosa che non si vedrà è rumore; `? unknown` **si manda** per i job che avvengono comunque (eventi solari) e **si sopprime** per quelli che dipendono dalla visibilità, entrambi con `// no recent data`; una notifica per job per occorrenza, deduplicata come le regole utente; **solo la città attiva** viene schedulata — una città pinnata sul widget non schedula notifiche del cielo, e lo dice `HELP.md` invece di lasciarlo scoprire.

- [x] `AlarmManager.setAndAllowWhileIdle` + receiver di boot + riarmo; nessun permesso di allarme esatto
- [x] Ciclo `--notify` sul token della riga; `notify_default` e `notify_on_fail` in `settings.config`
- [x] Dedup nello stesso stampo di `alerts` e `rule_state` (DataStore `sky_alerts`, 40 impronte, più recenti in testa)
- [x] Test: dedup fra fetch, soppressione su `fail` e su `unknown` per i job dipendenti dalla visibilità, riarmo dopo un boot simulato
- [x] `HELP.md`: una riga può avvisare, e l'avviso è approssimato apposta (EN/IT)
- [x] Suite verde (554 test, +33) e lint pulito

**Il bug più grosso della fase, e stava per essere spedito: il token `--notify` non era raggiungibile.** Il renderer mostra la colonna solo se qualche riga del file ha un lead — un file su cui nessuno ha messo un promemoria non paga una colonna per la possibilità, che è la regola giusta. Ma `SkySubscription.notifyLeadMinutes` nasce `null` **su ogni riga**, sia sulle quattro seminate al primo avvio sia su ogni job aggiunto dal catalogo: quindi la colonna non compariva mai, e non c'era **nessun modo di accendere un promemoria**. Provato con una sonda prima di ripararlo (`PROBE seeded leads = [null, null, null, null]`), come per la striscia dei Log: una diagnosi per lettura è un'ipotesi.

La riparazione non è stata rendere il token sempre visibile. Misurato: a 360dp la riga di default (`@daily` + `sun.rise` + `[rm]`) lascia una decina di caratteri di commento sullo schermo, cioè **esattamente l'istante risolto** — che è il motivo per cui il file esiste. Una colonna `--notify=off` fissa da 12 caratteri su ogni riga se lo sarebbe portato via, per mostrare "off" a chi non ha chiesto niente.

Quindi è cambiato il **significato di `notify_default`**: non più un valore-seme copiato dentro un job quando lo aggiungi (che avrebbe lasciato le righe vecchie mute per sempre), ma **il lead che una riga usa quando non ne ha uno suo**. `null` su una riga vuol dire "segui il default", non "niente promemoria". Con `notify_default = off`, che ora è il default di fabbrica, il file è identico a prima e non parte niente; portalo a `30m` in `settings.config` e **tutte** le righe mostrano il token, e da lì ognuna si cicla per conto suo. Il ciclo parte dal valore **mostrato**, non da quello memorizzato, o il primo tocco salterebbe altrove rispetto al numero scritto lì accanto.

**`notify_default` era 30m di fabbrica, ed era un altro modo di dire la stessa bugia.** Un'installazione che accende `sky.enabled` per *leggere* il file si sarebbe trovata quattro promemoria al giorno senza averne chiesto uno. Ora è `off`, e lo `0` con cui si memorizza (un `intPreferences` non tiene `null`) legge uguale all'assente.

**Il receiver è testabile perché `onReceive` non fa il lavoro.** `goAsync()` più una coroutine su `Dispatchers.Default` è un fire-and-forget con cui un test può solo correre una gara; estratto un `internal suspend fun handle(context, intent)` che contiene tutto tranne il dispatch, e i test lo attendono. Il `finally` che riarma è dentro `handle`, quindi è coperto: c'è un test che gli passa un job id fuori catalogo (una consegna impossibile, che è come si comporterebbe un'eccezione) e pretende comunque l'allarme successivo.

**Due asserzioni sbagliate mie, non due bug.** `ShadowAlarmManager.getNextScheduledAlarm()` fa `poll()` sulla coda e *deschedula*: leggerlo prima di contare gli allarmi svuotava la lista che stavo per contare. Il non distruttivo è `peekNextScheduledAlarm()`, verificato disassemblando lo shadow invece di indovinare. E `windowLengthMs` per `setAndAllowWhileIdle` vale `-1` = `WINDOW_HEURISTIC`, cioè "la finestra la sceglie il sistema": **è esattamente l'inesattezza che si vuole**. Pretendevo `0`, che è `WINDOW_EXACT`, cioè il contrario di quello che questa fase dichiara di fare — l'asserzione avrebbe fallito la build se il codice fosse stato giusto e passata se fosse stato sbagliato.

**Spegnere il modulo cancella l'allarme subito.** `reschedule` cancellava già quando `sky.enabled` è falso, ma nessuno lo chiamava al cambio dell'interruttore: l'allarme sarebbe sopravvissuto fino a scattare, avrebbe svegliato il telefono, avrebbe trovato il modulo spento e si sarebbe cancellato da solo. Corretto, ma una sveglia inutile dopo che l'utente ha detto di no. `SettingsViewModel` prende il riarmo iniettato (come `SkyViewModel`), in **una** coroutine dopo la scrittura: partire in parallelo avrebbe fatto rileggere il valore vecchio.

**L'id della notifica viene dall'indice nel catalogo, non da `jobId.hashCode()`.** Due hash che collidono avrebbero fatto sovrascrivere in silenzio il promemoria di un job con quello di un altro: un bug che si sarebbe visto solo su un telefono, e solo per due job su trentadue.

**Lint ha trovato l'unica cosa che `runCatching` nasconde.** `manager.notify` vuole `POST_NOTIFICATIONS` e lint pretende un `catch (SecurityException)` **esplicito**: `runCatching`, che prende tutto, non conta come "gestita". Allineato ad `AlertNotifier`, che lo faceva già bene dalla 9c.


### Fase 16 — fuori perimetro: `iss.pass`, pianeti, eclissi

La bozza schedulava i passaggi ISS come ultima fase con un go/no-go. La revisione 2 dà la raccomandazione: **non farlo**, lasciando il catalogo aperto se cambia idea.

Costerebbe una **seconda sorgente dati** (TLE da CelesTrak: gratis e senza chiave, ma la storia della "sorgente unica" finisce, ed è una delle tre cose con cui il README apre), **vera meccanica orbitale** (propagazione SGP4, angoli topocentrici, condizione di visibilità: satellite illuminato, osservatore nel buio astronomico, elevazione sopra ~10°) che sarebbe il pezzo di matematica più grande dell'app — più grande di tutto il resto di questa spec messo insieme — con vettori di test propri, la **freschezza del TLE come fatto di prima classe** (sopra i ~7 giorni degrada, quindi regola di staleness e riga dedicata nel canale commenti), e soprattutto **una notifica non consegnabile**: un passaggio dura sei minuti, un allarme inesatto deriva di dieci.

È l'ultimo punto a decidere. Ogni altro job del catalogo degrada con grazia quando l'app è imprecisa: un promemoria di tramonto in ritardo di otto minuti è ancora un promemoria di tramonto. Un passaggio ISS in ritardo di otto minuti è una notifica su una cosa finita. Un modulo la cui tesi è "il file può non sapere, non può inventare" non dovrebbe spedire una riga che strutturalmente non sa onorare.

Fuori perimetro anche pianeti, congiunzioni ed eclissi: ognuna vuole vero lavoro di effemeridi e una decisione sua. **Rinviate, non respinte** — il catalogo è una lista di valori `SkyJob`, quindi ognuna può arrivare dopo senza toccare formato del file, renderer o store.


## Fase 16g — Il `## Stato` del README parla la lingua del README

**Segnalazione del committente (27 ago 2026), con screenshot.** In `README.md` la sezione `## Astronomia` è in prosa, come deve essere, mentre la riga che il cielo mette in `## Stato` era:

```
> golden_hour.pm alle 19:21: ✗ fail  cloud 100%
```

L'id puntato di `sky.crontab` e il render del suo canale commenti, in mezzo all'unica pagina che questa app scrive in una lingua. Non è una questione di tono: `README.md` è prosa dalla Fase 10 — **headings compresi**, che è la deroga esplicita alla regola "le chiavi restano inglesi" — e quella riga chiedeva di conoscere il vocabolario di un altro file per farsi dire che sarà nuvoloso. La 16e l'aveva scritta contro la propria spec: `VISION_SKY.md` §9.1 dà come esempio `> Blue hour looks compromised: 45% cloud forecast at 20:22`, cioè una frase.

Adesso, stesso verdetto e stesso numero:

```
> 🌇 L'ora d'oro della sera alle 19:21: il cielo sarà coperto (nuvole al 100%)
> 🌇 The evening golden hour at 19:21: the sky will be overcast (100% cloud)
```

**La cifra sopravvive alla traduzione.** `VISION_SKY.md` §7 chiede il numero accanto al verdetto: un verdetto senza la cifra da cui è nato è un'opinione, e questa app non stampa opinioni. Mettere in prosa non voleva dire ammorbidire — `il cielo sarà coperto` senza il `100%` sarebbe stato *meno* onesto della riga tecnica che sostituisce, non più leggibile.

**L'id non si localizza: si traduce solo dove si legge.** `SkyJob.id` resta inglese e puntato ovunque (§4): è quello che `sky.crontab` stampa, che lo store persiste e che il log mette sulla check line, e sono tutte superfici di codice. `ui/sky/SkyJobNames.kt` è il **dizionario** fra le due, e vive nel layer UI perché è un fatto di lettura, non di dominio: niente di quello che c'è dentro può arrivare al crontab.

**Il dizionario è totale, e un test lo pretende.** Un job senza nome ricadrebbe sul proprio id — che *è* il bug — quindi `SkyJobNamesTest` cammina su tutto `SkyJobCatalog.all` nelle due lingue e pretende un nome diverso dall'id e senza punti dentro. Trentadue job più dieci sciami: chi aggiunge il trentatreesimo lo scopre dalla suite, non da uno screenshot fra sei mesi. Gli sciami hanno le loro dieci stringhe separate dalla frase (`Il picco delle %1$s` / `The peak of the %1$s`), perché il nome dello sciame è lo stesso in tutte e due le lingue mentre la frase intorno non lo è.

**Il motivo davanti alle nuvole.** `skyVerdictProse` guarda prima la `note` e poi il cielo: pioggia e luna sono *loro* la ragione del verdetto, e stampare la nuvolosità per una notte rovinata dalla luna sarebbe la stessa bugia della 16d in un carattere più gentile. Solo `~ unstable` e `✗ fail` arrivano al README (li filtra `SkyReadme.warning`) e portano sempre la propria cifra: l'ultimo ramo è una rete, non un caso.

**Il costo dichiarato: la riga è più lunga.** `> 🌇 L'ora d'oro della sera alle 19:21: il cielo sarà coperto (nuvole al 100%)` sono ~76 caratteri contro i ~46 di prima, e sullo schermo del committente ne entrano una quarantina: il motivo va cercato con uno scroll, dove prima si intravedeva `✗ fail`. Accettato, e non compensato accorciando: il verbo è quello che distingue `~ unstable` da `✗ fail` (*potrebbe essere nuvoloso* contro *sarà coperto*), quindi una frase nominale avrebbe fatto risparmiare otto caratteri buttando via l'unica informazione che il verdetto porta. Quello che si vede senza scrollare adesso è *quale* cosa e *a che ora*, in parole; prima era un id e un glifo. E il documento scorre in orizzontale per progetto — le due tabelle sono più larghe di questa riga da sempre.

**L'ordine è nome-prima, e non è un gusto: è grammatica.** Le altre due righe di `## Stato` mettono il motivo davanti (`⚠️ Temporale in arrivo verso le 14:00`), che per un badge sarebbe meglio anche qui. Non si può: in italiano il nome del job entrerebbe dopo una preposizione (*per l'ora d'oro*, *per il tramonto*, *per la finestra di buio*) e comporre una preposizione articolata con un pezzo di testo che arriva da `strings.xml` è il classico modo di generare *per Il tramonto*. Il nome tiene il proprio articolo e sta in testa, dove nessuna lingua deve accordarsi con lui.

**L'emoji viene dal job, non dalla riga.** Le altre due righe di `## Stato` aprono con `⚠️` e `🌧️`; questa apre con quella del job (`🌅` `🌇` `🌆` `🌌` `🌙` `🌠`), così la riga dice di cosa parla prima di dire cosa c'è che non va. Sta in `SkyJobNames` e non nel catalogo per lo stesso motivo dei nomi: `sky.crontab` non renderizza emoji, le sue righe sono codice.

- [x] `ui/sky/SkyJobNames.kt`: i 32 job e i 10 sciami in parole, EN/IT, più l'emoji per famiglia
- [x] `skyVerdictProse` in `WeatherReadme.kt`: pioggia / luna / nuvole, ognuna con la sua cifra
- [x] Test: la riga parola per parola in EN e in IT, il motivo giusto per pioggia e per luna, e il dizionario totale sul catalogo
- [x] Suite verde (560 test, +6) e lint pulito (gli stessi warning di prima, tutti preesistenti)

**Il resto del documento era già a posto.** Riletto tutto `toReadmeMarkdown` riga per riga cercando la stessa classe di errore: intestazioni, `## Stato` builtin, tabelle, qualità dell'aria, condizioni, astronomia e piè di pagina passano tutti da `strings.xml` o da `WeatherTranslations`, i giorni della settimana dal `Locale`, e `∅` è un glifo dichiarato (Fase 16e), non una parola non tradotta.

**Una cosa resta, ed è una decisione del committente, non una svista mia:** la direzione del vento (`Vento: 2.9 km/h W`) non è localizzata. In italiano quel punto cardinale si scrive `O`, e con lui `NO`, `SO`, `ONO`, `OSO`, `NNO`, `SSO`: sette dei sedici punti sono sbagliati per un lettore italiano. **Non l'ho toccata qui** perché non è un messaggio, è un *valore* — lo stesso che stampa `weather_data.json`, che finisce negli snapshot Room come `current.wind_dir` e che il widget rende — quindi tradurlo è una riga in `WeatherTranslations` più una decisione su tre superfici, e la regola "i valori si traducono, le chiavi no" direbbe di farlo su tutte e tre insieme. Fase a parte, se si vuole.


## Fase 17 — Il README parla anche quando l'aggiornamento fallisce

Due segnalazioni del committente (27 ago 2026) sulla stessa schermata: uno screenshot con l'app aperta senza rete, in cui `README.md` è **due righe di commento e basta**.

```
<!-- ERROR: net::ERR_INTERNET_DISCONNECTED — check your connection -->
<!-- hint: tap ( ↻ ) to retry -->
```

**1. Anche questi messaggi vanno in prosa. Sì, e per lo stesso motivo della 16g.** `net::ERR_INTERNET_DISCONNECTED` è il nome che Chrome dà a "il telefono è offline": perfetto dentro un file che è codice, un test di vocabolario dentro l'unico documento scritto per chi non legge `git` per lavoro. `weather_data.json` se lo tiene — lì il codice d'errore è la forma *utile* del fatto, e la riga `GET https://api.open-meteo.com/v1/forecast` accanto ha senso solo lì. Il README dice la stessa cosa in una frase, localizzata come tutto il resto della pagina, e la riga `GET` semplicemente non è parte di questo file: non ha una versione in prosa perché non è un fatto che riguardi il lettore.

`WeatherException.terminalMessage` non cambia di una virgola: è quello che rendono le altre tre superfici. `WeatherStateProse` è una **seconda lettura** dello stesso valore, non un rimpiazzo — lo stesso rapporto che `SkyJobNames` ha con `SkyJob.id`.

**2. I dati vecchi possono restare più a lungo? Sì, e non è un compromesso: è l'app che smette di buttare via quello che ha.** La risposta onesta comincia da una scoperta imbarazzante: **quel telefono aveva una settimana di previsioni su disco.** `ReportDiskCache` tiene l'ultima risposta per città, e `getWeather` la rilegge solo **dentro il TTL**; scaduto quello va in rete, fallisce, e il file scritto un'ora prima non viene nemmeno guardato. Non è che mancasse il dato: c'era, ed è stato scartato.

E c'è di peggio, come argomento: **il widget non l'ha mai fatto.** Dalla 9d rende l'ultimo snapshot che ha e lo marca `# stale` in rosso. Nella superficie con meno spazio di tutte l'app ha già preso questa decisione, e l'ha presa giusta; l'editor, che ha uno schermo intero per spiegarsi, mostrava una pagina bianca. Non stavo aggiungendo una feature, stavo allineando l'editor al widget.

### Perché non basta "mostrarli e avvisare"

Un fetch di tre ore fa apre con **tre ore che sono passate**. Stamparle sotto `## Prossime ore` non è un dato vecchio, è un dato **sbagliato**, e `## Oggi` — che legge `daily.first()` — sarebbe il giorno del fetch, cioè ieri. Un avviso in cima non ripara una tabella che mente: dice al lettore di diffidare di tutto invece di dirgli cosa non vale più.

Quindi `domain/WeatherRecency.kt`, il compagno di `WeatherFreshness`: la freschezza dice **se fidarsi**, la recency dice **quali righe** non sono ancora successe. Toglie le ore prima di quella corrente e i giorni prima di oggi, **nel fuso della città**, e non sposta né inventa niente: quello che resta è ciò che quel fetch ha sempre detto delle ore a venire. Dopo il taglio ogni sezione futura torna vera, e `## Oggi` è di nuovo oggi — un giorno di previsione fatto ieri, che è una cosa legittima da stampare.

Gira su **ogni** report, non solo su quelli recuperati: su un fetch appena atterrato è un no-op (ritorna la stessa istanza, c'è un test), ma su una **cache HIT** ripara un bug che c'era già — con `update_frequency_min = 120` un HIT può avere 119 minuti e `## Prossime ore` apriva con due ore finite.

### La scadenza è nel dato, non in una costante

Niente "mostralo fino a N ore". Un report vale finché **la sua previsione arriva al presente**: `WeatherRecency.coversNow`. Oltre l'orizzonte non è un dato vecchio, è il verbale di una settimana finita — `## Attuale` sarebbe una rilevazione di otto giorni fa, `## Oggi` il giorno di qualcun altro e le due tabelle vuote. Lì non c'è più niente su cui essere onesti e l'editor torna a mostrare il solo errore, com'era.

**E il disk cache è dovuto crescere, o la risposta sarebbe stata "quattro ore".** `ReportDiskCache.prune` cancellava tutto sopra le 4h — "il doppio del TTL massimo, quindi non potrà mai essere riletto come hit". Vero finché un'entry poteva essere solo un hit. Adesso è anche il documento offline, quindi il taglio d'età va all'orizzonte delle previsioni (7 giorni) e il lavoro che faceva prima — impedire che la pseudo-città GPS, una cacheKey ogni ~1,1 km, lasci un file per ogni posto in cui si è passati — lo fa un **tetto sul numero** di file (16, i più recenti). Un'entry sfrattata costa un fetch se c'è rete e il fallback di *quella* città se non c'è: per questo il tetto è sul conteggio e non sull'età, che è esattamente ciò che al fallback serve.

### Il badge non deve tacere per aver guardato l'ora sbagliata

`## Stato` valuta l'AlertEngine con `now = location.localTime`, cioè **l'orologio del fetch**. Su un documento recuperato di tre ore quella finestra è chiusa: la pioggia delle 18:00 cade fuori dalle sei ore contate dalle 11:30, e il badge risponde *"Tutto regolare"*. Un badge che tace perché sta guardando le ore sbagliate è peggio che nessun badge — è l'unico modo in cui questa sezione può mentire. Quindi `toReadmeMarkdown` prende `now`, che per default resta `location.localTime` (giusto per un fetch appena atterrato, e nessun chiamante o test è cambiato) e che il documento recuperato passa vero. C'è un test che pretende entrambi i comportamenti sullo stesso report.

`location.localTime` **non** viene ritoccato: è l'ora del fetch, e nel JSON `local_time` deve continuare a dire quella. Un unico campo vivo dentro un documento congelato farebbe sembrare fresco tutto il resto.

### Cosa si vede

```
<!-- Nessuna connessione: il meteo non si è aggiornato. -->
<!-- Qui sotto l'ultimo aggiornamento riuscito, delle 08:30 (3 ore fa). -->
<!-- Tocca ( ↻ ) per riprovare. -->
```
```
// ERROR: net::ERR_INTERNET_DISCONNECTED — check your connection
// stale: last good fetch 3h ago
// hint: tap ( ↻ ) to retry
```

Tre righe, tre fatti, una per riga: cos'è andato storto, cosa stai guardando, cosa puoi fare. L'età è in cima **prima** dei numeri, perché è lì che cambia cosa il lettore ne fa; l'orario esatto è anche in fondo, nel piè di pagina che c'era già, e nella barra di stato che è sempre visibile. La soglia è quella che l'app ha già: `WeatherFreshness`, il doppio dell'intervallo di sync — un HIT non può essere stale, quindi la nota compare solo su un documento recuperato.

**I plurali sono plurali veri** (`<plurals>`, con il `many` che il CLDR chiede all'italiano): `1 ore fa` è il genere di dettaglio che fa smettere di fidarsi del resto della pagina.

- [x] `WeatherStateProse`: gli otto `WeatherException` e l'età in parole, EN/IT
- [x] Righe di stato del README in prosa (errore, caricamento, GPS, nessuna posizione); il JSON invariato
- [x] `domain/WeatherRecency.kt`: `trim` + `coversNow`, con i test del fuso e dell'orizzonte
- [x] Fallback all'ultimo fetch riuscito nel ViewModel + `staleFor` nello stato
- [x] `ReportDiskCache`: età all'orizzonte delle previsioni, tetto di 16 entry
- [x] `now` in `toReadmeMarkdown`, con il test del badge che tace
- [x] Test end-to-end: cold start senza rete con e senza cache, e con una cache oltre l'orizzonte
- [x] `HELP.md` (EN/IT), `README.md` (senza trattini), CHANGELOG, CLAUDE.md
- [x] Suite verde (572 test, +12) e lint pulito

**Quello che ho deciso di NON fare, e perché.** `## Attuale` resta `## Attuale` anche su un documento di tre ore: è l'unica sezione che è una *rilevazione passata* e non una previsione, e la tentazione era di riempirla con la riga oraria corrispondente presa dalle previsioni. Sarebbe stato inventare un'osservazione da una previsione — esattamente la bugia che questo progetto non si concede — quindi la sezione resta quella del fetch e sono le tre righe in cima a dire di quando è. Per lo stesso motivo `## Astronomia` di un documento di ieri stampa l'alba di ieri (uno o due minuti di differenza): è un dato del fetch, e correggerlo di soppiatto sarebbe stato riscrivere il documento invece di datarlo.


## Fase 18 — I registri: la lingua segue la frase, non le barre

Domanda del committente (27 ago 2026), con davanti uno screenshot di `sky.crontab` su un telefono italiano: la regola "il codice resta inglese" si può ammorbidire almeno sui commenti, per non lasciare fuori chi non legge l'inglese, senza affogare la filosofia terminal/git del progetto?

**Sì, e non come concessione: la regola scritta in Fase 6b contiene un errore di categoria.** Dice `commenti // → inglese`, ma `//` non è una categoria semantica, è punteggiatura. Sotto quel simbolo l'app ha sempre messo due cose diverse:

```
// GET https://api.open-meteo.com/v1/forecast                        ← la macchina che parla
// light pollution is not modelled: the app does not know your sky   ← l'app che parla al lettore
```

La prima riga è **contenuto del file**: un identificatore, la stessa stringa che esiste altrove nel codice. La seconda è una frase che ha per unico scopo essere capita, e nella lingua sbagliata non fa niente. Stanno nello stesso canale per accidente tipografico, non per parentela.

### L'argomento vero: git è localizzato

Con `LANG=it_IT`, `git status` scrive "Sul branch main", "non c'è nulla di cui eseguire il commit": traduce le frasi e tiene i sostantivi, `branch`, `commit`, `HEAD`, `origin/main` restano. `gcc` fa lo stesso con le diagnostiche. **Gli strumenti su cui è costruita la metafora fanno già esattamente questo split**, quindi un `sky.crontab` con i job in inglese e le note in italiano non è un finto editor tradotto male: è com'è fatto un vero terminale italiano. La finzione non si indebolisce, diventa più fedele — oggi l'app è più inglese di git stesso, e non c'è nessun principio che lo giustifichi.

Il progetto ci era già arrivato tre volte, senza generalizzare: il messaggio di una regola in `alerts.rules` non è localizzato perché "è contenuto dell'utente, nella sua lingua per definizione" (Fase 11), `$ tweather init` lo è perché "è l'unica schermata il cui scopo è farsi capire da chi non legge `git` per mestiere" (14c), `README.md` (10) e `HELP.md` (14d) idem. Il canale dei commenti era l'ultima superficie in cui l'app si rivolge al lettore in una lingua che il lettore potrebbe non leggere.

### La regola nuova

> **La sintassi è la finzione, la lingua è del lettore.** Il codice resta inglese perché è codice, non perché sta dentro un commento.

Tre registri, e il registro decide la lingua — non il canale che lo circonda:

| registro | cos'è | lingua |
| --- | --- | --- |
| **Codice** | chiavi, nomi file, id dei job, variabili e operatori delle regole, campi cron, comandi `$`, chrome git (`commit`, `Author:`, `@@`, hash), verdetti (`✓ pass`, `~ unstable`, `✗ fail`), livelli (`ERROR:`, `WARN:`), codici (`net::ERR_*`), licenze, URL | inglese, sempre |
| **Dati** | valori meteo, nomi città, giorni della settimana, fasi lunari | localizzati (invariato dalla 6b) |
| **Prosa** | frasi rivolte al lettore, **ovunque si trovino**: `README.md`, `HELP.md`, first run, notifiche, accessibilità **e le righe di commento che sono frasi** | localizzate |

Due test operativi, in quest'ordine:

1. **Tradurlo romperebbe qualcosa?** Un lookup, un nome file, un copia-incolla, l'allineamento con una chiave stampata altrove → è codice, resta inglese.
2. **git lo tradurrebbe?** git traduce "nothing to commit, working tree clean" e non traduce `commit`. È l'intuizione giusta per ogni caso futuro.

E la **regola della cucitura**, che serve perché quasi nessuna riga è pura: una riga può contenere due registri, e allora si tengono i token e si traduce intorno.

```
// ERROR: permission denied — gps stays off
// ERROR: permesso negato — il gps resta spento

// refresh weather_data.json to record the first one
// aggiorna weather_data.json per registrare il primo
```

### Cosa cambia in `sky.crontab`, che è la schermata da cui è partita

Cambiano solo le righe intere: l'intestazione `# times are computed per occurrence, not fixed; see each line`, il `// evaluate every enabled job against the forecast in hand:` sopra il comando, e i quattro `//` del blocco finale (soglie, luna, inquinamento luminoso, "un verdetto è l'opinione della previsione, non un'osservazione"). Numeri e simboli dentro quelle righe restano come sono: `≤ 25%`, `≥ 60%`, `≥ 70%`.

Non cambia niente di quello che sta in colonna: `@daily`, `*/30 * * * *`, `@yearly`, `sun.rise`, `golden_hour.am`, `solstice.summer`, `[rm]`, `+ add job`, `$ tweather run sky`, e nel canale dei commenti `~ unstable`, `✓ pass`, `cloud 47%`, `+1m07s vs yesterday`, `in 297d`. `full moon` è un valore meteo, quindi si localizzava già dalla 6b.

**Non è una coincidenza fortunata, è una conseguenza del criterio**: i commenti in colonna sono quasi sempre dati, quelli a riga intera quasi sempre prosa. Il che protegge l'allineamento, che è il costo reale di questa fase — l'italiano è più lungo del 15-20%, e questo repo ha già pagato quel prezzo una volta (`Giorno` → `Gg` nella tabella giornaliera, Fase 11c: tre caratteri erano la differenza tra vedere la colonna Stato e inseguirla in pan).

### I casi di confine, decisi

- **`# stale`, `# amended`** (marcatori di una parola): **inglesi**. Sono della famiglia dei verdetti, e `README.md` dice già la stessa cosa in prosa (Fase 17).
- **`// ERROR:` / `// WARN:`**: il livello è un token e resta, la frase dopo si traduce. `net::ERR_*` e le righe `GET https://…` restano intere: lì il codice d'errore è la forma *utile* del fatto, dentro un file che è codice.
- **Intestazioni di file** (`// Tweather Configuration File`, `// Tweather CI — user-defined notification rules`, `// tweather editor canvas`): **inglesi**. Sono la firma dell'artefatto, non un messaggio al lettore: la stessa cosa di uno shebang o di un header di licenza.
- **`// hint:`**: si traduce anche l'etichetta. Non è un livello di log, è una parola che l'app si è inventata.
- **`// polling every 60 min`**: parole tradotte, unità no.

### Quello che questa fase NON è

**Non è "rendere l'app amichevole a chi non è tecnico", e sarebbe disonesto scriverlo qui.** Un lettore italiano che non programma continua a vedere `weather_data.json`, `precip_chance`, `cloud_cover`, `solstice.summer`, `@daily`: i commenti sono una frazione di quello che c'è a schermo. Lo strato in lingua piana esiste già ed è progettato apposta — `README.md`, `HELP.md`, le notifiche, l'accessibilità — e thabit lo mette per iscritto (`VISION.md §3.3.7`: nessun termine CI è mai l'unico posto dove un fatto esiste).

La motivazione giusta è un'altra, e regge da sola: **quando l'app parla al lettore, gli parla nella sua lingua.** Va tenuta stretta, perché con la motivazione sbagliata fra sei mesi lo stesso argomento chiederà di tradurre `precip_chance`, e lì la filosofia muore davvero.

**E si applica al 100% o non si applica**: una regola smarcata all'80% non sembra una scelta, sembra una traduzione lasciata a metà. Per questo l'implementazione è una fase chiusa per app, non una rifinitura opportunistica.

- [x] Regola dei tre registri in `CLAUDE.md`, con i due test e la regola della cucitura
- [x] Intestazioni di `values/strings.xml` e `values-it/strings.xml` riscritte: enunciavano la vecchia regola parola per parola, ed erano il posto in cui la si legge scrivendo una stringa nuova
- [x] Propagata a `tsteps` e `thabit` (`VISION.md §1.3`, `CLAUDE.md`, intestazioni delle risorse): è una regola di serie, non di questa app
- [x] Corrette le due formulazioni che dicevano il contrario: `VISION_SKY.md` §4 ("English, like every other code surface") e la 16c qui sopra, che chiamavano codice il *canale* invece dei token che ci passano dentro
- [x] `README.md` di root e `CHANGELOG.md` **non toccati, deliberatamente**: descrivono l'app spedita, e nel codice i commenti sono ancora inglesi. Aggiornarli adesso sarebbe il file che mente, che è la regola che questo progetto rispetta prima di tutte le altre; si aggiornano con l'implementazione
- [x] Implementata in thabit per prima (`../thabit/PLANNING.md` Fase 15), perché era quella che ci guadagnava di più — i suoi commenti erano già quasi tutti frasi rivolte al lettore

### L'implementazione qui (28 ago 2026)

**Il problema di architettura era un altro rispetto a thabit.** Lì i documenti sono valori puri e la prosa è diventata un `@StringRes` che il renderer risolve. Qui i costruttori di righe (`buildSettingsLines`, `buildRulesLines`, `buildLogLines`, …) sono funzioni normali chiamate da un composable, e uno di essi — quello dei Logs — vive dentro un `remember`, quindi non può diventare `@Composable`. Ma il pattern giusto era già nel repo: `buildReadmeLines` prende un `Resources`, `buildLogLines` prende un `translate: (String) -> String`, `SkyLabels` è costruita da `Resources` e passata dentro. **Si passa il traduttore, il costruttore resta una funzione.**

Quindi: **le schermate ricevono un `Resources`** e antepongono il marcatore a una risorsa (`fun note(id) = "// " + resources.getString(id)`, una riga per file). Nei Logs `resources` entra anche fra le chiavi del `remember`: un cambio di lingua per-app ricrea l'activity con risorse nuove, e il file va ricostruito nella lingua in cui adesso viene letto.

**I due builder che sono valori puri restano tali**, e ricevono le frasi come stringhe: `SkyDocumentBuilder` via un `SkyNotes`, `WidgetContentBuilder` via due parametri. È la condizione per non trascinare Robolectric dentro `SkyVerdictRenderTest` e compagni, che sono test JVM puri e devono restarlo.

**Il prezzo di quella scelta è una duplicazione dell'inglese**, e non l'ho lasciata correre: `SkyNotes.EN` e i default di `WidgetContentBuilder` sono legati a `values/strings.xml` da due test che li confrontano parola per parola. Toccarne uno solo fa diventare rossa la suite, che è l'unico motivo per cui la duplicazione può esistere.

### La riga che il modulo cielo non muove, e perché

`sky.crontab` ha una colonna allineata di **prove**: l'istante risolto, la parola del verdetto, la quantità da cui è nato (`cloud 47%`), la deriva (`+1m07s vs yesterday`). Quella colonna resta inglese per intero — è lo stesso vocabolario che `sky_runs.log` stampa e che le check line della history contengono, e una riga tradotta lascerebbe una colonna allineata a parlare due lingue. **La lettura localizzata di quegli stessi fatti esiste già ed è il `## Astronomia` del README**, scritto apposta nella Fase 16g.

Si muove invece tutto ciò che *spiega*: perché un job non è in programma (`giorno polare: qui il sole resta sopra l'orizzonte`), perché manca un verdetto (`(nessun fetch ancora)`), cosa sta facendo la luna, le tre righe di intestazione e le quattro note in fondo.

### Due cose trovate dalla traduzione, che non erano di traduzione

**1. `// current_location.json in explorer` puntava a una scheda che non esiste più.** Il primo tab ha perso quel nome nella Fase 11b e solo la sua rotta di navigazione l'ha tenuto: la riga mandava il lettore a cercare una parola che non è scritta da nessuna parte. Ora dice `in cities.json`, che è il file in cui quell'entry vive davvero. Il test che congelava la vecchia riga è stato riscritto, non cancellato.

**2. La fase lunare nel crontab era inglese** (`🌕 full moon, 99% lit`) mentre `weather_data.json` due tab più in là diceva `luna piena`. Non è una svista della Fase 18: una fase lunare è un **valore**, e i valori si localizzano dalla 6b. Il modulo cielo non l'aveva mai chiesto. Ora passa dallo stesso `WeatherTranslations` che usano schermata principale e widget.

- [x] 47 risorse nuove EN/IT; parità verificata
- [x] Tutte le superfici: `weather_data.json`, `cities.json`, `settings.config`, `alerts.rules`, `history.diff`, `forecast.diff`, `sky_runs.log`, `sky.crontab`, il widget e il suo selettore
- [x] `RegisterRuleTest`: guardia sulla regola, non su una schermata — nessuna nota porta il proprio marcatore o il proprio livello, e ognuna dice qualcosa di diverso in italiano. Ha già trovato l'unico caso che resta identico (`current_location.json in cities.json`: due nomi di file e una preposizione uguale nelle due lingue), esentato con la motivazione scritta invece che ammorbidendo il test
- [x] `SkyNotesTest` e `WidgetNotesTest`: le due guardie sulla duplicazione dell'inglese
- [x] Test in italiano su ogni superficie, ognuno che asserisce **entrambe** le metà — la frase tradotta e il token inglese accanto. `SkyRunsLogTest` è passato a Robolectric, perché quello che asserisce adesso dipende da chi legge
- [x] Suite a 593 (era 572), lint pulito, release minificata compilata
### Il giro sul device (28 ago 2026): sei righe rimaste, e il motivo per cui erano rimaste

Il committente ne ha vista **una** in uno screenshot di `settings.config`: `// every sky.crontab line without its own; 15m floor`, ancora inglese in mezzo a righe italiane. Rifatto il controllo per bene, ne sono saltate fuori **sei**, tutte della stessa classe.

**Il motivo è il metodo, non la fretta, e va scritto perché è la lezione della fase.** Avevo cercato i commenti con un `grep '"//[^"]*"'`, che ancora `//` all'**inizio** del literal. Queste sei sono continuazioni: `append(",  // every …")`, `append("  // tap again to confirm")`. Un'ancora sbagliata e la lista sembra completa. È successo tre volte in questa serie, e ogni volta se n'è accorto un occhio umano davanti a uno screenshot.

Le sei: la nota di `notify_default`, la conferma a due tocchi (`// tap again to confirm`, **la stessa frase su tutti e quattro i comandi `$` dell'app**, quindi una stringa sola) e la riga `sky_line` del selettore widget.

**Quindi lo sweep è diventato un test.** `CommentChannelSweepTest` cammina sui sorgenti Kotlin, prende ogni literal che porta un marcatore di commento e fallisce su quelli che **leggono come una frase**: tre parole minuscole di fila dopo il marcatore. È il criterio che separa `// tap again to confirm` da `// active`, `// gps`, `// CC BY 4.0` e `// 15 | 30 | 60 | 120`, ed è tarato sul repo — con la regola applicata gira su tutto `src/main/java` e trova due sole eccezioni, entrambe legittime e in allowlist con la motivazione scritta.

Verificato che non passi a vuoto: rimessa una delle sei righe al suo posto sbagliato, il test diventa rosso; ripristinata, torna verde. Una guardia che non ho visto fallire non è una guardia.

- [x] Le sei righe mancate, con `note_tap_again` condivisa dai quattro comandi
- [x] `CommentChannelSweepTest`: lo sweep come test, con l'allowlist come verbale di cosa resta inglese e perché
- [x] Suite a 596, lint pulito, release minificata compilata

**Una cosa lasciata com'è, e non per svista.** Nel crontab la riga di `moon.today` legge `# 🌕 luna piena, 99% lit`: la fase è un valore e si localizza, `lit` no. È l'etichetta di una quantità, esattamente come `cloud` in `cloud 57%` sulla riga sopra, e tradurne una sola renderebbe la colonna incoerente; tradurle tutte vuol dire muovere la colonna delle prove, che è la cosa che questa fase ha deciso di non muovere. Il README dice lo stesso fatto in italiano (`illuminata al 99%`), che è il registro localizzato del cielo dalla 16g. Segnalata al committente come scelta reversibile di una riga.

- [x] Poi tsteps, ultima della serie — **fatta**: la Fase 20 di tsteps ha chiuso il giro, e la regola dei tre registri vale ora al 100% su tutte e tre le app della serie

### Una nota sulla guardia (ago 2026, chiudendo la serie)

`RegisterRuleTest` elencava le sue note **a mano**. Quando la Fase 20 di tsteps è venuta a copiarla, il confronto con `strings.xml` ha detto che di 61 note ne sorvegliava 50: undici erano state aggiunte senza mai finire nell'elenco, e la guardia era verde perché guardava altrove. Nessuna delle undici era sbagliata — passano tutte gli invarianti, il che è la buona notizia — ma è esattamente il modo in cui una regola smette di valere al 100% senza che niente diventi rosso.

Ora la lista si prende **per riflessione** su `R.string`: quello che non si tiene aggiornato non si può dimenticare, e una nota scritta domani è sorvegliata il giorno che esiste. Con lei è arrivato un test che l'allowlist non marcisca (un nome esentato che non è più una nota è un'esenzione che nessuno ha più riletto) e uno che verifica che la riflessione non torni a mani vuote, il che farebbe passare tutto il resto per vuoto.


## Fase 19 — v2.0.0 su GitHub (ago 2026)

Il giro sul device del committente (29 ago 2026) è tornato pulito su tutte le superfici che la 13, la 16f e la 18 avevano lasciato in attesa, e con lui sono arrivati gli screenshot veri in `docs/screenshots/`. Le due caselle rimaste aperte in questo file erano quelle, e si chiudono qui.

**Perché 2.0.0 e non 1.1.0.** Il semver di un'app senza API pubblica non ha un contratto da rompere, quindi il numero non lo decide il compilatore: lo decide cosa trova in mano chi aggiorna. Dalla 1.0.0 tweather ha preso **un modulo intero** che prima non esisteva (il cielo: `sky.crontab` come terzo tab dell'editor, `sky_runs.log` come terzo file dei Log, i promemoria `--notify` con la loro sveglia inesatta), un **primo avvio diverso** (`$ tweather init`, e soprattutto la città seminata che non c'è più — "nessuna città" è diventato uno stato vero), un file che prima non c'era (`HELP.md`), il comportamento offline della Fase 17 e la regola dei tre registri della 18, che cambia **la lingua in cui l'app parla** a chi la legge in italiano. Chi apre la 2.0.0 dopo la 1.0.0 apre un'altra applicazione: la minor sarebbe stata una scortesia verso il changelog.

**Il percorso di aggiornamento è coperto, e vale la pena metterlo a verbale** perché è l'unica cosa che una release può rompere in modo silenzioso:

- **Room va da 3 a 4** (la colonna `sky_runs` della 16e). `MIGRATION_3_4` esiste ed è registrata in `ServiceLocator`, insieme alle due precedenti: nessun `fallbackToDestructiveMigration` in questo repo, quindi la storia dei commit di chi aggiorna resta dov'è.
- **`CityStore.migrateFirstRun(hasHistory)`** (Fase 14b) è ciò che impedisce alla 2.0.0 di presentare `$ tweather init` a chi usa l'app da mesi: distingue un'installazione usata da una fresca guardando se i Log hanno anche un solo commit, e chi aggiorna tiene la città che stava guardando.
- **La firma non cambia**: stessa chiave di release della 1.0.0, quindi l'APK si installa sopra senza disinstallare e senza perdere città né impostazioni.

- [x] `versionCode` 1 → **2**, `versionName` **2.0.0** in `app/build.gradle.kts`
- [x] `CHANGELOG.md`: la sezione `[Unreleased]` — che era già scritta fase per fase — diventa `[2.0.0] — 2026-08-30`, con il link al tag in fondo accanto a quello della 1.0.0
- [x] Caselle di verifica su device chiuse: l'UV di `## Oggi` (Fase 13) e il secondo giro dei registri (Fase 18). Chiusa anche la riga d'ordine della 18 (`Poi tsteps`): la Fase 20 di tsteps ha chiuso il giro, e la regola dei tre registri vale ora al 100% sulle tre app della serie
- [x] Suite e lint rieseguiti prima del tag: **598 test verdi**, lint **0 errori** (47 warning, la baseline del repo). Sono la stessa doppia guardia che `release.yml` rimette prima di firmare: qui servono a non scoprire una suite rossa dopo aver spinto un tag
- [x] `README.md`: il conteggio dei test nel blocco Build era fermo a 317, cioè a una release fa. Corretto a 598 — è la vetrina del repo, e un numero vecchio lì è l'unico posto in cui questo file può mentire
- [x] **`release.yml` prende il corpo della release dal `CHANGELOG.md`.** Il workflow si affidava al solo `generate_release_notes`, che elenca commit e PR dal tag precedente: un verbale di *chi ha spinto cosa*, non di cosa è cambiato per chi installa. Ora un passo estrae la sezione del tag e la passa come `body_path`, e le note generate da GitHub restano sotto. La sezione si cerca per **prefisso letterale** e non per regex — i punti di `2.0.0` in una regex matcherebbero qualunque carattere — e si chiude sul primo `## [` successivo **o** sul blocco dei link in fondo, che altrimenti finirebbe nel corpo dell'ultima sezione del file. Un tag senza sezione non fa fallire la release: scrive un `::warning::` e lascia le note generate da sole, perché fra la prosa e l'APK firmato è la prosa a poter aspettare. Vale da qui in avanti, non solo per questa release

## Note trasversali

- **Vincoli di design non negoziabili** (vedi `CLAUDE.md` e `DESIGN.md`): solo JetBrains Mono, griglia 4px, indent 20px, niente ombre (solo bordi 1px + glow del FAB), raggio 4px, controlli renderizzati come testo.
- **Ordine consigliato**: le fasi 1–2 sono il fondamento di tutte le schermate; la fase 3 può procedere in parallelo alla 2. Le fasi 4–8 dipendono da 1–3.
- Aggiornare questo file smarcando i passi completati e annotando eventuali deviazioni dal PRD.
- **CI (rivista ago 2026)**: `.github/workflows/android-ci.yml` — action portate alle major su Node 24 (checkout v7, setup-java v5, upload-artifact v7, gradle/actions v6), e soprattutto la pipeline ora esegue **test unitari e lint prima delle build**: i 181 test esistevano ma non facevano da guardia sul repo, e un artifact installabile non deve poter nascere da una suite rossa. Oltre all'APK debug produce l'APK **release minificato** (l'unica build dove i problemi R8 possono manifestarsi) e la **mapping R8**, senza la quale uno stack trace di release è illeggibile. La release è firmata con la chiave di debug **solo su flag esplicito**, così un artifact da store non può nascere per sbaglio con una chiave committata.
