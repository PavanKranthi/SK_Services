import bcrypt from "bcrypt";
import { pool } from "./pool.js";

async function seed() {
  const adminHash = await bcrypt.hash("admin", 10);
  await pool.query(
    `INSERT INTO admins (email, password_hash) VALUES ($1, $2)
     ON CONFLICT (email) DO NOTHING`,
    ["admin@gmail.com", adminHash]
  );

  const customerHash = await bcrypt.hash("Password123", 10);
  const customerResult = await pool.query(
    `INSERT INTO customers (name, email, phone, password_hash)
     VALUES ($1, $2, $3, $4)
     ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name
     RETURNING id`,
    ["Pavan Kranthi", "pavan@gmail.com", "9876543210", customerHash]
  );
  const customerId = customerResult.rows[0]?.id;

  if (customerId) {
    await pool.query(
      `INSERT INTO addresses (customer_id, address_line1, address_line2, city, district, state, country, pin_code, is_default)
       SELECT $1, $2, $3, $4, $5, $6, $7, $8, $9
       WHERE NOT EXISTS (SELECT 1 FROM addresses WHERE customer_id = $1 AND pin_code = $8)`,
      [
        customerId,
        "Muralinagar, House #12-3, VSP",
        "Near Petrol Pump",
        "Visakhapatnam",
        "Visakhapatnam",
        "Andhra Pradesh",
        "India",
        "530007",
        true,
      ]
    );
  }

  const techHash = await bcrypt.hash("tech123", 10);
  const technicians = [
    ["Ravi Prasad (Senior AC Expert)", "ravi@satyamservices.com", "AC Maintenance/Service,AC Repair & Gas Filing,AC Installation/Removal"],
    ["Suresh Kumar (Fridge Specialist)", "suresh@satyamservices.com", "Fridge Double Door Repair,Fridge Single Door Repair,Fridge General Maintenance"],
    ["Kiran Naidu (Washing Machine Expert)", "kiran@satyamservices.com", "Washing Machine Repair,Washing Machine Installation"],
    ["Appala Raju (TV Technical Expert)", "appala@satyamservices.com", "TV Wall Mounting/Installation,TV Screen/Backlight Repair"],
  ] as const;
  for (const [name, email, services] of technicians) {
    await pool.query(
      `INSERT INTO technicians (name, email, password_hash, services)
       VALUES ($1, $2, $3, $4) ON CONFLICT (email) DO NOTHING`,
      [name, email, techHash, services]
    );
  }

  const services = [
    ["AC", "Maintenance/Service", 499],
    ["AC", "Repair & Gas Filing", 1200],
    ["AC", "Installation/Removal", 1499],
    ["Washing Machine", "Repair", 850],
    ["Washing Machine", "Installation", 450],
    ["Fridge", "Double Door Repair", 950],
    ["Fridge", "Single Door Repair", 750],
    ["Fridge", "General Maintenance", 490],
    ["TV", "Wall Mounting/Installation", 350],
    ["TV", "Screen/Backlight Repair", 2499],
  ] as const;
  for (const [name, type, price] of services) {
    await pool.query(
      `INSERT INTO services (name, type, price, is_active)
       SELECT $1, $2, $3, true WHERE NOT EXISTS (
         SELECT 1 FROM services WHERE name = $1 AND type = $2
       )`,
      [name, type, price]
    );
  }

  console.log("Seed completed.");
  await pool.end();
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});
