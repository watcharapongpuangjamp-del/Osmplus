package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HouseSummary
import com.example.data.Household
import com.example.data.DataStatus
import com.example.data.PopulationEvent
import com.example.data.PopulationEventType
import com.example.data.sync.SyncState
import com.example.ui.theme.*
import com.example.ui.components.IdCardScannerDialog
import com.example.viewmodel.PersonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import com.example.data.api.NominatimService
import com.example.data.api.NominatimResponse


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: PersonViewModel,
    targetHouseholdId: Long = -1L,
    onHouseClick: (Long) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val houseSummary by viewModel.houseSummary.collectAsStateWithLifecycle()
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()
    val allHouseholdsWithPersons by viewModel.allHouseholdsWithPersons.collectAsStateWithLifecycle()
    val allScreenings by viewModel.allScreenings.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Village Baseline GIS Data State
    val villageBaselineRepository = remember { com.example.data.village.VillageBaselineRepository(context) }
    var villageBaselineList by remember { mutableStateOf<List<com.example.data.village.VillageBaseline>>(emptyList()) }
    var showVillageMarkers by remember { mutableStateOf(true) }
    var selectedVillageBaseline by remember { mutableStateOf<com.example.data.village.VillageBaseline?>(null) }

    LaunchedEffect(Unit) {
        villageBaselineList = villageBaselineRepository.getAllVillages()
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedHouse by remember { mutableStateOf<HouseSummary?>(null) }
    var selectedEvent by remember { mutableStateOf<PopulationEvent?>(null) }
    var showHouseholdSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var displayMode by remember { mutableStateOf(MapDisplayMode.HOUSEHOLDS) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PopulationFilter.ALL) }
    var showStatsPanel by remember { mutableStateOf(false) }

    // Geocoding states
    val nominatimService = remember { NominatimService.create() }
    var locationSearchResults by remember { mutableStateOf<List<NominatimResponse>>(emptyList()) }
    var isSearchingLocation by remember { mutableStateOf(false) }


    // Map Layer and House Marker Style State
    var selectedMapLayer by remember { mutableStateOf(MapLayerType.STANDARD_2D) }
    var selectedMarkerStyle by remember { mutableStateOf(MarkerStyle.PIN_3D_HOUSE) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var isClusteringEnabled by remember { mutableStateOf(true) }
    var isHealthRiskMode by remember { mutableStateOf(false) }
    var showResponsibilityPolygons by remember { mutableStateOf(false) }

    // Map of householdId to its computed HealthRiskLevel
    val householdRiskMap = remember(allHouseholdsWithPersons, allScreenings) {
        val map = mutableMapOf<Long, HealthRiskLevel>()
        allHouseholdsWithPersons.forEach { hp ->
            val householdId = hp.household.id
            val persons = hp.persons
            if (persons.isEmpty()) {
                map[householdId] = HealthRiskLevel.UNSCREENED
            } else {
                var hasHighRisk = false
                var hasMediumRisk = false
                var hasScreening = false
                persons.forEach { person ->
                    val personScreenings = allScreenings.filter { s -> s.personId == person.id }
                    if (personScreenings.isNotEmpty()) {
                        hasScreening = true
                        val latest = personScreenings.maxByOrNull { it.timestamp }
                        if (latest != null) {
                            val sys = latest.systolic ?: 0
                            val dia = latest.diastolic ?: 0
                            val sugar = latest.bloodSugar ?: 0
                            val temp = latest.temperature ?: 0.0
                            val oxy = latest.oxygenSaturation ?: 100
                            val bmiVal = latest.bmi ?: 0.0
                            
                            // High risk: high fever (e.g. dengue/infection indicator), severe hypertension, diabetic, low oxygen, or obese
                            if (sys >= 140 || dia >= 90 || sugar >= 126 || temp >= 38.5 || oxy < 95 || bmiVal >= 30.0) {
                                hasHighRisk = true
                            } else if (sys in 120..139 || dia in 80..89 || sugar in 100..125 || temp in 37.5..38.4 || bmiVal >= 25.0) {
                                hasMediumRisk = true
                            }
                        }
                    }
                }
                map[householdId] = when {
                    hasHighRisk -> HealthRiskLevel.HIGH
                    hasMediumRisk -> HealthRiskLevel.MEDIUM
                    hasScreening -> HealthRiskLevel.LOW
                    else -> HealthRiskLevel.UNSCREENED
                }
            }
        }
        map
    }

    // Pinning state
    var isPinningMode by remember { mutableStateOf(false) }
    var pinningTargetHousehold by remember { mutableStateOf<Household?>(null) }
    var pendingPinLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showHouseholdPickerDialog by remember { mutableStateOf(false) }
    var showUnpinnedHousesSheet by remember { mutableStateOf(false) }
    var houseToClearLocation by remember { mutableStateOf<HouseSummary?>(null) }
    var currentUserLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showAddHouseholdDialog by remember { mutableStateOf(false) }

    // Advanced GIS Field Assistance States
    var activeGisTool by remember { mutableStateOf(GisActiveTool.NONE) }
    var measuredPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var epidemicBufferState by remember { mutableStateOf(EpidemicBufferState()) }
    var visitRouteState by remember { mutableStateOf(FieldVisitRouteState()) }

    val totalMeasuredDistanceMeters = remember(measuredPoints) {
        if (measuredPoints.size < 2) 0.0
        else {
            var sum = 0.0
            for (i in 0 until measuredPoints.size - 1) {
                sum += calculateDistanceMeters(measuredPoints[i], measuredPoints[i + 1])
            }
            sum
        }
    }

    val selectedHousePersons = remember(selectedHouse, allHouseholdsWithPersons) {
        allHouseholdsWithPersons.find { it.household.id == selectedHouse?.householdId }?.persons ?: emptyList()
    }

    val mappedHouses = remember(houseSummary) { houseSummary.filter { it.latitude != null && it.longitude != null } }
    val unmappedHouses = remember(houseSummary) { houseSummary.filter { it.latitude == null || it.longitude == null } }

    val filteredHouses = remember(mappedHouses, activeFilter, searchQuery, allHouseholdsWithPersons) {
        val byFilter = when (activeFilter) {
            PopulationFilter.ALL -> mappedHouses
            PopulationFilter.HIGH_DENSITY -> mappedHouses.filter { it.totalMembers >= 4 }
            PopulationFilter.EARLY_CHILD -> mappedHouses.filter { it.earlyChild > 0 }
            PopulationFilter.SCHOOL_AGE -> mappedHouses.filter { it.schoolAge > 0 }
            PopulationFilter.TEENAGER -> mappedHouses.filter { it.teenager > 0 }
            PopulationFilter.WORKING_AGE -> mappedHouses.filter { it.workingAge > 0 }
            PopulationFilter.ELDERLY -> mappedHouses.filter { it.elderly > 0 }
            PopulationFilter.LOW_DENSITY -> mappedHouses.filter { it.totalMembers in 1..2 }
        }
        if (searchQuery.isBlank()) byFilter
        else {
            val trimmedQuery = searchQuery.trim()
            byFilter.filter { house ->
                val matchesHouseNo = house.houseNo.contains(trimmedQuery, ignoreCase = true)
                val matchesHead = house.headName?.contains(trimmedQuery, ignoreCase = true) ?: false
                val matchesMembers = allHouseholdsWithPersons
                    .find { it.household.id == house.householdId }
                    ?.persons
                    ?.any { it.fullName.contains(trimmedQuery, ignoreCase = true) } ?: false
                matchesHouseNo || matchesHead || matchesMembers
            }
        }
    }

    val currentZoom = mapViewRef?.zoomLevelDouble ?: 15.0

    val displayedClusters = remember(filteredHouses, isClusteringEnabled, currentZoom) {
        if (!isClusteringEnabled || currentZoom >= 16.5) {
            filteredHouses.map { HouseCluster(it.latitude ?: 0.0, it.longitude ?: 0.0, listOf(it)) }
        } else {
            val gridFactor = Math.pow(2.0, (16.5 - currentZoom).coerceAtLeast(0.0)) * 0.008
            class MutableCluster(var latSum: Double, var lonSum: Double, val houses: MutableList<HouseSummary>, var count: Int)
            val clusters = mutableListOf<MutableCluster>()
            
            for (house in filteredHouses) {
                val lat = house.latitude ?: continue
                val lon = house.longitude ?: continue
                
                var added = false
                for (cluster in clusters) {
                    val avgLat = cluster.latSum / cluster.count
                    val avgLon = cluster.lonSum / cluster.count
                    if (Math.abs(avgLat - lat) < gridFactor && Math.abs(avgLon - lon) < gridFactor) {
                        cluster.houses.add(house)
                        cluster.latSum += lat
                        cluster.lonSum += lon
                        cluster.count++
                        added = true
                        break
                    }
                }
                if (!added) {
                    clusters.add(MutableCluster(lat, lon, mutableListOf(house), 1))
                }
            }
            
            clusters.map { c ->
                HouseCluster(
                    centerLat = c.latSum / c.count,
                    centerLon = c.lonSum / c.count,
                    houses = c.houses
                )
            }
        }
    }

    // Population statistics for distribution analysis
    val totalMappedPopulation = remember(mappedHouses) { mappedHouses.sumOf { it.totalMembers } }
    val totalVillagePopulation = remember(houseSummary) { houseSummary.sumOf { it.totalMembers } }
    val mappedElderly = remember(mappedHouses) { mappedHouses.sumOf { it.elderly } }
    val mappedChildren = remember(mappedHouses) { mappedHouses.sumOf { it.earlyChild + it.schoolAge } }
    val mappedMales = remember(mappedHouses) { mappedHouses.sumOf { it.males } }
    val mappedFemales = remember(mappedHouses) { mappedHouses.sumOf { it.females } }
    val avgPerHouse = remember(mappedHouses) {
        if (mappedHouses.isNotEmpty()) totalMappedPopulation.toDouble() / mappedHouses.size else 0.0
    }
    val coveragePercent = remember(mappedHouses, houseSummary) {
        if (houseSummary.isNotEmpty()) (mappedHouses.size * 100) / houseSummary.size else 0
    }

    val firstLocation = mappedHouses.firstOrNull()
    val initialLat = firstLocation?.latitude ?: 14.2155
    val initialLon = firstLocation?.longitude ?: 101.0723

    // Handle incoming targetHouseholdId (e.g. from HouseDetailScreen or form)
    LaunchedEffect(targetHouseholdId, houseSummary) {
        if (targetHouseholdId != -1L && houseSummary.isNotEmpty()) {
            val target = houseSummary.find { it.householdId == targetHouseholdId }
            if (target != null) {
                if (target.latitude != null && target.longitude != null) {
                    selectedHouse = target
                    mapViewRef?.controller?.animateTo(GeoPoint(target.latitude, target.longitude))
                    mapViewRef?.controller?.setZoom(17.0)
                } else {
                    val hh = viewModel.getHouseholdById(targetHouseholdId)
                    if (hh != null) {
                        pinningTargetHousehold = hh
                        isPinningMode = true
                        val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                        pendingPinLocation = center
                        Toast.makeText(context, "แตะบนแผนที่เพื่อระบุพิกัดบ้านเลขที่ ${hh.houseNo}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ย้อนกลับ",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            if (displayMode == MapDisplayMode.HOUSEHOLDS) "แผนที่พิกัดครัวเรือน (GIS)" else "แผนที่พิกัดเหตุการณ์ประชากร",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isPinningMode) "โหมดปักหมุดตำแหน่งครัวเรือน" 
                            else if (displayMode == MapDisplayMode.HOUSEHOLDS) "กระจายตัวประชากร: $totalMappedPopulation/$totalVillagePopulation คน"
                            else "แสดงเหตุการณ์ประชากรทั้งหมด ${allEvents.size} รายการ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    // Toggle Display Mode (Households vs Events)
                    IconButton(onClick = { 
                        displayMode = if (displayMode == MapDisplayMode.HOUSEHOLDS) MapDisplayMode.EVENTS else MapDisplayMode.HOUSEHOLDS
                        selectedHouse = null
                        selectedEvent = null
                    }) {
                        Icon(
                            if (displayMode == MapDisplayMode.HOUSEHOLDS) Icons.Filled.Map else Icons.Filled.Home,
                            contentDescription = "สลับโหมดการแสดงผล",
                            tint = MintAccent
                        )
                    }
                    // Toggle map layer & visual perspective dialog (2D, 3D Terrain, Satellite, House markers)
                    IconButton(onClick = { showLayerMenu = true }) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = "รูปแบบแผนที่ 3D/2D",
                            tint = Color.White
                        )
                    }
                    // Toggle population distribution statistics card
                    IconButton(onClick = { showStatsPanel = !showStatsPanel }) {
                        Icon(
                            Icons.Filled.Analytics,
                            contentDescription = "สถิติการกระจายตัว",
                            tint = if (showStatsPanel) MintAccent else Color.White
                        )
                    }
                    // Pin Mode button
                    IconButton(
                        onClick = {
                            if (isPinningMode) {
                                isPinningMode = false
                                pinningTargetHousehold = null
                                pendingPinLocation = null
                            } else {
                                isPinningMode = true
                                pinningTargetHousehold = null
                                val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                                pendingPinLocation = center
                                Toast.makeText(context, "แตะตำแหน่งบนแผนที่ หรือลากหมุดสีแดง แล้วกดบันทึกพิกัด", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(
                            if (isPinningMode) Icons.Filled.Close else Icons.Filled.AddLocationAlt,
                            contentDescription = if (isPinningMode) "ยกเลิกปักหมุด" else "ปักหมุดใหม่",
                            tint = if (isPinningMode) Color(0xFFFF6B6B) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isPinningMode) Color(0xFF065F46) else EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // OpenStreetMap View
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val config = Configuration.getInstance()
                    config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                    config.userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        val tileSource = when (selectedMapLayer) {
                            MapLayerType.STANDARD_2D -> TileSourceFactory.MAPNIK
                            MapLayerType.SATELLITE -> ESRI_SATELLITE_TILE_SOURCE
                            MapLayerType.TERRAIN_3D -> OPENTOPO_TERRAIN_TILE_SOURCE
                            MapLayerType.HYBRID_SATELLITE -> GOOGLE_HYBRID_TILE_SOURCE
                        }
                        setTileSource(tileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(initialLat, initialLon))
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapViewRef = mapView
                    
                    val desiredTileSource = when (selectedMapLayer) {
                        MapLayerType.STANDARD_2D -> TileSourceFactory.MAPNIK
                        MapLayerType.SATELLITE -> ESRI_SATELLITE_TILE_SOURCE
                        MapLayerType.TERRAIN_3D -> OPENTOPO_TERRAIN_TILE_SOURCE
                        MapLayerType.HYBRID_SATELLITE -> GOOGLE_HYBRID_TILE_SOURCE
                    }
                    if (mapView.tileProvider.tileSource.name() != desiredTileSource.name()) {
                        mapView.setTileSource(desiredTileSource)
                    }

                    mapView.overlays.removeAll { it is Marker || it is MapEventsOverlay || it is org.osmdroid.views.overlay.Polygon || it is org.osmdroid.views.overlay.Polyline }

                    // Add Touch Events Overlay for interactive map tapping and long press
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (activeGisTool == GisActiveTool.MEASURE_DISTANCE) {
                                measuredPoints = measuredPoints + p
                                return true
                            }
                            if (activeGisTool == GisActiveTool.EPIDEMIC_BUFFER) {
                                val affected = findHouseholdsWithinBuffer(p, 100.0, mappedHouses)
                                epidemicBufferState = EpidemicBufferState(
                                    center = p,
                                    radiusMeters = 100.0,
                                    affectedHouses = affected,
                                    isEnabled = true
                                )
                                return true
                            }
                            if (isPinningMode) {
                                pendingPinLocation = p
                                return true
                            }
                            selectedHouse = null
                            selectedEvent = null
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            if (activeGisTool == GisActiveTool.NONE) {
                                pendingPinLocation = p
                                isPinningMode = true
                                Toast.makeText(context, "เลือกพิกัดแล้ว กดบันทึกเพื่อกำหนดครัวเรือน", Toast.LENGTH_SHORT).show()
                                return true
                            }
                            return false
                        }
                    }
                    mapView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))

                    // Add markers based on displayMode
                    if (displayMode == MapDisplayMode.HOUSEHOLDS) {
                        displayedClusters.forEach { cluster ->
                            if (cluster.houses.size == 1) {
                                val house = cluster.houses.first()
                                val lat = house.latitude ?: return@forEach
                                val lon = house.longitude ?: return@forEach
                                val riskColor = if (isHealthRiskMode) {
                                    val level = householdRiskMap[house.householdId] ?: HealthRiskLevel.UNSCREENED
                                    android.graphics.Color.rgb(level.rgb[0], level.rgb[1], level.rgb[2])
                                } else null

                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lon)
                                    title = "บ้านเลขที่ ${house.houseNo}"
                                    snippet = "ประชากร ${house.totalMembers} คน (ชาย ${house.males}, หญิง ${house.females})"
                                    subDescription = if (isHealthRiskMode) {
                                        val level = householdRiskMap[house.householdId] ?: HealthRiskLevel.UNSCREENED
                                        "สถานะกลุ่มเสี่ยง: ${level.label}"
                                    } else if (house.elderly > 0) "ผู้สูงอายุ: ${house.elderly} คน" else null
                                    icon = createHouseholdMarkerDrawable(
                                        context = context,
                                        totalMembers = house.totalMembers,
                                        hasElderly = house.elderly > 0,
                                        hasChildren = (house.earlyChild + house.schoolAge) > 0,
                                        dataStatus = house.dataStatus,
                                        isSelected = selectedHouse?.householdId == house.householdId,
                                        markerStyle = selectedMarkerStyle,
                                        healthRiskColor = riskColor
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                marker.setOnMarkerClickListener { _, _ ->
                                    if (!isPinningMode) {
                                        selectedHouse = house
                                        showHouseholdSheet = true
                                        mapView.controller.animateTo(GeoPoint(lat, lon))
                                    }
                                    true
                                }
                                mapView.overlays.add(marker)
                            } else {
                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(cluster.centerLat, cluster.centerLon)
                                    title = "กลุ่มครัวเรือน (${cluster.houses.size} หลัง)"
                                    snippet = "แตะเพื่อซูมเข้าหรือดูรายการครัวเรือนในกลุ่มนี้"
                                    icon = createClusterMarkerDrawable(
                                        context = context,
                                        count = cluster.houses.size,
                                        isSelected = cluster.houses.any { it.householdId == selectedHouse?.householdId }
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                marker.setOnMarkerClickListener { _, _ ->
                                    if (!isPinningMode) {
                                        mapView.controller.animateTo(GeoPoint(cluster.centerLat, cluster.centerLon))
                                        mapView.controller.setZoom(mapView.zoomLevelDouble + 2.0)
                                        Toast.makeText(context, "กลุ่มครัวเรือน: ${cluster.houses.size} หลังคาเรือน (ซูมเข้าเพื่อขยาย)", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                    } else {
                        allEvents.forEach { event ->
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(event.latitude, event.longitude)
                                title = event.type.value
                                snippet = event.title
                                icon = createEventMarkerDrawable(context, event.type, selectedEvent?.id == event.id)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            marker.setOnMarkerClickListener { _, _ ->
                                if (!isPinningMode) {
                                    selectedEvent = event
                                    mapView.controller.animateTo(GeoPoint(event.latitude, event.longitude))
                                }
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    }

                    // Add Village Baseline GIS Markers when enabled
                    if (showVillageMarkers && villageBaselineList.isNotEmpty()) {
                        villageBaselineList.forEach { v ->
                            val vLat = v.latitude
                            val vLon = v.longitude
                            if (vLat != 0.0 && vLon != 0.0) {
                                val vMarker = Marker(mapView).apply {
                                    position = GeoPoint(vLat, vLon)
                                    title = "หมู่บ้าน${v.villageName}"
                                    snippet = "ต.${v.subdistrictName} อ.${v.districtName} • เป้าหมาย: ${v.totalHouseCount} หลัง (${v.totalPopulation} คน)"
                                    icon = createVillageMarkerDrawable(
                                        context = context,
                                        villageName = v.villageName,
                                        houseCount = v.totalHouseCount,
                                        isSelected = selectedVillageBaseline?.villageCode == v.villageCode
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                vMarker.setOnMarkerClickListener { _, _ ->
                                    if (!isPinningMode) {
                                        selectedVillageBaseline = v
                                        mapView.controller.animateTo(GeoPoint(vLat, vLon))
                                    }
                                    true
                                }
                                mapView.overlays.add(vMarker)
                            }
                        }
                    }

                    // Add Village Responsibility Area Polygon Overlays when enabled (Option D)
                    if (showResponsibilityPolygons && villageBaselineList.isNotEmpty()) {
                        villageBaselineList.forEach { v ->
                            val vLat = v.latitude
                            val vLon = v.longitude
                            if (vLat != 0.0 && vLon != 0.0) {
                                val circlePoints = createCirclePolygonPoints(GeoPoint(vLat, vLon), 350.0)
                                val poly = org.osmdroid.views.overlay.Polygon(mapView).apply {
                                    points = circlePoints
                                    val isSelected = selectedVillageBaseline?.villageCode == v.villageCode
                                    val fillColor = if (isSelected) {
                                        android.graphics.Color.argb(40, 59, 130, 246)
                                    } else {
                                        android.graphics.Color.argb(20, 16, 185, 129)
                                    }
                                    val strokeColor = if (isSelected) {
                                        android.graphics.Color.rgb(59, 130, 246)
                                    } else {
                                        android.graphics.Color.rgb(16, 185, 129)
                                    }
                                    getFillPaint().color = fillColor
                                    getOutlinePaint().color = strokeColor
                                    getOutlinePaint().strokeWidth = if (isSelected) 3.5f else 1.8f
                                    title = "ขอบเขตพื้นที่รับผิดชอบ: หมู่บ้าน${v.villageName}"
                                    snippet = "พื้นที่ดูแล อสม. รัศมี 350 เมตรรอบศูนย์กลางหมู่บ้าน"
                                }
                                mapView.overlays.add(poly)
                            }
                        }
                    }

                    // Add pending pin marker when in pinning mode
                    if (isPinningMode && pendingPinLocation != null) {
                        val pendingMarker = Marker(mapView).apply {
                            position = pendingPinLocation
                            title = if (pinningTargetHousehold != null) "ปักหมุดบ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "หมุดตำแหน่งใหม่"
                            snippet = "แตะบนแผนที่เพื่อย้ายตำแหน่ง หรือลากหมุดนี้"
                            icon = createPendingPinMarkerDrawable(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            isDraggable = true
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(m: Marker) {}
                                override fun onMarkerDragEnd(m: Marker) {
                                    pendingPinLocation = m.position
                                }
                                override fun onMarkerDragStart(m: Marker) {}
                            })
                        }
                        mapView.overlays.add(pendingMarker)
                    }

                    // Add current user location blue marker if available
                    currentUserLocation?.let { loc ->
                        val userMarker = Marker(mapView).apply {
                            position = loc
                            title = "ตำแหน่งของคุณ"
                            snippet = "พิกัดปัจจุบันจาก GPS"
                            icon = createUserLocationMarkerDrawable(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        mapView.overlays.add(userMarker)
                    }

                    // 1. Distance Measurement Overlay
                    if (activeGisTool == GisActiveTool.MEASURE_DISTANCE && measuredPoints.isNotEmpty()) {
                        if (measuredPoints.size > 1) {
                            val line = org.osmdroid.views.overlay.Polyline().apply {
                                setPoints(measuredPoints)
                                outlinePaint.color = android.graphics.Color.rgb(124, 58, 237)
                                outlinePaint.strokeWidth = 6f
                            }
                            mapView.overlays.add(line)
                        }
                        measuredPoints.forEachIndexed { idx, pt ->
                            val ptMarker = Marker(mapView).apply {
                                position = pt
                                title = "จุดที่ ${idx + 1}"
                                icon = createTourStopMarkerDrawable(context, idx + 1, isCurrent = false, isDone = false)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            mapView.overlays.add(ptMarker)
                        }
                    }

                    // 2. 100-Meter Epidemic Buffer Overlay
                    if (epidemicBufferState.isEnabled && epidemicBufferState.center != null) {
                        val center = epidemicBufferState.center!!
                        val circlePoints = createCirclePoints(center, epidemicBufferState.radiusMeters)
                        val circlePolygon = org.osmdroid.views.overlay.Polygon().apply {
                            points = circlePoints
                            fillPaint.color = android.graphics.Color.argb(45, 220, 38, 38)
                            outlinePaint.color = android.graphics.Color.rgb(220, 38, 38)
                            outlinePaint.strokeWidth = 4f
                        }
                        mapView.overlays.add(circlePolygon)

                        val centerMarker = Marker(mapView).apply {
                            position = center
                            title = "จุดศูนย์กลางเฝ้าระวังโรค (100 เมตร)"
                            snippet = "พบครัวเรือนในรัศมี ${epidemicBufferState.affectedHouses.size} หลัง"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        mapView.overlays.add(centerMarker)
                    }

                    // 3. Smart Field Visit Route Overlay
                    if (visitRouteState.isRouteActive && visitRouteState.stops.isNotEmpty()) {
                        val stopPoints = visitRouteState.stops.mapNotNull {
                            if (it.latitude != null && it.longitude != null) GeoPoint(it.latitude!!, it.longitude!!) else null
                        }
                        if (stopPoints.size > 1) {
                            val routeLine = org.osmdroid.views.overlay.Polyline().apply {
                                setPoints(stopPoints)
                                outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                                outlinePaint.strokeWidth = 8f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            mapView.overlays.add(routeLine)
                        }

                        visitRouteState.stops.forEachIndexed { idx, st ->
                            val lat = st.latitude ?: return@forEachIndexed
                            val lon = st.longitude ?: return@forEachIndexed
                            val isCurrent = idx == visitRouteState.currentStopIndex
                            val isDone = st.householdId in visitRouteState.completedHouseholdIds
                            val tourMarker = Marker(mapView).apply {
                                position = GeoPoint(lat, lon)
                                title = "จุดแวะที่ ${idx + 1}: บ้านเลขที่ ${st.houseNo}"
                                snippet = "สมาชิก ${st.totalMembers} คน (ผู้สูงอายุ ${st.elderly} คน)"
                                icon = createTourStopMarkerDrawable(context, idx + 1, isCurrent = isCurrent, isDone = isDone)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            tourMarker.setOnMarkerClickListener { _, _ ->
                                selectedHouse = st
                                showHouseholdSheet = true
                                true
                            }
                            mapView.overlays.add(tourMarker)
                        }
                    }

                    mapView.invalidate()
                }
            )

            // Top Search & Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cloud Sync / Offline Status Indicator Pill
                val syncIndicatorColors = when (syncState) {
                    is SyncState.Syncing -> Pair(Color(0xFF2563EB), Color(0xFFDBEAFE))
                    is SyncState.Success -> Pair(Color(0xFF059669), Color(0xFFD1FAE5))
                    is SyncState.Error -> Pair(Color(0xFFDC2626), Color(0xFFFEE2E2))
                    else -> Pair(Color(0xFF4B5563), Color(0xFFF3F4F6))
                }

                val syncIndicatorText = when (val state = syncState) {
                    is SyncState.Syncing -> state.message.ifBlank { "กำลังซิงค์ข้อมูล..." }
                    is SyncState.Success -> "ซิงค์คลาวด์แล้ว (Online)"
                    is SyncState.Error -> "ซิงค์ไม่สำเร็จ (แตะเพื่อลองใหม่)"
                    else -> "โหมดออฟไลน์ (Room Local)"
                }

                val syncIndicatorIcon = when (syncState) {
                    is SyncState.Syncing -> Icons.Filled.Sync
                    is SyncState.Success -> Icons.Filled.CloudDone
                    is SyncState.Error -> Icons.Filled.CloudOff
                    else -> Icons.Filled.Storage
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = syncIndicatorColors.second,
                    border = BorderStroke(1.dp, syncIndicatorColors.first.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .wrapContentWidth()
                        .shadow(2.dp, RoundedCornerShape(20.dp))
                        .clickable {
                            if (syncState !is SyncState.Syncing) {
                                viewModel.bidirectionalSync { result ->
                                    result.onSuccess {
                                        Toast.makeText(context, "ซิงค์ข้อมูลสำเร็จ", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "ซิงค์ไม่สำเร็จ: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = syncIndicatorColors.first
                            )
                        } else {
                            Icon(
                                syncIndicatorIcon,
                                contentDescription = null,
                                tint = syncIndicatorColors.first,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = syncIndicatorText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = syncIndicatorColors.first
                        )
                    }
                }

                // Pinning mode status banner
                AnimatedVisibility(
                    visible = isPinningMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.PinDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pinningTargetHousehold != null) "กำลังปักหมุด: บ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "โหมดปักหมุดพิกัดครัวเรือน",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    pendingPinLocation?.let { "Lat: ${String.format("%.5f", it.latitude)}, Lon: ${String.format("%.5f", it.longitude)}" }
                                        ?: "แตะตำแหน่งบนแผนที่เพื่อวางหมุด",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    isPinningMode = false
                                    pinningTargetHousehold = null
                                    pendingPinLocation = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "ยกเลิก", tint = Color.White)
                            }
                        }
                    }
                }

                // Search Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ค้นบ้านเลขที่, เจ้าบ้าน หรือชื่อสมาชิก...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            if (isSearchingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                            } else {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        isSearchingLocation = true
                                        try {
                                            val results = nominatimService.search(searchQuery)
                                            locationSearchResults = results
                                            if (results.isEmpty()) {
                                                Toast.makeText(context, "ไม่พบสถานที่ที่ระบุ", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "การเชื่อมต่อล้มเหลว หรือ ไม่พบสถานที่", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSearchingLocation = false
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.TravelExplore, contentDescription = "ค้นหาสถานที่ทั่วโลก", tint = EmeraldPrimary)
                                }
                            }
                            IconButton(onClick = { 
                                searchQuery = ""
                                locationSearchResults = emptyList()
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "ล้างค้นหา", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Global Search Results List
                AnimatedVisibility(
                    visible = locationSearchResults.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ผลการค้นหาสถานที่บนแผนที่", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                IconButton(onClick = { locationSearchResults = emptyList() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "ปิด", modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            LazyColumn {
                                items(locationSearchResults) { result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val lat = result.lat.toDoubleOrNull()
                                                val lon = result.lon.toDoubleOrNull()
                                                if (lat != null && lon != null) {
                                                    val point = GeoPoint(lat, lon)
                                                    mapViewRef?.controller?.animateTo(point)
                                                    mapViewRef?.controller?.setZoom(17.0)
                                                    locationSearchResults = emptyList()
                                                    searchQuery = result.display_name
                                                }
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = result.display_name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }


                // Population Distribution Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(PopulationFilter.entries) { filter ->
                        val count = when (filter) {
                            PopulationFilter.ALL -> mappedHouses.size
                            PopulationFilter.HIGH_DENSITY -> mappedHouses.count { it.totalMembers >= 4 }
                            PopulationFilter.EARLY_CHILD -> mappedHouses.count { it.earlyChild > 0 }
                            PopulationFilter.SCHOOL_AGE -> mappedHouses.count { it.schoolAge > 0 }
                            PopulationFilter.TEENAGER -> mappedHouses.count { it.teenager > 0 }
                            PopulationFilter.WORKING_AGE -> mappedHouses.count { it.workingAge > 0 }
                            PopulationFilter.ELDERLY -> mappedHouses.count { it.elderly > 0 }
                            PopulationFilter.LOW_DENSITY -> mappedHouses.count { it.totalMembers in 1..2 }
                        }
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text("${filter.label} ($count)") },
                            leadingIcon = if (activeFilter == filter) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    // Village Baseline GIS chip
                    item {
                        FilterChip(
                            selected = showVillageMarkers,
                            onClick = { showVillageMarkers = !showVillageMarkers },
                            label = { Text("หมุดศูนย์กลางหมู่บ้าน (${villageBaselineList.size})") },
                            leadingIcon = {
                                Icon(Icons.Filled.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A8A),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    // Unpinned houses shortcut chip
                    item {
                        ActionChip(
                            label = "ยังไม่ระบุพิกัด (${unmappedHouses.size})",
                            icon = Icons.Filled.LocationOff,
                            tint = if (unmappedHouses.isNotEmpty()) Color(0xFFE11D48) else Color.Gray,
                            onClick = { showUnpinnedHousesSheet = true }
                        )
                    }
                }

                // Advanced GIS Floating Tool Selector Bar
                AdvancedGisFloatingBar(
                    activeTool = activeGisTool,
                    onSelectTool = { tool ->
                        activeGisTool = tool
                        if (tool != GisActiveTool.MEASURE_DISTANCE) measuredPoints = emptyList()
                        if (tool != GisActiveTool.EPIDEMIC_BUFFER) epidemicBufferState = EpidemicBufferState()
                        if (tool == GisActiveTool.VISIT_ROUTE && !visitRouteState.isRouteActive) {
                            val start = currentUserLocation ?: mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                            val elderlyHouses = mappedHouses.filter { it.elderly > 0 }
                            val targetHouses = if (elderlyHouses.isNotEmpty()) elderlyHouses else mappedHouses.take(8)
                            val optimized = optimizeVisitRouteOrder(start, targetHouses)
                            visitRouteState = FieldVisitRouteState(
                                stops = optimized,
                                currentStopIndex = 0,
                                isRouteActive = true
                            )
                            Toast.makeText(context, "สร้างเส้นทางเยี่ยมบ้านอัตโนมัติ ${optimized.size} หลัง", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCenterGps = {
                        val loc = currentUserLocation
                        if (loc != null) {
                            mapViewRef?.controller?.animateTo(loc)
                            mapViewRef?.controller?.setZoom(18.0)
                        } else {
                            Toast.makeText(context, "กำลังค้นหาสัญญาณ GPS...", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Collapsible Population Distribution Overview Card
                AnimatedVisibility(
                    visible = showStatsPanel,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = EmeraldPrimary)
                                    Text(
                                        "สรุปการกระจายตัวของประชากร",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "ครอบคลุม $coveragePercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                            }

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { if (houseSummary.isNotEmpty()) mappedHouses.size.toFloat() / houseSummary.size.toFloat() else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = EmeraldPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            // Population Breakdown Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "ประชากรบนแผนที่",
                                    value = "$totalMappedPopulation คน",
                                    subtext = "ชาย $mappedMales | หญิง $mappedFemales",
                                    color = EmeraldPrimary
                                )
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "ผู้สูงอายุ (60+)",
                                    value = "$mappedElderly คน",
                                    subtext = "กลุ่มเปราะบาง",
                                    color = Color(0xFF7C3AED)
                                )
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "เด็กเล็ก (0-12)",
                                    value = "$mappedChildren คน",
                                    subtext = "วัยเจริญเติบโต",
                                    color = Color(0xFF0284C7)
                                )
                            }

                            // Legend and Unpinned Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(end = 4.dp)
                                ) {
                                    if (isHealthRiskMode) {
                                        LegendDot(color = Color(0xFFDC2626), label = "เสี่ยงสูง")
                                        LegendDot(color = Color(0xFFEAB308), label = "เสี่ยงปานกลาง")
                                        LegendDot(color = Color(0xFF16A34A), label = "ปกติ")
                                        LegendDot(color = Color(0xFF9CA3AF), label = "ยังไม่ได้ตรวจ")
                                    } else {
                                        LegendDot(color = Color(0xFF059669), label = "1-3 คน")
                                        LegendDot(color = Color(0xFFEA580C), label = "4+ คน")
                                        LegendDot(color = Color(0xFF7C3AED), label = "ผู้สูงอายุ")
                                    }
                                }
                                if (unmappedHouses.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showUnpinnedHousesSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("ปักหมุดบ้านที่เหลือ (${unmappedHouses.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right-side Floating Control Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Map Layers and 3D Terrain Switcher FAB
                FloatingActionButton(
                    onClick = { showLayerMenu = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = EmeraldPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = "เปลี่ยนรูปแบบแผนที่ 3D/2D", modifier = Modifier.size(20.dp))
                }

                // Clustering Toggle FAB
                FloatingActionButton(
                    onClick = { isClusteringEnabled = !isClusteringEnabled },
                    containerColor = if (isClusteringEnabled) EmeraldPrimary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isClusteringEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.GroupWork,
                        contentDescription = "สลับการรวมกลุ่มหมุด (Cluster)",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Add New Household FAB
                FloatingActionButton(
                    onClick = { showAddHouseholdDialog = true },
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.AddHome,
                        contentDescription = "เพิ่มครัวเรือนใหม่",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // My GPS Location FAB
                FloatingActionButton(
                    onClick = {
                        if (locationPermissionState.status.isGranted) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    val userPoint = GeoPoint(location.latitude, location.longitude)
                                    currentUserLocation = userPoint
                                    mapViewRef?.controller?.animateTo(userPoint)
                                    mapViewRef?.controller?.setZoom(17.0)
                                    Toast.makeText(context, "ย้ายไปยังตำแหน่งปัจจุบันของคุณ", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ไม่พบตำแหน่ง GPS ปัจจุบัน", Toast.LENGTH_SHORT).show()
                                }
                            }.addOnFailureListener {
                                Toast.makeText(context, "เกิดข้อผิดพลาดในการดึง GPS", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF2563EB),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "ตำแหน่งปัจจุบันของฉัน",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Zoom In
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "ซูมเข้า", modifier = Modifier.size(20.dp))
                }

                // Zoom Out
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "ซูมออก", modifier = Modifier.size(20.dp))
                }

                // Pin Here via GPS (When in pinning mode)
                if (isPinningMode) {
                    FloatingActionButton(
                        onClick = {
                            if (locationPermissionState.status.isGranted) {
                                coroutineScope.launch {
                                    try {
                                        @SuppressLint("MissingPermission")
                                        val req = com.google.android.gms.location.CurrentLocationRequest.Builder()
                                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                                            .build()
                                        @SuppressLint("MissingPermission")
                                        val loc = fusedLocationClient.getCurrentLocation(req, null).await()
                                        if (loc != null) {
                                            val pt = GeoPoint(loc.latitude, loc.longitude)
                                            pendingPinLocation = pt
                                            mapViewRef?.controller?.animateTo(pt)
                                            mapViewRef?.controller?.setZoom(17.0)
                                            Toast.makeText(context, "วางหมุดที่ตำแหน่ง GPS ปัจจุบันแล้ว", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                locationPermissionState.launchPermissionRequest()
                            }
                        },
                        containerColor = Color(0xFFEA580C),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp).shadow(6.dp, CircleShape)
                    ) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = "วางหมุดตาม GPS ปัจจุบัน")
                    }
                }

                // My Location
                FloatingActionButton(
                    onClick = {
                        if (locationPermissionState.status.isGranted) {
                            coroutineScope.launch {
                                try {
                                    Toast.makeText(context, "กำลังค้นหาตำแหน่งของคุณ...", Toast.LENGTH_SHORT).show()
                                    @SuppressLint("MissingPermission")
                                    val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                                        .build()
                                    @SuppressLint("MissingPermission")
                                    val location = fusedLocationClient.getCurrentLocation(locationRequest, null).await()
                                    if (location != null) {
                                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                                        mapViewRef?.controller?.animateTo(geoPoint)
                                        mapViewRef?.controller?.setZoom(17.0)
                                        Toast.makeText(context, "ย้ายไปยังตำแหน่งปัจจุบันแล้ว", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ไม่พบตำแหน่งปัจจุบัน", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงพิกัด: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    },
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp).shadow(6.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "ตำแหน่งของฉัน")
                }
            }

            // Bottom Floating Card 1: Pinning Mode Confirmation Bar
            AnimatedVisibility(
                visible = isPinningMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = CardShadowTint),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, EmeraldPrimary)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF2F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PinDrop, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pinningTargetHousehold != null) "ปักหมุดบ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "พิกัดที่เลือกบนแผนที่",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    pendingPinLocation?.let { "Lat: ${String.format("%.5f", it.latitude)}, Lon: ${String.format("%.5f", it.longitude)}" } ?: "กรุณาแตะบนแผนที่",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isPinningMode = false
                                    pinningTargetHousehold = null
                                    pendingPinLocation = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ยกเลิก")
                            }

                            Button(
                                onClick = {
                                    val loc = pendingPinLocation
                                    if (loc == null) {
                                        Toast.makeText(context, "กรุณาแตะบนแผนที่เพื่อวางหมุดก่อน", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (pinningTargetHousehold != null) {
                                        val hh = pinningTargetHousehold!!
                                        viewModel.updateHouseholdLocation(
                                            householdId = hh.id,
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            provider = "MANUAL_PIN"
                                        ) { success, msg ->
                                            if (success) {
                                                Toast.makeText(context, "บันทึกพิกัดบ้านเลขที่ ${hh.houseNo} สำเร็จ", Toast.LENGTH_SHORT).show()
                                                isPinningMode = false
                                                pinningTargetHousehold = null
                                                pendingPinLocation = null
                                            } else {
                                                Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        showHouseholdPickerDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (pinningTargetHousehold != null) "บันทึกพิกัดนี้" else "เลือกครัวเรือน...")
                            }
                        }
                    }
                }
            }

            // Bottom Floating Card 2: Selected House Preview Card (Normal Mode)
            // Removed in favor of ModalBottomSheet for richer summary details

            // Bottom Floating Card 3: Selected Event Preview Card
            AnimatedVisibility(
                visible = selectedEvent != null && !isPinningMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                selectedEvent?.let { event ->
                    PopulationEventCard(
                        event = event,
                        onClose = { selectedEvent = null },
                        onDelete = { e -> viewModel.deleteEvent(e); selectedEvent = null }
                    )
                }
            }

            // Advanced GIS Tool HUD 1: Measure Distance
            AnimatedVisibility(
                visible = activeGisTool == GisActiveTool.MEASURE_DISTANCE,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                GisMeasureHud(
                    measuredPoints = measuredPoints,
                    totalMeters = totalMeasuredDistanceMeters,
                    onReset = { measuredPoints = emptyList() },
                    onClose = {
                        activeGisTool = GisActiveTool.NONE
                        measuredPoints = emptyList()
                    }
                )
            }

            // Advanced GIS Tool HUD 2: Epidemic Buffer Sheet
            AnimatedVisibility(
                visible = epidemicBufferState.isEnabled && epidemicBufferState.center != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                if (epidemicBufferState.center != null) {
                    EpidemicBufferInfoSheet(
                        centerPoint = epidemicBufferState.center!!,
                        radiusMeters = epidemicBufferState.radiusMeters,
                        affectedHouses = epidemicBufferState.affectedHouses,
                        onSelectHouse = { h ->
                            selectedHouse = h
                            showHouseholdSheet = true
                            if (h.latitude != null && h.longitude != null) {
                                mapViewRef?.controller?.animateTo(GeoPoint(h.latitude!!, h.longitude!!))
                            }
                        },
                        onNavigateGoogleMaps = { dest, label ->
                            launchGoogleMapsNavigation(context, dest, label)
                        },
                        onClose = {
                            epidemicBufferState = EpidemicBufferState()
                            if (activeGisTool == GisActiveTool.EPIDEMIC_BUFFER) activeGisTool = GisActiveTool.NONE
                        }
                    )
                }
            }

            // Advanced GIS Tool HUD 3: Field Visit Route HUD
            AnimatedVisibility(
                visible = visitRouteState.isRouteActive,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                FieldVisitRouteHud(
                    routeState = visitRouteState,
                    currentLocation = currentUserLocation,
                    onNextStop = {
                        if (visitRouteState.currentStopIndex < visitRouteState.stops.size - 1) {
                            val nextIdx = visitRouteState.currentStopIndex + 1
                            visitRouteState = visitRouteState.copy(currentStopIndex = nextIdx)
                            val nextStop = visitRouteState.stops[nextIdx]
                            if (nextStop.latitude != null && nextStop.longitude != null) {
                                mapViewRef?.controller?.animateTo(GeoPoint(nextStop.latitude!!, nextStop.longitude!!))
                            }
                        } else {
                            Toast.makeText(context, "เยี่ยมครบทุกหลังคาเรือนในเส้นทางแล้ว!", Toast.LENGTH_LONG).show()
                        }
                    },
                    onPreviousStop = {
                        if (visitRouteState.currentStopIndex > 0) {
                            val prevIdx = visitRouteState.currentStopIndex - 1
                            visitRouteState = visitRouteState.copy(currentStopIndex = prevIdx)
                        }
                    },
                    onNavigateGoogleMaps = { dest, label ->
                        launchGoogleMapsNavigation(context, dest, label)
                    },
                    onStopVisitDone = { h ->
                        visitRouteState = visitRouteState.copy(
                            completedHouseholdIds = visitRouteState.completedHouseholdIds + h.householdId
                        )
                        Toast.makeText(context, "บันทึกการเยี่ยมบ้านเลขที่ ${h.houseNo} เรียบร้อย", Toast.LENGTH_SHORT).show()
                    },
                    onCancelRoute = {
                        visitRouteState = FieldVisitRouteState()
                        if (activeGisTool == GisActiveTool.VISIT_ROUTE) activeGisTool = GisActiveTool.NONE
                    }
                )
            }
        }

        // Village Baseline GIS Information Dialog
        if (selectedVillageBaseline != null) {
            val village = selectedVillageBaseline!!
            AlertDialog(
                onDismissRequest = { selectedVillageBaseline = null },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.LocationCity,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "ข้อมูล GIS หมู่บ้าน${village.villageName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "ที่ตั้ง: หมู่บ้าน${village.villageName} (หมู่ ${village.villageNo})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ตำบล${village.subdistrictName} อำเภอ${village.districtName} จังหวัด${village.provinceName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Target Statistics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("หลังคาเรือนเป้าหมาย", style = MaterialTheme.typography.labelSmall)
                                    Text("${village.totalHouseCount} หลัง", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("ประชากรรวมเป้าหมาย", style = MaterialTheme.typography.labelSmall)
                                    Text("${village.totalPopulation} คน", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                    Text("(ชาย ${village.menCount} / หญิง ${village.womenCount})", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Infrastructure Details
                        if (village.mainRoadName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AddRoad, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Text("ถนนสายหลัก: ${village.mainRoadName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (village.riverName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Water, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                Text("แหล่งน้ำ/สายน้ำ: ${village.riverName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (village.damName.isNotBlank() || village.reservoirName.isNotBlank() || village.weirName.isNotBlank()) {
                            val waterAsset = listOf(village.damName, village.reservoirName, village.weirName).filter { it.isNotBlank() }.joinToString(", ")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Pool, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                Text("ชลประทาน/ฝาย: $waterAsset", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (village.localGovName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                Text("อปท. ในพื้นที่: ${village.localGovName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mapViewRef?.controller?.animateTo(GeoPoint(village.latitude, village.longitude))
                            mapViewRef?.controller?.setZoom(16.0)
                            selectedVillageBaseline = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ซูมไปยังจุดนี้")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedVillageBaseline = null }) {
                        Text("ปิด")
                    }
                }
            )
        }

        // Household Summary Bottom Sheet
        // Dialog: Household Details Sheet
        if (showHouseholdSheet && selectedHouse != null) {
            selectedHouse?.let { house ->
                HouseholdDetailBottomSheet(
                    house = house,
                    persons = selectedHousePersons,
                    sheetState = sheetState,
                    onDismiss = { showHouseholdSheet = false },
                    onDeleteLocation = { h -> houseToClearLocation = h; showHouseholdSheet = false },
                    onMovePin = { hhId, point ->
                        coroutineScope.launch {
                            val hh = viewModel.getHouseholdById(hhId)
                            if (hh != null) {
                                pinningTargetHousehold = hh
                                pendingPinLocation = point
                                isPinningMode = true
                                showHouseholdSheet = false
                            }
                        }
                    },
                    onViewDetails = { hhId -> onHouseClick(hhId) },
                    initialLat = initialLat,
                    initialLon = initialLon
                )
            }
        }
    }

    // Dialog: Household Picker (When dropping pin and selecting which house to assign)
    if (showHouseholdPickerDialog) {
        HouseholdPickerDialog(
            households = allHouseholdsWithPersons.map { it.household },
            unmappedHouseholdIds = unmappedHouses.map { it.householdId }.toSet(),
            onDismiss = { showHouseholdPickerDialog = false },
            onSelect = { household ->
                val loc = pendingPinLocation
                if (loc != null) {
                    viewModel.updateHouseholdLocation(
                        householdId = household.id,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        provider = "MANUAL_PIN"
                    ) { success, msg ->
                        if (success) {
                            Toast.makeText(context, "บันทึกพิกัดบ้านเลขที่ ${household.houseNo} สำเร็จ", Toast.LENGTH_SHORT).show()
                            isPinningMode = false
                            pinningTargetHousehold = null
                            pendingPinLocation = null
                            showHouseholdPickerDialog = false
                        } else {
                            Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // Dialog: Add New Household Manually
    if (showAddHouseholdDialog) {
        val center = mapViewRef?.mapCenter
        AddHouseholdDialog(
            initialLat = center?.latitude,
            initialLon = center?.longitude,
            onDismiss = { showAddHouseholdDialog = false },
            onSave = { hNo: String, name: String, lat: Double?, lon: Double? ->
                if (hNo.isBlank()) {
                    Toast.makeText(context, "กรุณากรอกบ้านเลขที่", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addNewHouseholdWithHead(
                        houseNo = hNo,
                        headName = name,
                        latitude = lat,
                        longitude = lon
                    ) { newId ->
                        Toast.makeText(context, "บันทึกครัวเรือนใหม่สำเร็จ (ID: $newId)", Toast.LENGTH_SHORT).show()
                        showAddHouseholdDialog = false
                        if (lat != null && lon != null) {
                            mapViewRef?.controller?.animateTo(GeoPoint(lat, lon))
                        }
                    }
                }
            }
        )
    }

    // Bottom Sheet / Dialog: Unpinned Houses List
    if (showUnpinnedHousesSheet) {
        UnpinnedHousesDialog(
            unmappedHouses = unmappedHouses,
            onDismiss = { showUnpinnedHousesSheet = false },
            onPinHouse = { houseSummaryItem ->
                coroutineScope.launch {
                    val hh = viewModel.getHouseholdById(houseSummaryItem.householdId)
                    if (hh != null) {
                        pinningTargetHousehold = hh
                        isPinningMode = true
                        val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                        pendingPinLocation = center
                        showUnpinnedHousesSheet = false
                        Toast.makeText(context, "แตะบนแผนที่เพื่อระบุตำแหน่งบ้านเลขที่ ${hh.houseNo}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Dialog: Confirm Clear Pin Location
    houseToClearLocation?.let { house ->
        AlertDialog(
            onDismissRequest = { houseToClearLocation = null },
            title = { Text("ยืนยันการลบพิกัด") },
            text = { Text("คุณต้องการลบพิกัด GPS ของบ้านเลขที่ ${house.houseNo} ใช่หรือไม่? (ข้อมูลสมาชิกและประวัติครัวเรือนจะยังคงอยู่ครบถ้วน)") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeHouseholdLocation(house.householdId) { success, msg ->
                            if (success) {
                                Toast.makeText(context, "ลบพิกัดเรียบร้อย", Toast.LENGTH_SHORT).show()
                                selectedHouse = null
                            } else {
                                Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                            }
                        }
                        houseToClearLocation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ลบพิกัด")
                }
            },
            dismissButton = {
                TextButton(onClick = { houseToClearLocation = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Dialog: Map Layer & Visual Perspective Selection
    if (showLayerMenu) {
        MapLayerSelectionDialog(
            currentLayer = selectedMapLayer,
            currentMarkerStyle = selectedMarkerStyle,
            isHealthRiskMode = isHealthRiskMode,
            showResponsibilityPolygons = showResponsibilityPolygons,
            onSelectLayer = { layer: MapLayerType -> selectedMapLayer = layer },
            onSelectMarkerStyle = { style: MarkerStyle -> selectedMarkerStyle = style },
            onToggleHealthRiskMode = { enabled: Boolean -> isHealthRiskMode = enabled },
            onToggleResponsibilityPolygons = { enabled: Boolean -> showResponsibilityPolygons = enabled },
            onDismiss = { showLayerMenu = false }
        )
    }
}
