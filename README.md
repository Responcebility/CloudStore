# Kasi-Crunch

**"Crunch outside, juicy inside"**

Kasi-Crunch is one kasi kitchen with its own online ordering website, like a
McDonald's app for a township fast-food brand. There are no other stores or
vendors. Customers in Soshanguve and nearby browse the menu, then choose
delivery (R16 per km) or free collection, and pay cash or card on arrival.
Regulars can buy a **Crunch Pass**: they choose how far free delivery should
reach (1–20 km), and that distance sets the price for the month.

Built for the Faculty of ICT **Joint Group Assignment (JGA) 2026**, theme
*Digital Entrepreneurship for Sustainable Community Development*. The visual
style follows the group's [JGA-Project](https://github.com/260780395/JGA-Project) design.

- **Back end:** Java 19 (JDK built-in HTTP server) + MySQL 8 via JDBC
- **Front end:** HTML, CSS and JavaScript: light paper background, red and orange accents, photo menu cards
- **Charts:** Chart.js
- **Revenue model:** e-commerce food sales + distance-based delivery fees + Crunch Pass subscription

---

## Pages

| Page | What's on it |
|---|---|
| Home | Name and slogan, hero photo, bestsellers, Crunch Pass teaser, how it works, elevator pitch (Problem → Solution → Who it's for → How AI helps) |
| Menu (Products & Services) | 28 items with photos in 8 categories (burgers, combos, chicken, sides, pies, rolls, ice cream, drinks), search and category filter |
| Crunch Pass | Distance slider with a live price (R49 + R15 per km), a "Is it worth it?" comparison, buy, change or cancel |
| Basket | Quantities, delivery or collection, distance in km, exact delivery fee (minus what your pass covers) before you pay |
| My account | Crunch Pass status and order history with live status |
| Our story (About) | Problem statement, aim and objectives, community impact, how it was built |
| Customer persona | Thandi the student, her journey, and why a 4 km pass saves her R403 a month |
| Revenue model | Income streams, formulas, pass price table, sales-by-category chart, interactive **first-year cash-flow, profit/loss and cost-benefit projection** |
| Research | Five case studies (Chicken Licken, Nando's, Galito's, Mr D, Uber Eats), the gap Kasi-Crunch fills, Chicago author-date references |
| Contact | Validated contact form (saved to MySQL) and quick answers |
| Dashboard (admin) | Revenue, gross profit and margin, delivery fees, pass income, mean / median / range of order value and delivery distance, per-day → per-month conversions, 4 charts, top items, margin by category, order status updates, messages, add menu items |

**Crunchy**, a rule-based ordering assistant on every page, explains delivery
and the Crunch Pass, suggests spicy or budget food, searches the menu and
tracks your latest order.

Every input is validated in the browser **and** again on the server, with
polite messages such as "Please enter a delivery distance between 0.1 and 30 km."

---

## Run it locally

**You need:** Java 17+ (JDK) and MySQL 8 running.

1. Clone the repository:
   ```sh
   git clone https://github.com/Responcebility/CloudStore.git
   cd CloudStore
   ```
2. Copy the settings file and add your MySQL password:
   ```sh
   cp config.example.properties config.properties
   ```
   Then open `config.properties` and set `db.password=` (and `db.user` if it isn't `root`).
3. Start it:
   - Windows (PowerShell): `.\run.ps1`
   - macOS / Linux / Git Bash: `./run.sh`

   On first start it creates the `kasi_crunch` database, the tables, the menu
   and fictional demo orders and passes.
4. Open **http://localhost:8080**

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@kasicrunch.local` | `Admin@2026` |
| Customer, no pass | `demo4@example.com` | `Demo@2026` |
| Customer with a 5 km Crunch Pass | `demo1@example.com` | `Demo@2026` |

(`demo2` has an 8 km pass and `demo3` a 3 km pass.) You can also register a new account.

> Change `admin.password` in `config.properties` **before the first start**
> when hosting publicly. All demo data is fictional.

### Run the tests

```sh
./test.sh        # or .\test.ps1 on Windows
```

47 checks on the delivery and pass formulas, statistics, validation (including
bad input such as letters, negatives, 0 km and 31 km) and password hashing.
No database is needed.

---

## Business rules (parameters)

| Parameter | Value |
|---|---|
| Delivery | R16 per km, 0.1–30 km |
| Collection | free |
| Crunch Pass | R49 + R15 × chosen km, per month (1–20 km) |
| With a pass | free delivery within the chosen km; R16 per km only for distance beyond it |
| Pass length | 30 days; choosing a new distance starts a new month at the new price |
| Max quantity per item | 20 |

```
deliveryFee = max(0, distance − pass km) × R16     (0 for collection)
passPrice   = R49 + R15 × chosen km
orderTotal  = food subtotal + deliveryFee
grossProfit = food sales − food cost
margin %    = grossProfit ÷ food sales × 100
```

Example: a Kasi Classic (R82) delivered 4.5 km costs R82 + R72 = R154. With a
5 km Crunch Pass (R124 a month) the same order is R82.

---

## Project structure

```
CloudStore/                (repository name; the app is Kasi-Crunch)
├── src/kasicrunch/        Java back end (server, API, database, formulas)
├── public/                Website: HTML pages, css/style.css, js/*.js, img/ (hero photo, menu photos, favicon)
├── tests/kasicrunch/      CoreTest.java: automatic checks
├── docs/
│   ├── design.md          Problem statement, variables & parameters, flowchart, pseudocode, IPO tables, database design
│   ├── report-notes.md    Title, aim, methods & tools, hosting, security, testing, recommendations, elevator pitch
│   └── hosting.md         How to put Kasi-Crunch on a public URL
├── config.example.properties
├── run.ps1 / run.sh       Build and run
├── test.ps1 / test.sh     Run the tests
└── Dockerfile             Container image for cloud hosting
```

## API overview

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register`, `/login`, `/logout`, `GET /api/auth/me` | anyone | Accounts and sessions |
| `GET /api/products?search=&category=&popular=1`, `GET /api/products/categories` | anyone | Menu |
| `POST /api/products` | admin | Add a menu item |
| `POST /api/orders`, `GET /api/orders/mine` | signed in | Checkout (delivery or collection) and order history |
| `GET /api/pass/quote?maxKm=`, `POST /api/pass`, `POST /api/pass/cancel` | signed in (quote: anyone) | Crunch Pass |
| `POST /api/contact` | anyone | Contact form |
| `GET /api/pricing`, `GET /api/insights` | anyone | Pricing parameters, sales share by category |
| `POST /api/assistant` | anyone | Crunchy |
| `GET /api/admin/stats`, `/orders`, `/messages`, `POST /api/admin/orders/status` | admin | Dashboard |

## Hosting

See [docs/hosting.md](docs/hosting.md): deploy the `Dockerfile` to a cloud host
with a managed MySQL database and set the `KASICRUNCH_*` environment variables.

## Credits

Food photos from [Unsplash](https://unsplash.com) under the
[Unsplash License](https://unsplash.com/license); hero photo by
[Brian Chan](https://unsplash.com/photos/NbXjZomyNEM). Fonts: DM Sans and
Space Grotesk (Google Fonts). Charts: Chart.js. Styling based on the group's
JGA-Project repository.

*JGA 2026 prototype, Faculty of ICT. Demo data only; no real payments are processed.*
