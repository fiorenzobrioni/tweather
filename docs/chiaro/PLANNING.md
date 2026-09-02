# PLANNING.md — Chiaro

Piano di lavoro a fasi, con step spuntabili. **Ogni decisione e ogni deviazione si
annotano qui con il motivo** (regola della serie, ereditata da tweather). Il perimetro
del prodotto sta in `VISION.md`, il sistema di design in `DESIGN.md`, la provenienza
del core in `UPSTREAM.md`.

Chiaro è la *daylight edition* di tweather: stesse feature, stessi motori, UI Material 3
per un pubblico che non apre un terminale. Non è un rewrite e non è un re-skin: è la
stessa app sotto, con sopra un prodotto diverso.

---

## Fase 0 — Repo, build, e il core che arriva già verificato ✅

Obiettivo: uno scheletro che compili, e i motori di tweather dentro casa **con la loro
suite verde**. La tesi del progetto è "il rischio non è tecnico, è di presentazione":
questa fase è dove quella tesi si dimostra o cade.

- [x] Scheletro Gradle multi-modulo (`:app`, `:core:domain`, `:core:data`), wrapper 9.1,
      version catalog, `gradle.properties` con configuration cache
- [x] `:core:domain` come modulo **Kotlin/JVM puro** — nessun Android, e il modulo è il
      posto dove quel vincolo è verificabile invece che sperato
- [x] `:core:data` come Android library (Open-Meteo, mapper, Room, DataStore)
- [x] Seed del core da tweather via `tools/seed_core.py` + `tools/seed_edits.py`
      (78 file), ledger in `UPSTREAM.md`
- [x] Keystore di debug condiviso (`keystore/debug.keystore`, alias `chiaro-debug`),
      `applicationIdSuffix ".debug"` per l'installazione affiancata
- [x] `signingConfig` release dietro le quattro proprietà `CHIARO_KEYSTORE*`, con
      `-PsignReleaseWithDebugKey` come opt-in per gli smoke test
- [x] CI: test di tutti i moduli e lint **prima** degli APK
- [x] `:app` minimo che compila e produce un APK installabile
- [x] **248 test verdi**: 141 in `:core:domain` (16 classi), 107 in `:core:data`
      (15 classi), zero failure, zero skipped

### Le tre modifiche non meccaniche, e perché

Il resto del seed è rinomina di package. Queste tre no, quindi stanno in un file a
parte (`tools/seed_edits.py`) con la motivazione accanto:

1. **Le impostazioni che i motori leggono si spostano nel dominio.**
   `TemperatureUnit`, `WindSpeedUnit`, `UnitSettings` e `NotificationSettings` stavano
   in `SettingsStore` accanto alle chiavi DataStore, e `RuleVariables` le importava:
   il dominio dipendeva dal layer dati per valutare una regola. Ora vivono in
   `domain/settings/`, e sotto `:core:domain` non c'è più niente. Quello che riguarda
   solo la UI (tema, intervallo, opacità del widget) **non** si è spostato: non è
   input di nessun motore.
2. **`ServiceLocator` smette di importare l'app.** Prendeva lo User-Agent da
   `BuildConfig` e il callback "sono arrivati dati nuovi" da una classe del widget.
   Adesso li riceve da `ServiceLocator.install()`, chiamato da `ChiaroApplication`:
   una libreria non conosce la versione dell'app, ed è esattamente il motivo per cui
   è una libreria.
3. **`sampleWeatherReport` diventa pubblica.** Ha attraversato un confine di modulo,
   quindi `internal` non arriva più ai suoi lettori (i test di `:core:data`, e dalla
   Fase 2 le preview dell'app).

### Cosa la Fase 0 NON semina, e perché

- **`:core:sync`** (il job WorkManager, gli scheduler degli allarmi). Il worker chiama
  i notifier, e i notifier sono *testo*: titoli, corpi, canali. Spostarli adesso
  vorrebbe dire inventare il vocabolario delle notifiche di Chiaro dentro un refactor
  meccanico. Arriva in **Fase 6**, insieme alla schermata che lo rende visibile.
  Il modulo non esiste ancora nemmeno vuoto: un modulo vuoto è un TODO che sembra
  architettura.
- **Il layer UI di tweather** (~6.000 righe): editor kit, document builder, syntax
  highlighter, componenti terminale, layout RemoteViews, i tre profili di tema. Buttato
  per intero, che è il punto del progetto.

### Deviazioni registrate

- **`cron-utils` resta**, come dipendenza di soli test di `:core:domain`. L'avevo tolto
  dal catalogo ("Chiaro non disegna nessun crontab") e due test di `SkyJobCatalogTest`
  sono caduti. Rimetterlo è la scelta giusta: **togliere una guardia in Fase 0
  contraddice la premessa del fork**, che è "il motore arriva già verificato". Se la
  resa cron di `SkyJob` non sopravvive alla Fase 5, spariscono insieme test e
  dipendenza, in quella fase e con quella motivazione.
- **`EditorSettings` e `showDetails` sono ancora in `AppSettings`.** Sono concetti da
  editor (numeri di riga, a capo automatico) e in Chiaro non vogliono dire niente.
  Non li ho tolti qui perché la Fase 0 è meccanica per scelta e toccarli significa
  toccare i test del data layer: si rimuovono in **Fase 4**, con le impostazioni.
- **I commenti ereditati parlano ancora il vocabolario di tweather** (dieci righe:
  `$ tweather init`, `$ tweather run rules`, un hint su un file che qui non esiste).
  Lasciati apposta: ognuno nomina una *superficie* di tweather, e la sostituzione
  onesta è il nome della superficie di Chiaro che fa lo stesso lavoro, che per quasi
  tutte non è ancora stata disegnata. **Ogni fase riscrive i commenti del codice che
  tocca**, e il conteggio in `UPSTREAM.md` è il metro di "fatto".
- **Toolchain**: `:core:domain` non usa `jvmToolchain(17)` ma `sourceCompatibility`
  come i moduli Android. Un toolchain pretende un JDK 17 su ogni macchina che builda,
  e quello di Android Studio non lo è.

---

## Fase 1 — Il sistema di design in Compose

`DESIGN.md` è scritto; questa fase lo rende codice. Nessuna schermata.

- [ ] `ui/theme/`: `Color.kt`, `Scheme.kt` (dynamic color + schema Chiaro),
      `ChiaroColors.kt` (i token semantici), `SkyPalette.kt`, `Type.kt`, `Shape.kt`,
      `Motion.kt`, `ChiaroTheme.kt`
- [ ] Inter come font variabile, cifre tabulari dove il DESIGN le richiede
- [ ] Le tre guardie: `PaletteContrastTest` (i rapporti stampati nel DESIGN),
      `ScrimContractTest`, `NoRawColorTest`
- [ ] Il kit componenti dell'§8, con preview in chiaro e scuro
- [ ] Decisione sul set di icone (§13.1 del DESIGN): adottare, commissionare, disegnare

## Fase 2 — Oggi

- [ ] `SkyCanvas` (gradiente calcolato, ribbon, scrim), `heroTemperature`
- [ ] **La frase**: `AlertEngine` + `WeatherRecency` → una riga di prosa in cima
- [ ] Strip orario + sparkline pioggia, timeline "il resto della giornata"
- [ ] La settimana con le barre di range su scala condivisa
- [ ] Griglia dei dettagli, ogni numero con la sua riga di significato
- [ ] Chip di freschezza, stati vuoto/errore/stale

## Fase 3 — Luoghi e primo avvio

- [ ] Pager tra i luoghi, sheet di gestione, ricerca, GPS, stato "nessun luogo"
- [ ] Primo avvio: una schermata, due risposte

## Fase 4 — Impostazioni e guida

- [ ] Preferenze M3; rimozione di `EditorSettings`/`showDetails` (deviazione Fase 0)
- [ ] La guida: dove nascono i dati, cosa vogliono dire i verdetti, perché niente radar

## Fase 5 — Cielo

- [ ] Stasera, i momenti di oggi, il catalogo raggruppato, i prossimi eventi
- [ ] Promemoria (allarmi inesatti, soglia 15 minuti)
- [ ] Decisione sulla resa cron di `SkyJob` (deviazione Fase 0)

## Fase 6 — Avvisi, e `:core:sync`

- [ ] Il modulo `:core:sync` con il job periodico condiviso e gli scheduler
- [ ] I notifier in `:app` dietro un'interfaccia, con il testo di Chiaro
- [ ] Avvisi pronti + template + builder a chip + anteprima

## Fase 7 — Diario

- [ ] Voci per fetch (snapshot, previsioni, regole scattate, run del cielo)
- [ ] La striscia di deriva delle previsioni, con vista tabellare

## Fase 8 — Widget

- [ ] Glance: Ora, Oggi, Cielo; `ServiceLocator.install` riceve il repaint

## Fase 9 — Accessibilità e prestazioni, con i numeri

- [ ] Contrasti, scala testo 200%, TalkBack, motion ridotto
- [ ] Avvio a freddo sotto 400 ms, canvas sotto 2 ms/frame
- [ ] Passata IT/EN completa

## Fase 10 — Store e v1.0.0

- [ ] Icona definitiva, screenshot, scheda, `release.yml`, tag

---

## Note trasversali

- **Il fork non si dimentica**: quando un bug del core va corretto due volte, si estrae
  `weather-core` (VISION §7.3). `UPSTREAM.md` è quello che rende l'estrazione un
  pomeriggio invece che uno scavo.
- **Batteria**: un solo job periodico per tutto, allarmi inesatti, nessun servizio in
  foreground, nessuna posizione in background. Vale già da adesso, non da una fase di
  ottimizzazione.
- **Niente radar**: il provider non ha immagini. È una posizione dichiarata, non una
  mancanza da nascondere.
