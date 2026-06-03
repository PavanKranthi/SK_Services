package com.example.data

import com.example.data.remote.CreateAddressRequest
import com.example.data.remote.CreateBookingRequest
import com.example.data.remote.CreateServiceRequest
import com.example.data.remote.PatchBookingRequest
import com.example.data.remote.PatchServiceRequest
import com.example.data.remote.RemoteApiClient
import com.example.data.remote.TokenStore
import com.example.data.remote.toAddress
import com.example.data.remote.toBooking
import com.example.data.remote.toService
import com.example.data.remote.toTechnician
import com.example.data.remote.toUserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val remote: RemoteApiClient = RemoteApiClient(tokenStore)
) {
    val serviceDao = db.serviceDao()
    val bookingDao = db.bookingDao()
    val technicianDao = db.technicianDao()
    val userDao = db.userDao()
    val addressDao = db.addressDao()

    val useCloud: Boolean get() = remote.enabled

    val allServices: Flow<List<ApplianceService>> = serviceDao.getAllServicesFlow()
    val activeServices: Flow<List<ApplianceService>> = serviceDao.getActiveServicesFlow()
    val allBookings: Flow<List<Booking>> = bookingDao.getAllBookingsFlow()
    val allTechnicians: Flow<List<Technician>> = technicianDao.getAllTechniciansFlow()

    fun getBookingsByPhone(phone: String): Flow<List<Booking>> =
        bookingDao.getBookingsByPhoneFlow(phone)

    fun getBookingsByTechnician(technicianId: Int): Flow<List<Booking>> =
        bookingDao.getBookingsByTechnicianFlow(technicianId)

    fun getAddressesForPhone(phone: String): Flow<List<UserAddress>> =
        addressDao.getAddressesForPhone(phone)

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        tokenStore.clear()
    }

    /** Sync cloud data into Room after successful login. */
    suspend fun syncFromCloud(role: String) = withContext(Dispatchers.IO) {
        if (!useCloud || tokenStore.token.isNullOrBlank()) return@withContext
        when (role) {
            "Admin" -> {
                val services = remote.getServices().map { it.toService() }
                val bookings = remote.getBookings().map { it.toBooking() }
                val technicians = remote.getTechnicians().map { it.toTechnician() }
                replaceServices(services)
                replaceBookings(bookings)
                replaceTechnicians(technicians)
            }
            "Customer" -> {
                val services = remote.getServices().filter { it.isActive }.map { it.toService() }
                val bookings = remote.getBookings().map { it.toBooking() }
                val addresses = remote.getAddresses().map { it.toAddress() }
                replaceServices(services)
                replaceBookings(bookings)
                replaceAddresses(addresses)
            }
            "Technician" -> {
                val dashboard = remote.getTechnicianDashboard()
                replaceTechnicians(listOf(dashboard.technician.toTechnician()))
                val all = remote.getBookings().map { it.toBooking() }
                replaceBookings(all)
            }
        }
    }

    private suspend fun replaceServices(services: List<ApplianceService>) {
        val existing = serviceDao.getAllServicesFlow()
        // Clear and re-insert via direct queries - use delete all pattern
        for (s in services) {
            serviceDao.insertService(s.copy(id = s.id))
        }
    }

    private suspend fun replaceBookings(bookings: List<Booking>) {
        for (b in bookings) {
            bookingDao.insertBooking(b)
        }
    }

    private suspend fun replaceTechnicians(technicians: List<Technician>) {
        for (t in technicians) {
            technicianDao.insertTechnician(t)
        }
    }

    private suspend fun replaceAddresses(addresses: List<UserAddress>) {
        for (a in addresses) {
            addressDao.insertAddress(a)
        }
    }

    suspend fun loginUser(email: String, password: String): UserAccount? = withContext(Dispatchers.IO) {
        if (useCloud) {
            try {
                val res = remote.loginCustomer(email, password)
                tokenStore.token = res.token
                val user = res.user!!.toUserAccount()
                userDao.signUpUser(user.copy(password = password))
                syncFromCloud("Customer")
                return@withContext user
            } catch (_: Exception) {
                return@withContext null
            }
        }
        userDao.loginUser(email, password)
    }

    suspend fun getUserByPhone(phone: String): UserAccount? = withContext(Dispatchers.IO) {
        userDao.getUserByPhone(phone)
    }

    suspend fun signUpUser(user: UserAccount): Long = withContext(Dispatchers.IO) {
        if (useCloud) {
            try {
                val res = remote.registerCustomer(user.name, user.email, user.phone, user.password)
                tokenStore.token = res.token
                val created = res.user!!.toUserAccount()
                userDao.signUpUser(created.copy(password = user.password))
                val seedAddr = UserAddress(
                    userPhone = user.phone,
                    addressLine1 = "Muralinagar, House #12-3, VSP",
                    addressLine2 = "Near Petrol Pump",
                    city = "Visakhapatnam",
                    district = "Visakhapatnam",
                    state = "Andhra Pradesh",
                    country = "India",
                    pinCode = "530007",
                    isDefault = true
                )
                insertAddress(seedAddr)
                syncFromCloud("Customer")
                return@withContext created.id.toLong()
            } catch (_: Exception) {
                return@withContext -1L
            }
        }
        userDao.signUpUser(user)
    }

    suspend fun loginAdmin(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (useCloud) {
            try {
                val res = remote.loginAdmin(email, password)
                tokenStore.token = res.token
                syncFromCloud("Admin")
                return@withContext true
            } catch (_: Exception) {
                return@withContext false
            }
        }
        val normalizedEmail = email.trim().lowercase()
        val normalizedPassword = password.trim()
        (normalizedEmail == "admin@gmail.com" || normalizedEmail == "admin") &&
            (normalizedPassword == "admin" || normalizedPassword == "admin123")
    }

    suspend fun loginTechnician(email: String, password: String): Technician? = withContext(Dispatchers.IO) {
        if (useCloud) {
            try {
                val res = remote.loginTechnician(email, password)
                tokenStore.token = res.token
                val tech = res.technician!!.toTechnician()
                technicianDao.insertTechnician(tech)
                syncFromCloud("Technician")
                return@withContext tech
            } catch (_: Exception) {
                return@withContext null
            }
        }
        technicianDao.loginTechnician(email, password)
    }

    suspend fun updateUser(user: UserAccount) = withContext(Dispatchers.IO) {
        userDao.updateUser(user)
    }

    suspend fun deleteUser(user: UserAccount) = withContext(Dispatchers.IO) {
        userDao.deleteUser(user)
    }

    suspend fun insertService(service: ApplianceService) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                val created = remote.createService(
                    CreateServiceRequest(service.name, service.type, service.price, service.isActive)
                )
                serviceDao.insertService(created.toService())
                return@withContext
            } catch (_: Exception) { /* fallback local */ }
        }
        serviceDao.insertService(service)
    }

    suspend fun toggleService(id: Int, isActive: Boolean) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.patchService(id, PatchServiceRequest(isActive = isActive))
                serviceDao.setServiceActive(id, isActive)
                return@withContext
            } catch (_: Exception) { /* fallback */ }
        }
        serviceDao.setServiceActive(id, isActive)
    }

    suspend fun deleteService(service: ApplianceService) = withContext(Dispatchers.IO) {
        if (useCloud && service.id > 0 && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.deleteService(service.id)
            } catch (_: Exception) { /* fallback */ }
        }
        serviceDao.deleteService(service)
    }

    suspend fun insertBooking(booking: Booking): Long = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                val created = remote.createBooking(
                    CreateBookingRequest(
                        serviceId = booking.serviceId,
                        serviceName = booking.serviceName,
                        serviceType = booking.serviceType,
                        pricePerUnit = booking.pricePerUnit,
                        unitsCount = booking.unitsCount,
                        date = booking.date,
                        timeSlot = booking.timeSlot,
                        serviceAddress = booking.serviceAddress,
                        customerName = booking.customerName,
                        customerPhone = booking.customerPhone
                    )
                )
                bookingDao.insertBooking(created.toBooking())
                return@withContext created.id.toLong()
            } catch (_: Exception) { /* fallback */ }
        }
        bookingDao.insertBooking(booking)
    }

    suspend fun assignTechnician(bookingId: Int, technicianId: Int, technicianName: String) =
        withContext(Dispatchers.IO) {
            if (useCloud && !tokenStore.token.isNullOrBlank()) {
                try {
                    remote.patchBooking(
                        bookingId,
                        PatchBookingRequest(
                            status = "Confirmed",
                            assignedTechnicianId = technicianId,
                            assignedTechnicianName = technicianName
                        )
                    )
                    bookingDao.assignTechnician(bookingId, "Confirmed", technicianId, technicianName)
                    return@withContext
                } catch (_: Exception) { /* fallback */ }
            }
            bookingDao.assignTechnician(bookingId, "Confirmed", technicianId, technicianName)
        }

    suspend fun declineBooking(bookingId: Int) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.patchBooking(bookingId, PatchBookingRequest(status = "Declined"))
            } catch (_: Exception) { /* fallback */ }
        }
        bookingDao.updateBookingStatus(bookingId, "Declined")
    }

    suspend fun updateStatus(bookingId: Int, status: String) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.patchBooking(bookingId, PatchBookingRequest(status = status))
            } catch (_: Exception) { /* fallback */ }
        }
        bookingDao.updateBookingStatus(bookingId, status)
    }

    suspend fun updateTechnician(technician: Technician) = withContext(Dispatchers.IO) {
        if (useCloud && technician.id > 0 && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.patchTechnicianServices(technician.id, technician.services)
                technicianDao.updateTechnician(technician)
                return@withContext
            } catch (_: Exception) { /* fallback */ }
        }
        technicianDao.updateTechnician(technician)
    }

    suspend fun getActiveBookingsForDate(date: String): List<Booking> = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                return@withContext remote.getActiveBookingsForDate(date).map { it.toBooking() }
            } catch (_: Exception) { /* fallback */ }
        }
        bookingDao.getBookingsForDate(date)
    }

    suspend fun insertAddress(address: UserAddress) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                val created = remote.createAddress(
                    CreateAddressRequest(
                        addressLine1 = address.addressLine1,
                        addressLine2 = address.addressLine2,
                        city = address.city,
                        district = address.district,
                        state = address.state,
                        country = address.country,
                        pinCode = address.pinCode,
                        isDefault = address.isDefault
                    )
                )
                if (address.isDefault) {
                    addressDao.clearDefaults(address.userPhone)
                }
                addressDao.insertAddress(created.toAddress())
                return@withContext
            } catch (_: Exception) { /* fallback */ }
        }
        if (address.isDefault) {
            addressDao.clearDefaults(address.userPhone)
        }
        addressDao.insertAddress(address)
    }

    suspend fun setDefaultAddress(addressId: Int, phone: String) = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.setDefaultAddress(addressId)
            } catch (_: Exception) { /* fallback */ }
        }
        addressDao.clearDefaults(phone)
        addressDao.setDefaultAddress(addressId)
    }

    suspend fun deleteAddress(address: UserAddress) = withContext(Dispatchers.IO) {
        if (useCloud && address.id > 0 && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.deleteAddress(address.id)
            } catch (_: Exception) { /* fallback */ }
        }
        addressDao.deleteAddress(address)
    }

    suspend fun checkAndSeedDatabase() = withContext(Dispatchers.IO) {
        if (useCloud) return@withContext
        if (serviceDao.getServiceCount() == 0) {
            val defaultServices = listOf(
                ApplianceService(name = "AC", type = "Maintenance/Service", price = 499.0, isActive = true),
                ApplianceService(name = "AC", type = "Repair & Gas Filing", price = 1200.0, isActive = true),
                ApplianceService(name = "AC", type = "Installation/Removal", price = 1499.0, isActive = true),
                ApplianceService(name = "Washing Machine", type = "Repair", price = 850.0, isActive = true),
                ApplianceService(name = "Washing Machine", type = "Installation", price = 450.0, isActive = true),
                ApplianceService(name = "Fridge", type = "Double Door Repair", price = 950.0, isActive = true),
                ApplianceService(name = "Fridge", type = "Single Door Repair", price = 750.0, isActive = true),
                ApplianceService(name = "Fridge", type = "General Maintenance", price = 490.0, isActive = true),
                ApplianceService(name = "TV", type = "Wall Mounting/Installation", price = 350.0, isActive = true),
                ApplianceService(name = "TV", type = "Screen/Backlight Repair", price = 2499.0, isActive = true)
            )
            for (service in defaultServices) {
                serviceDao.insertService(service)
            }
        }
        if (technicianDao.getTechnicianCount() == 0) {
            val defaultTechnicians = listOf(
                Technician(
                    name = "Ravi Prasad (Senior AC Expert)",
                    email = "ravi@satyamservices.com",
                    password = "tech123",
                    services = "AC Maintenance/Service,AC Repair & Gas Filing,AC Installation/Removal"
                ),
                Technician(
                    name = "Suresh Kumar (Fridge Specialist)",
                    email = "suresh@satyamservices.com",
                    password = "tech123",
                    services = "Fridge Double Door Repair,Fridge Single Door Repair,Fridge General Maintenance"
                ),
                Technician(
                    name = "Kiran Naidu (Washing Machine Expert)",
                    email = "kiran@satyamservices.com",
                    password = "tech123",
                    services = "Washing Machine Repair,Washing Machine Installation"
                ),
                Technician(
                    name = "Appala Raju (TV Technical Expert)",
                    email = "appala@satyamservices.com",
                    password = "tech123",
                    services = "TV Wall Mounting/Installation,TV Screen/Backlight Repair"
                )
            )
            for (tech in defaultTechnicians) {
                technicianDao.insertTechnician(tech)
            }
        }
        if (userDao.getUserCount() == 0) {
            val defaultUser = UserAccount(
                name = "Pavan Kranthi",
                email = "pavan@gmail.com",
                phone = "9876543210",
                password = "Password123"
            )
            userDao.signUpUser(defaultUser)
            addressDao.insertAddress(
                UserAddress(
                    userPhone = "9876543210",
                    addressLine1 = "Muralinagar, House #12-3, VSP",
                    addressLine2 = "Near Petrol Pump",
                    city = "Visakhapatnam",
                    district = "Visakhapatnam",
                    state = "Andhra Pradesh",
                    country = "India",
                    pinCode = "530007",
                    isDefault = true
                )
            )
        }
    }
}
