package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApplianceService
import com.example.data.Booking
import com.example.data.UserAddress
import com.example.data.UserAccount
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerBookingScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val currentRole by viewModel.currentRole.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    
    val customerName by viewModel.customerName.collectAsState()
    val customerPhone by viewModel.customerPhone.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val activeServices by viewModel.activeServicesForCategory.collectAsState(initial = emptyList())
    val selectedService by viewModel.selectedService.collectAsState()
    val unitsCount by viewModel.unitsCount.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedTimeSlot by viewModel.selectedTimeSlot.collectAsState()
    val hasConflict by viewModel.hasScheduleConflict.collectAsState()
    val bookedCount by viewModel.bookedSlotsCount.collectAsState()
    val isBookingSubmitted by viewModel.isBookingSubmitted.collectAsState()
    val lastSubmitted by viewModel.lastSubmittedBooking.collectAsState()
    
    val customerBookings by viewModel.customerBookings.collectAsState(initial = emptyList())
    val savedAddresses by viewModel.userAddresses.collectAsState(initial = emptyList())
    val selectedBookingAddress by viewModel.bookingAddress.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Book Service, 1: Booking History

    // Profile Settings UI state
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf("Profile") } // "Profile" or "Addresses"

    // Profile fields state
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }
    var showForgotPasswordPrompt by remember { mutableStateOf(false) }

    // Address fields state
    var isAddingAddress by remember { mutableStateOf(false) }
    var addressLine1 by remember { mutableStateOf("") }
    var addressLine2 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var pinCode by remember { mutableStateOf("") }
    var setAddressDefault by remember { mutableStateOf(false) }

    // Show booking addresses options state
    var showAddressSelectionDropdown by remember { mutableStateOf(false) }

    // Init fields from logged-in user when dialog opens
    LaunchedEffect(showSettingsDialog, loggedInUser) {
        loggedInUser?.let {
            editName = it.name
            editPhone = it.phone
            editEmail = it.email
            editPassword = it.password
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. HEADER SECTION (Identity + Profile Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SK Services",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hello, ${loggedInUser?.name ?: "Valued User"}!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Profile menu anchor
                Box {
                    IconButton(
                        onClick = { showProfileMenu = !showProfileMenu },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(40.dp)
                            .testTag("customer_profile_icon")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Profile Options", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showProfileMenu,
                        onDismissRequest = { showProfileMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings", fontWeight = FontWeight.Bold) },
                            onClick = {
                                showProfileMenu = false
                                settingsTab = "Profile"
                                isAddingAddress = false
                                showSettingsDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            modifier = Modifier.testTag("menu_settings")
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                            onClick = {
                                showProfileMenu = false
                                viewModel.logout()
                            },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.testTag("menu_logout")
                        )
                    }
                }
            }

            // 2. CLIENT TABS (Book Service, Booking History)
            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Book Service", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                    modifier = Modifier.testTag("tab_book_service")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Booking History", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.testTag("tab_booking_history")
                )
            }

            // 3. MAIN COMPOSABLE SLIDES
            Box(modifier = Modifier.weight(1f)) {
                if (activeTab == 0) {
                    // BOOK SERVICE TAB
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                    ) {
                        // A. Appliance selection
                        item {
                            Column {
                                Text(
                                    text = "Select Appliance",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val categories = listOf(
                                        "AC" to Icons.Default.Air,
                                        "Fridge" to Icons.Default.Kitchen,
                                        "Washing Machine" to Icons.Default.LocalLaundryService,
                                        "TV" to Icons.Default.Tv
                                    )

                                    categories.forEach { (catName, icon) ->
                                        val isSelected = selectedCategory == catName
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.selectCategory(catName) }
                                                .testTag("cat_$catName"),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = catName,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = if (catName == "Washing Machine") "Washer" else catName,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // B. Service Type options
                        item {
                            Column {
                                Text(
                                    text = "Select Service Type for $selectedCategory",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (activeServices.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text(
                                            text = "Currently, no active services are configured for $selectedCategory. Activate/Add in Admin mode to populate.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        activeServices.forEach { service ->
                                            val isSelected = selectedService?.id == service.id
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.selectService(service) }
                                                    .testTag("service_item_${service.id}"),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                                ),
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = service.type,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Certified appliance technician dispatch",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "₹${service.price.toInt()}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 18.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // C. Schedule, date & time slots
                        item {
                            Column {
                                Text(
                                    text = "Select Schedule Date & Time Slot",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Horizon scroll 7 days
                                val dateList = remember {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                                    val dateNumFormat = SimpleDateFormat("dd", Locale.getDefault())
                                    List(7) { offset ->
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, offset)
                                        Triple(sdf.format(cal.time), dayFormat.format(cal.time), dateNumFormat.format(cal.time))
                                    }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(dateList) { (rawDate, dayOfWeek, dayNum) ->
                                        val isSelected = selectedDate == rawDate
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.selectDate(rawDate) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                                .testTag("date_chip_$rawDate"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = dayOfWeek.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = dayNum,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val timeslots = listOf(
                                    "09:00 AM - 12:00 PM" to "Morning",
                                    "12:00 PM - 03:00 PM" to "Afternoon",
                                    "03:00 PM - 06:00 PM" to "Evening",
                                    "06:00 PM - 09:00 PM" to "Late Evening"
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    timeslots.forEach { (slotCode, slotLabel) ->
                                        val isSelected = selectedTimeSlot == slotCode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { viewModel.selectTimeSlot(slotCode) }
                                                .padding(12.dp)
                                                .testTag("time_slot_$slotLabel"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = slotLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = slotCode,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasConflict) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                                        border = BorderStroke(1.dp, Color(0xFFFFC107))
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Popular slot with conflict ($bookedCount bookings active)", fontWeight = FontWeight.Bold, color = Color(0xFF856404), style = MaterialTheme.typography.bodySmall)
                                                Text("This is in high request in Vizag. Your booking is prioritized, dependent on team member dispatch.", color = Color(0xFF856404), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // D. ADDRESS SERVICE SELECTION (CHOOSE DEFAULT OR PICK/ADD Saved Addresses)
                        item {
                            Column {
                                Text(
                                    text = "Select Service Address",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "We will dispatch the professional to this location:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (selectedBookingAddress != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("card_chosen_address"),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "Service Target Address",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (selectedBookingAddress?.isDefault == true) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Default",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = selectedBookingAddress?.fullAddressDisplay ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(
                                            text = "No address selected! Please tap Settings -> Address in the profile top-right corner to add or register a service address.",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick selector for multiple addresses if they exist
                                if (savedAddresses.size > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { showAddressSelectionDropdown = !showAddressSelectionDropdown },
                                            modifier = Modifier.testTag("btn_switch_address")
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change Booking Address (${savedAddresses.size} saved)")
                                        }
                                    }

                                    if (showAddressSelectionDropdown) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("Select location for this job:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(8.dp))
                                                savedAddresses.forEach { addr ->
                                                    val isThisSelected = selectedBookingAddress?.id == addr.id
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.selectBookingAddress(addr)
                                                                showAddressSelectionDropdown = false
                                                            }
                                                            .background(if (isThisSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isThisSelected,
                                                            onClick = {
                                                                viewModel.selectBookingAddress(addr)
                                                                showAddressSelectionDropdown = false
                                                            }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = addr.fullAddressDisplay,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick button to launch settings addresses
                                TextButton(
                                    onClick = {
                                        settingsTab = "Addresses"
                                        isAddingAddress = true
                                        showSettingsDialog = true
                                        addressLine1 = ""
                                        addressLine2 = ""
                                        city = "Visakhapatnam"
                                        district = "Visakhapatnam"
                                        state = "Andhra Pradesh"
                                        pinCode = ""
                                        setAddressDefault = savedAddresses.isEmpty()
                                    },
                                    modifier = Modifier.testTag("btn_quick_add_address")
                                ) {
                                    Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Another/New Address")
                                }
                            }
                        }

                        // E. Quantity Selector
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Number of Appliances",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Same appliance models count",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.decrementUnits() },
                                            modifier = Modifier
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                .size(36.dp)
                                                .testTag("btn_decrement")
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = unitsCount.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(24.dp).testTag("units_count_text"),
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = { viewModel.incrementUnits() },
                                            modifier = Modifier
                                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                .size(36.dp)
                                                .testTag("btn_increment")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increment", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // F. Submission summary & action
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Service Job Summary", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (selectedService == null) {
                                        Text(
                                            text = "Please select a service type to generate summary.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        val priceSum = (selectedService?.price ?: 0.0) * unitsCount
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${selectedCategory} (${selectedService?.type}) x $unitsCount units",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "₹${priceSum.toInt()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Date & Slot:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$selectedDate | $selectedTimeSlot", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Address Target:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = selectedBookingAddress?.city ?: "No location!",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedBookingAddress == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = { viewModel.submitBooking() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_submit_booking"),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = selectedBookingAddress != null
                                        ) {
                                            Icon(Icons.Default.Book, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Request Appointment Booking", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // BOOKING HISTORY / LIVE TRACKER TAB
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                    ) {
                        item {
                            Text(
                                text = "My Booking Live Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (customerBookings.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No booking history found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Enter a request in 'Book Service' tab to initiate dynamic dispatch tracker.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(customerBookings) { booking ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("booking_tracker_card_${booking.id}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Job #${booking.id} - ${booking.serviceName}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = "${booking.serviceType} | x ${booking.unitsCount} items",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val (pillColor, textColor) = getStatusColors(booking.status)
                                            Surface(
                                                color = pillColor,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = booking.status.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = textColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(booking.date, style = MaterialTheme.typography.labelSmall)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(booking.timeSlot, style = MaterialTheme.typography.labelSmall)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = booking.serviceAddress,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (booking.assignedTechnicianName != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Construction,
                                                        contentDescription = "Assigned",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Dispatch Person: ${booking.assignedTechnicianName}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        // Dynamic status visual stepper
                                        DynamicProgressStepper(booking.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. SETTINGS & PROFILE MASTER DIALOG (Profile details, Edit, Password Change, Forgot Password, Address manager)
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text(
                        text = "Account settings & locations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Dialog Tab indicators
                        TabRow(
                            selectedTabIndex = if (settingsTab == "Profile") 0 else 1,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = settingsTab == "Profile",
                                onClick = { 
                                    settingsTab = "Profile" 
                                    isAddingAddress = false
                                },
                                text = { Text("My Profile") },
                                icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
                                modifier = Modifier.testTag("tab_settings_profile")
                            )
                            Tab(
                                selected = settingsTab == "Addresses",
                                onClick = { settingsTab = "Addresses" },
                                text = { Text("My Addresses") },
                                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                modifier = Modifier.testTag("tab_settings_addresses")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (settingsTab == "Profile") {
                            // PROFILE SUB-VIEW (Name, Phone, Email, Password change, Forgot simulation, Delete Account)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_name")
                                )
                                OutlinedTextField(
                                    value = editPhone,
                                    onValueChange = { editPhone = it },
                                    label = { Text("Phone Number") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_phone")
                                )
                                OutlinedTextField(
                                    value = editEmail,
                                    onValueChange = { editEmail = it },
                                    label = { Text("Email Address") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_email")
                                )
                                OutlinedTextField(
                                    value = editPassword,
                                    onValueChange = { editPassword = it },
                                    label = { Text("Account Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_password")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (editName.isNotBlank() && editPhone.isNotBlank()) {
                                                viewModel.updateCustomerDetails(editName, editPhone)
                                                if (editPassword.isNotBlank()) {
                                                    viewModel.changePassword(editPassword) { /* Done */ }
                                                }
                                                showSettingsDialog = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("btn_save_profile")
                                    ) {
                                        Text("Save profile", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showForgotPasswordPrompt = true },
                                        modifier = Modifier.weight(1f).testTag("btn_forgot_password")
                                    ) {
                                        Text("Forgot pass?")
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                Button(
                                    onClick = {
                                        viewModel.deleteAccount()
                                        showSettingsDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().testTag("btn_delete_account")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete account permanently", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // ADDRESS MANAGER SUB-VIEW
                            if (isAddingAddress) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Add New Address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    
                                    OutlinedTextField(
                                        value = addressLine1,
                                        onValueChange = { addressLine1 = it },
                                        label = { Text("Address Line 1 (H.No, Street)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_address_line1")
                                    )
                                    OutlinedTextField(
                                        value = addressLine2,
                                        onValueChange = { addressLine2 = it },
                                        label = { Text("Address Line 2 (Area, Landmark)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_address_line2")
                                    )
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it },
                                        label = { Text("City") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = district,
                                        onValueChange = { district = it },
                                        label = { Text("District") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = state,
                                        onValueChange = { state = it },
                                        label = { Text("State") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = country,
                                        onValueChange = { country = it },
                                        label = { Text("Country") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = pinCode,
                                        onValueChange = { pinCode = it },
                                        label = { Text("Pin Code") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("input_address_pincode")
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = setAddressDefault,
                                            onCheckedChange = { setAddressDefault = it },
                                            modifier = Modifier.testTag("checkbox_default_address")
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Set as my standard/default address", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { isAddingAddress = false }) {
                                            Text("Cancel")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                if (addressLine1.isNotBlank() && pinCode.isNotBlank()) {
                                                    viewModel.addAddress(
                                                        addressLine1,
                                                        addressLine2,
                                                        city,
                                                        district,
                                                        state,
                                                        country,
                                                        pinCode,
                                                        setAddressDefault
                                                    )
                                                    isAddingAddress = false
                                                }
                                            },
                                            modifier = Modifier.testTag("btn_save_new_address")
                                        ) {
                                            Text("Save address")
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Registered addresses (${savedAddresses.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Button(
                                            onClick = {
                                                isAddingAddress = true
                                                addressLine1 = ""
                                                addressLine2 = ""
                                                city = "Visakhapatnam"
                                                district = "Visakhapatnam"
                                                state = "Andhra Pradesh"
                                                country = "India"
                                                pinCode = ""
                                                setAddressDefault = savedAddresses.isEmpty()
                                            },
                                            modifier = Modifier.testTag("btn_add_address_dialog")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box(modifier = Modifier.heightIn(max = 240.dp)) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(savedAddresses) { addr ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text(addr.fullAddressDisplay, style = MaterialTheme.typography.bodySmall)
                                                        Spacer(modifier = Modifier.height(6.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (addr.isDefault) {
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "DEFAULT ADDRESS",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            } else {
                                                                TextButton(
                                                                    onClick = { viewModel.setDefaultAddress(addr.id) },
                                                                    modifier = Modifier.testTag("btn_make_default_${addr.id}")
                                                                ) {
                                                                    Text("Make primary", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }

                                                            IconButton(
                                                                onClick = { viewModel.deleteAddress(addr) },
                                                                modifier = Modifier.size(24.dp).testTag("btn_delete_address_${addr.id}")
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Close Settings")
                    }
                }
            )
        }

        // Forgot password mock prompt
        if (showForgotPasswordPrompt) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordPrompt = false },
                title = { Text("Password Recovery") },
                text = {
                    Text("A reset code simulation has been triggered! For local development safety, your current login password is: '${loggedInUser?.password ?: "Password123"}'")
                },
                confirmButton = {
                    Button(onClick = { showForgotPasswordPrompt = false }) {
                        Text("Acknowledge")
                    }
                }
            )
        }

        // Submission success notification dialog
        if (isBookingSubmitted && lastSubmitted != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSuccessNotification() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Booking Submitted Successfully!", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Status: REVIEW PENDING",
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your request for ${lastSubmitted?.serviceName} is lodged! We will assign a specialist soon. Track progress under the 'Booking History' tab. Target address is: ${lastSubmitted?.serviceAddress}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            viewModel.dismissSuccessNotification()
                            activeTab = 1 // Switch to booking tracker tab automatically
                        },
                        modifier = Modifier.testTag("dismiss_success_dialog")
                    ) {
                        Text("Track booking status")
                    }
                }
            )
        }
    }
}

// Healthy colors for status indicators
fun getStatusColors(status: String): Pair<Color, Color> {
    return when (status) {
        "Pending" -> Color(0xFFF2B8B5) to Color(0xFF601410)      // Pink-red
        "Confirmed" -> Color(0xFFFFF3CD) to Color(0xFF856404)    // Gold amber
        "In Progress" -> Color(0xFFE8DEF8) to Color(0xFF21005D)  // Lavender
        "Completed" -> Color(0xFFD1E7DD) to Color(0xFF198754)    // Green
        else -> Color(0xFFFFD8E4) to Color(0xFF31111D)           // Red-pink
    }
}

@Composable
fun DynamicProgressStepper(currentStatus: String) {
    val steps = listOf("Pending", "Confirmed", "In Progress", "Completed")
    val currentIndex = steps.indexOf(currentStatus)

    if (currentStatus == "Declined") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Cancel, contentDescription = "Declined", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Booking request declined by service team.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, title ->
                val isDone = index <= currentIndex
                val isActive = index == currentIndex

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .height(2.dp)
                            .background(if (index < currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}
