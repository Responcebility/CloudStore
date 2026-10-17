# Kasi-Crunch — Notes for the Formal Report and Presentation

These notes cover the web-application parts of the JGA 2026 report: research
title, methods and tools, hosting environment, security, testing,
recommendations and the elevator pitch. Copy them into the Word report and
adjust the wording to your group's own voice.

---

## Research title

**An online ordering platform for a township fried-chicken business in Tshwane: A prototype investigation.**

## Aim

To design, build and host a working online ordering prototype for a township fried-chicken business.

## Objectives

1. Study at least five similar food businesses and identify the gap Kasi-Crunch fills.
2. Build a hosted website where customers can browse the menu and place delivery orders.
3. Calculate totals, club discounts and delivery fees correctly for every order.
4. Offer a subscription (club) revenue model alongside food sales.
5. Summarise sales and profit with statistics and charts on an admin dashboard.

---

## Methods and tools used

| Area | Tool | Why |
|---|---|---|
| Programming language (server) | Java 19, using the JDK's built-in HTTP server | Applies the Java skills from the Programming module; no framework to learn |
| Database | MySQL 8, via JDBC (MySQL Connector/J 8.4.0) | Reliable relational database for customers, menu and orders |
| Front end | HTML5, CSS3, JavaScript | Runs in any browser, including phones |
| Design | Red-and-orange monochrome theme, frosted-glass panels, a free Unsplash background photo | A clear, friendly brand that is easy to use |
| Charts | Chart.js 4 | Line, bar, pie and doughnut charts |
| Assistant | Crunchy, a rule-based (no-code style) assistant | Instant answers and menu suggestions without a paid AI service |
| Collaboration and version control | GitHub repository | Shared code and a full history of changes |
| Documents | Microsoft Word, Excel, PowerPoint | Report, financial projections, presentation |

### Code structure (`src/kasicrunch`)

| File | Responsibility |
|---|---|
| `Main.java` | Starts the database and web server, registers the API routes |
| `Config.java` | Reads `config.properties` and environment variables |
| `Database.java` | Creates the tables; seeds the menu, clubs and demo orders |
| `Pricing.java` | Business parameters and money formulas |
| `Statistics.java` | Mean, median, range, percentages and conversions |
| `Validator.java` | Checks every input and returns polite error messages |
| `PasswordHasher.java` | PBKDF2 password hashing and session tokens |
| `Auth.java`, `AuthHandler.java` | Sessions, club look-up, register, log in, log out |
| `ProductHandler.java` | Menu, search, categories, adding items |
| `OrderHandler.java` | Checkout and order history |
| `PlanHandler.java` | Joining, switching and cancelling clubs |
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
- **Server-side pricing:** prices, discounts and delivery fees are recalculated
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

`test.ps1` / `test.sh` runs 42 automatic checks on the formulas (delivery
threshold, club discount, gross profit and margin), statistics, validation and
cart parsing. They include deliberately bad input such as letters, negative
numbers, zero, decimals, huge numbers, empty values and tampered cart text.

Manual test plan (run through every option, then again with bad input):

| Test | Bad input to try | Expected result |
|---|---|---|
| Register | Short password, invalid email, mismatched passwords, existing email | Polite message under the field; no account created |
| Log in | Wrong password | "That email and password do not match our records." |
| Menu search | 61+ characters, nonsense words | Length message / "Nothing matches that" |
| Your order | Press + beyond stock or 20 | "You can order at most …" |
| Checkout | Logged out, empty address, phone "123" | Login prompt / field messages |
| Clubs | Join the same club twice | "You are already in the …" |
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
> **Solution:** Kasi-Crunch: crunch outside, juicy inside. A township
> fried-chicken brand with its own online ordering. You pick from the menu,
> see the delivery fee before you pay, and pay cash or card when local runners
> bring your food.
>
> **Who it's for:** Students, families and workers in Soshanguve and nearby.
>
> **How it makes money:** food sales, Crunch Club and Family Club
> subscriptions for steady monthly income, and delivery fees that pay local runners.
>
> **How AI makes it better:** Crunchy already answers questions and suggests
> spicy or budget meals instantly. Next, an AI assistant will recommend meals
> and chat in local languages, and automation will send WhatsApp updates.

**Demo order for the presentation:** Home (slogan and pitch) → Menu (search
"wings", filter Combos) → add items → Your order (free-delivery bar) →
Checkout → My Account (order status) → Revenue Model (clubs and projection) →
Dashboard as admin (statistics, margins and charts).

---

## Screenshots to add to the report appendix

- Home page with the slogan and elevator pitch
- Menu with a search and a category filter
- Your order, showing the delivery fee and the free-delivery progress bar
- Order confirmation and My Account order history
- Revenue Model clubs and the first-year projection chart
- Customer Persona and Research pages
- Admin dashboard statistics and charts
- Crunchy answering a question
- The live site's public URL in the browser address bar
