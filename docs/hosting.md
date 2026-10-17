# Hosting CloudStore online

The JGA brief gives 10% for a website that is **live on a public URL** on
presentation day. CloudStore needs a host that can run Java and a MySQL
database, so GitHub Pages (which only serves static files) is not enough on its own.

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
| `CLOUDSTORE_DB_HOST` | `your-mysql-host.example.com` |
| `CLOUDSTORE_DB_PORT` | `3306` |
| `CLOUDSTORE_DB_NAME` | `cloudstore` |
| `CLOUDSTORE_DB_USER` | `cloudstore_app` |
| `CLOUDSTORE_DB_PASSWORD` | *(secret)* |
| `CLOUDSTORE_SERVER_PORT` | `8080` (or the port your host gives you) |
| `CLOUDSTORE_ADMIN_EMAIL` | admin login created on first start |
| `CLOUDSTORE_ADMIN_PASSWORD` | *(choose a strong one before the first start)* |

The database user needs permission to create the `cloudstore` database and its
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
