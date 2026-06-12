package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApplianceService
import com.example.data.Booking
import com.example.data.Technician
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val allServices by viewModel.allServices.collectAsState(initial = emptyList())
    val allBookings by viewModel.allBookings.collectAsState(initial = emptyList())
    val technicians by viewModel.technicians.collectAsState(initial = emptyList())

    // Form states
    val newServiceName by viewModel.newServiceName.collectAsState()
    val newServiceType by viewModel.newServiceType.collectAsState()
    val newServicePrice by viewModel.newServicePrice.collectAsState()

    var activeAdminTab by remember { mutableStateOf(0) } // 0: Stats Report, 1: Booking Management, 2: Service Management, 3: Technician Management

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // ADMIN TITLE BAR WITH LOGOUT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Admin Desk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Vizag Division Control Centre",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                    .size(36.dp)
                    .testTag("admin_logout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Toggle tabs for Admin Sections
        ScrollableTabRow(
            selectedTabIndex = activeAdminTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            edgePadding = 0.dp
        ) {
            Tab(
                selected = activeAdminTab == 0,
                onClick = { activeAdminTab = 0 },
                text = { Text("Stats & Reports", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.testTag("admin_tab_stats")
            )
            Tab(
                selected = activeAdminTab == 1,
                onClick = { activeAdminTab = 1 },
                text = { Text("Booking Desk", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.testTag("admin_tab_bookings")
            )
            Tab(
                selected = activeAdminTab == 2,
                onClick = { activeAdminTab = 2 },
                text = { Text("Services", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.testTag("admin_tab_services")
            )
            Tab(
                selected = activeAdminTab == 3,
                onClick = { activeAdminTab = 3 },
                text = { Text("Technicians", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.testTag("admin_tab_technicians")
            )
            Tab(
                selected = activeAdminTab == 4,
                onClick = { activeAdminTab = 4 },
                text = { Text("Users", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.testTag("admin_tab_users")
            )
        }

        when (activeAdminTab) {
            0 -> StatsDashboardView(allBookings, technicians)
            1 -> BookingDeskView(viewModel, allBookings, technicians)
            2 -> ServiceConfiguratorView(
                viewModel = viewModel,
                allServices = allServices,
                newServiceName = newServiceName,
                newServiceType = newServiceType,
                newServicePrice = newServicePrice
            )
            3 -> TechnicianManagementView(
                viewModel = viewModel,
                technicians = technicians,
                allServices = allServices
            )
            4 -> AdminUserManagementView(viewModel = viewModel)
        }
    }
}

// ==================== 1. OPERATIONS STATS VIEW ====================
@Composable
fun StatsDashboardView(
    bookings: List<Booking>,
    technicians: List<Technician>
) {
    val totalBookings = bookings.size
    val pendingCount = bookings.count { it.status == "Pending" }
    val activeCount = bookings.count { it.status == "Confirmed" || it.status == "In Progress" }
    val completedBookings = bookings.filter { it.status == "Completed" }
    val completedCount = completedBookings.size

    val completedEarnings = completedBookings.sumOf { it.pricePerUnit * it.unitsCount }
    val potentialEarnings = bookings.filter { it.status != "Declined" }.sumOf { it.pricePerUnit * it.unitsCount }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize().testTag("stats_dashboard_scroll")
    ) {
        // High Level Metrics Summary Cards
        item {
            Text(
                text = "Key Operations Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Bookings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$totalBookings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("$pendingCount Pending | $completedCount Completed", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Earnings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₹${completedEarnings.toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("Expected: ₹${potentialEarnings.toInt()}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Team Member Wise report list
        item {
            Text(
                text = "Team Member Wise Report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (technicians.isEmpty()) {
                        Text("No registered team members found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        technicians.forEachIndexed { idx, tech ->
                            val assignedToTech = bookings.filter { it.assignedTechnicianId == tech.id }
                            val activeTechCount = assignedToTech.count { it.status == "Confirmed" || it.status == "In Progress" }
                            val completedTechCount = assignedToTech.count { it.status == "Completed" }
                            val techRevenue = assignedToTech.filter { it.status == "Completed" }.sumOf { it.pricePerUnit * it.unitsCount }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Engineering,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(tech.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "₹${techRevenue.toInt()} Revenue",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Active Jobs: $activeTechCount | Completed Jobs: $completedTechCount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Performance: ${if (completedTechCount + activeTechCount == 0) "0%" else "${(completedTechCount * 100) / (completedTechCount + activeTechCount)}%"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (idx < technicians.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
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

// ==================== 2. OPERATION DESK VIEW ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDeskView(
    viewModel: AppViewModel,
    bookings: List<Booking>,
    technicians: List<Technician>
) {
    var deskSubFilter by remember { mutableStateOf("Pending") } // "Pending", "Active", "Archived"

    val pendingBookings = bookings.filter { it.status == "Pending" }
    val activeBookings = bookings.filter { it.status == "Confirmed" || it.status == "In Progress" }
    val archivedBookings = bookings.filter { it.status == "Completed" || it.status == "Declined" }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category filters representing booking desks
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subFilters = listOf(
                "Pending" to pendingBookings.size,
                "Active" to activeBookings.size,
                "Archived" to archivedBookings.size
            )

            subFilters.forEach { (filterTab, badgeCount) ->
                val isSelected = deskSubFilter == filterTab
                FilterChip(
                    selected = isSelected,
                    onClick = { deskSubFilter = filterTab },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filterTab, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(badgeCount.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("admin_desk_$filterTab")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val currentList = when (deskSubFilter) {
            "Pending" -> pendingBookings
            "Active" -> activeBookings
            else -> archivedBookings
        }

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AllInbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Desk column empty",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No requests in this pipeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(currentList) { booking ->
                    AdminBookingCard(viewModel, booking, technicians)
                }
            }
        }
    }
}

@Composable
fun AdminBookingCard(
    viewModel: AppViewModel,
    booking: Booking,
    technicians: List<Technician>
) {
    var showAssignDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_booking_card_${booking.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Job details and price summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Req #${booking.id} - ${booking.customerName}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Phone: ${booking.customerPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "₹${(booking.pricePerUnit * booking.unitsCount).toInt()}",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${booking.serviceName} (${booking.serviceType}) x ${booking.unitsCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Surface(
                    color = getStatusColors(booking.status).first,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = booking.status.uppercase(),
                        color = getStatusColors(booking.status).second,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Location address output for admin assignment clarity
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.serviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(booking.date, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(booking.timeSlot, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                }
            }

            if (booking.assignedTechnicianName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Construction, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Assigned Team: ${booking.assignedTechnicianName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons based on status types
            when (booking.status) {
                "Pending" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.declineBooking(booking.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_decline_${booking.id}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decline")
                        }

                        Button(
                            onClick = { showAssignDialog = true },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .testTag("btn_accept_assign_${booking.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accept & Assign", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                "Confirmed" -> {
                    Button(
                        onClick = { viewModel.updateBookingProgress(booking.id, booking.status) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_mark_progress_${booking.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark: Start Job In Progress", fontWeight = FontWeight.Bold)
                    }
                }
                "In Progress" -> {
                    Button(
                        onClick = { viewModel.updateBookingProgress(booking.id, booking.status) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_mark_done_${booking.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)) // Green finish button
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark: Complete Job", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // TECHNICIAN SELECT LIST DIALOG (ACCEPT & ASSIGN)
    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Vizag Service Technician", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select an active specialist, and dispatch them to site location:", style = MaterialTheme.typography.bodySmall)

                    if (technicians.isEmpty()) {
                        Text("No technicians registered in local database.")
                    } else {
                        technicians.forEach { tech ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.acceptAndAssignTechnician(booking.id, tech)
                                        showAssignDialog = false
                                    }
                                    .testTag("tech_option_${tech.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Engineering,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(tech.name, fontWeight = FontWeight.SemiBold)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ==================== 3. CONFIGURATION MANAGEMENT VIEW ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceConfiguratorView(
    viewModel: AppViewModel,
    allServices: List<ApplianceService>,
    newServiceName: String,
    newServiceType: String,
    newServicePrice: String
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Form Section (Add new service type)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add New Appliance Service Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Appliance choice
                    Text("Select Appliance", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val appliances = listOf("AC", "Fridge", "Washing Machine", "TV")
                        appliances.forEach { appName ->
                            val isSelected = newServiceName == appName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.updateNewServiceForm(appName, newServiceType, newServicePrice) }
                                    .padding(vertical = 8.dp)
                                    .testTag("add_form_appliance_$appName"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (appName == "Washing Machine") "Washer" else appName,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newServiceType,
                        onValueChange = { viewModel.updateNewServiceForm(newServiceName, it, newServicePrice) },
                        label = { Text("Service Type (e.g. Master Clean, Gas Refilling)") },
                        modifier = Modifier.fillMaxWidth().testTag("add_service_type_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newServicePrice,
                        onValueChange = { viewModel.updateNewServiceForm(newServiceName, newServiceType, it) },
                        label = { Text("Base Price (₹ - INR)") },
                        placeholder = { Text("e.g. 599") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("add_service_price_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.addService() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_add_service_submit"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Service to Live List", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List Section (Edit/Toggle current services)
        item {
            Text(
                text = "Live Active/Inactive Configurator",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (allServices.isEmpty()) {
            item {
                Text(
                    "No services configured. Enter new manually above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(allServices) { service ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_config_card_${service.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = service.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "₹${service.price.toInt()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = service.type,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // On/Off Toggle
                            Switch(
                                checked = service.isActive,
                                onCheckedChange = { viewModel.toggleServiceActivation(service) },
                                modifier = Modifier.testTag("switch_active_${service.id}")
                            )

                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteService(service) },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), CircleShape)
                                    .size(36.dp)
                                    .testTag("btn_delete_config_${service.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ApplianceService.displayLabel(): String = "$name $type"

private fun parseTechnicianServiceLabels(csv: String): Set<String> =
    csv.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun joinTechnicianServiceLabels(labels: Set<String>): String =
    labels.sorted().joinToString(",")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TechnicianServicesSelector(
    allServices: List<ApplianceService>,
    selectedLabels: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val serviceOptions = remember(allServices) {
        allServices
            .filter { it.isActive }
            .map { it.displayLabel() }
            .distinct()
            .sorted()
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Assigned services",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (selectedLabels.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedLabels.sorted().forEach { label ->
                    InputChip(
                        selected = true,
                        onClick = { onSelectionChange(selectedLabels - label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $label",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        } else {
            Text(
                text = "No services selected yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it }
        ) {
            OutlinedTextField(
                value = if (selectedLabels.isEmpty()) {
                    "Tap to select services"
                } else {
                    "${selectedLabels.size} service(s) selected"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Add services") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                if (serviceOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No active services configured") },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    serviceOptions.forEach { label ->
                        val checked = label in selectedLabels
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null
                                    )
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                onSelectionChange(
                                    if (checked) selectedLabels - label else selectedLabels + label
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicianManagementView(
    viewModel: AppViewModel,
    technicians: List<Technician>,
    allServices: List<ApplianceService>
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Technician Profile Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Admins can manage which services each technician belongs to. Technicians only have read access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (technicians.isEmpty()) {
            item {
                Text("No technicians available.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(technicians) { technician ->
                var selectedLabels by remember(technician.id, technician.services) {
                    mutableStateOf(parseTechnicianServiceLabels(technician.services))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("technician_manage_card_${technician.id}"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(technician.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(technician.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        TechnicianServicesSelector(
                            allServices = allServices,
                            selectedLabels = selectedLabels,
                            onSelectionChange = { selectedLabels = it },
                            modifier = Modifier.testTag("technician_services_selector_${technician.id}")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateTechnicianServices(
                                    technician,
                                    joinTechnicianServiceLabels(selectedLabels)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("technician_services_save_${technician.id}")
                        ) {
                            Text("Save Services Mapping")
                        }
                    }
                }
            }
        }
    }
}
