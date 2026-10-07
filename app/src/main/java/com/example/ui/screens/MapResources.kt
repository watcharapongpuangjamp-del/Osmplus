package com.example.ui.screens

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

enum class MapLayerType(val title: String, val subtitle: String) {
    STANDARD_2D("แผนที่มาตรฐาน (2D)", "เส้นทาง คลอง และชื่อสถานที่คมชัด"),
    SATELLITE("ภาพถ่ายดาวเทียม (เห็นหลังคาบ้าน)", "Esri World Imagery เห็นตัวบ้านและหลังคาจริง"),
    TERRAIN_3D("ภูมิประเทศ 3D / Relief", "OpenTopoMap ระดับความสูง ภูเขา แม่น้ำ ลาดชัน"),
    HYBRID_SATELLITE("ดาวเทียม + เส้นทาง (Hybrid)", "Google Hybrid มองเห็นสิ่งปลูกสร้างพร้อมถนน")
}

enum class MarkerStyle(val title: String) {
    PIN_3D_HOUSE("หมุด 3D ทรงบ้านเรือน"),
    BADGE_2D("หมุด 2D สัญลักษณ์ประชากร")
}

enum class MapDisplayMode(val title: String) {
    HOUSEHOLDS("ครัวเรือน"),
    EVENTS("เหตุการณ์ประชากร")
}

enum class PopulationFilter(val label: String) {
    ALL("ทั้งหมด"),
    HIGH_DENSITY("หนาแน่น (4+ คน)"),
    EARLY_CHILD("เด็กปฐมวัย (0-5)"),
    SCHOOL_AGE("เด็กวัยเรียน (6-12)"),
    TEENAGER("วัยรุ่น (13-20)"),
    WORKING_AGE("วัยทำงาน (21-59)"),
    ELDERLY("ผู้สูงอายุ (60+)"),
    LOW_DENSITY("1-2 คน")
}

enum class HealthRiskLevel(val label: String, val colorHex: String, val rgb: IntArray) {
    HIGH("เสี่ยงสูง", "#DC2626", intArrayOf(220, 38, 38)),
    MEDIUM("เสี่ยงปานกลาง", "#EAB308", intArrayOf(234, 179, 8)),
    LOW("ปกติ/ความเสี่ยงต่ำ", "#16A34A", intArrayOf(22, 163, 74)),
    UNSCREENED("ยังไม่ได้ตรวจคัดกรอง", "#9CA3AF", intArrayOf(156, 163, 175))
}

// Custom Tile Sources for Satellite, Terrain 3D, and Google Hybrid
val ESRI_SATELLITE_TILE_SOURCE: ITileSource = object : OnlineTileSourceBase(
    "EsriSatellite",
    0,
    19,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x$mImageFilenameEnding"
    }
}

val OPENTOPO_TERRAIN_TILE_SOURCE: ITileSource = XYTileSource(
    "OpenTopoMap",
    0,
    17,
    256,
    ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/"
    ),
    "© OpenTopoMap, © OpenStreetMap contributors"
)

val GOOGLE_HYBRID_TILE_SOURCE: ITileSource = object : OnlineTileSourceBase(
    "GoogleHybrid",
    0,
    20,
    256,
    "",
    arrayOf("https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "https://mt1.google.com/vt/lyrs=y&x=$x&y=$y&z=$zoom"
    }
}
