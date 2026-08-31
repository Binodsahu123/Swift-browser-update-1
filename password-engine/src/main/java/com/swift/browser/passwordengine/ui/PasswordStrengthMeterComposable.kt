package com.swift.browser.passwordengine.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.passwordengine.security.StrengthResult
import com.swift.browser.passwordengine.security.StrengthTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordStrengthMeter(
    result: StrengthResult,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true
) {
    val activeColorHex = result.tier.colorHex
    val parsedColor = parseHexColor(activeColorHex)
    val animatedColor by animateColorAsState(
        targetValue = parsedColor,
        animationSpec = tween(durationMillis = 350),
        label = "strengthColor"
    )

    val progressFraction = (result.score / 100f).coerceIn(0.05f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 350),
        label = "strengthProgress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Tier & Entropy Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(animatedColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.tier.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${result.score}/100)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Entropy badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Entropy",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${result.entropyBits} bits entropy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5-segment Progress Meter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val activeSegments = when (result.tier) {
                    StrengthTier.VERY_WEAK -> 1
                    StrengthTier.WEAK -> 2
                    StrengthTier.MEDIUM -> 3
                    StrengthTier.STRONG -> 4
                    StrengthTier.VERY_STRONG -> 5
                }

                for (i in 1..5) {
                    val isActive = i <= activeSegments
                    val segColor = if (isActive) animatedColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(segColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time to crack estimate row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Estimated crack time: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
                Text(
                    text = result.crackTimeDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    fontSize = 11.sp
                )
            }

            if (showDetails) {
                Spacer(modifier = Modifier.height(10.dp))

                // Checklist requirement pills
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RequirementChip(
                        label = "12+ Chars (${result.length})",
                        isMet = result.hasMinLength
                    )
                    RequirementChip(
                        label = "Special (!@#$)",
                        isMet = result.hasSpecialChars
                    )
                    RequirementChip(
                        label = "Mixed Case (A/a)",
                        isMet = result.hasMixedCase
                    )
                    RequirementChip(
                        label = "Numbers (0-9)",
                        isMet = result.hasNumbers
                    )
                    RequirementChip(
                        label = "High Entropy",
                        isMet = result.hasHighEntropy
                    )
                }

                // Feedback messages if any
                if (result.feedback.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        result.feedback.take(2).forEach { fb ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = fb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementChip(
    label: String,
    isMet: Boolean
) {
    val bgColor = if (isMet) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val contentColor = if (isMet) Color(0xFF10B981) else MaterialTheme.colorScheme.outline

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isMet) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = if (isMet) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
