package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/customer/login")
    suspend fun loginCustomer(@Body body: LoginRequest): AuthResponse

    @POST("auth/customer/register")
    suspend fun registerCustomer(@Body body: RegisterRequest): AuthResponse

    @POST("auth/admin/login")
    suspend fun loginAdmin(@Body body: LoginRequest): AuthResponse

    @POST("auth/technician/login")
    suspend fun loginTechnician(@Body body: LoginRequest): AuthResponse

    @GET("bookings")
    suspend fun getBookings(): List<BookingDto>

    @POST("bookings")
    suspend fun createBooking(@Body body: CreateBookingRequest): BookingDto

    @PATCH("bookings/{id}")
    suspend fun patchBooking(@Path("id") id: Int, @Body body: PatchBookingRequest): BookingDto

    @GET("bookings/active-for-date")
    suspend fun getActiveBookingsForDate(@Query("date") date: String): List<BookingDto>

    @GET("services")
    suspend fun getServices(): List<ServiceDto>

    @POST("services")
    suspend fun createService(@Body body: CreateServiceRequest): ServiceDto

    @PATCH("services/{id}")
    suspend fun patchService(@Path("id") id: Int, @Body body: PatchServiceRequest): ServiceDto

    @DELETE("services/{id}")
    suspend fun deleteService(@Path("id") id: Int)

    @GET("technicians")
    suspend fun getTechnicians(): List<TechnicianDto>

    @PATCH("technicians/{id}/services")
    suspend fun patchTechnicianServices(
        @Path("id") id: Int,
        @Body body: PatchTechnicianServicesRequest
    ): TechnicianDto

    @GET("technicians/me/dashboard")
    suspend fun getTechnicianDashboard(): TechnicianDashboardResponse

    @GET("addresses")
    suspend fun getAddresses(): List<AddressDto>

    @POST("addresses")
    suspend fun createAddress(@Body body: CreateAddressRequest): AddressDto

    @PATCH("addresses/{id}/default")
    suspend fun setDefaultAddress(@Path("id") id: Int)

    @DELETE("addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: Int)
}
