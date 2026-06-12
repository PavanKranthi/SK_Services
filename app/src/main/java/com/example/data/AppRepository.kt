package com.example.data

import com.example.data.remote.CreateAddressRequest
import com.example.data.remote.CreateBookingRequest
import com.example.data.remote.CreatePortalUserRequest
import com.example.data.remote.CreateServiceRequest
import com.example.data.remote.PatchBookingRequest
import com.example.data.remote.PatchServiceRequest
import com.example.data.remote.RemoteApiClient
import com.example.data.remote.TokenStore
import com.example.data.remote.UpdatePortalUserRequest
import com.example.data.remote.toAddress
import com.example.data.remote.toBooking
import com.example.data.remote.toPortalUser
import com.example.data.remote.toService
import com.example.data.remote.toTechnician
import com.example.data.remote.toUserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** Signup details held between OTP send and verify. */
private data class PendingSignup(
    val name: String,
    val phone: String,
    val password: String,
    val offlineOtp: String? = null
)

class AppRepository(
    private val db: AppDatabase,
    private val tokenStore: TokenStore,
    private val remote: RemoteApiClient = RemoteApiClient(tokenStore)
) {
    private var pendingSignup: PendingSignup? = null

    companion object {
        /** Fixed OTP used when the app runs without a cloud API (Room-only mode). */
        const val OFFLINE_OTP = "123456"
    }
    val serviceDao = db.serviceDao()
    val bookingDao = db.bookingDao()
    val technicianDao = db.technicianDao()
    val userDao = db.userDao()
    val addressDao = db.addressDao()

    val useCloud: Boolean get() = remote.enabled

    private fun mapAuthException(e: Exception): AuthResult =
        when (e) {
            is HttpException ->
                when (e.code()) {
                    401, 403 -> AuthResult.InvalidCredentials
                    else -> AuthResult.NetworkError
                }
            is IOException -> AuthResult.NetworkError
            else -> AuthResult.NetworkError
        }

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

    suspend fun loginUser(phone: String, password: String): Pair<AuthResult, UserAccount?> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeIndianPhone(phone)
            if (normalized == null) return@withContext AuthResult.InvalidCredentials to null
            if (useCloud) {
                try {
                    val res = remote.loginCustomer(normalized, password)
                    tokenStore.token = res.token
                    val user = res.user!!.toUserAccount()
                    userDao.signUpUser(user.copy(password = password))
                    try {
                        syncFromCloud("Customer")
                    } catch (_: Exception) {
                        // Login succeeded; sync can retry later
                    }
                    return@withContext AuthResult.Success to user
                } catch (e: Exception) {
                    return@withContext mapAuthException(e) to null
                }
            }
            val user = userDao.loginUserByPhone(normalized, password)
            if (user != null) AuthResult.Success to user else AuthResult.InvalidCredentials to null
        }

    /** Step 1 of signup: send OTP (or stage offline OTP). */
    suspend fun initiateSignup(name: String, phone: String, password: String): Pair<AuthResult, String?> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeIndianPhone(phone)
                ?: return@withContext AuthResult.InvalidCredentials to "Enter a valid 10-digit Indian mobile number."
            val existing = userDao.getUserByPhone(normalized)
            if (existing != null) {
                return@withContext AuthResult.InvalidCredentials to "Phone number is already associated with an account."
            }
            pendingSignup = PendingSignup(name.trim(), normalized, password)
            if (useCloud) {
                try {
                    remote.initCustomerRegister(name.trim(), normalized, password)
                    return@withContext AuthResult.Success to "OTP will be sent to +91 $normalized via Firebase."
                } catch (e: HttpException) {
                    pendingSignup = null
                    val msg = e.response()?.errorBody()?.string()?.let { parseApiError(it) }
                    return@withContext when (e.code()) {
                        409 -> AuthResult.InvalidCredentials to (msg ?: "Phone already registered")
                        else -> mapAuthException(e) to (msg ?: "Could not send OTP. Try again.")
                    }
                } catch (_: Exception) {
                    pendingSignup = null
                    return@withContext AuthResult.NetworkError to "Unable to reach the server."
                }
            }
            pendingSignup = PendingSignup(name.trim(), normalized, password, OFFLINE_OTP)
            AuthResult.Success to "OTP sent. Use $OFFLINE_OTP to verify (offline mode)."
        }

    /** Step 2 of signup: verify Firebase phone token and create the account. */
    suspend fun verifySignupWithFirebase(phone: String, firebaseIdToken: String): Pair<AuthResult, UserAccount?> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeIndianPhone(phone)
                ?: return@withContext AuthResult.InvalidCredentials to null
            if (useCloud) {
                try {
                    val res = remote.verifyCustomerRegister(normalized, firebaseIdToken)
                    tokenStore.token = res.token
                    val user = res.user!!.toUserAccount()
                    val savedPassword = pendingSignup?.password.orEmpty()
                    userDao.signUpUser(user.copy(password = savedPassword))
                    seedDefaultAddress(user.phone)
                    try {
                        syncFromCloud("Customer")
                    } catch (_: Exception) {
                        // Account created; sync can retry later
                    }
                    pendingSignup = null
                    return@withContext AuthResult.Success to user.copy(password = savedPassword)
                } catch (e: HttpException) {
                    return@withContext when (e.code()) {
                        401, 400 -> AuthResult.InvalidCredentials to null
                        else -> mapAuthException(e) to null
                    }
                } catch (_: Exception) {
                    return@withContext AuthResult.NetworkError to null
                }
            }
            val pending = pendingSignup
            if (pending == null || pending.phone != normalized) {
                return@withContext AuthResult.InvalidCredentials to null
            }
            // Offline dev: accept fixed OTP without Firebase.
            if (firebaseIdToken != OFFLINE_OTP && firebaseIdToken != pending.offlineOtp) {
                return@withContext AuthResult.InvalidCredentials to null
            }
            val email = "${normalized}@satyam.local"
            val newUser = UserAccount(
                name = pending.name,
                email = email,
                phone = normalized,
                password = pending.password
            )
            val id = userDao.signUpUser(newUser)
            if (id <= 0) return@withContext AuthResult.NetworkError to null
            seedDefaultAddress(normalized)
            pendingSignup = null
            AuthResult.Success to newUser.copy(id = id.toInt())
        }

    private suspend fun seedDefaultAddress(phone: String) {
        val seedAddr = UserAddress(
            userPhone = phone,
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
    }

    private fun normalizeIndianPhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val normalized = when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> return null
        }
        return if (normalized.matches(Regex("^[6-9]\\d{9}$"))) normalized else null
    }

    private fun parseApiError(body: String): String? {
        return Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
    }

    suspend fun getUserByPhone(phone: String): UserAccount? = withContext(Dispatchers.IO) {
        userDao.getUserByPhone(phone)
    }


    suspend fun loginAdmin(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (useCloud) {
            try {
                val res = remote.loginAdmin(email, password)
                tokenStore.token = res.token
                try {
                    syncFromCloud("Admin")
                } catch (_: Exception) {
                    // Login succeeded; sync can retry later
                }
                return@withContext AuthResult.Success
            } catch (e: Exception) {
                return@withContext mapAuthException(e)
            }
        }
        val normalizedEmail = email.trim().lowercase()
        val normalizedPassword = password.trim()
        val ok =
            (normalizedEmail == "admin@gmail.com" || normalizedEmail == "admin") &&
                (normalizedPassword == "admin" || normalizedPassword == "admin123")
        if (ok) AuthResult.Success else AuthResult.InvalidCredentials
    }

    suspend fun loginTechnician(email: String, password: String): Pair<AuthResult, Technician?> =
        withContext(Dispatchers.IO) {
            if (useCloud) {
                try {
                    val res = remote.loginTechnician(email, password)
                    tokenStore.token = res.token
                    val tech = res.technician!!.toTechnician()
                    technicianDao.insertTechnician(tech)
                    try {
                        syncFromCloud("Technician")
                    } catch (_: Exception) {
                        // Login succeeded; sync can retry later
                    }
                    return@withContext AuthResult.Success to tech
                } catch (e: Exception) {
                    return@withContext mapAuthException(e) to null
                }
            }
            val tech = technicianDao.loginTechnician(email, password)
            if (tech != null) AuthResult.Success to tech else AuthResult.InvalidCredentials to null
        }

    suspend fun initiateResetPassword(phone: String): Pair<AuthResult, String?> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeIndianPhone(phone)
                ?: return@withContext AuthResult.InvalidCredentials to "Enter a valid 10-digit Indian mobile number."
            if (!useCloud) {
                val user = userDao.getUserByPhone(normalized)
                if (user == null) {
                    return@withContext AuthResult.InvalidCredentials to "No account found for this phone."
                }
                pendingSignup = PendingSignup(user.name, normalized, "", OFFLINE_OTP)
                return@withContext AuthResult.Success to "Use OTP $OFFLINE_OTP (offline mode)."
            }
            try {
                remote.initResetPassword(normalized)
                AuthResult.Success to "OTP will be sent to +91 $normalized via Firebase."
            } catch (e: HttpException) {
                val msg = e.response()?.errorBody()?.string()?.let { parseApiError(it) }
                when (e.code()) {
                    404 -> AuthResult.InvalidCredentials to (msg ?: "No account found")
                    else -> mapAuthException(e) to (msg ?: "Could not start reset.")
                }
            } catch (_: Exception) {
                AuthResult.NetworkError to "Unable to reach the server."
            }
        }

    suspend fun verifyResetPasswordWithFirebase(
        phone: String,
        firebaseIdToken: String,
        newPassword: String
    ): Pair<AuthResult, UserAccount?> = withContext(Dispatchers.IO) {
        val normalized = normalizeIndianPhone(phone)
            ?: return@withContext AuthResult.InvalidCredentials to null
        if (useCloud) {
            try {
                val res = remote.verifyResetPassword(normalized, firebaseIdToken, newPassword)
                tokenStore.token = res.token
                val user = res.user!!.toUserAccount().copy(password = newPassword)
                userDao.signUpUser(user)
                pendingSignup = null
                return@withContext AuthResult.Success to user
            } catch (e: HttpException) {
                return@withContext when (e.code()) {
                    401, 400, 404 -> AuthResult.InvalidCredentials to null
                    else -> mapAuthException(e) to null
                }
            } catch (_: Exception) {
                return@withContext AuthResult.NetworkError to null
            }
        }
        if (firebaseIdToken != OFFLINE_OTP) {
            return@withContext AuthResult.InvalidCredentials to null
        }
        val user = userDao.getUserByPhone(normalized)
            ?: return@withContext AuthResult.InvalidCredentials to null
        val updated = user.copy(password = newPassword)
        userDao.updateUser(updated)
        pendingSignup = null
        AuthResult.Success to updated
    }

    suspend fun changeCustomerPassword(
        currentPassword: String,
        newPassword: String
    ): AuthResult = withContext(Dispatchers.IO) {
        if (useCloud && !tokenStore.token.isNullOrBlank()) {
            try {
                remote.changeCustomerPassword(currentPassword, newPassword)
                return@withContext AuthResult.Success
            } catch (e: HttpException) {
                return@withContext when (e.code()) {
                    401 -> AuthResult.InvalidCredentials
                    else -> AuthResult.NetworkError
                }
            } catch (_: Exception) {
                return@withContext AuthResult.NetworkError
            }
        }
        AuthResult.NetworkError
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

    /** Admin-only: list all portal accounts from the cloud API. */
    suspend fun fetchPortalUsers(): Pair<AuthResult, List<PortalUser>> = withContext(Dispatchers.IO) {
        if (!useCloud || tokenStore.token.isNullOrBlank()) {
            return@withContext AuthResult.NetworkError to emptyList()
        }
        try {
            val users = remote.getPortalUsers().users.map { it.toPortalUser() }
            AuthResult.Success to users
        } catch (e: Exception) {
            mapAuthException(e) to emptyList()
        }
    }

    suspend fun createPortalUser(
        role: String,
        email: String,
        password: String,
        name: String,
        phone: String,
        services: String
    ): Pair<AuthResult, PortalUser?> = withContext(Dispatchers.IO) {
        if (!useCloud || tokenStore.token.isNullOrBlank()) {
            return@withContext AuthResult.NetworkError to null
        }
        try {
            val created = remote.createPortalUser(
                CreatePortalUserRequest(
                    role = role.lowercase(),
                    email = email.trim(),
                    password = password,
                    name = name.ifBlank { null },
                    phone = phone.ifBlank { null },
                    services = services.ifBlank { null }
                )
            )
            AuthResult.Success to created.toPortalUser()
        } catch (e: Exception) {
            mapAuthException(e) to null
        }
    }

    suspend fun updatePortalUser(
        user: PortalUser,
        email: String? = null,
        password: String? = null,
        name: String? = null,
        phone: String? = null,
        services: String? = null,
        isActive: Boolean? = null
    ): Pair<AuthResult, PortalUser?> = withContext(Dispatchers.IO) {
        if (!useCloud || tokenStore.token.isNullOrBlank()) {
            return@withContext AuthResult.NetworkError to null
        }
        try {
            val updated = remote.updatePortalUser(
                user.role.lowercase(),
                user.id,
                UpdatePortalUserRequest(
                    email = email?.trim(),
                    password = password?.trim()?.ifBlank { null },
                    name = name?.trim()?.ifBlank { null },
                    phone = phone?.trim()?.ifBlank { null },
                    services = services,
                    isActive = isActive
                )
            )
            AuthResult.Success to updated.toPortalUser()
        } catch (e: Exception) {
            mapAuthException(e) to null
        }
    }

    suspend fun deletePortalUser(user: PortalUser): AuthResult = withContext(Dispatchers.IO) {
        if (!useCloud || tokenStore.token.isNullOrBlank()) {
            return@withContext AuthResult.NetworkError
        }
        try {
            remote.deletePortalUser(user.role.lowercase(), user.id)
            AuthResult.Success
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }
}
