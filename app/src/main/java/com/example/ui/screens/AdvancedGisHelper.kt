package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HouseSummary
import com.example.ui.theme.*
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.*

/**
 * Advanced GIS Models & States for Field Assistance.
 */
enum class GisActiveTool {
    NONE,
    MEASURE_DISTANCE,    // วัดระยะทางบนแผนที่
    EPIDEMIC_BUFFER,     // วงรัศมีเฝ้าระวังไข้เลือดออก & สอบสวนโรค 100 เมตร
    VISIT_ROUTE          // ระบบวางแผนเส้นทางเยี่ยมบ้านอัจฉริยะ
}

data class EpidemicBufferState(
    val center: GeoPoint? = null,
    val radiusMeters: Double = 100.0,
    val affectedHouses: List<HouseSummary> = emptyList(),
    val isEnabled: Boolean = false
)

data class FieldVisitRouteState(
    val stops: List<HouseSummary> = emptyList(),
    val currentStopIndex: Int = 0,
    val isRouteActive: Boolean = false,
    val completedHouseholdIds: Set<Long> = emptySet()
)

/**
 * Calculates Great-Circle distance between two points in meters using Haversine formula.
 */
fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
    val r = 6371000.0 // Earth radius in meters
    val lat1Rad = Math.toRadians(p1.latitude)
    val lat2Rad = Math.toRadians(p2.latitude)
    val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
    val deltaLon = Math.toRadians(p2.longitude - p1.longitude)

    val a = sin(deltaLat / 2.0).pow(2.0) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return r * c
}

/**
 * Calculates Compass Bearing from p1 to p2.
 */
fun calculateBearingDirection(p1: GeoPoint, p2: GeoPoint): String {
    val lat1 = Math.toRadians(p1.latitude)
    val lon1 = Math.toRadians(p1.longitude)
    val lat2 = Math.toRadians(p2.latitude)
    val lon2 = Math.toRadians(p2.longitude)

    val y = sin(lon2 - lon1) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon2 - lon1)
    var bearing = Math.toDegrees(atan2(y, x))
    bearing = (bearing + 360.0) % 360.0

    return when (bearing) {
        in 22.5..67.5 -> "ทิศตะวันออกเฉียงเหนือ ↗"
        in 67.5..112.5 -> "ทิศตะวันออก →"
        in 112.5..157.5 -> "ทิศตะวันออกเฉียงใต้ ↘"
        in 157.5..202.5 -> "ทิศใต้ ↓"
        in 202.5..247.5 -> "ทิศตะวันตกเฉียงใต้ ↙"
        in 247.5..292.5 -> "ทิศตะวันตก ←"
        in 292.5..337.5 -> "ทิศตะวันตกเฉียงเหนือ ↖"
        else -> "ทิศเหนือ ↑"
    }
}

/**
 * Formats distance into human-friendly Thai string (e.g. 85 เมตร, 1.4 กม.).
 */
fun formatDistanceFriendly(meters: Double): String {
    return if (meters < 1000) {
        "${meters.roundToInt()} ม."
    } else {
        String.format(Locale.US, "%.1f กม.", meters / 1000.0)
    }
}

/**
 * Launches external Google Maps navigation intent.
 */
fun launchGoogleMapsNavigation(context: Context, destination: GeoPoint, label: String = "เป้าหมาย") {
    try {
        val gmmIntentUri = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback to web / generic geo intent
            val fallbackUri = Uri.parse("geo:0,0?q=${destination.latitude},${destination.longitude}($label)")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        }
    } catch (e: Exception) {
        val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}

/**
 * Creates circle points around a center GeoPoint for osmdroid Polygon overlay.
 */
fun createCirclePoints(center: GeoPoint, radiusMeters: Double, pointsCount: Int = 40): ArrayList<GeoPoint> {
    val points = ArrayList<GeoPoint>()
    val earthRadius = 6378137.0
    val d = radiusMeters / earthRadius
    val lat1 = Math.toRadians(center.latitude)
    val lon1 = Math.toRadians(center.longitude)

    for (i in 0 until pointsCount) {
        val bearing = 2.0 * Math.PI * i / pointsCount
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
        val lon2 = lon1 + atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        points.add(GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2)))
    }
    return points
}

/**
 * Finds all households whose coordinates fall within a radius of center.
 */
fun findHouseholdsWithinBuffer(
    center: GeoPoint,
    radiusMeters: Double,
    houses: List<HouseSummary>
): List<HouseSummary> {
    return houses.filter { h ->
        val lat = h.latitude
        val lon = h.longitude
        if (lat != null && lon != null) {
            calculateDistanceMeters(center, GeoPoint(lat, lon)) <= radiusMeters
        } else false
    }.sortedBy { h ->
        calculateDistanceMeters(center, GeoPoint(h.latitude!!, h.longitude!!))
    }
}

/**
 * Optimizes the sequence of home visit stops using Nearest-Neighbor heuristic.
 */
fun optimizeVisitRouteOrder(
    startPoint: GeoPoint,
    targetHouses: List<HouseSummary>
): List<HouseSummary> {
    if (targetHouses.isEmpty()) return emptyList()

    val remaining = targetHouses.filter { it.latitude != null && it.longitude != null }.toMutableList()
    val ordered = mutableListOf<HouseSummary>()
    var currentPoint = startPoint

    while (remaining.isNotEmpty()) {
        val nearest = remaining.minByOrNull { h ->
            calculateDistanceMeters(currentPoint, GeoPoint(h.latitude!!, h.longitude!!))
        } ?: break
        ordered.add(nearest)
        remaining.remove(nearest)
        currentPoint = GeoPoint(nearest.latitude!!, nearest.longitude!!)
    }

    return ordered
}

/**
 * Marker drawable generator for Tour Visit sequence (จุดที่ 1, 2, 3...).
 */
fun createTourStopMarkerDrawable(
    context: Context,
    stopNumber: Int,
    isCurrent: Boolean,
    isDone: Boolean
): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (38 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val bgColor = when {
        isDone -> android.graphics.Color.rgb(16, 185, 129) // Green completed
        isCurrent -> android.graphics.Color.rgb(239, 68, 68) // Red current target
        else -> android.graphics.Color.rgb(37, 99, 235) // Blue future stop
    }

    // Outer Glow / Ring for current stop
    if (isCurrent) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(90, 239, 68, 68)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, glowPaint)
    }

    // Main Circle
    paint.color = bgColor
    paint.style = Paint.Style.FILL
    val radius = if (isCurrent) (14 * density) else (15 * density)
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)

    // Border
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)

    // Text: Stop Number
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 12 * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val text = if (isDone) "✓" else "$stopNumber"
    canvas.drawText(text, size / 2f, size / 2f + (4.5f * density), textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Floating Tool Selector Bar (Bar ด้านบนแผนที่ สำหรับเลือกเครื่องมือขั้นสูง).
 */
@Composable
fun AdvancedGisFloatingBar(
    activeTool: GisActiveTool,
    onSelectTool: (GisActiveTool) -> Unit,
    onCenterGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(100.dp))
            .clip(RoundedCornerShape(100.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GPS Location center
            IconButton(
                onClick = onCenterGps,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "พิกัดปัจจุบัน", tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // Tool: 100m Epidemic Buffer
            FilterChip(
                selected = activeTool == GisActiveTool.EPIDEMIC_BUFFER,
                onClick = {
                    onSelectTool(if (activeTool == GisActiveTool.EPIDEMIC_BUFFER) GisActiveTool.NONE else GisActiveTool.EPIDEMIC_BUFFER)
                },
                label = { Text("รัศมี 100ม. เฝ้าระวังโรค", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFDC2626),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )

            // Tool: Smart Visit Route
            FilterChip(
                selected = activeTool == GisActiveTool.VISIT_ROUTE,
                onClick = {
                    onSelectTool(if (activeTool == GisActiveTool.VISIT_ROUTE) GisActiveTool.NONE else GisActiveTool.VISIT_ROUTE)
                },
                label = { Text("เส้นทางเยี่ยมบ้าน", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2563EB),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )

            // Tool: Measure Distance
            FilterChip(
                selected = activeTool == GisActiveTool.MEASURE_DISTANCE,
                onClick = {
                    onSelectTool(if (activeTool == GisActiveTool.MEASURE_DISTANCE) GisActiveTool.NONE else GisActiveTool.MEASURE_DISTANCE)
                },
                label = { Text("วัดระยะทาง", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF7C3AED),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )
        }
    }
}

/**
 * HUD Panel for Distance Measurement Mode.
 */
@Composable
fun GisMeasureHud(
    measuredPoints: List<GeoPoint>,
    totalMeters: Double,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7C3AED)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Straighten, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("โหมดวัดระยะทางจริง", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }

                Spacer(modifier = Modifier.height(4.dp))
                if (measuredPoints.isEmpty()) {
                    Text("แตะบนแผนที่ 2 จุดขึ้นไปเพื่อคำนวณระยะห่าง", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "ระยะทางรวม: ${formatDistanceFriendly(totalMeters)} (${measuredPoints.size} จุด)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF7C3AED)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (measuredPoints.isNotEmpty()) {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Filled.Delete, contentDescription = "ล้างจุด", tint = Color(0xFFDC2626))
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "ปิดเครื่องมือ")
                }
            }
        }
    }
}

/**
 * Bottom Sheet / Dialog for Epidemic 100-Meter Containment Zone.
 */
@Composable
fun EpidemicBufferInfoSheet(
    centerPoint: GeoPoint,
    radiusMeters: Double,
    affectedHouses: List<HouseSummary>,
    onSelectHouse: (HouseSummary) -> Unit,
    onNavigateGoogleMaps: (GeoPoint, String) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("วงรัศมีควบคุมโรค $radiusMeters ม.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFDC2626))
                        Text("เกณฑ์ สธ. เฝ้าระวังไข้เลือดออก & สอบสวนโรค", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "ปิด")
                }
            }

            HorizontalDivider(color = Color(0xFFDC2626).copy(alpha = 0.2f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEE2E2), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("พบครัวเรือนในรัศมีควบคุม:", fontSize = 12.sp, color = Color(0xFF991B1B))
                    Text("${affectedHouses.size} หลังคาเรือน", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF991B1B))
                }
                val totalPop = affectedHouses.sumOf { it.totalMembers }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ประชากรกลุ่มเฝ้าระวัง:", fontSize = 12.sp, color = Color(0xFF991B1B))
                    Text("$totalPop คน", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF991B1B))
                }
            }

            Text("รายการบ้านที่ต้องลงสำรวจลูกน้ำ/พ่นหมอกควัน:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(affectedHouses) { house ->
                    val dist = calculateDistanceMeters(centerPoint, GeoPoint(house.latitude!!, house.longitude!!))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { onSelectHouse(house) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("บ้านเลขที่ ${house.houseNo} (ม.${house.villageNo})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("หัวหน้า: ${house.headName ?: "ไม่ระบุ"} • ${house.totalMembers} คน", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${dist.roundToInt()} ม.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            IconButton(
                                onClick = { onNavigateGoogleMaps(GeoPoint(house.latitude!!, house.longitude!!), "บ้านเลขที่ ${house.houseNo}") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = "นำทาง", tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Navigation HUD when following a Home Visit Route.
 */
@Composable
fun FieldVisitRouteHud(
    routeState: FieldVisitRouteState,
    currentLocation: GeoPoint?,
    onNextStop: () -> Unit,
    onPreviousStop: () -> Unit,
    onNavigateGoogleMaps: (GeoPoint, String) -> Unit,
    onStopVisitDone: (HouseSummary) -> Unit,
    onCancelRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stops = routeState.stops
    if (stops.isEmpty() || !routeState.isRouteActive) return

    val currentStop = stops.getOrNull(routeState.currentStopIndex) ?: return
    val totalStops = stops.size
    val currentStopNumber = routeState.currentStopIndex + 1

    val distanceToStop = remember(currentLocation, currentStop) {
        if (currentLocation != null && currentStop.latitude != null && currentStop.longitude != null) {
            calculateDistanceMeters(currentLocation, GeoPoint(currentStop.latitude!!, currentStop.longitude!!))
        } else null
    }

    val bearingText = remember(currentLocation, currentStop) {
        if (currentLocation != null && currentStop.latitude != null && currentStop.longitude != null) {
            calculateBearingDirection(currentLocation, GeoPoint(currentStop.latitude!!, currentStop.longitude!!))
        } else ""
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFF2563EB).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$currentStopNumber", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "จุดแวะที่ $currentStopNumber จาก $totalStops หลัง",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1E40AF)
                    )
                }

                TextButton(onClick = onCancelRoute) {
                    Text("สิ้นสุดเส้นทาง", color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }

            // Target house info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("บ้านเลขที่ ${currentStop.houseNo} (หมู่ ${currentStop.villageNo})", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1E3A8A))
                    Text("หัวหน้าบ้าน: ${currentStop.headName ?: "ไม่ระบุ"} • สมาชิก: ${currentStop.totalMembers} คน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (currentStop.elderly > 0) {
                        Text("⚠️ ผู้สูงอายุในบ้าน: ${currentStop.elderly} คน", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                }

                if (distanceToStop != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDistanceFriendly(distanceToStop), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF2563EB))
                        Text(bearingText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (currentStop.latitude != null && currentStop.longitude != null) {
                            onNavigateGoogleMaps(GeoPoint(currentStop.latitude!!, currentStop.longitude!!), "บ้านเลขที่ ${currentStop.houseNo}")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("นำทาง GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onStopVisitDone(currentStop)
                        onNextStop()
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (currentStopNumber < totalStops) "เยี่ยมแล้ว (หลังต่อไป)" else "เยี่ยมเสร็จสิ้น", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
