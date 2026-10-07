package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Elderly
import com.example.data.VhvAgeGroup
import com.example.data.PersonStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.example.data.HealthScreening
import com.example.data.Person
import com.example.viewmodel.PersonViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.GeminiViewModel
import com.example.viewmodel.GeminiUiState
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.rememberScrollState
import java.time.LocalDate
import java.time.Period
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreeningScreen(
    viewModel: PersonViewModel,
    personId: Long,
    onNavigateBack: () -> Unit,
    geminiViewModel: GeminiViewModel = viewModel()
) {
    var person by remember { mutableStateOf<Person?>(null) }
    val screenings by viewModel.getScreeningsForPerson(personId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showAiAdvice by remember { mutableStateOf(false) }
    val geminiUiState by geminiViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(personId) {
        person = viewModel.getPersonById(personId)
    }

    val weightEntries = remember(screenings) {
        screenings.reversed().filter { it.weight != null }.mapIndexed { index, item ->
            FloatEntry(index.toFloat(), item.weight!!.toFloat())
        }
    }
    val chartModel = remember(weightEntries) {
        if (weightEntries.size >= 2) entryModelOf(weightEntries) else null
    }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("th", "TH"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.health_screening_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        person?.let {
                            Text(
                                it.fullName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel_button), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.ui.theme.EmeraldPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = com.example.ui.theme.EmeraldPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_screening))
            }
        }
    ) { padding ->
        val context = LocalContext.current
        val personAge = remember(person) {
            person?.let { viewModel.calculateAge(it.birthDate, it.personStatus) }
        }
        val personAgeGroup = remember(personAge) {
            VhvAgeGroup.fromAge(personAge)
        }

        val (ageColor, ageBg) = when (personAgeGroup) {
            VhvAgeGroup.EARLY_CHILD -> Color(0xFFD97706) to Color(0xFFFEF3C7)
            VhvAgeGroup.SCHOOL_AGE -> Color(0xFF0284C7) to Color(0xFFE0F2FE)
            VhvAgeGroup.TEENAGER -> Color(0xFF7C3AED) to Color(0xFFEDE9FE)
            VhvAgeGroup.WORKING_AGE -> Color(0xFF0D9488) to Color(0xFFCCFBF1)
            VhvAgeGroup.ELDERLY -> Color(0xFFE11D48) to Color(0xFFFFE4E6)
            VhvAgeGroup.UNKNOWN -> Color(0xFF64748B) to Color(0xFFF1F5F9)
        }

        val ageIcon = when (personAgeGroup) {
            VhvAgeGroup.EARLY_CHILD -> Icons.Filled.ChildCare
            VhvAgeGroup.SCHOOL_AGE -> Icons.Filled.School
            VhvAgeGroup.TEENAGER -> Icons.Filled.SelfImprovement
            VhvAgeGroup.WORKING_AGE -> Icons.Filled.Work
            VhvAgeGroup.ELDERLY -> Icons.Filled.Elderly
            VhvAgeGroup.UNKNOWN -> Icons.Filled.Person
        }

        val ageMission = when (personAgeGroup) {
            VhvAgeGroup.EARLY_CHILD -> "แนวทาง อสม. เด็ก 0-5 ปี: ติดตามวัคซีนครบตามเกณฑ์, ตรวจพัฒนาการสมวัย (DSPM) และบันทึกน้ำหนัก/ส่วนสูงเทียบกราฟ"
            VhvAgeGroup.SCHOOL_AGE -> "แนวทาง อสม. เด็ก 6-12 ปี: ประเมินภาวะโภชนาการ BMI สมส่วน, ตรวจสุขภาพช่องปาก ฟันผุ และคัดกรองสายตา"
            VhvAgeGroup.TEENAGER -> "แนวทาง อสม. วัยรุ่น 13-20 ปี: ส่งเสริมสุขภาวะทางเพศ ป้องกันพฤติกรรมเสี่ยง และดูแลสุขภาพจิตลดความเครียด"
            VhvAgeGroup.WORKING_AGE -> "แนวทาง อสม. วัยทำงาน 21-59 ปี: คัดกรองความดันโลหิต (เป้าหมาย < 140/90) และตรวจน้ำตาลในเลือด (เป้าหมาย < 126 mg/dL) ป้องกัน NCDs"
            VhvAgeGroup.ELDERLY -> "แนวทาง อสม. ผู้สูงอายุ 60+ ปี: คัดกรอง 9 ด้าน: ความดันโลหิต, ประเมินความเสี่ยงหกล้ม, สมองเสื่อม และประเมิน ADL (ติดสังคม/ติดบ้าน/ติดเตียง)"
            VhvAgeGroup.UNKNOWN -> "แนวทาง อสม.: ตรวจคัดกรองสุขภาพพื้นฐาน และบันทึกข้อมูลดัชนีสุขภาพ"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Person Profile & VHV Target Group Card
            person?.let { p ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ageBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(ageIcon, contentDescription = null, tint = ageColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ageBg
                                    ) {
                                        Text(
                                            text = personAgeGroup.value.substringBefore(" ("),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ageColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "อายุ ${personAge ?: "-"} ปี",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    p.nationalId?.let { nid ->
                                        Text(
                                            text = " | ปชช: $nid",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (!p.phoneNumber.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phoneNumber}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Phone, contentDescription = "โทรติดต่อ", tint = com.example.ui.theme.EmeraldPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ageBg.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = ageMission,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            if (screenings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.health_screening_empty),
                            contentDescription = "ไม่มีประวัติการคัดกรอง",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.no_screening_history),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "เริ่มบันทึกค่าดัชนีมวลกาย ความดันโลหิต และค่าน้ำตาลในเลือดครั้งแรกโดยกดปุ่มเครื่องหมายบวกด้านล่าง",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Latest Screening summary dashboard at the very top
                    item {
                        val latest = screenings.firstOrNull()
                        if (latest != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "สรุปดัชนีสุขภาพล่าสุด",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. BMI Card
                                        if (latest.bmi != null) {
                                            val bmiStatus = when {
                                                latest.bmi >= 30 -> "อ้วนอันตราย" to Color(0xFFB71C1C)
                                                latest.bmi >= 25 -> "อ้วน" to Color(0xFFD32F2F)
                                                latest.bmi >= 23 -> "ท้วม" to Color(0xFFF57C00)
                                                latest.bmi >= 18.5 -> "ปกติ" to Color(0xFF2E7D32)
                                                else -> "ผอม" to Color(0xFF0288D1)
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                    .border(1.dp, bmiStatus.second.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                    .padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("BMIล่าสุด", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("%.1f".format(latest.bmi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = bmiStatus.second.copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        bmiStatus.first,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = bmiStatus.second,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // 2. BP Card
                                        if (latest.systolic != null && latest.diastolic != null) {
                                            val bpStatus = when {
                                                latest.systolic >= 160 || latest.diastolic >= 100 -> "สูงรุนแรง" to Color(0xFFB71C1C)
                                                latest.systolic >= 140 || latest.diastolic >= 90 -> "สูง" to Color(0xFFD32F2F)
                                                latest.systolic >= 130 || latest.diastolic >= 85 -> "ค่อนข้างสูง" to Color(0xFFF57C00)
                                                else -> "ปกติ" to Color(0xFF2E7D32)
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                    .border(1.dp, bpStatus.second.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                    .padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("ความดัน (BP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("${latest.systolic}/${latest.diastolic}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = bpStatus.second.copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        bpStatus.first,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = bpStatus.second,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (chartModel != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("แนวโน้มน้ำหนักล่าสุด (กก.)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Chart(
                                        chart = lineChart(),
                                        model = chartModel,
                                        startAxis = rememberStartAxis(),
                                        bottomAxis = rememberBottomAxis(),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        item {
                            val latest = screenings.firstOrNull()
                            if (latest != null) {
                                // Futuristic Premium Gradient AI Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(com.example.ui.theme.EmeraldPrimary, com.example.ui.theme.MintAccent)
                                        )
                                    ),
                                    onClick = {
                                        person?.let { p ->
                                            val age = if (p.birthDate != null) {
                                                Period.between(p.birthDate, LocalDate.now()).years
                                            } else 0
                                            
                                            geminiViewModel.generateHealthAdvice(
                                                age = age,
                                                gender = p.gender.name,
                                                weightKg = latest.weight ?: 0.0,
                                                heightCm = latest.height ?: 0.0,
                                                systolic = latest.systolic ?: 0,
                                                diastolic = latest.diastolic ?: 0,
                                                sugar = (latest.bloodSugar ?: 0).toDouble()
                                            )
                                            showAiAdvice = true
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    colors = listOf(
                                                        com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.08f),
                                                        com.example.ui.theme.MintAccent.copy(alpha = 0.04f)
                                                    )
                                                )
                                            )
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = com.example.ui.theme.EmeraldPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.ai_get_advice),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = com.example.ui.theme.EmeraldPrimary
                                            )
                                            Text(
                                                text = "วิเคราะห์แนวโน้มสุขภาพและคำแนะนำด้วย AI อัจฉริยะ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    items(screenings) { item ->
                        ScreeningCard(screening = item, onDelete = { viewModel.deleteScreening(item) })
                    }
                }
            }
        }

        if (showAddDialog) {
            HealthScreeningFormDialog(
                onDismiss = { showAddDialog = false },
                onSave = { weight, height, systolic, diastolic, bloodSugar, note ->
                    val bmi = if (weight != null && height != null && height > 0) {
                        val heightMeter = height / 100.0
                        weight / (heightMeter * heightMeter)
                    } else null
                    
                    viewModel.insertScreening(
                        HealthScreening(
                            personId = personId,
                            weight = weight,
                            height = height,
                            bmi = bmi,
                            systolic = systolic,
                            diastolic = diastolic,
                            bloodSugar = bloodSugar,
                            note = note,
                            vhvName = "อสม. ในพื้นที่"
                        )
                    )
                    showAddDialog = false
                }
            )
        }

        if (showAiAdvice) {
            AiAdviceDialog(
                uiState = geminiUiState,
                onDismiss = {
                    showAiAdvice = false
                    geminiViewModel.clearState()
                }
            )
        }
    }
}

@Composable
fun AiAdviceDialog(
    uiState: GeminiUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = com.example.ui.theme.EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ai_advisor_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                when (uiState) {
                    is GeminiUiState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = com.example.ui.theme.EmeraldPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.ai_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is GeminiUiState.Success -> {
                        Text(uiState.response, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.ai_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    is GeminiUiState.Error -> {
                        Text("เกิดข้อผิดพลาด: ${uiState.message}", color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ตกลง", fontWeight = FontWeight.Bold, color = com.example.ui.theme.EmeraldPrimary)
            }
        }
    )
}

@Composable
fun ScreeningCard(screening: HealthScreening, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy • HH:mm น.", Locale("th", "TH"))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(com.example.ui.theme.EmeraldPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(Date(screening.timestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "ลบ",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Blood Pressure
                if (screening.systolic != null && screening.diastolic != null) {
                    val bpStatus = when {
                        screening.systolic >= 160 || screening.diastolic >= 100 -> "สูงมาก" to Color(0xFFB71C1C)
                        screening.systolic >= 140 || screening.diastolic >= 90 -> "สูง" to Color(0xFFD32F2F)
                        screening.systolic >= 130 || screening.diastolic >= 85 -> "ค่อนข้างสูง" to Color(0xFFF57C00)
                        else -> "ปกติ" to Color(0xFF2E7D32)
                    }
                    ScreeningItem(
                        label = stringResource(R.string.bp_label) + " (mmHg)",
                        value = "${screening.systolic}/${screening.diastolic}",
                        status = bpStatus.first,
                        statusColor = bpStatus.second,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // BMI
                if (screening.bmi != null) {
                    val bmiStatus = when {
                        screening.bmi >= 30 -> "อ้วนอันตราย" to Color(0xFFB71C1C)
                        screening.bmi >= 25 -> "อ้วน" to Color(0xFFD32F2F)
                        screening.bmi >= 23 -> "ท้วม" to Color(0xFFF57C00)
                        screening.bmi >= 18.5 -> "ปกติ" to Color(0xFF2E7D32)
                        else -> "ผอม" to Color(0xFF0288D1)
                    }
                    ScreeningItem(
                        label = "ดัชนีมวลกาย (BMI)",
                        value = "%.1f".format(screening.bmi),
                        status = bmiStatus.first,
                        statusColor = bmiStatus.second,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            if (screening.bloodSugar != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val sugarStatus = when {
                    screening.bloodSugar >= 126 -> "เสี่ยงเบาหวาน" to Color(0xFFD32F2F)
                    screening.bloodSugar >= 100 -> "เริ่มสูง" to Color(0xFFF57C00)
                    else -> "ปกติ" to Color(0xFF2E7D32)
                }
                ScreeningItem(
                    label = stringResource(R.string.sugar_label),
                    value = "${screening.bloodSugar} mg/dL",
                    status = sugarStatus.first,
                    statusColor = sugarStatus.second,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (!screening.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.note_label) + ": ${screening.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScreeningItem(
    label: String,
    value: String,
    status: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .height(IntrinsicSize.Min)
    ) {
        // Solid left accent bar
        Box(
            modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(statusColor)
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .weight(1f)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreeningFormDialog(
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Int?, Int?, Int?, String) -> Unit
) {
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var bloodSugar by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // Dynamic state calculations
    val calculatedBmi: Double? = remember(weight, height) {
        val w = weight.toDoubleOrNull()
        val h = height.toDoubleOrNull()
        if (w != null && h != null && h > 0) {
            val hMeter = h / 100.0
            w / (hMeter * hMeter)
        } else null
    }

    val bmiCategory = remember(calculatedBmi) {
        calculatedBmi?.let { bmi ->
            when {
                bmi >= 30 -> "อ้วนอันตราย (ระดับ 3)" to Color(0xFFB71C1C)
                bmi >= 25 -> "อ้วน (ระดับ 2)" to Color(0xFFD32F2F)
                bmi >= 23 -> "น้ำหนักเกิน (ท้วม)" to Color(0xFFF57C00)
                bmi >= 18.5 -> "น้ำหนักปกติ" to Color(0xFF2E7D32)
                else -> "น้ำหนักน้อยกว่าเกณฑ์" to Color(0xFF0288D1)
            }
        }
    }

    val calculatedBpCategory = remember(systolic, diastolic) {
        val sys = systolic.toIntOrNull()
        val dia = diastolic.toIntOrNull()
        if (sys != null && dia != null) {
            when {
                sys >= 160 || dia >= 100 -> "ความดันโลหิตสูงรุนแรง (ระดับ 2)" to Color(0xFFB71C1C)
                sys >= 140 || dia >= 90 -> "ความดันโลหิตสูง (ระดับ 1)" to Color(0xFFD32F2F)
                sys >= 130 || dia >= 85 -> "ความดันโลหิตค่อนข้างสูง" to Color(0xFFF57C00)
                else -> "ความดันโลหิตปกติ" to Color(0xFF2E7D32)
            }
        } else null
    }

    val calculatedSugarCategory = remember(bloodSugar) {
        val sugar = bloodSugar.toIntOrNull()
        if (sugar != null) {
            when {
                sugar >= 126 -> "เสี่ยงโรคเบาหวาน" to Color(0xFFD32F2F)
                sugar >= 100 -> "ระดับน้ำตาลเริ่มสูง" to Color(0xFFF57C00)
                else -> "ระดับน้ำตาลปกติ" to Color(0xFF2E7D32)
            }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_screening), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Physical Parameters
                Text("สัดส่วนร่างกาย", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = com.example.ui.theme.EmeraldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("น้ำหนัก (กก.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("ส่วนสูง (ซม.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Dynamic BMI Display
                calculatedBmi?.let { bmi ->
                    bmiCategory?.let { category ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = category.second.copy(alpha = 0.05f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, category.second.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("ดัชนีมวลกาย (BMI) คำนวณได้:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%.1f".format(bmi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = category.second)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = category.second.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        category.first,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = category.second,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Section 2: Blood Pressure
                Text("สัญญาณชีพ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = com.example.ui.theme.EmeraldPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = systolic,
                        onValueChange = { systolic = it },
                        label = { Text("ค่าบน (SYS) mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = diastolic,
                        onValueChange = { diastolic = it },
                        label = { Text("ค่าล่าง (DIA) mmHg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Dynamic Blood Pressure Category
                calculatedBpCategory?.let { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = category.second.copy(alpha = 0.05f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, category.second.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ระดับความดันโลหิต:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = category.second.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    category.first,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = category.second,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Section 3: Blood Sugar
                OutlinedTextField(
                    value = bloodSugar,
                    onValueChange = { bloodSugar = it },
                    label = { Text("ระดับน้ำตาลในเลือด (mg/dL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Dynamic Blood Sugar Category
                calculatedSugarCategory?.let { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = category.second.copy(alpha = 0.05f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, category.second.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ระดับน้ำตาลในเลือด:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = category.second.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    category.first,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = category.second,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("บันทึกหมายเหตุ / คำแนะนำ อสม.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        weight.toDoubleOrNull(),
                        height.toDoubleOrNull(),
                        systolic.toIntOrNull(),
                        diastolic.toIntOrNull(),
                        bloodSugar.toIntOrNull(),
                        note
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.EmeraldPrimary)
            ) {
                Text("บันทึกข้อมูล", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}
