package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val repository: AppRepository) : ViewModel() {

    init {
        // Automatically check and seed database with default services/technicians
        viewModelScope.launch {
            repository.checkAndSeedDatabase()
        }
    }

    // Auth & Identity Tracking
    private val _loggedInUser = MutableStateFlow<UserAccount?>(null)
    val loggedInUser: StateFlow<UserAccount?> = _loggedInUser.asStateFlow()

    private val _isAdminLoggedIn = MutableStateFlow(false)
    val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn.asStateFlow()

    private val _loggedInTechnician = MutableStateFlow<Technician?>(null)
    val loggedInTechnician: StateFlow<Technician?> = _loggedInTechnician.asStateFlow()

    // Active Role state ("Guest", "Customer", "Admin" or "Technician")
    private val _currentRole = MutableStateFlow("Guest")
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    // 1. CUSTOMER IDENTITY & BOOKING STATE (compatible with existing code)
    private val _customerName = MutableStateFlow("Pavan Kranthi")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("9876543210")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    fun updateCustomerDetails(name: String, phone: String) {
        viewModelScope.launch {
            val curUser = _loggedInUser.value
            if (curUser != null) {
                val updated = curUser.copy(name = name, phone = phone)
                repository.updateUser(updated)
                _loggedInUser.value = updated
                _customerName.value = name
                _customerPhone.value = phone
            }
        }
    }

    // Dynamic Flow of addresses for the logged-in user
    val userAddresses: Flow<List<UserAddress>> = _customerPhone.flatMapLatest { phone ->
        if (phone.isNotBlank()) {
            repository.getAddressesForPhone(phone)
        } else {
            flowOf(emptyList())
        }
    }

    // Selected address for the booking session
    private val _bookingAddress = MutableStateFlow<UserAddress?>(null)
    val bookingAddress: StateFlow<UserAddress?> = _bookingAddress.asStateFlow()

    fun selectBookingAddress(address: UserAddress) {
        _bookingAddress.value = address
    }

    // Authentication Actions
    fun loginCustomer(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (result, user) = repository.loginUser(email, password)
            when (result) {
                AuthResult.Success -> if (user != null) {
                    _loggedInUser.value = user
                    _customerName.value = user.name
                    _customerPhone.value = user.phone
                    _currentRole.value = "Customer"
                    _isAdminLoggedIn.value = false
                    repository.getAddressesForPhone(user.phone).firstOrNull()?.find { it.isDefault }?.let {
                        _bookingAddress.value = it
                    }
                    onResult(true, "Successfully logged in as ${user.name}")
                } else {
                    onResult(false, authErrorMessage(AuthResult.NetworkError))
                }
                AuthResult.InvalidCredentials -> onResult(false, authErrorMessage(AuthResult.InvalidCredentials))
                AuthResult.NetworkError -> onResult(false, authErrorMessage(AuthResult.NetworkError))
            }
        }
    }

    fun registerCustomer(name: String, email: String, phone: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getUserByPhone(phone)
            if (existing != null) {
                onResult(false, "Phone number is already associated with an account.")
                return@launch
            }
            val newUser = UserAccount(name = name, email = email, phone = phone, password = password)
            val id = repository.signUpUser(newUser)
            if (id > 0) {
                _loggedInUser.value = newUser
                _customerName.value = name
                _customerPhone.value = phone
                _currentRole.value = "Customer"
                _isAdminLoggedIn.value = false
                
                // Creation and seeding of default home address
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
                repository.insertAddress(seedAddr)
                _bookingAddress.value = seedAddr
                onResult(true, "Register successful for $name!")
            } else {
                onResult(false, "Sign up failed, database write error.")
            }
        }
    }

    fun loginAdmin(email: String, codeOrPassword: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (repository.loginAdmin(email.trim(), codeOrPassword.trim())) {
                AuthResult.Success -> {
                    _isAdminLoggedIn.value = true
                    _loggedInUser.value = null
                    _loggedInTechnician.value = null
                    _currentRole.value = "Admin"
                    onResult(true, "")
                }
                AuthResult.InvalidCredentials -> onResult(false, authErrorMessage(AuthResult.InvalidCredentials))
                AuthResult.NetworkError -> onResult(false, authErrorMessage(AuthResult.NetworkError))
            }
        }
    }

    fun loginTechnician(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (result, technician) = repository.loginTechnician(email.trim(), password.trim())
            when (result) {
                AuthResult.Success -> if (technician != null) {
                    _loggedInTechnician.value = technician
                    _loggedInUser.value = null
                    _isAdminLoggedIn.value = false
                    _currentRole.value = "Technician"
                    onResult(true, "Welcome ${technician.name}")
                } else {
                    onResult(false, authErrorMessage(AuthResult.NetworkError))
                }
                AuthResult.InvalidCredentials -> onResult(false, authErrorMessage(AuthResult.InvalidCredentials))
                AuthResult.NetworkError -> onResult(false, authErrorMessage(AuthResult.NetworkError))
            }
        }
    }

    private fun authErrorMessage(result: AuthResult): String = when (result) {
        AuthResult.InvalidCredentials -> "Invalid email or password. Please try again."
        AuthResult.NetworkError -> "Unable to reach the server. Check your internet and try again."
        AuthResult.Success -> ""
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
        }
        _loggedInUser.value = null
        _isAdminLoggedIn.value = false
        _loggedInTechnician.value = null
        _currentRole.value = "Guest"
        _bookingAddress.value = null
    }

    fun changePassword(newPass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val user = _loggedInUser.value
            if (user != null) {
                val updated = user.copy(password = newPass)
                repository.updateUser(updated)
                _loggedInUser.value = updated
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val user = _loggedInUser.value
            if (user != null) {
                repository.deleteUser(user)
                logout()
            }
        }
    }

    // Address Actions
    fun addAddress(
        addressLine1: String,
        addressLine2: String,
        city: String,
        district: String,
        state: String,
        country: String,
        pinCode: String,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            val phone = _customerPhone.value
            if (phone.isNotBlank()) {
                val newAddr = UserAddress(
                    userPhone = phone,
                    addressLine1 = addressLine1,
                    addressLine2 = addressLine2,
                    city = city,
                    district = district,
                    state = state,
                    country = country,
                    pinCode = pinCode,
                    isDefault = isDefault
                )
                repository.insertAddress(newAddr)
                // If it is the only address or default, set as active booking address
                if (isDefault || _bookingAddress.value == null) {
                    _bookingAddress.value = newAddr
                }
            }
        }
    }

    fun setDefaultAddress(addressId: Int) {
        viewModelScope.launch {
            val phone = _customerPhone.value
            if (phone.isNotBlank()) {
                repository.setDefaultAddress(addressId, phone)
                // Force reload/refresh active address
                repository.getAddressesForPhone(phone).firstOrNull()?.find { it.id == addressId }?.let {
                    _bookingAddress.value = it
                }
            }
        }
    }

    fun deleteAddress(address: UserAddress) {
        viewModelScope.launch {
            repository.deleteAddress(address)
            val phone = _customerPhone.value
            if (_bookingAddress.value?.id == address.id) {
                // Pick another or default if deleted
                val remaining = repository.getAddressesForPhone(phone).firstOrNull()
                _bookingAddress.value = remaining?.firstOrNull()
            }
        }
    }

    // Selected appliance category (AC, Fridge, Washing Machine, TV)
    private val _selectedCategory = MutableStateFlow("AC")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        // Reset selected service of previous category
        _selectedService.value = null
    }

    // Active services filtered by selectedCategory
    val activeServicesForCategory: Flow<List<ApplianceService>> = combine(
        repository.activeServices,
        _selectedCategory
    ) { services, category ->
        services.filter { it.name == category }
    }

    // Selected service for the current booking
    private val _selectedService = MutableStateFlow<ApplianceService?>(null)
    val selectedService: StateFlow<ApplianceService?> = _selectedService.asStateFlow()

    fun selectService(service: ApplianceService) {
        _selectedService.value = service
        checkOverlaps()
    }

    // Booking specifications
    private val _unitsCount = MutableStateFlow(1)
    val unitsCount: StateFlow<Int> = _unitsCount.asStateFlow()

    fun incrementUnits() { _unitsCount.value += 1 }
    fun decrementUnits() { if (_unitsCount.value > 1) _unitsCount.value -= 1 }

    private val _selectedDate = MutableStateFlow("2026-06-03")
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
        checkOverlaps()
    }

    private val _selectedTimeSlot = MutableStateFlow("09:00 AM - 12:00 PM")
    val selectedTimeSlot: StateFlow<String> = _selectedTimeSlot.asStateFlow()

    fun selectTimeSlot(slot: String) {
        _selectedTimeSlot.value = slot
        checkOverlaps()
    }

    // Overlap and schedule warnings state
    private val _hasScheduleConflict = MutableStateFlow(false)
    val hasScheduleConflict: StateFlow<Boolean> = _hasScheduleConflict.asStateFlow()

    private val _bookedSlotsCount = MutableStateFlow(0)
    val bookedSlotsCount: StateFlow<Int> = _bookedSlotsCount.asStateFlow()

    // Real-time booking check
    private fun checkOverlaps() {
        viewModelScope.launch {
            val bookingsOnDate = repository.getActiveBookingsForDate(_selectedDate.value)
            val slotBookingsCount = bookingsOnDate.count { it.timeSlot == _selectedTimeSlot.value }
            _bookedSlotsCount.value = slotBookingsCount
            // Consider slot high-demand if there are already 2 or more confirmed bookings for that slot.
            _hasScheduleConflict.value = slotBookingsCount >= 2
        }
    }

    // Submit request status Dialog/Success state
    private val _isBookingSubmitted = MutableStateFlow(false)
    val isBookingSubmitted: StateFlow<Boolean> = _isBookingSubmitted.asStateFlow()

    private val _lastSubmittedBooking = MutableStateFlow<Booking?>(null)
    val lastSubmittedBooking: StateFlow<Booking?> = _lastSubmittedBooking.asStateFlow()

    fun submitBooking() {
        val service = _selectedService.value ?: return
        val finalAddr = _bookingAddress.value?.fullAddressDisplay ?: "Vizag Primary Location"
        val booking = Booking(
            customerName = _customerName.value,
            customerPhone = _customerPhone.value,
            serviceId = service.id,
            serviceName = service.name,
            serviceType = service.type,
            pricePerUnit = service.price,
            unitsCount = _unitsCount.value,
            date = _selectedDate.value,
            timeSlot = _selectedTimeSlot.value,
            status = "Pending", // ALWAYS default initially
            serviceAddress = finalAddr
        )

        viewModelScope.launch {
            val id = repository.insertBooking(booking)
            _lastSubmittedBooking.value = booking.copy(id = id.toInt())
            _isBookingSubmitted.value = true
        }
    }

    fun dismissSuccessNotification() {
        _isBookingSubmitted.value = false
    }

    // Customer's specific bookings for tracking
    val customerBookings: Flow<List<Booking>> = _customerPhone.flatMapLatest { phone ->
        repository.getBookingsByPhone(phone)
    }

    // Technician's specific bookings for dashboard and profile insights.
    val technicianBookings: Flow<List<Booking>> = _loggedInTechnician.flatMapLatest { technician ->
        if (technician != null) {
            repository.getBookingsByTechnician(technician.id)
        } else {
            flowOf(emptyList())
        }
    }

    // Technician dashboard metrics.
    val technicianCompletedBookingsCount: Flow<Int> = technicianBookings.map { bookings ->
        bookings.count { it.status == "Completed" }
    }

    val technicianCompletedBookingsValue: Flow<Double> = technicianBookings.map { bookings ->
        bookings
            .filter { it.status == "Completed" }
            .sumOf { it.pricePerUnit * it.unitsCount }
    }

    // Upcoming bookings sorted nearest first by date and then by known time-slot ordering.
    val technicianUpcomingBookings: Flow<List<Booking>> = technicianBookings.map { bookings ->
        val timeSlotOrder = mapOf(
            "09:00 AM - 12:00 PM" to 0,
            "12:00 PM - 03:00 PM" to 1,
            "03:00 PM - 06:00 PM" to 2,
            "06:00 PM - 09:00 PM" to 3
        )
        bookings
            .filter { it.status == "Confirmed" || it.status == "In Progress" || it.status == "Pending" }
            .sortedWith(
                compareBy<Booking> { it.date }
                    .thenBy { timeSlotOrder[it.timeSlot] ?: Int.MAX_VALUE }
                    .thenBy { it.createdAt }
            )
    }

    // 2. ADMIN CONFIG & TEAM MANAGEMENT STATE
    val allServices: Flow<List<ApplianceService>> = repository.allServices
    val allBookings: Flow<List<Booking>> = repository.allBookings
    val technicians: Flow<List<Technician>> = repository.allTechnicians

    // Form fields for adding new service
    private val _newServiceName = MutableStateFlow("AC")
    val newServiceName: StateFlow<String> = _newServiceName.asStateFlow()

    private val _newServiceType = MutableStateFlow("")
    val newServiceType: StateFlow<String> = _newServiceType.asStateFlow()

    private val _newServicePrice = MutableStateFlow("")
    val newServicePrice: StateFlow<String> = _newServicePrice.asStateFlow()

    fun updateNewServiceForm(name: String, type: String, price: String) {
        _newServiceName.value = name
        _newServiceType.value = type
        _newServicePrice.value = price
    }

    fun addService() {
        val type = _newServiceType.value.trim()
        val priceStr = _newServicePrice.value.trim()
        if (type.isNotEmpty() && priceStr.isNotEmpty()) {
            val price = priceStr.toDoubleOrNull() ?: 500.0
            val service = ApplianceService(
                name = _newServiceName.value,
                type = type,
                price = price,
                isActive = true
            )
            viewModelScope.launch {
                repository.insertService(service)
                _newServiceType.value = ""
                _newServicePrice.value = ""
            }
        }
    }

    fun toggleServiceActivation(service: ApplianceService) {
        viewModelScope.launch {
            repository.toggleService(service.id, !service.isActive)
        }
    }

    fun deleteService(service: ApplianceService) {
        viewModelScope.launch {
            repository.deleteService(service)
        }
    }

    // Booking operation from Admin Side
    fun acceptAndAssignTechnician(bookingId: Int, technician: Technician) {
        viewModelScope.launch {
            repository.assignTechnician(bookingId, technician.id, technician.name)
        }
    }

    fun updateTechnicianServices(technician: Technician, services: String) {
        viewModelScope.launch {
            val normalized = services
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(",")
            repository.updateTechnician(technician.copy(services = normalized))

            // Keep currently logged-in technician profile in sync if admin updates this account.
            if (_loggedInTechnician.value?.id == technician.id) {
                _loggedInTechnician.value = technician.copy(services = normalized)
            }
        }
    }

    fun declineBooking(bookingId: Int) {
        viewModelScope.launch {
            repository.declineBooking(bookingId)
        }
    }

    fun updateBookingProgress(bookingId: Int, currentStatus: String) {
        val nextStatus = when (currentStatus) {
            "Confirmed" -> "In Progress"
            "In Progress" -> "Completed"
            else -> "Completed"
        }
        viewModelScope.launch {
            repository.updateStatus(bookingId, nextStatus)
        }
    }
}

// Factory to inject repository dependency
class AppViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
