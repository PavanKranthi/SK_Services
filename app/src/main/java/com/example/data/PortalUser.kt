package com.example.data

/** Account managed by an admin (cloud-backed). */
data class PortalUser(
    val id: Int,
    val role: String,
    val email: String,
    val name: String = "",
    val phone: String = "",
    val services: String = "",
    val isActive: Boolean = true
) {
    val roleLabel: String
        get() = when (role.lowercase()) {
            "admin" -> "Admin"
            "customer" -> "Customer"
            "technician" -> "Technician"
            else -> role
        }

    val displayTitle: String
        get() = when (role.lowercase()) {
            "admin" -> email
            "customer", "technician" -> name.ifBlank { email }
            else -> email
        }
}
