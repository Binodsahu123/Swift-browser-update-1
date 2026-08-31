package com.swift.browser.searchengine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.searchengine.VoiceChatMessage
import com.swift.browser.searchengine.VoiceHistoryEntry
import com.swift.browser.searchengine.VoiceNote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SwiftVoiceOverlay(
    isListening: Boolean,
    rmsValue: Float,
    activeMode: String,
    activeLanguageCode: String,
    transcript: String,
    errorMessage: String?,
    noteFormat: String,
    voiceNotes: List<VoiceNote>,
    voiceHistory: List<VoiceHistoryEntry>,
    voiceChatSessions: List<VoiceChatMessage>,
    historySearchQuery: String,
    onDismiss: () -> Unit,
    onModeChange: (String) -> Unit,
    onLanguageChange: (name: String, code: String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onNoteFormatChange: (String) -> Unit,
    onSaveVoiceNote: () -> Unit,
    onDeleteVoiceNote: (String) -> Unit,
    onClearVoiceHistory: () -> Unit,
    onClearVoiceChat: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlaySpokenFeedback: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Smooth dynamic wave ripple scaling synced directly to live voice decibels
    val pulseScale by animateFloatAsState(
        targetValue = 1f + (rmsValue * 1.5f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swift_rms_pulse"
    )

    // Sub-tab selection for Saved Logs mode
    var logActiveTab by remember { mutableStateOf("notes") } // "notes", "history", "chat"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = true, onClick = onDismiss)
            .testTag("swift_voice_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .navigationBarsPadding()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. TOP HEADER & SYSTEM TICKET
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isListening) Color(0xFF10B981) else Color(0xFF3B82F6),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(
                            text = "SWIFT VOICE INTELLIGENCE V3",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.2.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop voice capture",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                // 2. PRIMARY MODE PILLS TABS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf(
                        "Assistant" to "🎙 Assistant",
                        "Notes" to "📝 Voice Note",
                        "Chat" to "💬 Voice Chat",
                        "Saved" to "📜 Logs & History"
                    )
                    modes.forEach { (modeKey, modeTitle) ->
                        val isSelected = activeMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) Color(0xFF334155) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onModeChange(modeKey) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeTitle,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 3. TARGET SPEECH LANGUAGES
                if (activeMode != "Saved") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Lang:",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                            val languages = listOf(
                                Triple("English", "en-US", "EN"),
                                Triple("Hindi", "hi-IN", "HI"),
                                Triple("Tamil", "ta-IN", "TA"),
                                Triple("Telugu", "te-IN", "TE"),
                                Triple("Marathi", "mr-IN", "MR"),
                                Triple("Bengali", "bn-IN", "BN")
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                languages.forEach { (langName, langCode, shortLbl) ->
                                    val isLangActive = activeLanguageCode == langCode
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isLangActive) Color(0xFF2563EB) else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onLanguageChange(langName, langCode) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = shortLbl,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLangActive) Color.White else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. MAIN INTERACTION VIEW BASED ON THE ACTIVE SCREEN MODE
                val isSavedLogsTab = activeMode == "Saved"

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isSavedLogsTab) {
                        // Wave Splash / Mic Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(110.dp)
                                .padding(vertical = 4.dp)
                        ) {
                            // Ripple 1
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                        alpha = (1f - (rmsValue * 0.45f)).coerceIn(0.1f, 1f)
                                    }
                                    .size(90.dp)
                                    .background(
                                        color = when (activeMode) {
                                            "Notes" -> Color(0xFF10B981).copy(alpha = 0.16f)
                                            "Chat" -> Color(0xFF8B5CF6).copy(alpha = 0.16f)
                                            else -> Color(0xFF3B82F6).copy(alpha = 0.16f)
                                        },
                                        shape = CircleShape
                                    )
                            )

                            // Ripple 2
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = 1f + (rmsValue * 0.75f)
                                        scaleY = 1f + (rmsValue * 0.75f)
                                    }
                                    .size(70.dp)
                                    .background(
                                        color = when (activeMode) {
                                            "Notes" -> Color(0xFF34D399).copy(alpha = 0.28f)
                                            "Chat" -> Color(0xFFA78BFA).copy(alpha = 0.28f)
                                            else -> Color(0xFF60A5FA).copy(alpha = 0.28f)
                                        },
                                        shape = CircleShape
                                    )
                            )

                            // Primary Mic circle toggle button
                            Button(
                                onClick = {
                                    if (isListening) {
                                        onStopListening()
                                    } else {
                                        onStartListening()
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isListening) Color(0xFFEF4444) else {
                                        when (activeMode) {
                                            "Notes" -> Color(0xFF059669)
                                            "Chat" -> Color(0xFF7C3AED)
                                            else -> Color(0xFF2563EB)
                                        }
                                    },
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag("swift_mic_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Microphone Toggle",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.White
                                )
                            }
                        }

                        // Speech transcript
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = transcript,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = Color.White,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                fontSize = 11.sp,
                                color = Color(0xFFF87171),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        when (activeMode) {
                            "Notes" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Target Note Format:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Text", "Markdown", "Browser").forEach { f ->
                                            val isAct = noteFormat == f
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (isAct) Color(0xFF0F766E) else Color(0xFF334155),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable { onNoteFormatChange(f) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = f,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = onSaveVoiceNote,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("save_voice_note_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("💾 SAVE AS ${noteFormat.uppercase()} NOTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            "Chat" -> {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "💬 Active Chat Session Log",
                                                fontSize = 11.sp,
                                                color = Color(0xFFC084FC),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "🧹 Clear Session",
                                                fontSize = 10.sp,
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { onClearVoiceChat() }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (voiceChatSessions.isEmpty()) {
                                            Box(
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No conversation in progress. Ask any question,\nor ask \"Summarize this page\" and \"Translate it\"!",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(voiceChatSessions.size) { index ->
                                                    val chat = voiceChatSessions[index]
                                                    val isUser = chat.role == "user"
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                                    ) {
                                                        Card(
                                                            shape = RoundedCornerShape(
                                                                topStart = 12.dp,
                                                                topEnd = 12.dp,
                                                                bottomStart = if (isUser) 12.dp else 0.dp,
                                                                bottomEnd = if (isUser) 0.dp else 12.dp
                                                            ),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (isUser) Color(0xFF6D28D9) else Color(0xFF334155)
                                                            ),
                                                            modifier = Modifier.fillMaxWidth(0.85f)
                                                        ) {
                                                            Column(modifier = Modifier.padding(8.dp)) {
                                                                Text(
                                                                    text = if (isUser) "You:" else "Swift Assistant:",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isUser) Color(0xFFDDD6FE) else Color(0xFFE2E8F0)
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text(
                                                                    text = chat.text,
                                                                    fontSize = 11.sp,
                                                                    color = Color.White
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

                            else -> {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "💡 Voice Actions Router Guide:",
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "• Navigating: \"Open x.com\", \"Go to wikipedia\"\n• Browser Control: \"Open settings\", \"Show history\", \"Downloads\"\n• Tab Management: \"Open new tab\", \"Close tab\", \"Undo close tab\"\n• Media Assist: \"Resume video\", \"Playback speed 2x\"\n• Intelligent NLP: \"Summarize this webpage\", \"Translate page to Tamil\"",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Saved logs
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .padding(2.dp)
                            ) {
                                val logsSubtabs = listOf(
                                    "notes" to "Saved Notes",
                                    "history" to "Spoken Words",
                                    "chat" to "Chat Archives"
                                )
                                logsSubtabs.forEach { (tabKey, title) ->
                                    val isTabSelected = logActiveTab == tabKey
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isTabSelected) Color(0xFF334155) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { logActiveTab = tabKey }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 10.sp,
                                            color = if (isTabSelected) Color.White else Color(0xFF94A3B8),
                                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = historySearchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("swift_history_search_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                placeholder = { Text("Filter logs by queries...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Filter",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true
                            )

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    val sq = historySearchQuery.trim().lowercase(Locale.ROOT)
                                    when (logActiveTab) {
                                        "notes" -> {
                                            val filteredNotes = voiceNotes.filter {
                                                sq.isEmpty() || it.title.lowercase(Locale.ROOT).contains(sq) || it.noteContent.lowercase(Locale.ROOT).contains(sq)
                                            }
                                            if (filteredNotes.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("No saved notes found.", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            } else {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    items(filteredNotes.size) { noteIndex ->
                                                        val note = filteredNotes[noteIndex]
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(modifier = Modifier.padding(10.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = note.title,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF2DD4BF)
                                                                    )
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = Color(0xFF14B8A6)
                                                                    ) {
                                                                        Text(
                                                                            text = note.format,
                                                                            fontSize = 7.sp,
                                                                            color = Color.White,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = note.noteContent,
                                                                    fontSize = 10.sp,
                                                                    color = Color(0xFFCBD5E1),
                                                                    maxLines = 4
                                                                )
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.End,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    TextButton(
                                                                        onClick = {
                                                                            try {
                                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                                val clip = ClipData.newPlainText("Swift Note", note.noteContent)
                                                                                clipboard.setPrimaryClip(clip)
                                                                                onPlaySpokenFeedback?.invoke("Note copied to clipboard")
                                                                            } catch (e: Exception) {
                                                                                e.printStackTrace()
                                                                            }
                                                                        },
                                                                        modifier = Modifier.height(26.dp)
                                                                    ) {
                                                                        Text("📋 Copy", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                    }
                                                                    TextButton(
                                                                        onClick = { onDeleteVoiceNote(note.id) },
                                                                        modifier = Modifier.height(26.dp)
                                                                    ) {
                                                                        Text("🗑 Delete", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        "history" -> {
                                            val filteredHist = voiceHistory.filter {
                                                sq.isEmpty() || it.text.lowercase(Locale.ROOT).contains(sq)
                                            }
                                            if (filteredHist.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("No spoken phrases recorded.", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            } else {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = "🧹 Clear Command History",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFFEF4444),
                                                            modifier = Modifier
                                                                .clickable { onClearVoiceHistory() }
                                                                .padding(4.dp)
                                                        )
                                                    }
                                                    LazyColumn(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                                    ) {
                                                        items(filteredHist.size) { histIdx ->
                                                            val h = filteredHist[histIdx]
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(Color(0xFF334155), RoundedCornerShape(6.dp))
                                                                    .padding(8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1.0f)) {
                                                                    Text(
                                                                        text = "\"" + h.text + "\"",
                                                                        fontSize = 11.sp,
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                    Text(
                                                                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(h.timestamp)),
                                                                        fontSize = 8.sp,
                                                                        color = Color(0xFF64748B)
                                                                    )
                                                                }
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = when (h.type) {
                                                                        "command" -> Color(0xFF2563EB)
                                                                        "chat" -> Color(0xFF7C3AED)
                                                                        "transcript" -> Color(0xFF0D9488)
                                                                        else -> Color(0xFF475569)
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        text = h.type.uppercase(Locale.ROOT),
                                                                        fontSize = 7.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color.White,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        "chat" -> {
                                            val filteredChat = voiceHistory.filter { it.type == "chat" }
                                            if (filteredChat.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("No conversational chat archive found.", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            } else {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    items(filteredChat.size) { chatIndex ->
                                                        val chatLog = filteredChat[chatIndex]
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFF334155), RoundedCornerShape(6.dp))
                                                                .padding(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "Conversation Query: \"" + chatLog.text + "\"",
                                                                fontSize = 11.sp,
                                                                color = Color(0xFFE2E8F0)
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
                    }
                }
            }
        }
    }
}
