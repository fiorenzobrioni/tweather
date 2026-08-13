# PLANNING.md — Piano di realizzazione tweather

Piano di sviluppo per **tweather**, app meteo Android (Kotlin + Jetpack Compose) con UI in stile code editor (tema "Obsidian Syntax"). Ogni passo è smarcabile: `[ ]` da fare → `[x]` completato.

Riferimenti: `tweather_comprehensive_project_prd_final.md` (requisiti), `obsidian_syntax/DESIGN.md` (design system), mockup HTML/PNG nelle cartelle `*_code_editor`, `search_*`, `settings_*`, `logs_*`, sample dati in `weather_data.json_full_sample.json`.

---

## Fase 0 — Setup progetto

- [x] Inizializzare il repository git (`git init`) e creare `.gitignore` per Android/Kotlin — remote: https://github.com/fiorenzobrioni/tweather.git, licenza GPL-3.0
- [ ] Creare progetto Android Studio: template "Empty Activity" (Compose), package `app.tweather`
- [ ] Configurare Gradle (Kotlin DSL): Kotlin 1.9+, Compose BOM, Material 3, minSdk 26+, targetSdk aggiornato
- [ ] Aggiungere dipendenze: Retrofit + OkHttp, Kotlinx.serialization (+ converter Retrofit), Navigation Compose, DataStore (preferenze), Room (storico/log), Hilt (DI), Coil non necessario (icone = emoji Unicode)
- [ ] Importare il font **JetBrains Mono** (pesi 400/500/600/700) in `res/font`
- [ ] Configurare versioning e build variants (debug/release)
- [ ] Primo commit: progetto scheletro che compila e mostra una schermata vuota

## Fase 1 — Design system e tema

- [ ] Definire la palette Material 3 dal frontmatter di `obsidian_syntax/DESIGN.md` (surface `#10141a`, container `#181c22`, primary `#b5d9ff`, ecc.) in `ui/theme/Color.kt`
- [ ] Definire i colori di syntax highlighting come token dedicati: keys `#79c0ff`, strings `#a5d6ff`, numbers/booleans `#ffa657`, comments/braces `#8b949e`, diff add `#2ea043`, diff del `#f85149`, bordi `#30363d`
- [ ] Creare `Typography` con JetBrains Mono (headline-lg 32/24px, body-md 14px, code-block 13px, label-sm 11px, status-bar 12px) in `ui/theme/Type.kt`
- [ ] Creare `Shapes`: raggio 4px per tutti i container; FAB circolare come unica eccezione
- [ ] Implementare il tema scuro "Obsidian Syntax" come default (`ui/theme/Theme.kt`); predisporre la struttura per profili tema multipli (Obsidian, Dracula, Monokai)
- [ ] Regola "no shadow": profondità solo con bordi 1px e tonal stacking; glow solo per il FAB (`0 0 15px #79c0ff88`)

## Fase 2 — Componenti UI riusabili ("editor kit")

- [ ] `CodeCanvas`: container scrollabile con gutter dei numeri di riga a sinistra e contenuto monospaziato allineato
- [ ] `SyntaxText` / renderer JSON: trasforma una struttura dati in `AnnotatedString` con highlighting (chiavi blu, stringhe azzurre, numeri arancio, punteggiatura grigia) e indentazione 20px per livello
- [ ] `EditorTab` (top bar): mostra il nome del file attivo (es. `terminal tweather.json`) in stile tab di editor
- [ ] `TerminalStatusBar`: barra fissa 28px per metadati secondari (es. "Last Updated: 12:01:04", icona branch per la località)
- [ ] `CodeBlockContainer`: box con bordo 1px `#30363d`, header con filename e icona collapse
- [ ] `TreeViewItem`: elemento con simboli `▸`/`▾` (o `+`/`-`) e guide verticali 1px per i figli annidati
- [ ] `TerminalInput`: input a riga singola stile prompt (`> Search Location _`) con cursore underscore lampeggiante
- [ ] `CodeCheckbox` / `CodeToggle`: rendering testuale `[x]` / `[ ]` interattivo
- [ ] `GlowFab`: FAB circolare con glow, icona stroke 2pt (refresh/"Run")
- [ ] Anteprime `@Preview` per ogni componente per validarli contro i mockup PNG

## Fase 3 — Layer dati (dominio e rete)

- [ ] Definire i modelli di dominio dal sample `weather_data.json_full_sample.json`: `Location`, `CurrentConditions` (temp, feels like, umidità, vento, precipitazioni, UV), `AirQuality` (AQI + inquinanti PM2.5/PM10/O3/NO2/SO2/CO), `PollenReport`, `Astronomical` (alba/tramonto, fase lunare, durata giorno), `HourlyForecast` (24h), `DailyForecast` (7 giorni), `SystemInfo` (source, last_sync, cache status)
- [ ] Integrare **Open-Meteo** come provider (gratuito, nessuna API key): Forecast API (`api.open-meteo.com/v1/forecast`) per condizioni correnti, orarie e giornaliere + dati astronomici (sunrise/sunset); Air Quality API (`air-quality-api.open-meteo.com/v1/air-quality`) per AQI, inquinanti (PM2.5/PM10/O3/NO2/SO2/CO) e pollini (disponibili solo in Europa — gestire l'assenza del dato altrove)
- [ ] Implementare il client Retrofit + OkHttp con Kotlinx.serialization; DTO separati dai modelli di dominio + mapper
- [ ] Implementare la ricerca città con la Geocoding API di Open-Meteo (`geocoding-api.open-meteo.com/v1/search`) per la schermata Search
- [ ] Nota dati non forniti da Open-Meteo: fase lunare da calcolare localmente (algoritmo astronomico) o omettere in v1; `source` in `system_info` = `"Open-Meteo API"`
- [ ] Mappare le condizioni meteo alle emoji Unicode (`☀️`, `🌧️`, `⛅`, `☁️`, `🌙`, fasi lunari `🌔`…) in un'unica utility
- [ ] Repository con cache locale: ultimo dato per città, stato cache HIT/MISS, timestamp `last_sync` per la sezione `system_info`
- [ ] Persistenza dello storico aggiornamenti (Room): ogni fetch salvato come "commit" (hash generato, autore `sys@tweather.app`, timestamp, snapshot valori) per la schermata Logs
- [ ] Gestione errori: assenza rete, città non trovata, errore API — con messaggi in stile terminale

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
