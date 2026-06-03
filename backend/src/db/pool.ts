import pg from "pg";
import dotenv from "dotenv";

dotenv.config();

const connectionString = process.env.DATABASE_URL?.trim();

if (!connectionString) {
  console.warn("DATABASE_URL is not set. API will fail until Supabase is configured.");
} else if (
  connectionString.includes("[project-ref]") ||
  connectionString.includes("[password]") ||
  connectionString.includes("[region]")
) {
  throw new Error(
    "DATABASE_URL still contains placeholder text like [password]. " +
      "Copy the full URI from Supabase Dashboard (Direct connection) into backend/.env"
  );
} else {
  try {
    const parsed = new URL(connectionString);
    if (!parsed.hostname) {
      throw new Error("missing hostname");
    }
  } catch {
    throw new Error(
      "DATABASE_URL is not a valid URL. If your password has special characters (@ # % etc.), " +
        "URL-encode them (e.g. @ becomes %40) or copy the ready-made URI from Supabase."
    );
  }
}

const needsSsl =
  connectionString?.includes("supabase.com") ||
  connectionString?.includes("supabase.co");

export const pool = new pg.Pool({
  connectionString,
  ssl: needsSsl ? { rejectUnauthorized: false } : undefined,
});
