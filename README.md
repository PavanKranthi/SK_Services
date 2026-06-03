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

### Android APK (no Android Studio needed)

Use **GitHub Actions** to build the APK in the cloud:

1. Deploy the API to Railway and copy your HTTPS domain (e.g. `https://sk-services-production.up.railway.app`).
2. Open GitHub -> **Actions** -> **Build Android APK** -> **Run workflow**.
3. Paste your Railway URL into **api_base_url** -> **Run workflow**.
4. When finished, download **sk-services-debug-apk** from the run's **Artifacts** section.
5. Share `app-debug.apk` with family (Google Drive / WhatsApp). They enable "Install unknown apps" and open the file.

Optional: open in Android Studio only if you want local builds (JDK 17+ bundled).

## Test the app in the cloud (no phone, no Android Studio)

### 1. Automated UI tests (GitHub Actions)

Runs Robolectric tests in the cloud (login flows, admin dashboard, customer screen):

1. GitHub -> **Actions** -> **Test App (cloud)** -> **Run workflow**
2. Green check = app UI tests passed

Also runs automatically on pushes to `main` when `app/` changes.

### 2. Interactive testing in your browser (Appetize.io)

Use a cloud phone in Chrome/Safari:

1. Build the APK via **Build Android APK** workflow (above) and download the artifact.
2. Go to [appetize.io](https://appetize.io) -> sign up (free tier available).
3. **Upload** -> select `app-debug.apk`.
4. Open the preview link and tap through Customer / Admin / Technician flows against your live Railway API.

Share the Appetize link with family so they can try the app in a browser before installing the APK.

### 3. Real phones (best for final testing)

Send the APK file; real Android installs exercise notifications, performance, and network more accurately than a browser emulator.


| Role | Email | Password |
|------|-------|----------|
| Admin | admin@gmail.com | admin |
| Customer | pavan@gmail.com | Password123 |
| Technician | ravi@satyamservices.com | tech123 |
