package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "services")
data class ApplianceService(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,         // e.g. "AC", "Fridge", "Washing Machine", "TV"
    val type: String,         // e.g. "Installation", "Repair", "Maintenance"
    val price: Double,
    val isActive: Boolean = true
) {
    val displayName: String get() = "$name ($type)"
}

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val serviceId: Int,
    val serviceName: String, // Cached service name in case active status toggles
    val serviceType: String, // Cached service type
    val pricePerUnit: Double,
    val unitsCount: Int,
    val date: String,        // YYYY-MM-DD
    val timeSlot: String,    // e.g. "09:00 AM - 12:00 PM"
    val status: String,      // "Pending", "Confirmed", "In Progress", "Completed", "Declined"
    val assignedTechnicianId: Int? = null,
    val assignedTechnicianName: String? = null,
    val serviceAddress: String = "Default Service Location", // Store where this specific service is provided
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "technicians")
data class Technician(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val password: String,
    val services: String = "",
    val role: String = "Technician",
    val status: String = "Active" // "Active", "On Leave"
)

@Entity(tableName = "users")
data class UserAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

@Entity(tableName = "addresses")
data class UserAddress(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userPhone: String,
    val addressLine1: String,
    val addressLine2: String,
    val city: String,
    val district: String,
    val state: String,
    val country: String,
    val pinCode: String,
    val isDefault: Boolean = false
) {
    val fullAddressDisplay: String 
        get() = "$addressLine1, ${if (addressLine2.isNotBlank()) "$addressLine2, " else ""}$city, $district, $state, $country - $pinCode"
}

// 2. DAOs
@Dao
interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY name ASC, type ASC")
    fun getAllServicesFlow(): Flow<List<ApplianceService>>

    @Query("SELECT * FROM services WHERE isActive = 1 ORDER BY name ASC, type ASC")
    fun getActiveServicesFlow(): Flow<List<ApplianceService>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ApplianceService)

    @Update
    suspend fun updateService(service: ApplianceService)

    @Query("UPDATE services SET isActive = :isActive WHERE id = :id")
    suspend fun setServiceActive(id: Int, isActive: Boolean)

    @Delete
    suspend fun deleteService(service: ApplianceService)

    @Query("SELECT COUNT(*) FROM services")
    suspend fun getServiceCount(): Int
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY createdAt DESC")
    fun getAllBookingsFlow(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE customerPhone = :phone ORDER BY createdAt DESC")
    fun getBookingsByPhoneFlow(phone: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE assignedTechnicianId = :technicianId ORDER BY createdAt DESC")
    fun getBookingsByTechnicianFlow(technicianId: Int): Flow<List<Booking>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking): Long

    @Update
    suspend fun updateBooking(booking: Booking)

    @Query("UPDATE bookings SET status = :status, assignedTechnicianId = :techId, assignedTechnicianName = :techName WHERE id = :id")
    suspend fun assignTechnician(id: Int, status: String, techId: Int?, techName: String?)

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Int, status: String)

    @Query("SELECT * FROM bookings WHERE date = :date AND status IN ('Confirmed', 'In Progress')")
    suspend fun getBookingsForDate(date: String): List<Booking>
}

@Dao
interface TechnicianDao {
    @Query("SELECT * FROM technicians ORDER BY name ASC")
    fun getAllTechniciansFlow(): Flow<List<Technician>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTechnician(technician: Technician)

    @Update
    suspend fun updateTechnician(technician: Technician)

    @Query("SELECT * FROM technicians WHERE (email = :email OR name = :email) AND password = :password LIMIT 1")
    suspend fun loginTechnician(email: String, password: String): Technician?

    @Query("SELECT COUNT(*) FROM technicians")
    suspend fun getTechnicianCount(): Int
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE phone = :phone AND password = :password LIMIT 1")
    suspend fun loginUserByPhone(phone: String, password: String): UserAccount?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun signUpUser(user: UserAccount): Long

    @Update
    suspend fun updateUser(user: UserAccount)

    @Delete
    suspend fun deleteUser(user: UserAccount)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface AddressDao {
    @Query("SELECT * FROM addresses WHERE userPhone = :phone ORDER BY isDefault DESC, id DESC")
    fun getAddressesForPhone(phone: String): Flow<List<UserAddress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: UserAddress): Long

    @Query("UPDATE addresses SET isDefault = 0 WHERE userPhone = :phone")
    suspend fun clearDefaults(phone: String)

    @Query("UPDATE addresses SET isDefault = 1 WHERE id = :addressId")
    suspend fun setDefaultAddress(addressId: Int)

    @Delete
    suspend fun deleteAddress(address: UserAddress)
}

// 3. Database
@Database(
    entities = [ApplianceService::class, Booking::class, Technician::class, UserAccount::class, UserAddress::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceDao(): ServiceDao
    abstract fun bookingDao(): BookingDao
    abstract fun technicianDao(): TechnicianDao
    abstract fun userDao(): UserDao
    abstract fun addressDao(): AddressDao
}
