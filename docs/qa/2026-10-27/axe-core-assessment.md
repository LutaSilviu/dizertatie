# Evaluarea axe-core din aplicație

**Data evaluării:** 27 octombrie 2026

**Scop:** evaluarea motorului axe-core integrat în aplicație și a modului în care aplicația colectează, calculează și afișează rezultatele.

## Concluzie

Motorul axe-core integrat pare funcțional: a raportat probleme concrete pentru mai multe site-uri și rezultate fără constatări pentru NASA și Microsoft. Comparația cu Sitefolio nu dovedește un defect al motorului; cele două produse au versiuni și reguli care nu sunt vizibile în mod egal, iar Sitefolio nu expune selectorii afectați. Din zece site-uri, șase au avut rezultate utilizabile în ambele produse.

Aplicația are însă probleme importante în jurul motorului: folosește axe-core 4.9.0, calculează un scor propriu care poate deveni înșelător, afișează greșit procentele WCAG și raportează erorile de încărcare ca erori generice. Recomand actualizarea motorului și corectarea acestor probleme înainte ca scorurile să fie folosite pentru comparații sau decizii de conformitate.

## Configurația curentă

- Codul include `axe.min.js` 4.9.0 și declară aceeași versiune în [AxeService.java](../../../backend/src/main/java/usv/dizertatie/axe/AxeService.java).
- Apelul `axe.run` analizează documentul curent și cere regulile etichetate `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa` și `wcag22aa`. Această listă cumulativă este potrivită pentru scanarea regulilor WCAG A/AA automatizate de axe-core; nu recomand schimbarea tagurilor pe baza acestor rezultate. [Explicația echipei axe-core](https://github.com/dequelabs/axe-core/issues/4579)
- Aplicația serializează numai `r.violations`; rezultatele `incomplete` de la axe-core nu sunt salvate sau afișate. Acestea pot necesita revizie manuală, deci utilizatorul nu primește în raport această listă de verificări nedeterminate. [README axe-core](https://github.com/dequelabs/axe-core)
- Scorarea și interfața sunt logica aplicației, nu scoruri furnizate de axe-core.
- [axe-core 4.14.0](https://github.com/dequelabs/axe-core/releases) este versiunea oficială curentă la data evaluării. Notele versiunii menționează o regulă nouă, modificări de rezultate în reguli existente și îmbunătățiri de performanță. După actualizare, numărul constatărilor se poate schimba.

## Rezultatele celor zece site-uri

| Site | AXE local — axe-core 4.9.0 | Sitefolio | Rezumat |
|---|---|---|---|
| [NASA](https://www.nasa.gov/) | 100/100; 0 constatări | 100/100; 0 categorii | Acord: zero constatări automate. |
| [Apple](https://www.apple.com/) | 87,36/100; 14 constatări: 2 critice și 12 serioase — 11 ținte tactile prea mici, 2 roluri ARIA fără copii ceruți și 1 contrast insuficient. | 82/100; 3 constatări în 2 categorii — 2 roluri ARIA fără copii ceruți și 1 contrast insuficient; raportate ca 1 critică și 1 serioasă. | Categoriile ARIA și contrast coincid; aplicația a raportat în plus 11 ținte tactile mici. Diferență de scor: 5,36 puncte. |
| [Microsoft](https://www.microsoft.com/) | 100/100; 0 constatări | 100/100; 0 categorii | Acord: zero constatări automate. |
| [BBC](https://www.bbc.com/) | 99,14/100; 1 constatare serioasă — link diferențiat de text doar prin culoare. | 100/100; 0 categorii | Doar scanarea locală a raportat constatarea. |
| [Cloudflare](https://www.cloudflare.com/) | 96,55/100; 4 constatări serioase de contrast insuficient. | 94/100; 2 constatări serioase de contrast insuficient. | Aceeași categorie, dar număr de elemente și scor diferite. |
| [Python.org](https://www.python.org/) | 0/100; 109 constatări: 61 critice și 48 serioase. Categorii: 57 roluri ARIA fără rolurile părinte cerute, 25 elemente `<li>` folosite semantic greșit, 15 contraste insuficiente, 8 liste structurate greșit și 4 roluri ARIA fără copii ceruți. | `Check failed` | Comparație incompletă; eroarea Sitefolio nu înseamnă zero constatări. |
| [MDN](https://developer.mozilla.org/en-US/) | 89,66/100; 12 constatări serioase — ținte tactile insuficient de mari. | `Check failed` | Comparație incompletă. |
| [Khan Academy](https://www.khanacademy.org/) | `Internal Server Error`; Playwright a expirat după 30 de secunde așteptând `networkidle`. | 100/100; 0 categorii | Fără rezultat local utilizabil. |
| [Shopify](https://www.shopify.com/) | 98,85/100; 1 constatare critică — buton fără text discernibil. | 100/100; 0 categorii | Doar scanarea locală a raportat o constatare. |
| [GNU](https://www.gnu.org/) | Scanare eșuată; site-ul țintă a răspuns cu HTTP 429. | 100/100; 0 categorii | Fără rezultat local utilizabil; limita serverului nu este un rezultat de accesibilitate. |

Rezultatele complete și testarea anterioară a aplicației sunt în [raportul de testare în browser](browser-testing.md).

## Probleme ale aplicației observate

### Scorurile nu trebuie interpretate ca scor axe-core sau certificare WCAG

Scorul aplicației este calculat în [SeverityScoringService.java](../../../backend/src/main/java/usv/dizertatie/scoring/SeverityScoringService.java), folosind numărul de constatări și severitatea lor raportat la numărul de criterii WCAG din registrul aplicației. Nu este un scor standard axe-core și nici o măsură de conformitate validată. Pentru Python.org, 109 constatări duc formula la 0/100, fără să diferențieze severitatea după ce scorul a atins limita minimă.

Recomand să afișăm direct numărul de constatări pe severitate și numărul de reguli distincte. Dacă păstrăm scorul, trebuie să-i definim clar semnificația, să-l numim scor intern al aplicației și să evităm comparația numerică directă cu Sitefolio.

### Interfața inversează eticheta scorului și multiplică procentele

În [ReportPage.tsx](../../../frontend/src/report/ReportPage.tsx), un scor mai mare este etichetat `High` și colorat ca severitate ridicată. În test, un raport cu 100/100 și zero constatări a apărut ca `High`. Aceeași pagină înmulțește valorile de conformitate deja exprimate în procente încă o dată cu 100, afișând `10000%` în loc de `100%`.

Acestea sunt defecte de prezentare care pot face un rezultat bun să pară rău și pot induce în eroare utilizatorul. Trebuie corectate înainte ca scorurile să fie folosite operațional.

În plus, scannerul rulează numai tagurile WCAG A și AA, dar interfața afișează și un rezultat pentru Level AAA. Constatarea lipsei tagurilor AAA este în [AxeService.java](../../../backend/src/main/java/usv/dizertatie/axe/AxeService.java), iar calculul AAA tratează criteriile fără findings drept trecute în [ConformanceScoringService.java](../../../backend/src/main/java/usv/dizertatie/scoring/ConformanceScoringService.java). Valoarea AAA afișată nu reprezintă o scanare AAA; trebuie eliminată sau trebuie adăugată o scanare AAA separată și explicită.

### Scanarea paginii și tratarea erorilor

[PageNavigator.java](../../../backend/src/main/java/usv/dizertatie/playwright/PageNavigator.java) așteaptă `networkidle`. Khan Academy nu a ajuns în acea stare în limita Playwright de 30 de secunde. GNU a răspuns cu HTTP 429, iar scanarea s-a oprit înainte de rularea axe-core. În UI, aceste situații au fost afișate ca `Internal Server Error` sau nu au produs raport utilizabil.

Recomandăm:

1. Tratarea timeoutului, a răspunsurilor HTTP 4xx/5xx și a paginilor cu protecție anti-bot prin stări clare precum `Timed out`, `Blocked` și `Rate limited`.
2. Evaluarea unei strategii de încărcare care nu depinde exclusiv de `networkidle`. Schimbarea trebuie însoțită de o așteptare explicită/stabilizare a DOM-ului, astfel încât scanarea să nu ruleze înainte ca pagina să fie pregătită.
3. Înregistrarea versiunii axe-core, browserului, stării scanării și a tagurilor folosite în fiecare raport.

## Pași recomandați

1. **Prioritate mare:** corectați eticheta și culoarea scorului, apoi eliminarea multiplicării suplimentare a procentelor WCAG.
2. **Prioritate mare:** transformați erorile de încărcare în rezultate explicite, fără să le confundați cu rapoarte fără constatări.
3. **Prioritate medie:** actualizați axe-core de la 4.9.0 la o versiune curentă, inclusiv valoarea versiunii salvate, apoi reluați un set fix de pagini și fixture-uri. Comparați rezultatele înainte și după, deoarece regulile și numărul constatărilor se pot schimba.
4. **Prioritate medie:** salvați și afișați separat rezultatele `incomplete`, marcate clar ca necesitând verificare manuală.
5. **Prioritate medie:** eliminați scorul AAA dacă aplicația rulează numai reguli A/AA; redenumiți sau eliminați și scorul compozit dacă nu există o definiție stabilă și ușor de explicat.
6. **Pentru concluzii de conformitate:** combinați scanarea automată cu verificare manuală. axe-core precizează că automatizarea nu găsește toate problemele, iar unele rezultate necesită revizuire umană. [Documentația axe-core](https://github.com/dequelabs/axe-core)

## Limitări

- Sitefolio nu a expus versiunea axe-core, selectorii afectați sau toate regulile active; diferențele nu pot fi atribuite sigur unei singure cauze.
- Conținutul dinamic, banner-ele de consimțământ și starea paginii pot varia între rulări și produse.
- Un rezultat cu zero constatări indică doar că scanerul nu a raportat încălcări detectabile în starea analizată; nu dovedește că pagina este complet accesibilă.
