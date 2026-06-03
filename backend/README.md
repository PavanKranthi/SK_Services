# SK Services API (Supabase + Express)

REST API for the SK Services Android app. **Database lives on Supabase Postgres**; this server connects via the Supabase connection string (not Neon).

## Architecture

```
Android App  --HTTPS/JWT-->  Express API  --SSL-->  Supabase Postgres
```

## 1. Supabase setup

1. Create a project at [supabase.com](https://supabase.com).
2. Open **Project Settings -> Database -> Connection string**.
3. Copy the **URI** (Transaction pooler, port 6543 recommended for servers).
4. Paste into `backend/.env` as `DATABASE_URL`.

## 2. Local API setup

```bash
cd backend
cp .env.example .env
# Edit .env: DATABASE_URL, JWT_SECRET

npm install
npm run db:migrate
npm run db:seed
npm run dev
```

API runs at `http://localhost:3000`. Health check: `GET /health`.

## 3. Deploy API (Railway)

Repo: [github.com/PavanKranthi/SK_Services](https://github.com/PavanKranthi/SK_Services)

**Important:** Railway must deploy the **API only**, not the Android app. Either approach works:

### Option A (recommended): Root Directory

1. [Railway](https://railway.app) -> **New Project** -> **Deploy from GitHub repo** -> select `SK_Services`.
2. Service **Settings** -> **Root Directory** = `backend` (not repo root).
3. **Variables**:
   - `DATABASE_URL` = Supabase connection URI
   - `JWT_SECRET` = long random string
4. **Networking** -> **Generate Domain** -> test `GET https://YOUR-DOMAIN/health`

### Option B: Repo root (Dockerfile)

If Root Directory is left blank, the repo root `railway.toml` + `Dockerfile` build **only** `backend/` via Docker (avoids Gradle/Android build errors).

Same variables as Option A.

## 4. Android app configuration

In project root `satyam_services/.env`:

```
API_BASE_URL=https://your-deployed-api-url
```

For Android emulator against local API:

```
API_BASE_URL=http://10.0.2.2:3000
```

## 5. Build test APK

Requires **JDK 17+** (Gradle 9.3 + Android Gradle Plugin 9.1).

```bash
cd /path/to/satyam_services
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

In Android Studio: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** (uses bundled JDK 17).

## Default accounts (after seed)

| Role | Login | Password |
|------|-------|----------|
| Admin | admin@gmail.com | admin |
| Customer | pavan@gmail.com | Password123 |
| Technician | ravi@satyamservices.com | tech123 |

## Smoke test checklist

- Customer: login, book service, view booking history
- Admin: login, accept booking, assign technician, manage services
- Technician: login, view completed count/value, upcoming bookings, profile services (read-only)
