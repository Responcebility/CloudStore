# 🍗 Kasi-Crunch

**"Crunch outside, juicy inside"**

Kasi-Crunch is a township fried-chicken brand with its own online ordering
website. Customers in Soshanguve and nearby browse the menu, order, and pay
cash or card when local runners deliver. It was built for the Faculty of ICT
**Joint Group Assignment (JGA) 2026**, theme *Digital Entrepreneurship for
Sustainable Community Development*.

- **Back end:** Java 19 (JDK built-in HTTP server) + MySQL 8 via JDBC
- **Front end:** HTML, CSS (red-and-orange glass design over a background photo) and JavaScript
- **Charts:** Chart.js
- **Revenue model:** e-commerce food sales + monthly club subscriptions + delivery fees

---

## Pages

| Page | What's on it |
|---|---|
| Home | Name, slogan, bestsellers, how ordering works, and the elevator pitch (Problem → Solution → Who it's for → How AI helps) |
| Menu (Products & Services) | 21 items in 7 categories, search, category filter, 🔥 popular and 🌶️ spicy tags |
| About | Problem statement, aim and objectives, community impact, how it was built |
| Customer Persona | Thandi, the student customer, and her ordering journey |
| Revenue Model | Income streams, the two clubs (join here), formulas, sales-by-category chart, interactive **first-year cash-flow, profit/loss and cost-benefit projection** |
| Research | Five case studies (Chicken Licken, Nando's, Galito's, Mr D, Uber Eats), the gap Kasi-Crunch fills, Chicago author-date references |
| Contact | Validated contact form (saved to MySQL) and quick answers |
| Your order | Live totals, club discount, R25 delivery or free over R250, free-delivery progress bar, checkout |
| My account | Club membership and order history with live status |
| Dashboard (admin) | Revenue, gross profit and margin, club income, mean / median / range, per-day → per-month conversions, 4 charts, top items, margin by category, order status updates, messages, add menu items |

**Crunchy**, a rule-based ordering assistant, sits on every page. It answers
questions about delivery, clubs and payment, suggests spicy or budget meals,
searches the menu and checks your latest order.

Every input is validated in the browser **and** again on the server, with
polite messages such as "Please enter a quantity between 1 and 20." instead of "ERROR".

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

   On first start it creates the `kasicrunch` database, the tables, the menu
   and fictional demo orders.
4. Open **http://localhost:8080**

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@kasicrunch.local` | `Admin@2026` |
| Customer (demo) | `demo4@example.com` | `Demo@2026` |

(`demo1` and `demo2` are Crunch Club members and `demo3` is in the Family Club,
handy for testing the discounts.) You can also register a new account.

> Change `admin.password` in `config.properties` **before the first start**
> when hosting publicly. All demo data is fictional.

### Run the tests

```sh
./test.sh        # or .\test.ps1 on Windows
```

This runs 42 checks on the money formulas, statistics, validation (including
bad input such as letters, negatives and huge numbers) and password hashing.
No database is needed.

---

## Business rules (parameters)

| Parameter | Value |
|---|---|
| Delivery fee | R25.00 |
| Free delivery | orders **over** R250, or any club member |
| Crunch Club | R49 / month: free delivery |
| Family Club | R129 / month: free delivery + 10% off every order |
| Max quantity per item | 20 |

```
discount    = subtotal × club discount
orderTotal  = subtotal − discount + deliveryFee
grossProfit = food sales − food cost
margin %    = grossProfit ÷ food sales × 100
```

---

## Project structure

```
CloudStore/                (repository name; the app is Kasi-Crunch)
├── src/kasicrunch/        Java back end (server, API, database, formulas)
├── public/                Website: HTML pages, css/style.css, js/*.js, img/ (background photo, favicon)
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
| `POST /api/orders`, `GET /api/orders/mine` | logged in | Checkout and order history |
| `GET /api/plans`, `POST /api/subscriptions`, `POST /api/subscriptions/cancel` | logged in (plans: anyone) | Clubs |
| `POST /api/contact` | anyone | Contact form |
| `GET /api/pricing`, `GET /api/insights` | anyone | Pricing parameters, sales share by category |
| `POST /api/assistant` | anyone | Crunchy |
| `GET /api/admin/stats`, `/orders`, `/messages`, `POST /api/admin/orders/status` | admin | Dashboard |

## Hosting

See [docs/hosting.md](docs/hosting.md): deploy the `Dockerfile` to a cloud host
with a managed MySQL database and set the `KASICRUNCH_*` environment variables.

## Credits

Background photo by [Brian Chan](https://unsplash.com/@tigerrulezzz) on
[Unsplash](https://unsplash.com/photos/NbXjZomyNEM) (Unsplash License).
Fonts: Baloo 2 and Poppins (Google Fonts, open licence). Charts: Chart.js.

*JGA 2026 prototype, Faculty of ICT. Demo data only; no real payments are processed.*
