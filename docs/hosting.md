# Hosting Kasi-Crunch online

The JGA brief gives 10% for a website that is **live on a public URL** on
presentation day. Kasi-Crunch needs a host that can run Java and a MySQL
database. GitHub Pages only serves static files, so it can't run the app on its own.

## What the host must provide

| Need | Value |
|---|---|
| Runtime | Java 17 or newer (the `Dockerfile` uses Java 21) |
| Database | MySQL 8 (a managed MySQL service is easiest) |
| Port | The app listens on `server.port` (default 8080) |
| HTTPS | Provided by the host |

## Settings as environment variables

Instead of `config.properties`, set these on the host:

| Variable | Example |
|---|---|
| `KASICRUNCH_DB_HOST` | `your-mysql-host.example.com` |
| `KASICRUNCH_DB_PORT` | `3306` |
| `KASICRUNCH_DB_NAME` | `kasicrunch` |
| `KASICRUNCH_DB_USER` | `kasicrunch_app` |
| `KASICRUNCH_DB_PASSWORD` | *(secret)* |
| `KASICRUNCH_SERVER_PORT` | `8080` (or the port your host gives you) |
| `KASICRUNCH_ADMIN_EMAIL` | admin login created on first start |
| `KASICRUNCH_ADMIN_PASSWORD` | *(choose a strong one before the first start)* |

The database user needs permission to create the `kasicrunch` database and its
tables the first time; after that, normal read/write access is enough.

## Option A: a Docker-based host (Railway, Render, Azure Container Apps, AWS App Runner)

1. Create a managed MySQL database on the platform and note its host, port, user and password.
2. Create a new service from this GitHub repository. The platform detects the `Dockerfile`.
3. Add the environment variables above.
4. Deploy, then open the public URL the platform gives you.
5. Log in with the admin email and password you set, and check the Dashboard.

## Option B: a virtual machine (Azure, AWS EC2)

1. Install Java 17+ and MySQL 8 on the server.
2. Clone the repository, copy `config.example.properties` to `config.properties` and fill it in.
3. Run `./run.sh` (or install it as a service).
4. Put a reverse proxy such as Caddy or Nginx in front for HTTPS on port 443.

## Before presentation day

- Open the live URL on a phone and a laptop.
- Place a test order and check it shows on the Dashboard.
- Write the live URL on the report's cover page.
- Keep the tab open during the presentation; don't rely on screenshots.
