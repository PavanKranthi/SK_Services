package com.example.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Sends OTP via Firebase Phone Auth and returns an ID token for backend verification.
 * Firebase session is cleared after the backend accepts the token (we use our own JWT).
 */
class FirebasePhoneAuthClient {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var verificationId: String? = null

    fun toE164IndianPhone(phone10: String): String = "+91$phone10"

    suspend fun sendOtp(activity: Activity, phone10: String): Result<Unit> {
        val e164 = toE164IndianPhone(phone10)
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-retrieval on some devices; store credential path via immediate verify if needed.
                    verificationId = null
                    if (cont.isActive) cont.resume(Result.success(Unit))
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = id
                    if (cont.isActive) cont.resume(Result.success(Unit))
                }
            }
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    suspend fun verifyOtpAndGetIdToken(otp: String): Result<String> {
        val id = verificationId
            ?: return Result.failure(IllegalStateException("OTP was not requested yet"))
        val credential = PhoneAuthProvider.getCredential(id, otp)
        return signInAndGetIdToken(credential)
    }

    private suspend fun signInAndGetIdToken(credential: PhoneAuthCredential): Result<String> =
        suspendCancellableCoroutine { cont ->
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    val user = auth.currentUser
                    if (user == null) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(IllegalStateException("Firebase user missing")))
                        }
                        return@addOnSuccessListener
                    }
                    user.getIdToken(false)
                        .addOnSuccessListener { tokenResult ->
                            val token = tokenResult.token
                            if (token.isNullOrBlank()) {
                                if (cont.isActive) {
                                    cont.resume(Result.failure(IllegalStateException("Empty Firebase ID token")))
                                }
                            } else if (cont.isActive) {
                                cont.resume(Result.success(token))
                            }
                        }
                        .addOnFailureListener { e ->
                            if (cont.isActive) cont.resume(Result.failure(e))
                        }
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
        }

    fun signOutFirebase() {
        auth.signOut()
        verificationId = null
    }

    fun clearPendingVerification() {
        verificationId = null
    }
}
