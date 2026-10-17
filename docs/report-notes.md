# Kasi-Crunch — Notes for the Formal Report and Presentation

These notes cover the web-application parts of the JGA 2026 report: research
title, methods and tools, hosting environment, security, testing,
recommendations and the elevator pitch. Copy them into the Word report and
adjust the wording to your group's own voice.

---

## Research title

**An online ordering platform for a township fast-food business in Tshwane: A prototype investigation.**

## Aim

To design, build and host a working online ordering prototype for a township fast-food business.

## Objectives

1. Study at least five similar food businesses and identify the gap Kasi-Crunch fills.
2. Build a hosted website where customers can browse the menu and order for delivery or collection.
3. Calculate distance-based delivery fees and Crunch Pass prices correctly.
4. Offer a subscription (the Crunch Pass) alongside food sales.
5. Summarise sales, profit and delivery data with statistics and charts on an admin dashboard.

---

## Methods and tools used

| Area | Tool | Why |
|---|---|---|
| Programming language (server) | Java 19, using the JDK's built-in HTTP server | Applies the Java skills from the Programming module; no framework to learn |
| Database | MySQL 8, via JDBC (MySQL Connector/J 8.4.0) | Reliable relational database for customers, menu and orders |
| Front end | HTML5, CSS3, JavaScript | Runs in any browser, including phones |
| Design | Light paper theme with red and orange accents (based on the group's JGA-Project styling), Unsplash food photos | A clear, friendly look that is easy to use |
| Charts | Chart.js 4 | Line, bar, pie and doughnut charts |
| Assistant | Crunchy, a rule-based (no-code style) assistant | Instant answers and menu suggestions without a paid AI service |
| Collaboration and version control | GitHub repository | Shared code and a full history of changes |
| Documents | Microsoft Word, Excel, PowerPoint | Report, financial projections, presentation |

### Code structure (`src/kasicrunch`)

| File | Responsibility |
|---|---|
| `Main.java` | Starts the database and web server, registers the API routes |
| `Config.java` | Reads `config.properties` and environment variables |
| `Database.java` | Creates the tables; seeds the menu, demo orders and passes |
| `Pricing.java` | Business parameters and money formulas |
| `Statistics.java` | Mean, median, range, percentages and conversions |
| `Validator.java` | Checks every input and returns polite error messages |
| `PasswordHasher.java` | PBKDF2 password hashing and session tokens |
| `Auth.java`, `AuthHandler.java` | Sessions, Crunch Pass look-up, register, log in, log out |
| `ProductHandler.java` | Menu, search, categories, adding items |
| `OrderHandler.java` | Checkout (delivery or collection) and order history |
| `PassHandler.java` | Crunch Pass quotes, buying, changing and cancelling |
| `AdminHandler.java` | Dashboard statistics, order status, messages |
| `InsightsHandler.java` | Public pricing parameters and sales share |
| `AssistantHandler.java` | Crunchy |
| `ContactHandler.java` | Contact form |
| `StaticHandler.java` | Serves the website files safely |
| `Json.java`, `Http.java`, `ApiHandler.java`, `ApiException.java` | Shared request/response helpers |

Every file starts with a header comment (author, date, purpose), and methods
are kept short and named for what they do.

---

## Hosting environment, database and development tools (one paragraph)

Kasi-Crunch is a Java 19 web application that serves both the website and its
JSON API from the JDK's built-in HTTP server, backed by a MySQL 8 database
reached through JDBC. During development it ran locally on Windows 11 with
MySQL 8 and was edited in Visual Studio Code, with all code kept in a GitHub
repository. For public hosting, the same build runs in a Docker container
(see `Dockerfile`) on a cloud platform such as Microsoft Azure, AWS or Railway,
connected to a managed MySQL database. Database credentials are supplied as
environment variables, and the host's HTTPS certificate encrypts traffic
between the browser and the server.

---

## Cybersecurity measures

- **No plain-text passwords:** PBKDF2-HMAC-SHA256 with a random salt and
  120 000 iterations, checked with a constant-time comparison.
- **Sessions:** a random 256-bit token in an `HttpOnly`, `SameSite=Lax`
  cookie that page scripts cannot read. Sessions expire after 7 days.
- **SQL injection:** every query uses parameterised `PreparedStatement`s.
- **Cross-site scripting:** user content is inserted as text or escaped, and
  the JSON writer escapes `<` and `>`.
- **Server-side pricing:** prices, delivery fees and pass prices are recalculated
  on the server, so a tampered cart cannot change what is charged.
- **Roles:** admin-only actions are checked on the server.
- **Path traversal:** the file server refuses paths outside the `public` folder.
- **No real personal data:** demo customers use `@example.com` addresses, and
  no real payments are processed.
- **HTTPS:** enabled by the cloud host in production.
- **Secrets:** `config.properties` is ignored by Git, so the database password
  is never published.

---

## Testing

`test.ps1` / `test.sh` runs 47 automatic checks on the formulas (delivery per
km, Crunch Pass price and coverage, gross profit and margin), statistics,
validation (including distances of 0 and 31 km) and basket parsing. They include deliberately bad input such as letters, negative
numbers, zero, decimals, huge numbers, empty values and tampered cart text.

Manual test plan (run through every option, then again with bad input):

| Test | Bad input to try | Expected result |
|---|---|---|
| Register | Short password, invalid email, mismatched passwords, existing email | Polite message under the field; no account created |
| Log in | Wrong password | "That email and password do not match our records." |
| Menu search | 61+ characters, nonsense words | Length message / "Nothing matches that" |
| Basket | Press + beyond stock or 20 | "You can order at most …" |
| Checkout | Signed out, empty address, distance 0 or 45 km, phone "123" | Sign-in prompt / field messages |
| Crunch Pass | Buy the same distance twice; ask for 25 km | "You already have…" / distance message |
| Contact | 3-character message | "Please write a message of at least 10 characters." |
| Add menu item (admin) | Price "abc", cost higher than price, stock −1 | Field messages |
| Dashboard | Open `dashboard.html` as a customer | Redirected to log in |
| Unknown page | `/nothing-here` | Friendly 404 page |

---

## Recommendations

**How could this kind of digital business foster innovation and job creation in South Africa?**

1. **Six months of mentorship plus free hosting credits for student-run food
   start-ups** would let groups like ours launch real ordering sites for kasi
   kitchens, turning class prototypes into businesses.
2. **Partnerships with local small-business support programmes** to bring
   existing township food vendors onto shared online ordering, giving them a
   digital sales channel with no set-up cost.
3. **Paid local delivery runners:** every order creates delivery work, so
   growing to more townships directly creates part-time jobs for young people.

**Integrations that would extend the prototype**

1. **AI chatbot:** replace the rule-based Crunchy with an AI model that
   understands free-form questions in several South African languages and
   recommends meals from past orders.
2. **Payment gateway for South African users** (for example Yoco, PayFast or
   Ozow), so customers can pay online by card, instant EFT or QR code.
3. **No-code workflow automation** (for example Zapier, Make or Microsoft Power
   Automate) to send WhatsApp or SMS order confirmations and "out for delivery"
   alerts automatically.

---

## Elevator pitch (about 2 minutes)

> **Problem:** In Soshanguve, good fried chicken is often a taxi ride away, and
> delivery apps don't always reach kasi streets. Students like Thandi end up
> spending their allowance on transport just to eat.
>
> **Solution:** Kasi-Crunch: crunch outside, juicy inside. Our own kasi
> kitchen with simple online ordering, like a McDonald's app. You pick from the
> menu, choose delivery (R16 per km) or free collection, see the exact fee
> before you pay, and pay cash or card on arrival.
>
> **Who it's for:** Students, families and workers in Soshanguve and nearby.
>
> **How it makes money:** food sales, delivery fees that pay local runners,
> and the Crunch Pass: you choose how far free delivery reaches, and that
> distance sets your monthly price.
>
> **How AI makes it better:** Crunchy already answers questions, explains the
> Crunch Pass and suggests spicy or budget meals instantly. Next, an AI assistant will recommend meals
> and chat in local languages, and automation will send WhatsApp updates.

**Demo order for the presentation:** Home (slogan and pitch) → Menu (search
"wings", filter Meal Combos) → add items → Basket (delivery 4.5 km = R72, then
collection = free) → Checkout → Crunch Pass (slide to 5 km = R124) → place
the same order again (delivery now free) → My account → Revenue model
(projection) → Dashboard as admin (statistics, distances and charts).

---

## Screenshots to add to the report appendix

- Home page with the slogan and elevator pitch
- Menu with a search and a category filter
- Basket, showing the delivery fee for a distance and the collection option
- Order confirmation and My Account order history
- Crunch Pass slider and price, and the Revenue model projection chart
- Customer Persona and Research pages
- Admin dashboard statistics and charts
- Crunchy answering a question
- The live site's public URL in the browser address bar
