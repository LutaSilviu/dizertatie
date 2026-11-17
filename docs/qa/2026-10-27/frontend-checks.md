# Verificări frontend — 27 octombrie 2026

## Domeniu și limitări

Testat checkout-ul curent, inclusiv modificările locale preexistente. Niciun fișier de produs nu a fost modificat. Acest document acoperă compilarea, verificarea TypeScript, redarea componentelor reale React în Node și inspecția fluxurilor frontend. **Nu reprezintă o sesiune de testare în browser.** Accesul browserului la originile necesare a fost blocat de mediul de control, conform coordonatorului. Nu s-a încercat ocolirea acestei restricții.

Nu există script `test` în `frontend/package.json`; căutarea fișierelor de test în frontend, excluzând node_modules/dist, nu a găsit o suită existentă. Pentru verificările de mai jos s-a adăugat doar un harness de diagnostic în acest director de documentație, fără dependențe noi.

## Comenzi executate

Din `frontend`:

```powershell
npm run typecheck
npm run build
```

Ambele au ieșit cu cod **0**. Build: Vite 6.4.4, 55 module transformate, bundle JS 294,54 kB (gzip 91,78 kB), CSS 9,02 kB (gzip 2,37 kB).

Din rădăcina repository-ului:

```powershell
node docs/qa/2026-10-27/frontend-contract-check.cjs
```

Cod de ieșire **1**, așteptat pentru harness: patru verificări ale comportamentului dorit au eșuat. Harness-ul citește sursa TSX curentă, o transpilează în memorie, expune temporar componentele private și le redă folosind `react-dom/server`; importurile CSS și importurile locale nefolosite sunt izolate. Nu execută browser, rețea sau efectele React.

```text
FAIL API percentages stay in the range 0..100
  10000.0%, 10000%, 8000.0%, 8000%, 0.0%, 0%
FAIL Clean score 100 is shown as low severity
  Severity score: 100 out of 100 (High)
FAIL Lowest latency receives the best-value highlight
  COMBINED latency 2000 receives green best-value styling: true
FAIL Accordion trigger is natively keyboard accessible
  <div ... role="button" aria-expanded="false">...
4 failed desired-behavior checks (diagnostic evidence, not a browser test).
```

## Defecte reproduse prin redarea componentelor

| ID | Prioritate | Scenariu / rezultat real | Rezultat așteptat | Dovezi |
|---|---|---|---|---|
| FE-01 | P1 | Raport cu scoruri API A=100, AA=80, AAA=0 afișează 10000,0%, 8000,0%, 0,0%; lățimile barelor sunt 10000%, 8000%, 0%. | 100%, 80%, 0%, cu bare între 0 și 100%. | `frontend/src/report/ReportPage.tsx:70,74` multiplică cu 100. `backend/src/main/java/usv/dizertatie/scoring/ConformanceScoringService.java:23,51` produce deja procente 0–100. `controller/ScanController.java:154,162` le transmite direct în răspuns. |
| FE-02 | P1 | Scorul 100 este redat cu clasa `severityRed`, eticheta `High` și aria-label `(High)`. | Scorul de calitate maximă trebuie prezentat favorabil; dacă eticheta descrie severitatea problemelor, aceasta este scăzută pentru 100. | `frontend/src/report/ReportPage.tsx:35` tratează >=70 drept severitate mare. `backend/src/main/java/usv/dizertatie/scoring/SeverityScoringService.java:20,30` întoarce 100 pentru zero constatări și scade pentru probleme; `controller/ScanResponse.java` documentează explicit „higher is better”. |
| FE-03 | P2 | Date sintetice: AXE 100 ms, AI 1000 ms, COMBINED 2000 ms. Celula 2000 primește culoarea verde și bold pentru cea mai bună valoare. | Latența minimă ar trebui evidențiată favorabil. | `frontend/src/benchmark/BenchmarkResultPage.tsx:100–105` aplică `Math.max` tuturor coloanelor, inclusiv latenței. Reprodus prin redarea `SummaryTable`. |

Aceste rezultate sunt probe deterministe ale prezentării pentru intrări controlate, compatibile cu contractul backend; nu sunt capturi ale unei scanări W3C efectuate în UI.

## Accesibilitate confirmată în markup și sursă

**FE-04, P1/P2 — Exemplele FP/FN nu au un declanșator operabil prin tastatură.** `MethodAccordion` în `frontend/src/benchmark/BenchmarkResultPage.tsx` utilizează un `div` cu `role="button"`, `onClick` și `aria-expanded`. Markup-ul redat nu are `tabindex`; inspecția sursei confirmă lipsa `tabIndex` și `onKeyDown`. Un asemenea `div` nu intră implicit în ordinea Tab și Enter/Space nu activează `onClick`. Impact: utilizatorul exclusiv cu tastatura nu poate deschide exemplele dintr-un benchmark cu FP/FN. Proba este structură DOM redată și inspecție de cod, fără parcurgere reală cu tastatura.

## Riscuri statice — nereproduse interactiv

- **FE-R01 — API indisponibil în modul Vite de dezvoltare.** `frontend/vite.config.ts` transmite `/api` fără `rewrite`; `ScanController` și `LabelController` sunt mapate la `/scans`, respectiv `/labels`, iar configurația backend nu definește context `/api`. Astfel `/api/labels` pare să ajungă neschimbat la un backend care așteaptă `/labels`. Nginx de producție folosește `proxy_pass http://backend:8080/` și elimină prefixul corect. De verificat într-o sesiune Vite + backend: `/api/labels` prin Vite față de `/labels` direct la backend. Fișierul Vite avea modificări locale înaintea testării; nu s-a atribuit acest risc exclusiv versiunii committed.
- **FE-R02 — Date de raport vechi după navigare rapidă.** Efectul `ReportPage` nu anulează cererea anterioară și nu verifică identitatea scanării înainte de `setReport`. O cerere lentă pentru A poate suprascrie B dacă se schimbă parametrul `/report/:scanId` fără demontarea componentei. Necesită probă cu răspunsuri întârziate controlat. `HistoryPage` are deja protecție `cancelled`.
- **FE-R03 — Stare de benchmark veche la schimbarea runId.** `BenchmarkResultPage` nu resetează `status`, `metrics` sau `error` în efectul dependent de `runId`, nici nu exclude răspunsurile vechi. Se pot afișa metrici din runda precedentă sau o eroare persistentă. Necesită navigare controlată între două runde și întârzierea răspunsurilor.
- **FE-R04 — Focus vizual URL.** Inputul URL din `AnalyzerForm.tsx:214` setează `outline: 'none'` și nu definește un stil de focus alternativ. Riscul este pierderea indicatorului vizual; trebuie confirmat vizual într-un browser.
- **FE-R05 — Benchmark custom fără validare locală.** `BenchmarkForm` permite lista custom goală sau URL-uri cu scheme incorecte și face imediat POST. Validarea serverului poate respinge cererea, dar UI nu oferă verificare locală pe câmp și afișează `response.text()` brut pentru erori. Necesită verificarea răspunsului real API înainte de clasificarea drept defect funcțional.

## Scenarii acoperite prin inspecție, fără verdict de execuție UI

Formularul Analyzer validează schema HTTP(S), dezactivează câmpurile în timpul cererii și afișează erori `role=alert`; raportul afișează stări loading/error/empty, iar exportul CSV verifică statusul HTTP. History gestionează încărcare/eroare/listă goală și paginare. Aceste observații descriu codul, nu certifică comportamentul în browser.

Pentru închiderea testării UI rămân necesare: trimitere URL valid/invalid, scanare W3C before/after, raport și CSV, navigare History → Report, paginare, eșec API, benchmark complet/eșuat, navigare între runde, operare cu tastatura și verificare responsive. Nicio interacțiune Sitefolio și nicio comparație de rezultate Sitefolio nu este inclusă în acest subraport.
