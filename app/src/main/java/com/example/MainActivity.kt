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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                                    WelcomeAuthPortal(viewModel = viewModel)
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
fun WelcomeAuthPortal(viewModel: AppViewModel) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var selectedAuthMode by remember { mutableStateOf("Customer") }

    // Forms
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var loginEmail by remember { mutableStateOf("pavan@gmail.com") } // Pre-populated for quick tester sign-in
    var loginPassword by remember { mutableStateOf("Password123") } // Pre-populated password corresponding to seed account

    var adminEmail by remember { mutableStateOf("admin@gmail.com") }
    var adminPassword by remember { mutableStateOf("admin") }
    var technicianEmail by remember { mutableStateOf("ravi@satyamservices.com") }
    var technicianPassword by remember { mutableStateOf("tech123") }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

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
                                errorMessage = stringResource(R.string.auth_error_required)
                            } else {
                                viewModel.loginAdmin(adminEmail, adminPassword) { success, message ->
                                    if (success) {
                                        successMessage = stringResource(R.string.auth_success_admin)
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
                } else {
                    if (isSignUpMode) {
                        // CUSTOMER CHOOSE SIGN UP
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
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_email_input")
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                singleLine = true,
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
                                    if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
                                        errorMessage = "All registration fields are required."
                                    } else {
                                        viewModel.registerCustomer(name, email, phone, password) { success, msg ->
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
                                    .testTag("signup_submit")
                            ) {
                                Text("Complete Sign Up", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = { isSignUpMode = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Already a partner? Sign In here")
                            }
                        }
                    } else {
                        // CUSTOMER STANDARD LOGIN
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = loginEmail,
                                onValueChange = { loginEmail = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input")
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
                                    if (loginEmail.isBlank() || loginPassword.isBlank()) {
                                        errorMessage = "Please enter both Email and Password"
                                    } else {
                                        viewModel.loginCustomer(loginEmail, loginPassword) { success, msg ->
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
                                Text("Create modern account and address setup")
                            }
                        }
                    }
                }
            }
        }
    }
}
