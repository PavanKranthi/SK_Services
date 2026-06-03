package com.example.data.remote

import com.example.data.ApplianceService
import com.example.data.Booking
import com.example.data.Technician
import com.example.data.UserAccount
import com.example.data.UserAddress
import com.squareup.moshi.Json

data class AuthResponse(
    val token: String,
    val user: CustomerDto? = null,
    val admin: AdminDto? = null,
    val technician: TechnicianDto? = null
)

data class CustomerDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String
)

data class AdminDto(val id: Int, val email: String)

data class TechnicianDto(
    val id: Int,
    val name: String,
    val email: String,
    val services: String = "",
    val status: String = "Active",
    val role: String = "Technician"
)

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

data class BookingDto(
    val id: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val serviceId: Int,
    val serviceName: String,
    val serviceType: String,
    val pricePerUnit: Double,
    val unitsCount: Int,
    val date: String,
    val timeSlot: String,
    val status: String,
    val assignedTechnicianId: Int? = null,
    val assignedTechnicianName: String? = null,
    val serviceAddress: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ServiceDto(
    val id: Int = 0,
    val name: String,
    val type: String,
    val price: Double,
    @Json(name = "isActive") val isActive: Boolean = true
)

data class AddressDto(
    val id: Int = 0,
    val userPhone: String,
    val addressLine1: String,
    val addressLine2: String = "",
    val city: String,
    val district: String,
    val state: String,
    val country: String = "India",
    val pinCode: String,
    @Json(name = "isDefault") val isDefault: Boolean = false
)

data class CreateBookingRequest(
    val serviceId: Int,
    val serviceName: String,
    val serviceType: String,
    val pricePerUnit: Double,
    val unitsCount: Int,
    val date: String,
    val timeSlot: String,
    val serviceAddress: String,
    val customerName: String,
    val customerPhone: String
)

data class PatchBookingRequest(
    val status: String? = null,
    val assignedTechnicianId: Int? = null,
    val assignedTechnicianName: String? = null
)

data class PatchServiceRequest(
    @Json(name = "isActive") val isActive: Boolean? = null,
    val name: String? = null,
    val type: String? = null,
    val price: Double? = null
)

data class CreateServiceRequest(
    val name: String,
    val type: String,
    val price: Double,
    @Json(name = "isActive") val isActive: Boolean = true
)

data class PatchTechnicianServicesRequest(val services: String)

data class CreateAddressRequest(
    val addressLine1: String,
    val addressLine2: String = "",
    val city: String,
    val district: String,
    val state: String,
    val country: String = "India",
    val pinCode: String,
    @Json(name = "isDefault") val isDefault: Boolean = false
)

data class TechnicianDashboardResponse(
    val technician: TechnicianDto,
    val completedCount: Int,
    val completedValue: Double,
    val upcomingBookings: List<BookingDto>
)

fun CustomerDto.toUserAccount() = UserAccount(id = id, name = name, email = email, phone = phone, password = "")

fun TechnicianDto.toTechnician() = Technician(
    id = id,
    name = name,
    email = email,
    password = "",
    services = services,
    role = role,
    status = status
)

fun BookingDto.toBooking() = Booking(
    id = id,
    customerName = customerName,
    customerPhone = customerPhone,
    serviceId = serviceId,
    serviceName = serviceName,
    serviceType = serviceType,
    pricePerUnit = pricePerUnit,
    unitsCount = unitsCount,
    date = date,
    timeSlot = timeSlot,
    status = status,
    assignedTechnicianId = assignedTechnicianId,
    assignedTechnicianName = assignedTechnicianName,
    serviceAddress = serviceAddress,
    createdAt = createdAt
)

fun ServiceDto.toService() = ApplianceService(id = id, name = name, type = type, price = price, isActive = isActive)

fun AddressDto.toAddress() = UserAddress(
    id = id,
    userPhone = userPhone,
    addressLine1 = addressLine1,
    addressLine2 = addressLine2,
    city = city,
    district = district,
    state = state,
    country = country,
    pinCode = pinCode,
    isDefault = isDefault
)
