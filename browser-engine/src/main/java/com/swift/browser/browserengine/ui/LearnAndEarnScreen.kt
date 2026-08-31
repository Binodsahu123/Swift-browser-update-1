package com.swift.browser.browserengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LearnCourse(
    val id: String,
    val title: String,
    val description: String,
    val level: String,
    val xpReward: Int,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val modulesCount: Int
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnAndEarnScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var userXp by remember { mutableIntStateOf(350) }
    var userStreak by remember { mutableIntStateOf(4) }
    var selectedCategory by remember { mutableStateOf("All") }
    var activeQuizQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var isAnswerSubmitted by remember { mutableStateOf(false) }

    val categories = listOf("All", "Web Dev", "AI & Search", "Privacy & Security", "Fast Tips")

    val courses = remember {
        listOf(
            LearnCourse(
                id = "c1",
                title = "HTML5 & Modern Web",
                description = "Master semantic markup, canvas rendering, and responsive structures.",
                level = "Beginner",
                xpReward = 100,
                icon = Icons.Default.Code,
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                modulesCount = 6
            ),
            LearnCourse(
                id = "c2",
                title = "AI Prompt Engineering",
                description = "Learn advanced querying techniques for LLMs, summaries, and synthesis.",
                level = "Intermediate",
                xpReward = 150,
                icon = Icons.Default.AutoAwesome,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                modulesCount = 8
            ),
            LearnCourse(
                id = "c3",
                title = "Browser Security & Ad-Block",
                description = "Understand tracker blocking, fingerprint protection, and secure cookies.",
                level = "All Levels",
                xpReward = 120,
                icon = Icons.Default.Security,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857)),
                modulesCount = 5
            ),
            LearnCourse(
                id = "c4",
                title = "JavaScript ESNext Engine",
                description = "Async/await patterns, DOM manipulation, and V8 optimization internals.",
                level = "Advanced",
                xpReward = 200,
                icon = Icons.Default.Terminal,
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
                modulesCount = 10
            )
        )
    }

    val dailyQuiz = remember {
        QuizQuestion(
            question = "Which HTTP header is primarily used to prevent Clickjacking attacks in browsers?",
            options = listOf(
                "X-Frame-Options",
                "Content-Security-Policy-Report-Only",
                "Strict-Transport-Security",
                "Cache-Control"
            ),
            correctIndex = 0,
            explanation = "X-Frame-Options (or CSP frame-ancestors) instructs the browser whether the page can be rendered inside a <frame> or <iframe>."
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Surface(
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Learn & Earn",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // XP & Streak Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "$userStreak Days", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "$userXp XP", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Daily Quiz Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Quiz, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Daily Knowledge Challenge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Text(text = "+50 XP", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = dailyQuiz.question, color = Color(0xFFE2E8F0), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            dailyQuiz.options.forEachIndexed { index, option ->
                                val isSelected = selectedAnswerIndex == index
                                val isCorrect = isAnswerSubmitted && index == dailyQuiz.correctIndex
                                val isWrong = isAnswerSubmitted && isSelected && index != dailyQuiz.correctIndex

                                val bgColor = when {
                                    isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                    isWrong -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                    isSelected -> Color(0xFF3B82F6).copy(alpha = 0.25f)
                                    else -> Color(0xFF0F172A)
                                }
                                val borderColor = when {
                                    isCorrect -> Color(0xFF10B981)
                                    isWrong -> Color(0xFFEF4444)
                                    isSelected -> Color(0xFF3B82F6)
                                    else -> Color(0xFF334155)
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable(enabled = !isAnswerSubmitted) { selectedAnswerIndex = index },
                                    shape = RoundedCornerShape(10.dp),
                                    color = bgColor,
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${('A' + index)}. ",
                                            color = Color(0xFF94A3B8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = option,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCorrect) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        } else if (isWrong) {
                                            Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            if (!isAnswerSubmitted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (selectedAnswerIndex != null) {
                                            isAnswerSubmitted = true
                                            if (selectedAnswerIndex == dailyQuiz.correctIndex) {
                                                userXp += 50
                                            }
                                        }
                                    },
                                    enabled = selectedAnswerIndex != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                ) {
                                    Text("Submit Answer", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dailyQuiz.explanation,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Course Category Filter
                item {
                    Text(text = "Interactive Tracks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Courses List
                items(courses) { course ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(course.gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(course.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = course.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${course.xpReward} XP",
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = course.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF334155)
                                    ) {
                                        Text(
                                            text = course.level,
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${course.modulesCount} lessons",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
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
