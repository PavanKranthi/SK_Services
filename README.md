# SK Services

Appliance service booking app (Android) with Express API and Supabase Postgres.

## Structure

| Folder | Purpose |
|--------|---------|
| `app/` | Android app (Kotlin + Jetpack Compose) |
| `backend/` | Express REST API (deploy this folder to Railway) |

## Quick start

### Backend (local)

```bash
cd backend
cp .env.example .env   # add DATABASE_URL + JWT_SECRET
npm install
npm run db:migrate
npm run db:seed
npm run dev
```

See [backend/README.md](backend/README.md) for Supabase setup and Railway deploy.

### Android APK

1. Set `API_BASE_URL` in `.env` to your hosted API URL (HTTPS).
2. Open project in Android Studio (JDK 17+).
3. **Build → Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`

## Default test accounts (after seed)

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@gmail.com | admin |
| Customer | pavan@gmail.com | Password123 |
| Technician | ravi@satyamservices.com | tech123 |
