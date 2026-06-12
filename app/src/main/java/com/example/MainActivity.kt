package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.auth.FirebasePhoneAuthClient
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.remote.TokenStore
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CustomerBookingScreen
import com.example.ui.screens.TechnicianDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.AppViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "vizag_appliances_db"
        ).fallbackToDestructiveMigration().build()

        // 2. Initialize repository (Supabase-backed API when API_BASE_URL is configured)
        repository = AppRepository(database, TokenStore(applicationContext))

        // 3. Setup AppViewModel with Factory
        viewModel = ViewModelProvider(
            this,
            AppViewModelFactory(repository)
        )[AppViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val currentRole by viewModel.currentRole.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentRole,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "MainAppTransition"
                        ) { roleState ->
                            when (roleState) {
                                "Customer" -> {
                                    CustomerBookingScreen(viewModel = viewModel)
                                }
                                "Admin" -> {
                                    AdminDashboardScreen(viewModel = viewModel)
                                }
                                "Technician" -> {
                                    TechnicianDashboardScreen(viewModel = viewModel)
                                }
                                else -> {
                                    WelcomeAuthPortal(
                                        viewModel = viewModel,
                                        activity = this@MainActivity
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeAuthPortal(viewModel: AppViewModel, activity: ComponentActivity) {
    val phoneAuth = remember { FirebasePhoneAuthClient() }
    val scope = rememberCoroutineScope()
    var isSignUpMode by remember { mutableStateOf(false) }
    var selectedAuthMode by remember { mutableStateOf("Customer") }
    // form | signup_otp | reset_phone | reset_verify
    var customerAuthStep by remember { mutableStateOf("form") }
    var isSendingOtp by remember { mutableStateOf(false) }

    // Forms
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }

    var loginPhone by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var technicianEmail by remember { mutableStateOf("") }
    var technicianPassword by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val authErrorRequired = stringResource(R.string.auth_error_required)
    val authSuccessAdmin = stringResource(R.string.auth_success_admin)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("auth_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Icon
                Icon(
                    imageVector = Icons.Default.HomeRepairService,
                    contentDescription = "Logo icon representation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SK Services",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Premium Domestic Appliance Repair, Service & Dispatch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Role selection tabs for customer, admin and technician.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedAuthMode == "Customer") MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                selectedAuthMode = "Customer"
                                isSignUpMode = false
                                customerAuthStep = "form"
                                errorMessage = ""
                                successMessage = ""
                            }
                            .padding(vertical = 10.dp)
                            .testTag("portal_tab_customer"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Customer", fontWeight = FontWeight.Bold, color = if (selectedAuthMode == "Customer") Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedAuthMode == "Admin") MaterialTheme.colorScheme.secondary else Color.Transparent)
                            .clickable {
                                selectedAuthMode = "Admin"
                                isSignUpMode = false
                                errorMessage = ""
                                successMessage = ""
                            }
                            .padding(vertical = 10.dp)
                            .testTag("portal_tab_admin"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Admin", fontWeight = FontWeight.Bold, color = if (selectedAuthMode == "Admin") Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedAuthMode == "Technician") MaterialTheme.colorScheme.tertiary else Color.Transparent)
                            .clickable {
                                selectedAuthMode = "Technician"
                                isSignUpMode = false
                                errorMessage = ""
                                successMessage = ""
                            }
                            .padding(vertical = 10.dp)
                            .testTag("portal_tab_technician"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Technician", fontWeight = FontWeight.Bold, color = if (selectedAuthMode == "Technician") Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (errorMessage.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("error_banner")
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (successMessage.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (selectedAuthMode == "Admin") {
                    // ADMIN HUB
                    Text(
                        text = "Admin Credentials Required",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = adminEmail,
                        onValueChange = { adminEmail = it },
                        label = { Text("Admin Email/Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_email_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = { Text("Admin Password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                                errorMessage = authErrorRequired
                            } else {
                                viewModel.loginAdmin(adminEmail, adminPassword) { success, message ->
                                    if (success) {
                                        successMessage = authSuccessAdmin
                                        errorMessage = ""
                                    } else {
                                        errorMessage = message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_login_submit")
                    ) {
                        Text("Verify & Enter Portal", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else if (selectedAuthMode == "Technician") {
                    Text(
                        text = "Technician Credentials Required",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = technicianEmail,
                        onValueChange = { technicianEmail = it },
                        label = { Text("Technician Email/Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("technician_email_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = technicianPassword,
                        onValueChange = { technicianPassword = it },
                        label = { Text("Technician Password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("technician_password_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (technicianEmail.isBlank() || technicianPassword.isBlank()) {
                                errorMessage = "Email/Name and Password are required."
                            } else {
                                viewModel.loginTechnician(technicianEmail, technicianPassword) { success, message ->
                                    if (success) {
                                        successMessage = message
                                    } else {
                                        errorMessage = message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("technician_login_submit")
                    ) {
                        Text("Login as Technician", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else if (customerAuthStep == "signup_otp") {
                    CustomerOtpStep(
                        title = "Verify your phone",
                        subtitle = "Enter the 6-digit OTP sent to +91 $phone",
                        otpCode = otpCode,
                        onOtpChange = { otpCode = it },
                        isLoading = isSendingOtp,
                        submitLabel = "Verify & Create Account",
                        submitTag = "signup_otp_submit",
                        onSubmit = {
                            if (otpCode.length == 6) {
                                isSendingOtp = true
                                scope.launch {
                                    try {
                                        val token = if (viewModel.requiresFirebasePhoneAuth()) {
                                            phoneAuth.verifyOtpAndGetIdToken(otpCode).getOrElse {
                                                errorMessage = it.message ?: "OTP verification failed"
                                                return@launch
                                            }
                                        } else {
                                            otpCode
                                        }
                                        viewModel.verifyCustomerSignupWithFirebase(phone, token) { success, msg ->
                                            if (success) {
                                                phoneAuth.signOutFirebase()
                                                successMessage = msg
                                                errorMessage = ""
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    } finally {
                                        isSendingOtp = false
                                    }
                                }
                            } else {
                                errorMessage = "Please enter the 6-digit OTP."
                            }
                        },
                        onBack = {
                            phoneAuth.clearPendingVerification()
                            customerAuthStep = "form"
                            otpCode = ""
                            errorMessage = ""
                            successMessage = ""
                        },
                        backLabel = "Back to sign up"
                    )
                } else if (customerAuthStep == "reset_phone") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Reset password",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Registered phone number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("reset_phone_input")
                        )
                        Button(
                            onClick = {
                                if (phone.isBlank()) {
                                    errorMessage = "Enter your phone number."
                                } else {
                                    isSendingOtp = true
                                    viewModel.initiateResetPassword(phone) { success, msg ->
                                        if (!success) {
                                            isSendingOtp = false
                                            errorMessage = msg
                                            return@initiateResetPassword
                                        }
                                        successMessage = msg
                                        errorMessage = ""
                                        if (viewModel.requiresFirebasePhoneAuth()) {
                                            scope.launch {
                                                phoneAuth.sendOtp(activity, phone.filter { it.isDigit() }.takeLast(10))
                                                    .onSuccess { customerAuthStep = "reset_verify" }
                                                    .onFailure { errorMessage = it.message ?: "Could not send OTP" }
                                                isSendingOtp = false
                                            }
                                        } else {
                                            customerAuthStep = "reset_verify"
                                            isSendingOtp = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSendingOtp,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_send_otp")
                        ) {
                            Text(if (isSendingOtp) "Sending..." else "Send OTP", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customerAuthStep = "form"; errorMessage = "" }) {
                            Text("Back to sign in")
                        }
                    }
                } else if (customerAuthStep == "reset_verify") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Verify OTP & set new password",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                            label = { Text("OTP Code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("reset_otp_input")
                        )
                        OutlinedTextField(
                            value = resetNewPassword,
                            onValueChange = { resetNewPassword = it },
                            label = { Text("New password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reset_new_password_input")
                        )
                        Button(
                            onClick = {
                                if (otpCode.length != 6 || resetNewPassword.length < 6) {
                                    errorMessage = "Enter 6-digit OTP and a password (min 6 chars)."
                                    return@Button
                                }
                                isSendingOtp = true
                                scope.launch {
                                    try {
                                        val token = if (viewModel.requiresFirebasePhoneAuth()) {
                                            phoneAuth.verifyOtpAndGetIdToken(otpCode).getOrElse {
                                                errorMessage = it.message ?: "OTP verification failed"
                                                return@launch
                                            }
                                        } else {
                                            otpCode
                                        }
                                        viewModel.verifyResetPasswordWithFirebase(phone, token, resetNewPassword) { success, msg ->
                                            if (success) {
                                                phoneAuth.signOutFirebase()
                                                successMessage = msg
                                                errorMessage = ""
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    } finally {
                                        isSendingOtp = false
                                    }
                                }
                            },
                            enabled = !isSendingOtp,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_verify_submit")
                        ) {
                            Text("Update password", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customerAuthStep = "reset_phone"; otpCode = ""; resetNewPassword = "" }) {
                            Text("Back")
                        }
                    }
                } else if (isSignUpMode) {
                    // CUSTOMER SIGN UP (step 1: details + password)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_name_input")
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_phone_input")
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Set Password") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_password_input")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (name.isBlank() || phone.isBlank() || password.isBlank()) {
                                    errorMessage = "Name, phone and password are required."
                                } else {
                                    isSendingOtp = true
                                    viewModel.initiateCustomerSignup(name, phone, password) { success, msg ->
                                        if (!success) {
                                            isSendingOtp = false
                                            errorMessage = msg
                                            return@initiateCustomerSignup
                                        }
                                        successMessage = msg
                                        errorMessage = ""
                                        otpCode = ""
                                        val phone10 = phone.filter { it.isDigit() }.let {
                                            if (it.length >= 10) it.takeLast(10) else it
                                        }
                                        if (viewModel.requiresFirebasePhoneAuth()) {
                                            scope.launch {
                                                phoneAuth.sendOtp(activity, phone10)
                                                    .onSuccess { customerAuthStep = "signup_otp" }
                                                    .onFailure { errorMessage = it.message ?: "Could not send OTP" }
                                                isSendingOtp = false
                                            }
                                        } else {
                                            customerAuthStep = "signup_otp"
                                            isSendingOtp = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSendingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("signup_submit")
                        ) {
                            Text(if (isSendingOtp) "Sending OTP..." else "Send OTP & Sign Up", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { isSignUpMode = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Already a partner? Sign In here")
                        }
                    }
                } else {
                    // CUSTOMER SIGN IN (phone + password)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = loginPhone,
                            onValueChange = { loginPhone = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_phone_input")
                        )
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = { Text("Account Password") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (loginPhone.isBlank() || loginPassword.isBlank()) {
                                    errorMessage = "Please enter both phone and password."
                                } else {
                                    viewModel.loginCustomer(loginPhone, loginPassword) { success, msg ->
                                        if (success) {
                                            successMessage = msg
                                        } else {
                                            errorMessage = msg
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_submit")
                        ) {
                            Text("Verify & Sign In", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { isSignUpMode = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("portal_toggle_signup")
                        ) {
                            Text(stringResource(R.string.auth_signup_prompt))
                        }
                        TextButton(
                            onClick = {
                                phone = loginPhone
                                customerAuthStep = "reset_phone"
                                errorMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("login_forgot_password")
                        ) {
                            Text("Forgot password?")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerOtpStep(
    title: String,
    subtitle: String,
    otpCode: String,
    onOtpChange: (String) -> Unit,
    isLoading: Boolean,
    submitLabel: String,
    submitTag: String,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    backLabel: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = otpCode,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onOtpChange(it) },
            label = { Text("OTP Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("signup_otp_input")
        )
        Button(
            onClick = onSubmit,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag(submitTag)
        ) {
            Text(if (isLoading) "Verifying..." else submitLabel, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(backLabel)
        }
    }
}
