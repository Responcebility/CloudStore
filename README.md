# ☁ CloudStore

**Shop local. Grow your community.**

CloudStore is an online marketplace that connects township and campus shoppers
in Tshwane with local community vendors: home cooks, small farmers, crafters,
gadget sellers and more. It was built for the Faculty of ICT **Joint Group
Assignment (JGA) 2026**, theme *Digital Entrepreneurship for Sustainable
Community Development*.

- **Back end:** Java 19 (JDK built-in HTTP server) + MySQL 8 via JDBC
- **Front end:** HTML, CSS (modern glassmorphism design) and JavaScript
- **Charts:** Chart.js
- **Revenue model:** sales commission + vendor subscription plans + CloudStore Plus + delivery fees

---

## Features

| Area | What it does |
|---|---|
| Home | Elevator pitch (Problem → Solution → Who it's for → How AI makes it better), live community numbers, featured products |
| Shop (Products & Services) | Search, category filters, featured vendors, vendor directory, add to cart |
| About | Problem statement, aim and objectives, South African context, methods and tools |
| Customer Persona | Shopper and vendor persona cards, plus the shopper's journey |
| Revenue Model | Revenue streams, live plans with subscribe, formulas, sales-by-category chart, and an interactive **first-year cash-flow, profit/loss and cost-benefit projection** |
| Research | Five case studies (Takealot, Mr D, Yoco, Facebook Marketplace, Etsy), how they shaped the prototype, Chicago author-date reference list |
| Contact | Validated contact form saved to MySQL, FAQ |
| Cart & checkout | Live totals, R35 delivery or free over R500 / with Plus, free-delivery progress bar, cash or card on delivery |
| Accounts | Register and log in (PBKDF2-hashed passwords), order history, plan management |
| Vendors | Open a shop by choosing a plan, add products (plan limits apply), see sales and payout after commission |
| Admin dashboard | Revenue, commission, subscriptions, mean / median / range of order values, per-day to per-month conversions, 4 charts, top products, order status updates, contact messages |
| CloudBot | Rule-based assistant on every page: answers about delivery, plans and payments, searches products, checks your latest order |

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

   The script downloads the MySQL JDBC driver the first time, compiles the Java
   code and starts the server. On first start it creates the `cloudstore`
   database, its tables and demo data.
4. Open **http://localhost:8080**

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@cloudstore.local` | `Admin@2026` |
| Customer (demo) | `demo1@example.com` | `Demo@2026` |

You can also register a new account. To try the vendor side, log in as a
customer, open **Revenue Model → Plans** and choose a vendor plan.

> Change `admin.password` in `config.properties` **before the first start**
> when hosting publicly. All demo data is fictional.

### Run the tests

```sh
./test.sh        # or .\test.ps1 on Windows
```

This runs 39 checks on the money formulas, statistics, validation (including
bad input such as letters, negatives and huge numbers) and password hashing.
No database is needed.

---

## Business rules (parameters)

| Parameter | Value |
|---|---|
| Delivery fee | R35.00 |
| Free delivery | orders **over** R500, or CloudStore Plus members |
| Starter plan | free, 10% commission, up to 10 products |
| Growth plan | R149 / month, 6% commission, up to 100 products, featured |
| Pro plan | R349 / month, 3% commission, unlimited products, featured |
| CloudStore Plus | R49 / month, free delivery for shoppers |
| Max quantity per product | 20 |

```
orderTotal = subtotal + deliveryFee
commission = subtotal × plan commission rate
payout     = subtotal − commission
```

---

## Project structure

```
CloudStore/
├── src/cloudstore/        Java back end (server, API, database, formulas)
├── public/                Website: HTML pages, css/style.css, js/*.js, img/
├── tests/cloudstore/      CoreTest.java: automatic checks
├── docs/
│   ├── design.md          Problem statement, variables & parameters, flowchart, pseudocode, IPO tables, database design
│   ├── report-notes.md    Title, aim, methods & tools, hosting, security, testing, recommendations, elevator pitch
│   └── hosting.md         How to put CloudStore on a public URL
├── config.example.properties
├── run.ps1 / run.sh       Build and run
├── test.ps1 / test.sh     Run the tests
└── Dockerfile             Container image for cloud hosting
```

## API overview

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register`, `/login`, `/logout`, `GET /api/auth/me` | anyone | Accounts and sessions |
| `GET /api/products?search=&category=`, `GET /api/products/categories`, `GET /api/vendors` | anyone | Catalogue |
| `POST /api/products` | vendor, admin | Add a product |
| `POST /api/orders`, `GET /api/orders/mine` | logged in | Checkout and history |
| `GET /api/orders/shop` | vendor | Shop sales and payout |
| `GET /api/plans`, `POST /api/subscriptions`, `POST /api/subscriptions/cancel`, `GET /api/subscriptions/mine` | logged in (plans: anyone) | Subscriptions |
| `POST /api/contact` | anyone | Contact form |
| `GET /api/pricing`, `GET /api/insights` | anyone | Pricing parameters, impact figures |
| `POST /api/assistant` | anyone | CloudBot |
| `GET /api/admin/stats`, `/orders`, `/messages`, `POST /api/admin/orders/status` | admin | Dashboard |

## Hosting

See [docs/hosting.md](docs/hosting.md). In short: deploy the `Dockerfile` to a
cloud host with a managed MySQL database, and set the `CLOUDSTORE_*`
environment variables.

---

*JGA 2026 prototype, Faculty of ICT. Demo data only; no real payments are processed.*
