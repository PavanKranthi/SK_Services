import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";
import { pool } from "./db/pool.js";
import { authRouter } from "./routes/auth.js";
import { adminStatsRouter } from "./routes/admin-stats.js";
import { bookingsRouter } from "./routes/bookings.js";
import { servicesRouter } from "./routes/services.js";
import { techniciansRouter } from "./routes/technicians.js";
import { addressesRouter } from "./routes/addresses.js";
import { syncRouter } from "./routes/sync.js";
import { usersRouter } from "./routes/users.js";

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, "../public");

const app = express();
const port = Number(process.env.PORT ?? 3000);

app.use(cors());
app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "sk-services-api" });
});

app.get("/health/db", async (_req, res) => {
  if (!process.env.DATABASE_URL?.trim()) {
    res.status(503).json({ ok: false, database: "DATABASE_URL not configured" });
    return;
  }
  try {
    await pool.query("SELECT 1");
    res.json({ ok: true, database: "connected" });
  } catch (err) {
    console.error("Database health check failed:", err);
    res.status(503).json({ ok: false, database: "disconnected" });
  }
});

app.use("/auth", authRouter);
app.use("/bookings", bookingsRouter);
app.use("/services", servicesRouter);
app.use("/technicians", techniciansRouter);
app.use("/addresses", addressesRouter);
app.use("/sync", syncRouter);
app.use("/users", usersRouter);
app.use("/admin/stats", adminStatsRouter);

// Web admin portal (browser only — not in the mobile app)
app.use("/admin", express.static(path.join(publicDir, "admin")));
app.get("/admin", (_req, res) => {
  res.sendFile(path.join(publicDir, "admin", "index.html"));
});

app.listen(port, "0.0.0.0", () => {
  console.log(`SK Services API listening on port ${port}`);
  console.log(`Admin portal: http://localhost:${port}/admin`);
});
