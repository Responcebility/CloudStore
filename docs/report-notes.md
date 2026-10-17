# CloudStore — Notes for the Formal Report and Presentation

These notes cover the web-application parts of the JGA 2026 report: the
research title, methods and tools, hosting environment, security, testing,
recommendations and the elevator pitch. Copy them into the Word report and
adjust the wording to your group's own voice.

---

## Research title

**A digital marketplace for township and campus vendors in Tshwane: A prototype investigation.**

## Aim

To design, build and host a working e-commerce prototype that lets community
vendors sell online to nearby shoppers.

## Objectives

1. Research at least five similar digital businesses and identify the gap CloudStore fills.
2. Build a hosted website where shoppers can browse, search and order from at least five vendors.
3. Calculate order totals, delivery fees and commission correctly for every order.
4. Offer a subscription-based revenue model with at least three vendor plans and one shopper plan.
5. Summarise sales with mean, median, range and charts on an admin dashboard.

---

## Methods and tools used

| Area | Tool | Why |
|---|---|---|
| Programming language (server) | Java 19, using the JDK's built-in HTTP server | Applies the Java skills from the Programming module; no framework to learn |
| Database | MySQL 8, accessed with JDBC (MySQL Connector/J 8.4.0) | Reliable relational database for users, products and orders |
| Front end | HTML5, CSS3 (glassmorphism), JavaScript | Runs in any browser, including phones |
| Charts | Chart.js 4 | Line, bar and pie charts on the dashboard and Revenue Model page |
| Assistant | CloudBot, a rule-based (no-code style) assistant | Instant answers and product search without a paid AI service |
| Collaboration and version control | GitHub repository | Shared code, history of every change, one source of truth |
| Documents | Microsoft Word, Excel, PowerPoint | Report, financial projections, presentation |

### Code structure (`src/cloudstore`)

| File | Responsibility |
|---|---|
| `Main.java` | Starts the database and web server, registers the API routes |
| `Config.java` | Reads `config.properties` and environment variables |
| `Database.java` | Creates the tables and seeds demo data |
| `Pricing.java` | Business parameters and money formulas |
| `Statistics.java` | Mean, median, range, percentages and conversions |
| `Validator.java` | Checks every input and returns polite error messages |
| `PasswordHasher.java` | PBKDF2 password hashing and session tokens |
| `Auth.java`, `AuthHandler.java` | Sessions, register, log in, log out |
| `ProductHandler.java` | Catalogue, search, vendors, adding products |
| `OrderHandler.java` | Checkout, order history, vendor shop summary |
| `PlanHandler.java` | Plans and subscriptions |
| `AdminHandler.java` | Dashboard statistics, order status, messages |
| `InsightsHandler.java` | Public pricing parameters and impact figures |
| `AssistantHandler.java` | CloudBot |
| `ContactHandler.java` | Contact form |
| `StaticHandler.java` | Serves the website files safely |
| `Json.java`, `Http.java`, `ApiHandler.java`, `ApiException.java` | Shared request/response helpers |

Every file starts with a header comment (author, date, purpose), and methods
are kept short and named for what they do.

---

## Hosting environment, database and development tools (one paragraph)

CloudStore is a Java 19 web application that serves both the website and its
JSON API from the JDK's built-in HTTP server, backed by a MySQL 8 database
reached through JDBC. During development it ran locally on Windows 11 with
MySQL 8 and was edited in Visual Studio Code, with all code kept in a GitHub
repository. For public hosting, the same build runs in a Docker container
(see `Dockerfile`) on a cloud platform such as Microsoft Azure App Service, AWS
or Railway, connected to a managed MySQL database. Database credentials are
supplied as environment variables, and the host's HTTPS certificate encrypts
traffic between the browser and the server.

---

## Cybersecurity measures

- **No plain-text passwords:** passwords are hashed with PBKDF2-HMAC-SHA256,
  using a random salt and 120 000 iterations, and checked with a constant-time comparison.
- **Sessions:** a random 256-bit token is kept in an `HttpOnly`, `SameSite=Lax`
  cookie that page scripts cannot read. Sessions expire after 7 days.
- **SQL injection:** every query uses parameterised `PreparedStatement`s.
- **Cross-site scripting:** the browser inserts user content as text or
  escapes it, and the JSON writer escapes `<` and `>`.
- **Server-side checks:** prices, delivery fees and commission are recalculated
  on the server, so a tampered cart cannot change what is charged.
- **Roles:** admin-only and vendor-only actions are checked on the server.
- **Path traversal:** the file server refuses paths outside the `public` folder.
- **No real personal data:** demo customers use `@example.com` addresses and no
  real payments are processed.
- **HTTPS:** enabled by the cloud host in production.
- **Secrets:** `config.properties` is ignored by Git, so database passwords
  are never published.

---

## Testing

`test.ps1` / `test.sh` runs 39 automatic checks on the formulas, statistics,
validation and cart parsing. They include deliberately bad input such as
letters, negative numbers, zero, decimals, huge numbers, empty values and
tampered cart text.

Manual test plan (run through every option, then again with bad input):

| Test | Bad input to try | Expected result |
|---|---|---|
| Register | Short password, invalid email, mismatched passwords, existing email | Polite message under the field; no account created |
| Log in | Wrong password | "That email and password do not match our records." |
| Search | 61+ characters, nonsense words | Length message / "No products match" |
| Cart | Press + beyond stock or 20 | "You can order at most …" |
| Checkout | Logged out, empty address, phone "123" | Login prompt / field messages |
| Contact | 3-character message | "Please write a message of at least 10 characters." |
| Add product | Price "abc", stock −1 | Field messages |
| Admin pages | Open `dashboard.html` as a customer | Redirected to log in |
| Unknown page | `/nothing-here` | Friendly 404 page |

---

## Recommendations

**How could this kind of digital business foster innovation and job creation in South Africa?**

1. **Free hosting credits plus a six-month mentorship for first-year ICT
   students** would let student teams launch marketplace prototypes like
   CloudStore for real township vendors, turning class projects into small
   businesses.
2. **Municipal and university partnerships**, for example listing vendors from
   local business support programmes on a shared marketplace, would give
   informal traders their first online sales channel at no set-up cost.
3. **Paid local delivery runners**: every order creates delivery work, so
   scaling to more townships directly creates part-time jobs for young people.

**Integrations that would extend the prototype**

1. **AI chatbot:** replace the rule-based CloudBot with an AI model that
   understands free-form questions in several South African languages and
   recommends products.
2. **Payment gateway for South African users** (for example Yoco, PayFast or
   Ozow), so shoppers can pay online by card, instant EFT or QR code.
3. **No-code workflow automation** (for example Zapier, Make or Microsoft Power
   Automate) to send WhatsApp or SMS order confirmations to vendors and
   shoppers automatically.

---

## Elevator pitch (about 2 minutes)

> **Problem:** In townships like Soshanguve and Mabopane, home cooks, farmers and
> crafters make great products but can only sell to people who walk past.
> They can't afford a website, and shoppers like Thandi, a student, travel to
> the mall for things made down the road.
>
> **Solution:** CloudStore is an online marketplace where any community vendor
> can open a shop in minutes, for free, and sell with local delivery. Shoppers
> browse by category, see the delivery fee up front, and pay cash or card on
> delivery.
>
> **Who it's for:** Students and residents in Tshwane's townships, and the small
> businesses that serve them.
>
> **How it makes money:** a small commission on each sale, optional vendor
> plans with lower commission, CloudStore Plus for free delivery, and delivery
> fees that pay local runners.
>
> **How AI makes it better:** CloudBot already answers questions and finds
> products instantly. Next, an AI assistant will recommend products and chat in
> local languages, and automation will confirm orders on WhatsApp.

**Demo order for the presentation:** Home (pitch) → Shop (search "calculator")
→ add to cart → Cart (free-delivery bar) → Checkout → My Account (order) →
Revenue Model (plans and projection) → Dashboard (statistics and charts, as admin).

---

## Screenshots to add to the report appendix

- Home page with the elevator pitch
- Shop with a search and a category filter
- Cart showing the delivery fee and the free-delivery progress bar
- Order confirmation and My Account order history
- Revenue Model plans and first-year projection chart
- Customer Persona and Research pages
- Admin dashboard statistics and charts
- CloudBot answering a question
- The live site's public URL in the browser address bar
