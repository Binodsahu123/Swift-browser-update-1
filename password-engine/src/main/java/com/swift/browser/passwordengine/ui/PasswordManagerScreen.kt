package com.swift.browser.passwordengine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swift.browser.passwordengine.model.PasswordCategory
import com.swift.browser.passwordengine.model.PasswordEntry
import com.swift.browser.passwordengine.security.PasswordStrengthEvaluator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(
    viewModel: PasswordManagerViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val passwords by viewModel.passwords.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isMasterUnlocked by viewModel.isMasterUnlocked.collectAsStateWithLifecycle()
    val auditSummary by viewModel.auditSummary.collectAsStateWithLifecycle()
    val revealedIds by viewModel.revealedPasswordIds.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var showUnlockDialog by remember { mutableStateOf(!isMasterUnlocked) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Password Manager",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Credentials",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        if (isMasterUnlocked) {
                            viewModel.lockMaster()
                        } else {
                            showUnlockDialog = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isMasterUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Lock State",
                            tint = if (isMasterUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Password")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Master Lock Banner
            if (!isMasterUnlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Vault is locked. Unlock to view passwords.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { showUnlockDialog = true }) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Security Audit Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (auditSummary.averageScore >= 70) Color(0xFF10B981) else Color(0xFFEAB308)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security Score: ${auditSummary.averageScore}/100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${auditSummary.totalCount} saved passwords • ${auditSummary.weakCount} weak • ${auditSummary.reusedCount} reused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search logins, sites, or usernames...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All (${auditSummary.totalCount})") }
                    )
                }
                items(PasswordCategory.entries.toTypedArray()) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(if (selectedCategory == cat) null else cat) },
                        label = { Text(cat.displayName) }
                    )
                }
            }

            // Password List
            if (passwords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching credentials found" else "No passwords saved yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add your first password to secure vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(passwords, key = { it.id }) { entry ->
                        val isRevealed = revealedIds.contains(entry.id)
                        val decryptedPassword = if (isMasterUnlocked && isRevealed) {
                            viewModel.decryptPassword(entry)
                        } else {
                            "••••••••••••"
                        }

                        PasswordCard(
                            entry = entry,
                            decryptedPassword = decryptedPassword,
                            isUnlocked = isMasterUnlocked,
                            isRevealed = isRevealed,
                            onToggleReveal = {
                                if (isMasterUnlocked) {
                                    viewModel.togglePasswordVisibility(entry.id)
                                } else {
                                    showUnlockDialog = true
                                }
                            },
                            onCopyUsername = { copyToClipboard("Username", entry.username) },
                            onCopyPassword = {
                                if (isMasterUnlocked) {
                                    copyToClipboard("Password", viewModel.decryptPassword(entry))
                                } else {
                                    showUnlockDialog = true
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(entry) },
                            onEdit = {
                                editingEntry = entry
                                showAddDialog = true
                            },
                            onDelete = { viewModel.deletePassword(entry.id) }
                        )
                    }
                }
            }
        }
    }

    // Unlock Master PIN Dialog
    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
            title = { Text("Unlock Vault") },
            text = {
                Column {
                    Text("Enter Master PIN to reveal and copy passwords (Default: 1234):")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            pinError = null
                        },
                        label = { Text("Master PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null,
                        singleLine = true
                    )
                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.unlockMaster(pinInput)) {
                        showUnlockDialog = false
                        pinInput = ""
                        Toast.makeText(context, "Vault unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        pinError = "Incorrect PIN. Try 1234."
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit Password Dialog
    if (showAddDialog) {
        AddEditPasswordDialog(
            entry = editingEntry,
            onDismiss = { showAddDialog = false },
            onSave = { id, siteUrl, siteTitle, username, rawPassword, notes, category, isFavorite ->
                viewModel.savePassword(id, siteUrl, siteTitle, username, rawPassword, notes, category, isFavorite)
                showAddDialog = false
            },
            onGeneratePassword = { length, incLower, incUpper, incNum, incSym, avoidAmb ->
                viewModel.generateNewPassword(
                    length = length,
                    includeLower = incLower,
                    includeUpper = incUpper,
                    includeNumbers = incNum,
                    includeSymbols = incSym,
                    avoidAmbiguous = avoidAmb
                )
            },
            decryptExistingPassword = { entry -> viewModel.decryptPassword(entry) }
        )
    }

    if (showImportDialog) {
        ImportCredentialsDialog(
            onDismiss = { showImportDialog = false },
            onImportUri = { uri, callback ->
                viewModel.importCredentialsFromUri(uri, callback)
            },
            onImportText = { text, callback ->
                viewModel.importCredentialsFromText(text, callback)
            }
        )
    }
}

@Composable
fun PasswordCard(
    entry: PasswordEntry,
    decryptedPassword: String,
    isUnlocked: Boolean,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.siteTitle.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = entry.siteTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = entry.siteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (entry.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Username Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username / Email",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onCopyUsername, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Username",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Password Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Password",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = decryptedPassword,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row {
                    IconButton(onClick = onToggleReveal, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isRevealed && isUnlocked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Visibility",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopyPassword, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Password",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Strength bar & Category badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = entry.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Strength: ${entry.strengthRating}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.strengthRating >= 70) Color(0xFF10B981) else Color(0xFFEAB308)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LinearProgressIndicator(
                        progress = { entry.strengthRating / 100f },
                        modifier = Modifier
                            .width(60.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (entry.strengthRating >= 70) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditPasswordDialog(
    entry: PasswordEntry?,
    onDismiss: () -> Unit,
    onSave: (id: Long, siteUrl: String, siteTitle: String, username: String, rawPassword: String, notes: String, category: String, isFavorite: Boolean) -> Unit,
    onGeneratePassword: (length: Int, includeLower: Boolean, includeUpper: Boolean, includeNumbers: Boolean, includeSymbols: Boolean, avoidAmbiguous: Boolean) -> String,
    decryptExistingPassword: (PasswordEntry) -> String
) {
    var siteUrl by remember { mutableStateOf(entry?.siteUrl ?: "") }
    var siteTitle by remember { mutableStateOf(entry?.siteTitle ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(entry?.let { decryptExistingPassword(it) } ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: PasswordCategory.GENERAL.displayName) }
    var isFavorite by remember { mutableStateOf(entry?.isFavorite ?: false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Generator Options State
    var showGeneratorOptions by remember { mutableStateOf(false) }
    var genLength by remember { mutableStateOf(16f) }
    var genUpper by remember { mutableStateOf(true) }
    var genLower by remember { mutableStateOf(true) }
    var genNumbers by remember { mutableStateOf(true) }
    var genSymbols by remember { mutableStateOf(true) }
    var genAvoidAmbiguous by remember { mutableStateOf(true) }

    val strength = remember(password) { PasswordStrengthEvaluator.evaluate(password) }

    fun generate() {
        password = onGeneratePassword(
            genLength.toInt(),
            genLower,
            genUpper,
            genNumbers,
            genSymbols,
            genAvoidAmbiguous
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Add Credential" else "Edit Credential") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = siteUrl,
                        onValueChange = { siteUrl = it },
                        label = { Text("Website URL / Domain") },
                        placeholder = { Text("e.g. google.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = siteTitle,
                        onValueChange = { siteTitle = it },
                        label = { Text("Site / App Name") },
                        placeholder = { Text("e.g. Google Account") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { generate() }) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Generate Strong Password")
                                    }
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        PasswordStrengthMeter(
                            result = strength,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showGeneratorOptions = !showGeneratorOptions }) {
                                Text(if (showGeneratorOptions) "Hide Generator Options" else "Show Generator Options")
                            }
                        }

                        AnimatedVisibility(visible = showGeneratorOptions) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Length: ${genLength.toInt()} characters",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    androidx.compose.material3.Slider(
                                        value = genLength,
                                        onValueChange = {
                                            genLength = it
                                            generate()
                                        },
                                        valueRange = 8f..32f,
                                        steps = 23
                                    )

                                    Text(
                                        text = "Character Sets:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = genUpper,
                                            onClick = {
                                                genUpper = !genUpper
                                                generate()
                                            },
                                            label = { Text("A-Z", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = genLower,
                                            onClick = {
                                                genLower = !genLower
                                                generate()
                                            },
                                            label = { Text("a-z", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = genNumbers,
                                            onClick = {
                                                genNumbers = !genNumbers
                                                generate()
                                            },
                                            label = { Text("0-9", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = genSymbols,
                                            onClick = {
                                                genSymbols = !genSymbols
                                                generate()
                                            },
                                            label = { Text("!@#$", fontSize = 11.sp) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilterChip(
                                        selected = genAvoidAmbiguous,
                                        onClick = {
                                            genAvoidAmbiguous = !genAvoidAmbiguous
                                            generate()
                                        },
                                        label = { Text("Avoid Ambiguous (1, l, I, 0, O)", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Category:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PasswordCategory.entries.toTypedArray()) { cat ->
                            FilterChip(
                                selected = category == cat.displayName,
                                onClick = { category = cat.displayName },
                                label = { Text(cat.displayName) }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = siteUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    onSave(
                        entry?.id ?: 0L,
                        siteUrl,
                        siteTitle,
                        username,
                        password,
                        notes,
                        category,
                        isFavorite
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

