# Testare exploratorie în browser — 27 octombrie 2026

## Domeniu și configurare

Am pornit checkout-ul curent cu `docker compose up -d --build` și am testat aplicația în browser la `http://localhost` (frontend Nginx + backend Spring Boot + PostgreSQL). Am folosit și serverul de dezvoltare Vite la `http://localhost:5173`. Am verificat separat checker-ul [Sitefolio Accessibility Checker](https://sitefolio.io/tools/accessibility-checker), folosind pagina W3C WAI BAD Before și o pagină de control.

Aplicația locală folosește axe-core 4.9.0, conform versiunii din codul proiectului. Sitefolio nu afișează versiunea motorului sau regulile complete în interfață. Rezultatele celor două produse nu sunt direct echivalente numeric: scorul local este calculat de proiect, iar Sitefolio publică propriul scor.

Nu am modificat codul aplicației. Am creat un benchmark custom gol în baza de date locală; acesta este run-ul ID 3 și apare cu status Failed. Nu s-a trimis cheie API sau date personale.

## Cazuri rulate

| ID | Acțiune | Rezultat observat |
|---|---|---|
| BR-01 | Deschidere aplicație full-stack | Frontend-ul s-a încărcat la `/analyzer`; serviciile backend și DB au devenit healthy. |
| BR-02 | Analyzer, URL `javascript:alert(1)` | Formularul a refuzat URL-ul și a afișat `Please enter a valid http:// or https:// URL.` fără să trimită scanarea. **Trecut.** |
| BR-03 | Scanare AXE `https://example.com` din aplicația full-stack | Scanarea s-a încheiat; raport ID 72, 0 findings și scor 100/100. Istoricul a afișat raportul. **Flux funcțional trecut; prezentarea scorului a eșuat (FE-01/02).** |
| BR-04 | Scanare [W3C WAI BAD Before](https://www.w3.org/WAI/demos/bad/before/home.html) în aplicația locală | W3C a răspuns browserului headless cu protecție anti-bot (`cf-mitigated`). Aplicația a afișat `Internal Server Error`; backend-ul a aruncat `PageBlockedException`. Nu există rezultat axe local pentru această pagină. **Blocat de pagina țintă; gestionarea erorii este neclară.** |
| BR-05 | Sitefolio pe W3C WAI BAD Before | Scanare finalizată: **58/100**, 5 tipuri de probleme — 2 critice și 3 serioase. Rezultatele nu expun elementele afectate sau selectorii. |
| BR-06 | Sitefolio pe W3C WAI BAD After | **100/100**, 0 tipuri de probleme. Schimbarea față de Before este clar detectată de checker-ul online. |
| BR-07 | Sitefolio pe `example.com` | **100/100**, 0 tipuri de probleme; corespunde scanării locale ca număr de findings (0), dar nu ca prezentare: UI local afișează severitate `High` și procente WCAG `10000%`. |
| BR-08 | Istoric după scanarea reușită | Raportul 72 a apărut primul în tabel; linkul către raport funcționează. Lista are paginare, în acest set o singură pagină. **Trecut.** |
| BR-09 | Lista Benchmark | Run-urile existente s-au încărcat cu status `COMPLETE`, 5/5 pagini. **Trecut.** |
| BR-10 | Formular benchmark custom fără URL-uri | Formularul a permis submit-ul; aplicația a creat run-ul 3 și a navigat la raportul `The benchmark run failed.` fără validare sau explicație vizibilă. **Eșuat.** |
| BR-11 | Scanare AXE validă în Vite (`http://localhost:5173`) | API-ul a răspuns `Not Found`; în modul Vite, `/api/scans` este transmis backend-ului fără rescriere, însă endpoint-ul backend este `/scans`. **Eșuat.** |

## Rezultatul Sitefolio pentru W3C Before

Sitefolio a raportat:

- contrast minim insuficient: 2 elemente (serios);
- `<html>` fără atribut `lang`: 1 (serios);
- imagini fără text alternativ: 33 (critic);
- linkuri fără text discernibil: 7 (serios);
- `<select>` fără nume accesibil: 1 (critic).

Checker-ul a acceptat atât URL-ul cu HTTP, cât și cu HTTPS, cu aceleași constatări conform repetării independente. UI-ul grupează constatările pe tip, fără detalii per nod, deci nu am putut confirma identitatea fiecărui element.

Ca a doua comparație independentă, [raportul WAVE pentru aceeași pagină](https://wave.webaim.org/report#/https://www.w3.org/WAI/demos/bad/before/home.html) a arătat: 1 eroare de refresh/redirecționare, 0 erori de contrast, 2 alerte (fără structură de heading și element `noscript`), limbă detectată `en-US`, 1 structură main și scor AIM 7,9/10. WAVE a afișat temporar un challenge în pagina încorporată, deși indicatorii raportului s-au încărcat. Clasificarea și acoperirea diferă vizibil față de Sitefolio; acestea sunt motoare și categorii diferite, nu o comparație directă a scorurilor.

## Comparație multi-site — reluare după feedback

La repetarea testului am ales pagini scanabile de ambele produse și am comparat numai perechile reușite:

| Pagină | AXE local — axe-core 4.9.0 | Sitefolio | Comparație |
|---|---|---|---|
| [example.com](https://example.com/) | 100/100, 0 findings; A/AA/AAA în API 100/100/100 | 100/100, 0 tipuri de probleme | Aceeași detecție (zero); aplicația locală afișează greșit `High` și `10000%` (FE-01/02). |
| [mozilla.org](https://www.mozilla.org/en-US/) | 99,14/100, 1 finding serios: `<blink>` (`2.2.2`) | 94/100, 1 tip de problemă serios: `<blink>` ×1 | Aceeași problemă detectată; scorurile diferă cu aproximativ 5,14 puncte. |
| [wikipedia.org](https://www.wikipedia.org/) | 100/100, 0 findings | 100/100, 0 tipuri de probleme | Aceeași detecție (zero). |
| [GOV.UK](https://www.gov.uk/) | 100/100, 0 findings | Sitefolio a afișat `Check failed` de două ori | Fără rezultat Sitefolio, deci perechea este exclusă din comparația scorurilor. |
| [Deque Mars demo](https://dequeuniversity.com/demo/mars/) | 58,33/100, 46 findings: 7 critice și 39 serioase | `Check failed` după starea `Running`; checker-ul a afișat și limita `Too many checks — please wait a minute` | Rezultatul Sitefolio este inconcludent; nu îl tratăm drept zero findings și nu îl comparăm numeric. |

Scanările locale suplimentare au reușit și au creat rapoartele 73 (GOV.UK), 74 (Mozilla), 75 (Wikipedia) și 76 (Deque). Pe Deque, cele mai frecvente categorii locale au fost ținte tactile prea mici (22), linkuri fără nume discernibil (8), contrast (6), imagini fără text alternativ (4), selectoare fără nume accesibil (2), plus câte o problemă pentru iframe fără nume, linkuri diferențiate numai prin culoare, buton fără nume și atribut `lang` lipsă. Sitefolio nu a furnizat un raport utilizabil pentru această pagină.

Cele trei perechi cu scanări reușite (example.com, Mozilla, Wikipedia) arată concordanță la categoriile de constatări și divergență în scorul Mozilla. Paginile GOV.UK și Deque au fost testate local, dar erorile checker-ului împiedică o concluzie comparativă pentru ele. W3C Before/After rămâne un rezultat Sitefolio-only din cauza challenge-ului local descris mai sus.

## Comparație suplimentară — 10 site-uri noi

Am scanat aceleași URL-uri în AXE local din aplicație și în [Sitefolio Accessibility Checker](https://sitefolio.io/tools/accessibility-checker), în 27 octombrie 2026. AXE local raportează constatări per nod; Sitefolio afișează doar categorii agregate. Un eșec de scanare este marcat ca atare, nu interpretat ca zero probleme.

| Site | AXE local — axe-core 4.9.0 | Sitefolio | Diferență observată |
|---|---|---|---|
| [NASA](https://www.nasa.gov/) | 100/100; 0 constatări | 100/100; 0 categorii | Acord: zero constatări automate. |
| [Apple](https://www.apple.com/) | 87,36/100; 14 constatări: 2 critice, 12 serioase. Categorii: 11 ținte tactile prea mici, 2 roluri ARIA fără copii ceruți, 1 contrast insuficient. | 82/100; 3 constatări în 2 categorii: 2 roluri ARIA fără copii ceruți și 1 contrast insuficient; niveluri raportate: 1 critică, 1 serioasă. | Detectează aceleași probleme ARIA și contrast; local mai semnalează 11 ținte tactile insuficiente. Scorurile diferă cu 5,36 puncte. |
| [Microsoft](https://www.microsoft.com/) | 100/100; 0 constatări | 100/100; 0 categorii | Acord: zero constatări automate. |
| [BBC](https://www.bbc.com/) | 99,14/100; 1 constatare serioasă: link diferențiat de text doar prin culoare. | 100/100; 0 categorii | Diferență: numai AXE local a semnalat linkul respectiv. |
| [Cloudflare](https://www.cloudflare.com/) | 96,55/100; 4 constatări serioase de contrast insuficient. | 94/100; 2 constatări serioase de contrast insuficient. | Aceeași categorie, dar număr de noduri și scor diferite. |
| [Python.org](https://www.python.org/) | 0/100; 109 constatări: 61 critice și 48 serioase. Categorii: 57 roluri ARIA fără roluri părinte cerute, 25 elemente `<li>` folosite semantic greșit, 15 contraste insuficiente, 8 liste structurate greșit, 4 roluri ARIA fără copii ceruți. | `Check failed` | Pereche incompletă; nu există rezultat Sitefolio pentru comparație. |
| [MDN](https://developer.mozilla.org/en-US/) | 89,66/100; 12 constatări serioase: ținte tactile insuficient de mari. | `Check failed` | Pereche incompletă; nu există rezultat Sitefolio pentru comparație. |
| [Khan Academy](https://www.khanacademy.org/) | Scanarea locală a eșuat cu `Internal Server Error`; logul backend indică expirarea navigării Playwright în așteptarea `networkidle`. | 100/100; 0 categorii | Fără rezultat local utilizabil; cauza pare timeout de încărcare, nu finding-uri zero. |
| [Shopify](https://www.shopify.com/) | 98,85/100; 1 constatare critică: buton fără text discernibil. | 100/100; 0 categorii | Diferență: numai AXE local a semnalat butonul fără nume. |
| [GNU](https://www.gnu.org/) | Scanarea locală a eșuat; logul backend confirmă HTTP 429 de la site-ul țintă. | 100/100; 0 categorii | Fără rezultat local utilizabil; targetul a limitat solicitările. |

### Concluzii din cele 10 perechi

- Am obținut rezultate valide în ambele produse pentru 6 dintre cele 10 site-uri. NASA și Microsoft au zero constatări în ambele; BBC și Shopify au constatări doar în AXE local; Apple și Cloudflare au categorii comune, dar numere și scoruri diferite.
- Cele mai mari diferențe de detecție între perechile reușite sunt BBC (1 finding local, 0 Sitefolio) și Shopify (1 critic local, 0 Sitefolio). Pentru Apple, Sitefolio nu a raportat constatarea locală repetată de ținte tactile mici. Pentru Cloudflare, ambele indică probleme de contrast, cu 4 versus 2 noduri.
- Python.org are cel mai mare număr de constatări locale, 109, dar Sitefolio nu a finalizat scanarea. Nu putem deduce că Sitefolio ar fi găsit zero.
- Sitefolio a returnat `Check failed` pe Python.org și MDN; aplicația locală a eșuat pentru Khan Academy și GNU (429 la GNU). Aceste patru rânduri nu sunt comparații complete.
- Sitefolio a raportat 100/100 și zero categorii pe Khan Academy, Shopify și GNU, însă doar Shopify are pereche locală de succes și aceea a găsit un buton fără text discernibil. Aceasta este o discrepanță concretă, nu o confirmare că pagina este fără probleme.

Limitare: scanerele au evaluat starea paginilor la momentul rulării, iar Sitefolio nu expune selectorii sau versiunea motorului. Diferențele pot proveni din acoperirea/nodurile efectiv analizate și regulile fiecărui produs; scorurile numerice nu au aceeași definiție. AXE automat nu certifică accesibilitatea completă a unui site.

## Defecte și observații

| ID | Prioritate | Defect / impact | Probă |
|---|---|---|---|
| FE-01 | P1 | Procentele de conformitate sunt multiplicate cu 100 încă o dată. O pagină fără constatări afișează A/AA/AAA `10000.0%`. | BR-03; raport local 72. Backend-ul transmite deja valori 0–100. Detaliile de contract și sursele sunt în [frontend-checks.md](frontend-checks.md) și [implementation-analysis.md](implementation-analysis.md). |
| FE-02 | P1 | Scorul de calitate 100/100 este colorat/etichetat ca severitate `High`, deși pagina are zero findings și scorul mai mare este mai bun. | BR-03; raport local 72 arată `100 / 100 — High`; zero findings. |
| FE-03 | P2 | Benchmark-ul custom acceptă o listă URL goală și creează un run eșuat fără mesaj de validare lângă câmp. | BR-10; run local 3. |
| FE-04 | P1 pentru dezvoltare | Fluxul Analyzer nu funcționează prin serverul Vite: cererea `/api/scans` ajunge la backend nemodificată și primește 404, backend-ul expunând `/scans`. Proxy-ul Nginx de producție elimină prefixul, de aceea BR-03 a reușit. | BR-11; confirmat prin submit valid în browser și configurările `frontend/vite.config.ts`, `frontend/nginx.conf`, `ScanController`. Configurația Vite era modificare locală preexistentă la începutul testării. |
| OBS-01 | P2 | Scanarea unei pagini protejate de bot se termină cu HTTP 500 afișat ca `Internal Server Error`, fără mesaj de utilizator despre challenge sau un rezultat recuperabil. | BR-04; log backend `PageBlockedException: Target site served a bot challenge`. Este o incompatibilitate cu targetul și o problemă de prezentare a erorii, nu o constatare de accesibilitate a paginii. |

## Limitări

- Nu s-a putut compara setul de findings local vs. Sitefolio pe W3C Before: Playwright-ul local a primit challenge anti-bot, în timp ce Sitefolio a scanat pagina.
- Versiunea axe-core, tag-urile și filtrarea Sitefolio nu sunt vizibile, iar instrumentul nu oferă selectorii afectați. Cauza diferențelor Sitefolio–WAVE nu poate fi izolată doar din rapoartele sumare.
- Nu am rulat AI/Combined deoarece acestea depind de `OPENAI_API_KEY`; scopul comparației a fost AXE.
- Build-ul de producție a trecut în `docker compose up --build`; scanarea locală reușită și navigarea raportului/istoricului au confirmat funcționarea fluxului AXE.
