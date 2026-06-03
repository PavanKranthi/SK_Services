import { Router } from "express";
import { pool } from "../db/pool.js";
import { requireAuth } from "../middleware/auth.js";

export const adminStatsRouter = Router();

function mapBooking(row: Record<string, unknown>) {
  return {
    id: row.id,
    customerName: row.customer_name,
    customerPhone: row.customer_phone,
    serviceName: row.service_name,
    serviceType: row.service_type,
    status: row.status,
    date: row.booking_date,
    timeSlot: row.time_slot,
    createdAt: row.created_at,
  };
}

adminStatsRouter.get("/", requireAuth(["admin"]), async (_req, res) => {
  try {
    const [
      adminCounts,
      customerCounts,
      technicianCounts,
      bookingCounts,
      revenueRow,
      recentBookings,
      bookingsByDay,
    ] = await Promise.all([
      pool.query(`
        SELECT
          COUNT(*)::int AS total,
          COUNT(*) FILTER (WHERE COALESCE(is_active, TRUE))::int AS active,
          COUNT(*) FILTER (WHERE NOT COALESCE(is_active, TRUE))::int AS disabled
        FROM admins
      `),
      pool.query(`
        SELECT
          COUNT(*)::int AS total,
          COUNT(*) FILTER (WHERE COALESCE(is_active, TRUE))::int AS active,
          COUNT(*) FILTER (WHERE NOT COALESCE(is_active, TRUE))::int AS disabled
        FROM customers
      `),
      pool.query(`
        SELECT
          COUNT(*)::int AS total,
          COUNT(*) FILTER (WHERE COALESCE(is_active, TRUE))::int AS active,
          COUNT(*) FILTER (WHERE NOT COALESCE(is_active, TRUE))::int AS disabled
        FROM technicians
      `),
      pool.query(`
        SELECT
          COUNT(*)::int AS total,
          COUNT(*) FILTER (WHERE status = 'Pending')::int AS pending,
          COUNT(*) FILTER (WHERE status = 'Confirmed')::int AS confirmed,
          COUNT(*) FILTER (WHERE status = 'In Progress')::int AS in_progress,
          COUNT(*) FILTER (WHERE status = 'Completed')::int AS completed,
          COUNT(*) FILTER (WHERE status = 'Declined')::int AS declined,
          COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '7 days')::int AS last_7_days
        FROM bookings
      `),
      pool.query(`
        SELECT COALESCE(SUM(price_per_unit * units_count), 0)::float AS value
        FROM bookings
        WHERE status = 'Completed'
      `),
      pool.query(`
        SELECT * FROM bookings
        ORDER BY created_at DESC
        LIMIT 12
      `),
      pool.query(`
        SELECT created_at::date AS day, COUNT(*)::int AS count
        FROM bookings
        WHERE created_at >= NOW() - INTERVAL '14 days'
        GROUP BY created_at::date
        ORDER BY day ASC
      `),
    ]);

    res.json({
      counts: {
        admins: adminCounts.rows[0],
        customers: customerCounts.rows[0],
        technicians: technicianCounts.rows[0],
        bookings: bookingCounts.rows[0],
      },
      revenue: {
        completedValue: Number(revenueRow.rows[0]?.value ?? 0),
      },
      recentBookings: recentBookings.rows.map(mapBooking),
      bookingsByDay: bookingsByDay.rows.map((r) => ({
        date: r.day,
        count: r.count,
      })),
    });
  } catch (err) {
    console.error("Admin stats error:", err);
    res.status(503).json({ error: "Could not load dashboard stats" });
  }
});
