package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class SkServicesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (_: Exception) {
            // google-services.json not configured yet
        }
    }
}
