import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { pool } from "./pool.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

async function migrate() {
  const schemaPath = path.join(__dirname, "schema.sql");
  const sql = fs.readFileSync(schemaPath, "utf8");
  await pool.query(sql);

  const migrationsDir = path.join(__dirname, "migrations");
  if (fs.existsSync(migrationsDir)) {
    const files = fs
      .readdirSync(migrationsDir)
      .filter((f) => f.endsWith(".sql"))
      .sort();
    for (const file of files) {
      const migrationSql = fs.readFileSync(path.join(migrationsDir, file), "utf8");
      await pool.query(migrationSql);
      console.log(`Applied migration: ${file}`);
    }
  }

  console.log("Migration completed.");
  await pool.end();
}

migrate().catch((err) => {
  console.error(err);
  process.exit(1);
});
