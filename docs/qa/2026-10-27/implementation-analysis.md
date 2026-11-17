# Analiza implementarii AXE si a interpretarii rezultatelor

Data: 2026-10-27. Analiza pe arborele de lucru existent, care continea deja modificari ale utilizatorului. Nu am modificat codul aplicatiei. Acest document separa constatarile din cod de testele efectuate in browser, documentate de fluxul principal QA.

## Configuratia efectiva pentru comparatie

| Aspect | Implementare verificata | Efect asupra comparatiei |
| --- | --- | --- |
| Motor | axe-core **4.9.0**; antetul fisierului inclus si `require(...).version` coincid cu `AxeService.AXE_VERSION` | Versiunea unui serviciu extern trebuie consemnata separat; nu presupunem aceeasi versiune. |
| Reguli | `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa` | Regulile best-practice si AAA nu sunt incluse. Diferente precum `region` nu sunt automat buguri. |
| Executie | Chromium headless, context nou, `bypassCSP=true`, navigare cu `NETWORKIDLE` | Fara sesiune autentificata/cookie-uri preexistente sau configuratie explicita de viewport/locale in `runAxeScan`. Continutul poate diferi intre servicii. |
| Pagina verificata | `axe.run(document, options)` pe o singura pagina | Nu este crawler; nu urmareste toate paginile site-ului sau starile interactive. |
| Rezultate pastrate | Numai `r.violations`, transformate in cate un `Finding` pentru fiecare nod afectat | Comparati numarul de elemente afectate cu elemente afectate; nu cu numarul regulilor incalcate. |
| Informatii omise | `passes`, `incomplete`, `inapplicable`, rule ID, help URL, HTML si `failureSummary` nu ajung in raport | Zero findings nu inseamna zero verificari manuale necesare. Raportul nu permite o comparatie exacta pe rule ID fara colectare separata. |
| Scor severitate | `max(0, 100 - sum(weights)/348*100)`, weights critical=4, serious=3, moderate=2, minor=1 | Scor propriu aplicatiei; nu este scor standard axe si nu poate fi comparat numeric direct cu scor Sitefolio. |
| Scoruri nivel | Procent de criterii fara finding, separat pentru A/AA/AAA; registru cu A=32, AA=24, AAA=31, total=87 | Nu sunt niveluri cumulative si nici dovada conformitatii WCAG. Criteriile neverificate sunt implicit socotite fara incalcare. |

Surse locale: `backend/src/main/java/usv/dizertatie/axe/AxeService.java:17-46`, `backend/src/main/java/usv/dizertatie/service/ScanService.java:104-140`, `backend/src/main/java/usv/dizertatie/playwright/PageNavigator.java:24-36`, `backend/src/main/java/usv/dizertatie/axe/AxeResultParser.java:47-130`, `backend/src/main/java/usv/dizertatie/scoring/SeverityScoringService.java:18-45`, `backend/src/main/java/usv/dizertatie/scoring/ConformanceScoringService.java:21-51`, `backend/src/main/resources/wcag/wcag22-criteria.json`.

## Probleme certe in cod, de confruntat cu executia browser/API

1. **P1: procentele de conformitate sunt multiplicate de doua ori cu 100.** Backend-ul produce deja 0-100 (`ConformanceScoringService.java:51`), iar UI foloseste `value * 100` atat pentru text cat si pentru latimea barei (`frontend/src/report/ReportPage.tsx:70,74`). Un raspuns 100 produce `10000.0%`; 95.8333 produce `9583.3%`. Este incompatibilitate directa intre contract si prezentare.
2. **P1: interpretarea vizuala a severitatii inverseaza sensul scorului.** Backend-ul si contractul API definesc 100 drept cel mai bun rezultat/no findings (`SeverityScoringService.java:19-20`, `controller/ScanResponse.java:19`); UI afiseaza scorurile >=70 rosu/High si <30 verde/Low (`ReportPage.tsx:32-41`). O pagina fara probleme detectate apare ca severitate High.
3. **P2: nivelurile WCAG 2.1/2.2 AA sunt etichetate A.** `AxeResultParser.extractWcagLevel` recunoaste numai `wcag2a/aa/aaa`, apoi revine la A (`AxeResultParser.java:100-110`). Metadatele motorului inclus confirma reguli `autocomplete-valid` cu `wcag21aa/wcag135`, `avoid-inline-spacing` cu `wcag21aa/wcag1412`, `target-size` cu `wcag22aa/wcag258`. Prin urmare, un finding al acestor reguli va avea nivel A, desi este AA. Scorul de conformitate foloseste registrul dupa ID, deci etichetarea gresita nu muta singura criteriul in alt scor.
4. **P2: criteriile cu ultima componenta din doua cifre nu sunt normalizate.** `formatWcagCriterion` formateaza numai siruri de 2 sau 3 cifre; `wcag1412` devine `1412`, nu `1.4.12` (`AxeResultParser.java:91-97`). `avoid-inline-spacing` din motorul inclus are exact aceasta eticheta. Astfel finding-ul nu se potriveste cu registrul si nu reduce scorul de conformitate. Impactul in browser depinde de declansarea acestei reguli.
5. **P2: se pastreaza doar primul criteriu si primul segment al selectorului.** Pentru reguli cu mai multe criterii se pierde asocierea suplimentara (`AxeResultParser.java:74-82`); pentru iframe-uri, un target cu mai multe segmente este redus la `target[0]`, iar array-urile de shadow DOM sunt convertite cu `asText("")` (`:112-118`). Raportul poate indica doar iframe-ul sau un selector gol. Constatare structurala; nu a fost reprodusa prin browser in aceasta subanaliza.
6. **P2: scorurile pot sugera conformitate pe criterii pe care motorul nu le-a verificat.** Scorul AAA va ramane 100 pentru scanarea AXE curenta care selecteaza doar A/AA, inclusiv atunci cand pagina are numeroase incalcari A/AA. Acest lucru provine din formula actuala si trebuie explicat utilizatorului; nu este echivalent cu conformitate AAA.

## Verificari executate si limite

- Executat Node asupra fisierului axe inclus: `version = 4.9.0`; `getRules(['wcag21a','wcag21aa','wcag22aa'])` a confirmat regulile si etichetele mentionate mai sus. Interogarea tuturor etichetelor configurate a identificat `avoid-inline-spacing` ca regula cu criteriu din patru cifre.
- Registrul WCAG a fost incarcat din JSON si grupat dupa nivel: 32 A + 24 AA + 31 AAA = 87.
- Am incercat testele existente prin `mvn -o -DskipPlaywrightInstall=true "-Dtest=AxeResultParserTest,SeverityScoringServiceTest,ConformanceScoringServiceTest" test`. Procesul s-a oprit **inainte de executarea testelor**: Maven nu a putut crea repository-ul local `C:\.m2\repository`. Nu raportam aceste teste drept trecute. Rapoartele deja prezente in `backend/target/surefire-reports` sunt mai vechi si nu constituie dovada pentru sesiunea curenta.
- Testele existente din sursa acopera fixture-urile WCAG 2.0/image-alt/color-contrast, scorurile si excluderea best-practice `region`. `AxeResultParserTest` nu contine cazuri pentru `wcag21aa`, `wcag22aa`, criterii cu patru cifre, iframe sau shadow DOM. Frontend-ul nu declara script de teste, doar build/typecheck.
- `gh` nu a fost disponibil pe PATH in shell-ul acestei subanalize; nu au fost create/modificate issue-uri. Nu am efectuat navigare browser in acest subtask pentru a evita interferenta cu fluxul principal de testare.

## Interpretarea unei diferente fata de Sitefolio

Pentru fiecare URL trebuie retinute URL-ul final dupa redirect, ora, versiunea motorului daca este vizibila, regulile/tag-urile, numarul regulilor incalcate, suma nodurilor afectate si severitatile. Separa diferentele de acoperire/configuratie (best-practice, AAA, versiuni, pagina partial incarcata, protectie anti-bot) de erorile de transformare/prezentare documentate mai sus. Daca Sitefolio nu publica versiunea sau configuratia, aceasta ramane o necunoscuta, nu o explicatie demonstrata.
