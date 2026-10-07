package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.domain.VhvReportExporter
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.PersonViewModel
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Screen for Official VHV Monthly Performance Reports (รายงานผลการปฏิบัติงาน อสม. ประจำเดือน).
 * Supports OSM 1 (Monthly Summary), OSM 2 (Elderly & Vulnerable Care), and Family Health Folder printing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VhvMonthlyReportScreen(
    viewModel: PersonViewModel,
    authViewModel: AuthViewModel? = null,
    onBack: () -> Unit = {},
    onNavigateToHouseDetail: (Long) -> Unit = {},
    onNavigateToScreening: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isDark = isSystemInDarkTheme()

    val allHouseholds by viewModel.allHouseholds.collectAsStateWithLifecycle()
    val allPersons by viewModel.allPersons.collectAsStateWithLifecycle()
    val allScreenings by viewModel.allScreenings.collectAsStateWithLifecycle()
    val userProfile by authViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedVillage by remember { mutableStateOf("ALL") }
    var selectedMonthYear by remember {
        val now = LocalDate.now()
        val thaiYear = now.year + 543
        val monthNames = listOf("มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม")
        mutableStateOf("${monthNames[now.monthValue - 1]} $thaiYear")
    }

    val villageList = remember {
        listOf(
            "ALL" to "ทุกหมู่บ้าน (13 หมู่บ้าน)",
            "1" to "หมู่ 1 บ้านหนองเคี่ยม",
            "2" to "หมู่ 2 บ้านคลองผักหนาม",
            "3" to "หมู่ 3 บ้านป่าขะ",
            "4" to "หมู่ 4 บ้านท่ามะเฟือง",
            "5" to "หมู่ 5 บ้านโคกประเสริฐ",
            "6" to "หมู่ 6 บ้านหนองยาง",
            "7" to "หมู่ 7 บ้านกร่างประตูวัง",
            "8" to "หมู่ 8 บ้านคลองส่ง",
            "9" to "หมู่ 9 บ้านคลองกระโดน",
            "10" to "หมู่ 10 บ้านต้นกระบก",
            "11" to "หมู่ 11 บ้านดงขี้พุก",
            "12" to "หมู่ 12 บ้านทุ่งกระโปรง",
            "13" to "หมู่ 13 บ้านคลองนางหงษ์"
        )
    }

    // Auto-select user's village if available
    LaunchedEffect(userProfile) {
        val profileVillage = userProfile?.villageNo
        if (!profileVillage.isNullOrBlank() && selectedVillage == "ALL") {
            selectedVillage = profileVillage
        }
    }

    val reportSummary = remember(selectedVillage, allHouseholds, allPersons, allScreenings) {
        VhvReportExporter.computeOsm1Summary(selectedVillage, allHouseholds, allPersons, allScreenings)
    }

    val filteredHouseholds = remember(selectedVillage, allHouseholds) {
        if (selectedVillage == "ALL") allHouseholds else allHouseholds.filter { it.villageNo == selectedVillage }
    }

    val filteredHouseholdIds = remember(filteredHouseholds) {
        filteredHouseholds.map { it.id }.toSet()
    }

    val filteredPersons = remember(selectedVillage, allPersons, filteredHouseholdIds) {
        allPersons.filter { it.householdId in filteredHouseholdIds }
    }

    val now = remember { LocalDate.now() }
    val elderlyPersons = remember(filteredPersons, now) {
        filteredPersons.filter { p ->
            p.personStatus == PersonStatus.ALIVE &&
                    p.birthDate != null &&
                    Period.between(p.birthDate, now).years >= 60
        }.sortedByDescending { Period.between(it.birthDate, now).years }
    }

    val latestScreeningMap = remember(allScreenings) {
        allScreenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }
    }

    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "รายงานการปฏิบัติงาน อสม.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "รายงาน อสม. 1 & อสม. 2 • $selectedMonthYear",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_report_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (activity != null) {
                                if (selectedTabIndex == 0) {
                                    val html = VhvReportExporter.generateOsm1Html(reportSummary, selectedVillage)
                                    VhvReportExporter.printHtmlDocument(activity, html, "รายงาน_อสม1_$selectedVillage")
                                } else if (selectedTabIndex == 1) {
                                    val html = VhvReportExporter.generateOsm2Html(selectedVillage, allHouseholds, allPersons, allScreenings)
                                    VhvReportExporter.printHtmlDocument(activity, html, "รายงาน_อสม2_$selectedVillage")
                                } else {
                                    Toast.makeText(context, "กรุณาเลือกบ้านในแท็บแฟ้มสุขภาพเพื่อพิมพ์", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_print_pdf")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "พิมพ์ A4 / Save PDF", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            try {
                                if (selectedTabIndex == 0) {
                                    val uri = VhvReportExporter.generateOsm1Excel(context, selectedVillage, allHouseholds, allPersons, allScreenings)
                                    VhvReportExporter.shareFile(context, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "รายงาน อสม. 1")
                                } else {
                                    val uri = VhvReportExporter.generateOsm2Excel(context, selectedVillage, allHouseholds, allPersons, allScreenings)
                                    VhvReportExporter.shareFile(context, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "รายงาน อสม. 2 ผู้สูงอายุ")
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "ส่งออกไฟล์ล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.testTag("btn_export_excel")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "ส่งออก Excel", tint = Color.White)
                    }
                    ThemeQuickToggleButton(iconTint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Village Selector Filter Chips
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "เลือกพื้นที่รายงาน:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = villageList.find { it.first == selectedVillage }?.second ?: selectedVillage,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        villageList.forEach { (code, label) ->
                            val isSelected = selectedVillage == code
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVillage = code },
                                label = {
                                    Text(
                                        text = if (code == "ALL") "ทุกหมู่บ้าน" else "ม.$code",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Tabs: อสม. 1, อสม. 2, แฟ้มครอบครัว
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = EmeraldPrimary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("รายงาน อสม. 1", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("รายงาน อสม. 2", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Elderly, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("แฟ้มสุขภาพบ้าน", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> Osm1ReportContent(
                    summary = reportSummary,
                    selectedVillage = selectedVillage,
                    onPrint = {
                        activity?.let {
                            val html = VhvReportExporter.generateOsm1Html(reportSummary, selectedVillage)
                            VhvReportExporter.printHtmlDocument(it, html, "รายงาน_อสม1_$selectedVillage")
                        }
                    },
                    onExportExcel = {
                        try {
                            val uri = VhvReportExporter.generateOsm1Excel(context, selectedVillage, allHouseholds, allPersons, allScreenings)
                            VhvReportExporter.shareFile(context, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "รายงาน อสม. 1")
                        } catch (e: Exception) {
                            Toast.makeText(context, "ส่งออกล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onShareText = {
                        val shareMsg = buildString {
                            appendLine("📊 สรุปรายงานผลการปฏิบัติงาน อสม. 1")
                            appendLine("พื้นที่: ${villageList.find { it.first == selectedVillage }?.second ?: selectedVillage}")
                            appendLine("เดือน: $selectedMonthYear")
                            appendLine("-------------------------")
                            appendLine("• ครัวเรือน: ${reportSummary.totalHouseholds} หลัง (มีพิกัด ${reportSummary.householdsWithGps} หลัง)")
                            appendLine("• ประชากรมีชีวิต: ${reportSummary.alivePopulation} คน (ชาย ${reportSummary.maleCount}, หญิง ${reportSummary.femaleCount})")
                            appendLine("• 5 กลุ่มวัย: เด็กปฐมวัย ${reportSummary.earlyChildCount}, วัยเรียน ${reportSummary.schoolAgeCount}, วัยรุ่น ${reportSummary.teenagerCount}, วัยทำงาน ${reportSummary.workingAgeCount}, ผู้สูงอายุ ${reportSummary.elderlyCount}")
                            appendLine("• คัดกรองแล้ว: ${reportSummary.screenedCount} คน")
                            appendLine("• ความดันสูง: ${reportSummary.bpHigh} คน, น้ำตาลสูง: ${reportSummary.dtxHigh} คน")
                            appendLine("ส่งผ่านระบบ Smart OSM ต.ป่าขะ")
                        }
                        VhvReportExporter.shareText(context, shareMsg, "สรุปรายงาน อสม. 1")
                    }
                )
                1 -> Osm2ReportContent(
                    elderlyPersons = elderlyPersons,
                    households = filteredHouseholds,
                    screenings = allScreenings,
                    selectedVillage = selectedVillage,
                    onScreeningClick = onNavigateToScreening,
                    onPrint = {
                        activity?.let {
                            val html = VhvReportExporter.generateOsm2Html(selectedVillage, allHouseholds, allPersons, allScreenings)
                            VhvReportExporter.printHtmlDocument(it, html, "รายงาน_อสม2_$selectedVillage")
                        }
                    },
                    onExportExcel = {
                        try {
                            val uri = VhvReportExporter.generateOsm2Excel(context, selectedVillage, allHouseholds, allPersons, allScreenings)
                            VhvReportExporter.shareFile(context, uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "รายงาน อสม. 2")
                        } catch (e: Exception) {
                            Toast.makeText(context, "ส่งออกล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                2 -> FamilyFolderReportContent(
                    households = filteredHouseholds,
                    persons = allPersons,
                    screenings = allScreenings,
                    onNavigateToHouseDetail = onNavigateToHouseDetail,
                    onPrintFamilyFolder = { house, housePersons, houseScreenings ->
                        activity?.let {
                            val html = VhvReportExporter.generateFamilyFolderHtml(house, housePersons, houseScreenings)
                            VhvReportExporter.printHtmlDocument(it, html, "แฟ้มสุขภาพ_บ้านเลขที่_${house.houseNo}")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Tab 0: OSM 1 Monthly Performance Report Content.
 */
@Composable
private fun Osm1ReportContent(
    summary: VhvReportExporter.Osm1Summary,
    selectedVillage: String,
    onPrint: () -> Unit,
    onExportExcel: () -> Unit,
    onShareText: () -> Unit
) {
    val totalAlive = summary.alivePopulation.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPrint,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = EmeraldPrimary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("พิมพ์ PDF A4", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = onExportExcel,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excel .xlsx", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = onShareText,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF06C755))
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "แชร์ไป LINE", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Section 1: Demographics & Households
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("หมวดที่ 1: ข้อมูลประชากรและครัวเรือนทั่วไป", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatMiniBox("ครัวเรือนทั้งหมด", "${summary.totalHouseholds} หลัง", EmeraldPrimary, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        StatMiniBox("มีพิกัด GPS", "${summary.householdsWithGps} หลัง", Color(0xFF059669), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        StatMiniBox("ประชากรมีชีวิต", "${summary.alivePopulation} คน", Color(0xFF2563EB), Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text("ชาย: ${summary.maleCount} คน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("หญิง: ${summary.femaleCount} คน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("เสียชีวิต: ${summary.deceasedCount} คน", fontSize = 12.sp, color = Color(0xFFDC2626))
                        Text("ย้ายออก: ${summary.movedCount} คน", fontSize = 12.sp, color = Color(0xFFD97706))
                    }
                }
            }
        }

        // Section 2: VHV 5 Age Groups Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD97706)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("หมวดที่ 2: สรุปกลุ่มอายุตามเกณฑ์ อสม. (5 กลุ่ม)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    AgeGroupBarItem("1. เด็กปฐมวัย (0-5 ปี)", summary.earlyChildCount, totalAlive, Color(0xFFF59E0B), "วัคซีนครบ, DSPM สมวัย")
                    AgeGroupBarItem("2. เด็กวัยเรียน (6-12 ปี)", summary.schoolAgeCount, totalAlive, Color(0xFF0284C7), "อนามัยโรงเรียน, สุขภาพฟัน")
                    AgeGroupBarItem("3. วัยรุ่น (13-20 ปี)", summary.teenagerCount, totalAlive, Color(0xFF7C3AED), "สุขภาพจิต, ลดพฤติกรรมเสี่ยง")
                    AgeGroupBarItem("4. วัยทำงาน (21-59 ปี)", summary.workingAgeCount, totalAlive, Color(0xFF0D9488), "คัดกรอง NCDs ความดัน/เบาหวาน")
                    AgeGroupBarItem("5. ผู้สูงอายุ (60+ ปี)", summary.elderlyCount, totalAlive, Color(0xFFDC2626), "เยี่ยมบ้าน, ดูแล ADL 9 ด้าน")
                }
            }
        }

        // Section 3: Health Screening & NCDs Results
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE11D48)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("หมวดที่ 3: สรุปผลการคัดกรองสุขภาพ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "ตรวจแล้ว ${summary.screenedCount} คน",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Text("ความดันโลหิต (Blood Pressure):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HealthBadgeItem("ปกติ (<130/85)", summary.bpNormal, Color(0xFF059669), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(6.dp))
                        HealthBadgeItem("กลุ่มเสี่ยง (130-139)", summary.bpRisk, Color(0xFFD97706), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(6.dp))
                        HealthBadgeItem("สงสัยป่วย (≥140/90)", summary.bpHigh, Color(0xFFDC2626), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ระดับน้ำตาลในเลือด (DTX):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HealthBadgeItem("ปกติ (<100 mg%)", summary.dtxNormal, Color(0xFF059669), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(6.dp))
                        HealthBadgeItem("กลุ่มเสี่ยง (100-125)", summary.dtxRisk, Color(0xFFD97706), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(6.dp))
                        HealthBadgeItem("สงสัยป่วย (≥126)", summary.dtxHigh, Color(0xFFDC2626), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: OSM 2 Elderly & Vulnerable Care Report Content.
 */
@Composable
private fun Osm2ReportContent(
    elderlyPersons: List<Person>,
    households: List<Household>,
    screenings: List<HealthScreening>,
    selectedVillage: String,
    onScreeningClick: (Long) -> Unit,
    onPrint: () -> Unit,
    onExportExcel: () -> Unit
) {
    val context = LocalContext.current
    val householdMap = remember(households) { households.associateBy { it.id } }
    val latestScreeningMap = remember(screenings) {
        screenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }
    }
    val now = remember { LocalDate.now() }

    var searchQuery by remember { mutableStateOf("") }
    val filteredElderly = remember(elderlyPersons, searchQuery) {
        if (searchQuery.isBlank()) elderlyPersons
        else elderlyPersons.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.nationalId?.contains(searchQuery) == true ||
                    it.phoneNumber?.contains(searchQuery) == true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Action Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("รายงาน อสม. 2 ผู้สูงอายุ", fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF), fontSize = 15.sp)
                        Text("ทั้งหมด ${elderlyPersons.size} คน ในพื้นที่", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onPrint,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("พิมพ์ A4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onExportExcel,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Search Input
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ค้นหาชื่อผู้สูงอายุ หรือเบอร์โทร") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "ล้าง")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        if (filteredElderly.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ไม่พบรายชื่อผู้สูงอายุในเงื่อนไขที่เลือก", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filteredElderly, key = { it.id }) { person ->
                val house = householdMap[person.householdId]
                val age = person.birthDate?.let { Period.between(it, now).years } ?: 60
                val sc = latestScreeningMap[person.id]
                val sys = sc?.systolic
                val dia = sc?.diastolic
                val dtx = sc?.bloodSugar

                val isHighRisk = (sys != null && dia != null && (sys >= 140 || dia >= 90)) || (dtx != null && dtx >= 126)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isHighRisk) Color(0xFFDC2626).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(person.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (age >= 80) Color(0xFFFEE2E2) else Color(0xFFEFF6FF)
                                    ) {
                                        Text(
                                            "อายุ $age ปี",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (age >= 80) Color(0xFF991B1B) else Color(0xFF1D4ED8)
                                        )
                                    }
                                }
                                Text(
                                    "บ้านเลขที่ ${house?.houseNo ?: "-"} หมู่ ${house?.villageNo ?: "-"} • สิทธิ: ${person.healthInsurance ?: "บัตรทอง"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                if (!person.phoneNumber.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phoneNumber}"))
                                            context.startActivity(dialIntent)
                                        }
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = "โทร", tint = EmeraldPrimary)
                                    }
                                }
                                IconButton(
                                    onClick = { onScreeningClick(person.id) }
                                ) {
                                    Icon(Icons.Filled.HealthAndSafety, contentDescription = "ตรวจคัดกรอง", tint = Color(0xFFE11D48))
                                }
                            }
                        }

                        // Vitals row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val bpStr = if (sys != null && dia != null) "$sys/$dia mmHg" else "ยังไม่ได้ตรวจ BP"
                            val dtxStr = if (dtx != null) "$dtx mg/dL" else "ยังไม่ได้ตรวจน้ำตาล"

                            Text("ความดัน: $bpStr", fontSize = 12.sp, fontWeight = if (isHighRisk) FontWeight.Bold else FontWeight.Normal, color = if (sys != null && sys >= 140) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface)
                            Text("น้ำตาล: $dtxStr", fontSize = 12.sp, fontWeight = if (dtx != null && dtx >= 126) FontWeight.Bold else FontWeight.Normal, color = if (dtx != null && dtx >= 126) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Family Health Folder (แฟ้มสุขภาพประจำบ้าน).
 */
@Composable
private fun FamilyFolderReportContent(
    households: List<Household>,
    persons: List<Person>,
    screenings: List<HealthScreening>,
    onNavigateToHouseDetail: (Long) -> Unit,
    onPrintFamilyFolder: (Household, List<Person>, List<HealthScreening>) -> Unit
) {
    val personsByHousehold = remember(persons) { persons.groupBy { it.householdId } }
    var searchQuery by remember { mutableStateOf("") }

    val filteredHouseholds = remember(households, searchQuery) {
        if (searchQuery.isBlank()) households
        else households.filter {
            it.houseNo.contains(searchQuery, ignoreCase = true) ||
                    it.villageNo.contains(searchQuery) ||
                    it.houseId?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("แฟ้มประวัติสุขภาพประจำบ้าน (Family Health Folder)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "เลือกบ้านเพื่อพิมพ์ใบสรุปแฟ้มครอบครัวขนาด A4 สำหรับติดตามดูแลสุขภาพ รายชื่อสมาชิก ค่าความดัน น้ำตาล และสิทธิการรักษา",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ค้นหาบ้านเลขที่ หรือรหัสประจำบ้าน") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        items(filteredHouseholds, key = { it.id }) { house ->
            val members = personsByHousehold[house.id] ?: emptyList()
            val head = members.find { it.houseStatus == HouseholdRole.HEAD }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                            Text("บ้านเลขที่ ${house.houseNo}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "หมู่ ${house.villageNo}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                            }
                        }

                        Text("หัวหน้าบ้าน: ${head?.fullName ?: "ไม่ระบุ"} • สมาชิก: ${members.size} คน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (house.latitude != null && house.longitude != null) {
                            Text("พิกัด GPS: บันทึกแล้ว", fontSize = 11.sp, color = Color(0xFF059669))
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { onPrintFamilyFolder(house, members, screenings) }
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "พิมพ์ A4", tint = EmeraldPrimary)
                        }
                        IconButton(
                            onClick = { onNavigateToHouseDetail(house.id) }
                        ) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = "เปิดดู", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp, color = color)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgeGroupBarItem(
    title: String,
    count: Int,
    total: Int,
    color: Color,
    mission: String
) {
    val percent = if (total > 0) (count.toFloat() / total.toFloat()) else 0f
    val percentStr = String.format(Locale.US, "%.1f%%", percent * 100)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("$count คน ($percentStr)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
        }

        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )

        Text("ภารกิจ อสม.: $mission", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HealthBadgeItem(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            Text(label, fontSize = 9.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
