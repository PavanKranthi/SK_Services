import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import { authRouter } from "./routes/auth.js";
import { bookingsRouter } from "./routes/bookings.js";
import { servicesRouter } from "./routes/services.js";
import { techniciansRouter } from "./routes/technicians.js";
import { addressesRouter } from "./routes/addresses.js";
import { syncRouter } from "./routes/sync.js";

dotenv.config();

const app = express();
const port = Number(process.env.PORT ?? 3000);

app.use(cors());
app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "sk-services-api" });
});

app.use("/auth", authRouter);
app.use("/bookings", bookingsRouter);
app.use("/services", servicesRouter);
app.use("/technicians", techniciansRouter);
app.use("/addresses", addressesRouter);
app.use("/sync", syncRouter);

app.listen(port, "0.0.0.0", () => {
  console.log(`SK Services API listening on port ${port}`);
});
