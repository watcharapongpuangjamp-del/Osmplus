package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.CardShadowTint
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import org.osmdroid.util.GeoPoint

@Composable
fun PopulationEventCard(
    event: PopulationEvent,
    onClose: () -> Unit,
    onDelete: (PopulationEvent) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = CardShadowTint),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MintAccent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val eventColor = when (event.type) {
                        PopulationEventType.BIRTH -> Color(0xFF10B981)
                        PopulationEventType.DEATH -> Color(0xFF6B7280)
                        PopulationEventType.MOVE_IN -> Color(0xFF3B82F6)
                        PopulationEventType.MOVE_OUT -> Color(0xFFF59E0B)
                        PopulationEventType.HEALTH_CHECK -> Color(0xFF8B5CF6)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(eventColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (event.type) {
                                PopulationEventType.BIRTH -> Icons.Filled.ChildCare
                                PopulationEventType.DEATH -> Icons.Filled.PersonRemove
                                PopulationEventType.MOVE_IN -> Icons.Filled.Login
                                PopulationEventType.MOVE_OUT -> Icons.Filled.Logout
                                PopulationEventType.HEALTH_CHECK -> Icons.Filled.MedicalInformation
                            },
                            contentDescription = null,
                            tint = eventColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = event.type.value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "ปิด", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!event.description.isNullOrBlank()) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.personName != null) {
                    Text(
                        event.personName,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { onDelete(event) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ลบเหตุการณ์นี้")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdDetailBottomSheet(
    house: HouseSummary,
    persons: List<Person>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onDeleteLocation: (HouseSummary) -> Unit,
    onMovePin: (Long, GeoPoint) -> Unit,
    onViewDetails: (Long) -> Unit,
    initialLat: Double,
    initialLon: Double
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(EmeraldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(32.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "บ้านเลขที่ ${house.houseNo}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!house.headName.isNullOrBlank()) {
                        Text(
                            text = "เจ้าบ้าน: ${house.headName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (house.dataStatus == DataStatus.VERIFIED) EmeraldPrimary.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (house.dataStatus == DataStatus.VERIFIED) EmeraldPrimary.copy(alpha = 0.3f) else Color.Red.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = house.dataStatus.value,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (house.dataStatus == DataStatus.VERIFIED) EmeraldPrimary else Color.Red
                    )
                }
            }

            // Demographic Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "ทั้งหมด",
                    value = "${house.totalMembers}",
                    subtext = "คนในบ้าน",
                    color = MaterialTheme.colorScheme.primary
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "ชาย / หญิง",
                    value = "${house.males} / ${house.females}",
                    subtext = "เพศสภาพ",
                    color = Color(0xFF0EA5E9)
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "สูงอายุ / เด็ก",
                    value = "${house.elderly} / ${house.earlyChild + house.schoolAge}",
                    subtext = "กลุ่มเปราะบาง",
                    color = Color(0xFF8B5CF6)
                )
            }

            // GPS Coordinates Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmeraldPrimary)
                        Column {
                            Text("พิกัดแผนที่ (GIS)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Lat: ${house.latitude}, Lon: ${house.longitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { onDeleteLocation(house) }) {
                        Text("ลบพิกัด", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Household Members List Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("รายชื่อผูอยู่อาศัย (${persons.size} คน)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (persons.isEmpty()) {
                    Text("ไม่พบข้อมูลรายชื่อ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        items(persons) { person ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (person.gender == com.example.data.Gender.MALE) Icons.Filled.Man else Icons.Filled.Woman,
                                        contentDescription = null,
                                        tint = if (person.gender == com.example.data.Gender.MALE) Color.Blue else Color.Magenta,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(person.fullName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Text(person.houseStatus.value, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onMovePin(house.householdId, GeoPoint(house.latitude ?: initialLat, house.longitude ?: initialLon))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.EditLocation, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ย้ายหมุด")
                }

                Button(
                    onClick = { onViewDetails(house.householdId) },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ดูข้อมูลครัวเรือน", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
