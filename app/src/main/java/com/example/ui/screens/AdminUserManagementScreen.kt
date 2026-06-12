package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PortalUser
import com.example.ui.viewmodel.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminUserManagementView(viewModel: AppViewModel) {
    val allUsers by viewModel.portalUsers.collectAsState()
    var filterRole by remember { mutableStateOf("All") }
    var statusMessage by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var userToReset by remember { mutableStateOf<PortalUser?>(null) }
    var userToDelete by remember { mutableStateOf<PortalUser?>(null) }
    var userToEdit by remember { mutableStateOf<PortalUser?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshPortalUsers { ok, msg ->
            if (!ok) statusMessage = msg
        }
    }

    val visibleUsers = allUsers.filter { user ->
        filterRole == "All" || user.roleLabel.equals(filterRole, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "User accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Create admins, customers, and technicians. Reset passwords or disable access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    viewModel.refreshPortalUsers { ok, msg ->
                        statusMessage = if (ok) "List refreshed" else msg
                    }
                },
                modifier = Modifier.testTag("admin_users_refresh")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Admin", "Customer", "Technician").forEach { role ->
                FilterChip(
                    selected = filterRole == role,
                    onClick = { filterRole = role },
                    label = { Text(role) },
                    modifier = Modifier.testTag("admin_users_filter_$role")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_users_add")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Add user")
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (visibleUsers.isEmpty()) {
                item {
                    Text(
                        text = "No users in this filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(visibleUsers, key = { "${it.role}-${it.id}" }) { user ->
                    PortalUserCard(
                        user = user,
                        onResetPassword = { userToReset = user },
                        onToggleActive = {
                            viewModel.updatePortalUser(user, isActive = !user.isActive) { ok, msg ->
                                statusMessage = if (ok) {
                                    if (user.isActive) "User disabled" else "User enabled"
                                } else msg
                            }
                        },
                        onEdit = { userToEdit = user },
                        onDelete = { userToDelete = user }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePortalUserDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { role, email, password, name, phone, services ->
                viewModel.createPortalUser(role, email, password, name, phone, services) { ok, msg ->
                    statusMessage = msg
                    if (ok) showCreateDialog = false
                }
            }
        )
    }

    userToReset?.let { user ->
        ResetPasswordDialog(
            user = user,
            onDismiss = { userToReset = null },
            onConfirm = { newPassword ->
                viewModel.updatePortalUser(user, password = newPassword) { ok, msg ->
                    statusMessage = msg
                    if (ok) userToReset = null
                }
            }
        )
    }

    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete user?") },
            text = { Text("Permanently remove ${user.displayTitle} (${user.roleLabel})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePortalUser(user) { ok, msg ->
                            statusMessage = msg
                            if (ok) userToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }

    userToEdit?.let { user ->
        EditPortalUserDialog(
            user = user,
            onDismiss = { userToEdit = null },
            onSave = { email, name, phone ->
                viewModel.updatePortalUser(user, email = email, name = name, phone = phone) { ok, msg ->
                    statusMessage = msg
                    if (ok) userToEdit = null
                }
            }
        )
    }
}

@Composable
private fun PortalUserCard(
    user: PortalUser,
    onResetPassword: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portal_user_card_${user.role}_${user.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(user.displayTitle, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (user.isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (user.isActive) "Active" else "Disabled",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text("${user.roleLabel} · ${user.email}", style = MaterialTheme.typography.bodySmall)
            if (user.phone.isNotBlank()) {
                Text("Phone: ${user.phone}", style = MaterialTheme.typography.bodySmall)
            }
            if (user.services.isNotBlank()) {
                Text("Services: ${user.services}", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onResetPassword, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onToggleActive, modifier = Modifier.weight(1f)) {
                    Text(if (user.isActive) "Disable" else "Enable", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" Delete", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CreatePortalUserDialog(
    onDismiss: () -> Unit,
    onCreate: (role: String, email: String, password: String, name: String, phone: String, services: String) -> Unit
) {
    var role by remember { mutableStateOf("admin") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var services by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add user") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role (admin / customer / technician)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (role != "admin") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (role == "customer") {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (role == "technician") {
                    OutlinedTextField(
                        value = services,
                        onValueChange = { services = it },
                        label = { Text("Services (comma-separated, optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(role.lowercase(), email, password, name, phone, services)
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ResetPasswordDialog(
    user: PortalUser,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set a new password for ${user.displayTitle}")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (password.isNotBlank()) onConfirm(password) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditPortalUserDialog(
    user: PortalUser,
    onDismiss: () -> Unit,
    onSave: (email: String, name: String, phone: String) -> Unit
) {
    var email by remember(user.id) { mutableStateOf(user.email) }
    var name by remember(user.id) { mutableStateOf(user.name) }
    var phone by remember(user.id) { mutableStateOf(user.phone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${user.roleLabel}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (user.role != "admin") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (user.role == "customer") {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(email, name, phone) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
