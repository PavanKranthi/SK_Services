package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class RemoteApiClient(tokenStore: TokenStore) {
    val enabled: Boolean = BuildConfig.API_BASE_URL.isNotBlank() &&
        !BuildConfig.API_BASE_URL.contains("DISABLED", ignoreCase = true)

    private val api: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    suspend fun loginCustomer(phone: String, password: String) =
        api.loginCustomer(PhoneLoginRequest(phone, password))

    suspend fun initCustomerRegister(name: String, phone: String, password: String) =
        api.initCustomerRegister(RegisterInitRequest(name, phone, password))

    suspend fun verifyCustomerRegister(phone: String, firebaseIdToken: String) =
        api.verifyCustomerRegister(RegisterVerifyRequest(phone, firebaseIdToken))

    suspend fun initResetPassword(phone: String) =
        api.initResetPassword(ResetPasswordInitRequest(phone))

    suspend fun verifyResetPassword(phone: String, firebaseIdToken: String, newPassword: String) =
        api.verifyResetPassword(ResetPasswordVerifyRequest(phone, firebaseIdToken, newPassword))

    suspend fun changeCustomerPassword(currentPassword: String, newPassword: String) =
        api.changeCustomerPassword(ChangePasswordRequest(currentPassword, newPassword))

    suspend fun loginAdmin(email: String, password: String) =
        api.loginAdmin(LoginRequest(email, password))

    suspend fun loginTechnician(email: String, password: String) =
        api.loginTechnician(LoginRequest(email, password))

    suspend fun getBookings() = api.getBookings()
    suspend fun createBooking(body: CreateBookingRequest) = api.createBooking(body)
    suspend fun patchBooking(id: Int, body: PatchBookingRequest) = api.patchBooking(id, body)
    suspend fun getActiveBookingsForDate(date: String) = api.getActiveBookingsForDate(date)

    suspend fun getServices() = api.getServices()
    suspend fun createService(body: CreateServiceRequest) = api.createService(body)
    suspend fun patchService(id: Int, body: PatchServiceRequest) = api.patchService(id, body)
    suspend fun deleteService(id: Int) = api.deleteService(id)

    suspend fun getTechnicians() = api.getTechnicians()
    suspend fun patchTechnicianServices(id: Int, services: String) =
        api.patchTechnicianServices(id, PatchTechnicianServicesRequest(services))

    suspend fun getTechnicianDashboard() = api.getTechnicianDashboard()

    suspend fun getAddresses() = api.getAddresses()
    suspend fun createAddress(body: CreateAddressRequest) = api.createAddress(body)
    suspend fun setDefaultAddress(id: Int) = api.setDefaultAddress(id)
    suspend fun deleteAddress(id: Int) = api.deleteAddress(id)

    suspend fun getPortalUsers() = api.getPortalUsers()
    suspend fun createPortalUser(body: CreatePortalUserRequest) = api.createPortalUser(body)
    suspend fun updatePortalUser(role: String, id: Int, body: UpdatePortalUserRequest) =
        api.updatePortalUser(role, id, body)
    suspend fun deletePortalUser(role: String, id: Int) = api.deletePortalUser(role, id)
}
