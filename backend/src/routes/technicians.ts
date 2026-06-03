import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const techniciansRouter = Router();

function mapTechnician(row: Record<string, unknown>) {
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    services: row.services ?? "",
    status: row.status ?? "Active",
    role: "Technician",
  };
}

function mapBooking(row: Record<string, unknown>) {
  return {
    id: row.id,
    customerName: row.customer_name,
    customerPhone: row.customer_phone,
    serviceId: row.service_id,
    serviceName: row.service_name,
    serviceType: row.service_type,
    pricePerUnit: Number(row.price_per_unit),
    unitsCount: row.units_count,
    date: row.booking_date,
    timeSlot: row.time_slot,
    status: row.status,
    assignedTechnicianId: row.assigned_technician_id,
    assignedTechnicianName: row.assigned_technician_name,
    serviceAddress: row.service_address,
    createdAt: new Date(row.created_at as string).getTime(),
  };
}

techniciansRouter.get("/", requireAuth(["admin"]), async (_req, res) => {
  const result = await pool.query("SELECT * FROM technicians ORDER BY name ASC");
  res.json(result.rows.map(mapTechnician));
});

techniciansRouter.patch("/:id/services", requireAuth(["admin"]), async (req, res) => {
  const id = Number(req.params.id);
  const { services } = req.body as { services?: string };
  const normalized = (services ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
    .join(",");
  await pool.query("UPDATE technicians SET services = $1 WHERE id = $2", [normalized, id]);
  const row = await pool.query("SELECT * FROM technicians WHERE id = $1", [id]);
  res.json(mapTechnician(row.rows[0]));
});

techniciansRouter.get("/me/dashboard", requireAuth(["technician"]), async (req, res) => {
  const techId = req.auth!.sub;
  const techRow = await pool.query("SELECT * FROM technicians WHERE id = $1", [techId]);
  const bookings = await pool.query(
    "SELECT * FROM bookings WHERE assigned_technician_id = $1 ORDER BY booking_date ASC, created_at ASC",
    [techId]
  );
  const all = bookings.rows.map(mapBooking);
  const completed = all.filter((b) => b.status === "Completed");
  const timeSlotOrder: Record<string, number> = {
    "09:00 AM - 12:00 PM": 0,
    "12:00 PM - 03:00 PM": 1,
    "03:00 PM - 06:00 PM": 2,
    "06:00 PM - 09:00 PM": 3,
  };
  const upcoming = all
    .filter((b) => ["Pending", "Confirmed", "In Progress"].includes(b.status as string))
    .sort((a, b) => {
      const dateA = String(a.date);
      const dateB = String(b.date);
      if (dateA !== dateB) return dateA.localeCompare(dateB);
      return (timeSlotOrder[String(a.timeSlot)] ?? 99) - (timeSlotOrder[String(b.timeSlot)] ?? 99);
    });

  res.json({
    technician: mapTechnician(techRow.rows[0]),
    completedCount: completed.length,
    completedValue: completed.reduce(
      (sum, b) => sum + Number(b.pricePerUnit) * Number(b.unitsCount),
      0
    ),
    upcomingBookings: upcoming,
  });
});
