package com.example.ui.screens

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DataStatus
import com.example.data.HouseSummary
import com.example.data.PopulationEventType
import org.osmdroid.util.GeoPoint

@Composable
fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
fun StatMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(subtext, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MiniBadge(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun createHouseholdMarkerDrawable(
    context: Context,
    totalMembers: Int,
    hasElderly: Boolean,
    hasChildren: Boolean,
    dataStatus: DataStatus = DataStatus.VERIFIED,
    isSelected: Boolean,
    markerStyle: MarkerStyle = MarkerStyle.PIN_3D_HOUSE,
    healthRiskColor: Int? = null
): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (44 * density).toInt()
    val height = (54 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val pinColor = when {
        healthRiskColor != null -> healthRiskColor
        isSelected -> android.graphics.Color.rgb(16, 185, 129)
        hasElderly -> android.graphics.Color.rgb(124, 58, 237)
        totalMembers >= 4 -> android.graphics.Color.rgb(234, 88, 12)
        hasChildren -> android.graphics.Color.rgb(2, 132, 199)
        else -> android.graphics.Color.rgb(5, 150, 105)
    }

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
    }
    val shadowRadius = 16 * density
    canvas.drawCircle(width / 2f, 19 * density, shadowRadius + (2 * density), shadowPaint)

    if (markerStyle == MarkerStyle.PIN_3D_HOUSE) {
        val path = Path()
        val circleCenterY = 20 * density
        path.moveTo(width / 2f, 2 * density)
        path.lineTo(width - (4 * density), 16 * density)
        path.lineTo(width - (6 * density), circleCenterY + (10 * density))
        path.lineTo(width / 2f, height - (2 * density))
        path.lineTo(6 * density, circleCenterY + (10 * density))
        path.lineTo(4 * density, 16 * density)
        path.close()

        paint.color = pinColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawPath(path, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = if (totalMembers > 99) "99+" else "$totalMembers"
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, width / 2f, circleCenterY + (4 * density) + (textBounds.height() / 2f), textPaint)
    } else {
        val path = Path()
        val circleCenterY = 18 * density
        val circleRadius = 15 * density
        path.addCircle(width / 2f, circleCenterY, circleRadius, Path.Direction.CW)
        val trianglePath = Path().apply {
            moveTo((width / 2f) - (8 * density), circleCenterY + (9 * density))
            lineTo(width / 2f, height - (2 * density))
            lineTo((width / 2f) + (8 * density), circleCenterY + (9 * density))
            close()
        }
        path.op(trianglePath, Path.Op.UNION)
        paint.color = pinColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawPath(path, strokePaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = if (totalMembers > 99) "99+" else "$totalMembers"
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, width / 2f, circleCenterY + (textBounds.height() / 2f), textPaint)
    }

    if (hasElderly) {
        val badgeCenterY = if (markerStyle == MarkerStyle.PIN_3D_HOUSE) 10 * density else 8 * density
        paint.color = android.graphics.Color.rgb(251, 191, 36)
        paint.style = Paint.Style.FILL
        canvas.drawCircle((width / 2f) + (11 * density), badgeCenterY, 4.5f * density, paint)
    }
    return BitmapDrawable(context.resources, bitmap)
}

fun createPendingPinMarkerDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (46 * density).toInt()
    val height = (56 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.rgb(220, 38, 38)
    val circleCenterY = 19 * density
    canvas.drawCircle(width / 2f, circleCenterY, 16 * density, paint)
    return BitmapDrawable(context.resources, bitmap)
}

fun createEventMarkerDrawable(context: Context, type: PopulationEventType, isSelected: Boolean): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (42 * density).toInt()
    val height = (52 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.BLUE
    canvas.drawCircle(width / 2f, 17 * density, 14 * density, paint)
    return BitmapDrawable(context.resources, bitmap)
}

data class HouseCluster(
    val centerLat: Double,
    val centerLon: Double,
    val houses: List<HouseSummary>
)

fun createClusterMarkerDrawable(context: Context, count: Int, isSelected: Boolean): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.GREEN
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (4 * density), paint)
    return BitmapDrawable(context.resources, bitmap)
}

fun createUserLocationMarkerDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer glow / pulse circle
    paint.color = android.graphics.Color.argb(70, 37, 99, 235)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // Inner solid blue circle
    paint.color = android.graphics.Color.rgb(37, 99, 235)
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (6 * density), paint)

    // White center dot
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, 4 * density, paint)

    return BitmapDrawable(context.resources, bitmap)
}

fun createCirclePolygonPoints(center: GeoPoint, radiusMeters: Double): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    val earthRadius = 6378137.0
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val dR = radiusMeters / earthRadius
    for (i in 0 until 16) {
        val bearing = 2.0 * Math.PI * i / 16.0
        val tLat = Math.asin(Math.sin(latRad) * Math.cos(dR) + Math.cos(latRad) * Math.sin(dR) * Math.cos(bearing))
        val tLon = lonRad + Math.atan2(Math.sin(bearing) * Math.sin(dR) * Math.cos(latRad), Math.cos(dR) - Math.sin(latRad) * Math.sin(tLat))
        points.add(GeoPoint(Math.toDegrees(tLat), Math.toDegrees(tLon)))
    }
    if (points.isNotEmpty()) points.add(points.first())
    return points
}
