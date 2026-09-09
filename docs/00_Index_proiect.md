# Indexul proiectului de disertație

Versiune: 1.0  
Data: 3 septembrie 2026
Autor: Silviu Vasilică Luța  
Stare generală: F1–F7 sunt incluse în versiunea curentă a repository-ului nou; experimentul plătit integral nu a fost executat

## Tema de lucru

Evaluarea accesibilității interfețelor web responsive folosind inteligența artificială

Analiza vizează site-uri și aplicații web responsive, redate controlat în desktop, mobil și, pentru S12, reflow la 320 px. Aplicațiile native Android/iOS, certificarea WCAG, crawlingul complet, autentificarea și repararea automată sunt în afara proiectului.

## Documente

| Nr. | Document | Stare | Rol |
|---:|---|---|---|
| 01 | [Planul cercetării](01_Planul_cercetarii.docx) | v1.0 | Problemă, scop, obiective, întrebări, ipoteze și contribuție |
| 02 | [Caiet de sarcini](02_Caiet_de_sarcini.docx) | v1.0 | Actori, cazuri de utilizare, cerințe, acceptare și arhitectura aprobată |
| 03 | [Protocol experimental](03_Protocol_experimental.docx) | v1.0 | Design, condiții, repetări, metrici, plan statistic, pilot, buget și evaluatori |
| 04 | [Catalog scenarii WCAG](04_Catalog_scenarii_WCAG.docx) | v1.0 | Cele 12 perechi BAD/GOOD, ground truth, viewporturi și etichetare |
| 05 | [Registru experiment](05_Registru_experiment.xlsx) | v1.0 | Rulări, constatări, evaluări, consistență, costuri și sumar |
| 06 | [Ghid implementare Codex](06_Ghid_implementare_Codex.docx) | v1.0 | Stack, contracte, F2 implementată și specificația completă F3–F7 cu prompt master |
| 07 | [Plan disertație și calendar](07_Plan_disertatie_si_calendar.docx) | v1.0 | Capitole, calendar de 16 săptămâni, variante și puncte de control |
| 08 | [Bibliografie și jurnal](08_Bibliografie_si_jurnal.docx) | v1.0 | Surse și verificările factuale F1–F7 |

## Starea implementării

Repository-ul activ este cel nou, pe branch-ul `main`, cu remote `https://github.com/LutaSilviu/dizertatie.git`. Istoricul Git actual conține inițial doar commiturile `193a12e` (`first commit`) și `a381ad4` (`changes`); întreaga funcționalitate F1–F7 este inclusă în această versiune curentă a repository-ului nou.

### Istoric al repository-ului anterior (nu există în istoricul Git actual)

Tabelul de mai jos consemnează checkpointurile din **repository-ul anterior** și este păstrat exclusiv ca istoric. Aceste commituri **nu** se regăsesc în istoricul Git al repository-ului nou și nu trebuie tratate ca referințe existente în `main`.

| Fază | Stare | Checkpoint istoric (repository anterior) |
|---|---|---|
| F1 — Schelet | Închisă | `e606527` — `feat: checkpoint approved F1 foundation` |
| F2 — Browser și snapshot | Închisă | `5b32b2e9d194a156701c0d968147eddcbdd14764` — `feat: checkpoint approved F2 browser snapshots` |
| F3 — axe-core | Închisă | `8bcdd30867c280b58fff74a884a82bc855ec1a78` — `feat: checkpoint approved F3 axe analysis` |
| F4 — AI | Închisă | `1f9f0482a22f57f59e70e25dd6f2f53be0e03c36` — `feat: checkpoint approved F4 AI analysis` |
| F5 — Normalizare și raport | Închisă | `a728c629e851091a1b52b487fdccfa80d700cedf` — `feat: checkpoint approved F5 normalized reporting` |
| F6 — Persistență și export | Închisă | `6498ffddf3cb91f3181a656085c321ec9d16d9f2` — `feat: checkpoint approved F6 persistence exports` |
| F7 — Experiment | Implementată | Secțiunea 22 din documentul 06; funcționalitatea F7 este inclusă în repository-ul nou |

În repository-ul anterior, suita implicită F1–F7 avea 82 de teste. Numărul de teste din versiunea curentă se reconfirmă exclusiv prin rularea Maven pe JDK 21 și se actualizează în documentul 08 conform rezultatului efectiv. Experimentul complet plătit (576 de apeluri AI) nu a fost executat.

## Decizii tehnice înghețate

- Java 21, Spring Boot, Maven Wrapper, Thymeleaf, HTML/CSS responsive și JavaScript simplu; fără React.
- PostgreSQL pentru aplicație, H2 pentru teste și Flyway pentru migrații.
- Playwright Java și Chromium pentru capturi izolate, sigure și deterministe.
- axe-core rulează în aceeași pagină Playwright ca snapshotul, cu profilul `WCAG_22_AA_V1`.
- Se păstrează separat `violations`, `incomplete`, `passes` și `inapplicable` și rezultatul original imuabil.
- OpenAI Responses API prin SDK-ul Java oficial; cheia există numai pe server.
- Catalogul inițial configurabil conține `gpt-5-nano` și `gpt-4.1-nano`; modelele și prețurile pot fi schimbate prin configurație.
- Condițiile principale sunt `AI_TEXT` și `AI_MULTIMODAL`; rezultatul axe-core nu este trimis AI-ului în experimentul principal.
- Promptul, schema, context builderul, normalizarea și configurația experimentului sunt versionate.
- Hibridul combină axe-core cu AI multimodal pentru același caz/model/repetare, fără apel suplimentar.
- Originalele, rezultatele normalizate, rezultatele hibride, ground truth-ul și evaluările rămân entități separate și trasabile.
- Politica implicită de păstrare pentru datele cercetării este `KEEP_ALL`; nu există ștergere automată.
- Benchmarkul conține 24 fixture-uri, 48 de cazuri, 48 de rulări axe, 576 de apeluri AI și 288 de rezultate hibride.
- Runnerul are dry-run, pilot, random seed, reluare idempotentă și limită financiară strictă.
- Experimentul plătit integral nu pornește fără confirmarea explicită a bugetului.

## Parametrii experimentului

- Desktop: `1366 × 768` pentru S01–S12 BAD/GOOD.
- Mobile: `390 × 844` pentru S01–S11 BAD/GOOD.
- Reflow: `320 × 800` pentru S12 BAD/GOOD, în locul viewportului mobile.
- Două condiții AI × două modele × trei repetări × 48 cazuri = 576 apeluri AI.
- Retry AI: maximum 1 în utilizarea normală, 0 în experimentul final.
- Plan statistic: intervale Wilson, bootstrap pereche, McNemar, Wilcoxon și corecție Holm.
- Al doilea evaluator este recomandat pe minimum 20% stratificat; absența sa este limitare, nu blocaj.

## Elemente care nu blochează implementarea

Următoarele valori se completează ca date administrative sau configurație efectivă și nu necesită redefinirea aplicației:

- titlul final și datele de copertă cerute de facultate;
- termenul instituțional exact;
- cheia API și limitele contului OpenAI;
- versiunile efective ale modelelor și tarifele valabile la pilot;
- disponibilitatea celui de-al doilea evaluator.

## Următorul pas

Cu F1–F7 incluse în repository-ul nou, urmează pilotul aprobat și înghețarea parametrilor efectivi ai rulării. Orice apel AI real necesită confirmare explicită a costului, iar experimentul complet de 576 de apeluri rămâne separat de verificarea tehnică.

Nu mai este necesară o etapă de redefinire înaintea fiecărei faze. Documentele se modifică ulterior numai pentru a consemna versiuni reale, rezultate, deviații tehnice justificate sau schimbări experimentale aprobate.

## Regula de versiune

Versiunea 1.0 reprezintă înghețarea specificației pentru implementare. O modificare ulterioară care schimbă designul experimental primește versiune nouă și decizie explicită; actualizările factuale ale jurnalului nu schimbă retroactiv configurațiile experimentale deja înghețate.
