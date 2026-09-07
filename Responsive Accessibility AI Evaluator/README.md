# Responsive Accessibility AI Evaluator

Monolit modular Java 21 / Spring Boot pentru capturarea și evaluarea controlată a paginilor web responsive.

## Starea implementării

F1–F7 sunt implementate. F3 rulează axe-core în aceeași pagină Playwright stabilizată folosită pentru snapshot. F4 adaugă analiza AI structurată prin OpenAI Responses API. F5 normalizează determinist sursele, deduplică fiecare sursă, combină axe cu AI multimodal fără apel suplimentar și afișează raportul agregat. F6 persistă metadatele în PostgreSQL/H2, păstrează artefactele mari în filesystem și oferă istoric, repetare și exporturi versionate. F7 adaugă benchmarkul controlat, runnerul reproductibil, evaluarea umană, metricile și exportul pachetului experimental.

## Cerințe locale

- Java 21;
- Docker și Docker Compose pentru PostgreSQL local;
- Chromium instalat pentru versiunea Playwright fixată în `pom.xml`.

Instalarea reproductibilă a browserului:

```powershell
.\mvnw.cmd -q exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

Rularea testelor:

```powershell
.\mvnw.cmd test
```

Testele Playwright pornesc exclusiv un server HTTP local controlat. Excepția loopback este activată prin profilul Spring `test`; profilurile `local` și implicite blochează loopback și rețelele private.

## Profilul axe-core F3

- adaptor Maven: `com.deque.html.axe-core:playwright:4.13.0`;
- profil: `WCAG_22_AA_V1`;
- tags: `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa`;
- timeout implicit: 30 s;
- rezultate păstrate separat: `violations`, `incomplete`, `passes`, `inapplicable`;
- artefacte imuabile: `storage/runs/{runId}/axe/axe-results.json` și `axe-metadata.json`.

Analiza poate fi pornită din interfața Thymeleaf cu „Capturează și rulează axe-core”. Rezultatul automat este informativ și nu reprezintă certificare oficială WCAG.

## Analiza AI F4

- SDK oficial `com.openai:openai-java:4.55.0`, Responses API și retry controlat exclusiv de aplicație;
- modele active configurabile prin `app.ai.models`; endpoint: `GET /api/v1/models`;
- estimare cu marjă de 20% înainte de apel: `GET /api/v1/analyses/estimate`;
- condiții principale: `AI_TEXT` și `AI_MULTIMODAL`; rezultatele axe-core nu sunt trimise modelului;
- contracte înghețate: `PROMPT_ACCESSIBILITY_V1`, `AI_FINDINGS_SCHEMA_V1`, `PAGE_CONTEXT_V1`;
- artefacte: `storage/runs/{runId}/ai/{request,response,parsed,metadata}.json`;
- cheia se citește numai din `OPENAI_API_KEY`; formularul cere confirmare explicită și plafon de cost;
- profilul normal permite cel mult un retry, iar `EXPERIMENT_V1` nu permite retry.

Testele F4 folosesc un adaptor fals și un server HTTP local. Nu efectuează apeluri OpenAI și nu generează cost.

## Normalizare și raport F5

- `NORMALIZER_V1`: un `PredictedFinding` pentru fiecare nod axe sau obiect AI, cu referință la artefactul brut;
- chei și UUID-uri deterministe din viewport, snapshot, criterii WCAG, fingerprint și categorie;
- duplicatele intra-source păstrează toate `sourceRefs` și numărul de apariții;
- `HYBRID_MATCHER_V1`: intersecție WCAG și scor de localizare minimum `0.75`; ambiguitățile nu sunt forțate;
- hibridul acceptă numai axe + `AI_MULTIMODAL` pentru același caz/model/repetare și raportează zero apeluri AI suplimentare;
- raportul separă `AXE_ONLY`, `AI_ONLY` și `BOTH`, componentele eșuate, limitările, costurile și viewporturile;
- filtrele client-side acoperă viewport, sursă, criteriu WCAG și severitate; metoda, modelul și starea sunt parte din configurația raportului;
- smoke test axe-core propriu, reflow la 320 px, focus vizibil și target size.

## Persistență și export F6

- Flyway `V1__create_research_schema.sql`; `ddl-auto=validate`, fără creare automată Hibernate;
- PostgreSQL 16 pentru profilul local și H2 în mod PostgreSQL pentru testele rapide;
- agregat trasabil pentru analize, rulări, snapshoturi, artefacte, rezultate brute, normalizări, findings, configurații, ground truth și evaluări;
- fișierele mari rămân sub `app.storage.directory`; baza reține numai calea relativă, tipul, dimensiunea, SHA-256 și versiunea producătorului;
- verificare de integritate și blocare path traversal înaintea persistării unui artefact;
- `GET /api/v1/analyses/history`, `POST /api/v1/analyses/{id}/repeat` și exporturi `json`, `findings.csv`, `evaluations.csv`, `runs.csv`;
- export JSON `RESEARCH_EXPORT_SCHEMA_V1` / `EXPORT_V1`, CSV UTF-8 și RFC 4180, cu secrete eliminate;
- rulările rămase `RUNNING` la restart sunt marcate explicit `ABANDONED`; nu se inventează succes;
- politica implicită este `KEEP_ALL`: nu există ștergere automată a datelor sau artefactelor.

Testul PostgreSQL explicit necesită Docker și negocierea API compatibilă cu daemonul curent:

```powershell
.\mvnw.cmd -Dapi.version=1.41 -Dtest=PostgresqlFlywayContainerIT test
```

Rularea locală cu PostgreSQL:

```powershell
docker compose up -d
$env:SPRING_PROFILES_ACTIVE='local'
.\mvnw.cmd spring-boot:run
```

## Limite de securitate F2

Aplicația validează URL-ul, rezolvarea DNS, redirecționările și cererile browserului, însă această protecție nu elimină singură riscul DNS rebinding. Într-un mediu real browserul trebuie rulat într-un container/rețea izolată, cu reguli firewall care blochează rețelele interne și endpointurile de metadata cloud.

## Benchmark și experiment F7

- catalog versionat în `benchmark/ground-truth-v1.json`, validat la pornire;
- 24 de fixture-uri locale stabile la `/benchmark/S01/BAD` … `/benchmark/S12/GOOD`, fără resurse externe;
- 48 de cazuri: 24 desktop, 22 mobile și 2 reflow la 320 px;
- ground truth sincronizat idempotent în baza de date la crearea unui plan;
- dry-run determinist: 48 axe, 288 AI_TEXT, 288 AI_MULTIMODAL și 288 rezultate hibride;
- pilot implicit separat pentru S01 și S04; planul complet conține 576 apeluri AI și nu pornește fără confirmare plătită explicită;
- seed, concurență AI 1, versiuni, hashuri, limite per apel și hard budget înghețate în configurație;
- rezervare, finalizare și reluare persistentă prin API; intrările finalizate nu sunt duplicate, iar intrările `RUNNING` trebuie reconciliate explicit;
- evaluări individuale și adjudecate, inclusiv FN/TN fără `findingId`, cu mod blind;
- metrici regenerabile și statistici: precision/recall/F1/specificity, Wilson 95%, bootstrap pereche, McNemar, Wilcoxon, Holm și kappa;
- export experimental JSON versionat cu configurația înghețată, planul, evaluările și metricile.

Interfața este disponibilă la `/experiment`, iar manifestul controlat la `GET /api/v1/benchmark/manifest`. Dry-run-ul poate fi verificat fără browser și fără API:

```powershell
$body = '{"randomSeed":20260901,"aiConcurrency":1,"hardBudgetUsd":10.00,"maxCostPerAiCallUsd":0.01,"pilotScenarioIds":["S01","S04"]}'
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $body http://localhost:8080/api/v1/experiments/dry-run
```

Pentru pilot sau experiment final, un worker rezervă următorul pas prin `POST /api/v1/experiments/plans/{planId}/reserve-next`, persistă rezultatele prin serviciile F2–F6 și închide pasul prin `POST /api/v1/experiments/executions/{executionId}/complete`. `POST /api/v1/experiments/plans/{planId}/resume` reîncarcă manifestul și stările persistate. Orice pas AI cere `paidActionConfirmed=true`; suita completă de 576 apeluri nu a fost executată în verificarea implementării.
