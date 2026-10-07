package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthKnowledgeScreen(
    onBack: () -> Unit,
    onNavigateToVideoTutorials: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("บทบาทหน้าที่ อสม.", "เกณฑ์ความดัน", "ดัชนีมวลกาย (BMI)", "ความรู้ตามช่วงวัย", "ป้องกันไข้เลือดออก", "ภาวะฉุกเฉิน")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "คู่มือ อสม. และเกณฑ์สุขภาพ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "กลับ", tint = Color.White)
                    }
                },
                actions = {
                    if (onNavigateToVideoTutorials != null) {
                        IconButton(onClick = onNavigateToVideoTutorials) {
                            Icon(Icons.Filled.SmartDisplay, contentDescription = "วิดีโอสอนใช้งาน", tint = Color.White)
                        }
                    }
                    ThemeQuickToggleButton(iconTint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmeraldPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = EmeraldPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dynamic Hero Image matching the topic
                val heroImageRes = when (selectedTab) {
                    4 -> R.drawable.ic_dengue_fever_guide
                    5 -> R.drawable.ic_emergency_guide
                    else -> R.drawable.img_knowledge_hero
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Image(
                        painter = painterResource(id = heroImageRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                when (selectedTab) {
                    0 -> VhvRolesContent()
                    1 -> BloodPressureGuideContent()
                    2 -> BmiGuideContent()
                    3 -> AgeGroupKnowledgeContent()
                    4 -> DenguePreventionContent()
                    5 -> MedicalEmergencyContent()
                }
            }
        }
    }
}

@Composable
fun VhvRolesContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "อาสาสมัครสาธารณสุขประจำหมู่บ้าน (อสม.)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "“แจ้งข่าวร้าย กระจายข่าวดี ชี้บริการ ประสานงานสาธารณสุข บำบัดทุกข์ให้ประชาชน ดำรงตนเป็นตัวอย่างที่ดี”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(
            "อสม. คือ อาสาสมัครที่ได้รับการคัดเลือกจากชุมชนและได้รับการอบรมจากเจ้าหน้าที่สาธารณสุข เพื่อทำหน้าที่ดูแลสุขภาพของตนเอง ครอบครัว และชุมชน โดยมีบทบาทสำคัญในฐานะ “หมอคนที่ 1” ของชุมชน",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("บทบาทหน้าที่หลัก 8 ประการ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)

        val roles = listOf(
            "1. การสื่อสารและให้ความรู้" to "ถ่ายทอดข่าวสารสาธารณสุขระหว่างเจ้าหน้าที่และประชาชน แจ้งเตือนโรคระบาด และประชาสัมพันธ์ข้อมูลสุขภาพ",
            "2. การส่งเสริมสุขภาพ" to "เฝ้าเะวังและติดตามดูแลหญิงตั้งครรภ์ เด็กแรกเกิด และผู้สูงอายุ ให้คำแนะนำด้านโภชนาการและการออกกำลังกาย",
            "3. การป้องกันและควบคุมโรค" to "คัดกรองโรคเบื้องต้น (ความดัน/เบาหวาน) สำรวจแหล่งเพาะพันธุ์ยุงลาย และแจ้งเตือนโรคติดต่อในพื้นที่",
            "4. การรักษาพยาบาลเบื้องต้น" to "ให้บริการช่วยเหลือและปฐมพยาบาลเบื้องต้น โดยใช้ยาและเวชภัณฑ์ตามขอบเขตที่กำหนด",
            "5. การประสานงานและส่งต่อ" to "ประสานกิจกรรมพัฒนาสาธารณสุข และส่งต่อผู้ป่วยไปยัง รพ.สต. หรือหน่วยงานที่เกี่ยวข้อง",
            "6. การฟื้นฟูสภาพ" to "ติดตามดูแลและฟื้นฟูสภาพผู้ป่วยติดเตียง ผู้พิการ และผู้สูงอายุในชุมชนอย่างต่อเนื่อง",
            "7. การจัดการสิ่งแวดล้อม" to "ร่วมป้องกันและแก้ไขปัญหามลภาวะสิ่งแวดล้อม ตรวจสอบคุณภาพน้ำ และสารเคมีตกค้างในชุมชน",
            "8. การเป็นแบบอย่างที่ดี" to "ปฏิบัติตนเป็นแบบอย่างด้านสุขภาพ และเป็นผู้นำในการพัฒนาสาธารณสุขของหมู่บ้าน"
        )

        roles.forEach { (title, desc) ->
            RoleItem(title, desc)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RoleItem(title: String, desc: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun BloodPressureGuideContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("เกณฑ์การอ่านค่าความดันโลหิต (Blood Pressure)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        Text("การวัดความดันโลหิตประกอบด้วยตัวเลข 2 ค่า คือ ค่าบน (Systolic) และค่าล่าง (Diastolic) หน่วยเป็น มิลลิเมตรปรอท (mmHg)")

        BpCard("ระดับเหมาะสม (ปกติ)", "ตัวบน < 120 และ ตัวล่าง < 80", Color(0xFF2E7D32), "ความดันโลหิตอยู่ในเกณฑ์ดีเยี่ยม ควรักษาระดับพฤติกรรมสุขภาพ")
        BpCard("ระดับปกติ", "ตัวบน 120-129 และ/หรือ ตัวล่าง 80-84", Color(0xFF388E3C), "ปกติ แต่ควรควบคุมอาหารและออกกำลังกายสม่ำเสมอ")
        BpCard("ระดับปกติค่อนสูง", "ตัวบน 130-139 และ/or ตัวล่าง 85-89", Color(0xFFF57C00), "เริ่มสูงกว่าเกณฑ์ เสี่ยงต่อโรคความดันโลหิตสูง ควรตรวจวัดสม่ำเสมอ")
        BpCard("ระดับสูง (ระยะที่ 1)", "ตัวบน 140-159 และ/or ตัวล่าง 90-99", Color(0xFFD32F2F), "เข้าเกณฑ์ความดันโลหิตสูง ควรพบแพทย์เพื่อรับคำแนะนำและตรวจซ้ำ")
        BpCard("ระดับสูงมาก (ระยะที่ 2)", "ตัวบน ≥ 160 และ/or ตัวล่าง ≥ 100", Color(0xFFB71C1C), "อันตราย! ความดันสูงมาก ควรพบแพทย์ด่วนเพื่อรับการรักษา")
    }
}

@Composable
fun BpCard(title: String, range: String, color: Color, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
                Badge(containerColor = color) {
                    Text(range, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BmiGuideContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ดัชนีมวลกาย (BMI - Body Mass Index)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        Text("สูตรคำนวณ: น้ำหนักตัว (กิโลกรัม) ÷ [ส่วนสูง (เมตร)]²")

        BmiCard("น้ำหนักน้อย / ผอม", "< 18.5 กก./ม.²", Color(0xFF0288D1), "เสี่ยงต่อภาวะขาดสารอาหาร โรคเรื้อรัง หรือภูมิคุ้มกันต่ำ")
        BmiCard("ปกติ (สมส่วน)", "18.5 - 22.9 กก./ม.²", Color(0xFF2E7D32), "อยู่ในเกณฑ์สุขภาพดี อัตราเสี่ยงต่อโรคเรื้อรังต่ำที่สุด")
        BmiCard("ท้วม / โรคอ้วนระดับ 1", "23.0 - 24.9 กก./ม.²", Color(0xFFF57C00), "เริ่มมีน้ำหนักเกิน เสี่ยงต่อโรคเบาหวานและความดันโลหิตสูง")
        BmiCard("อ้วน / โรคอ้วนระดับ 2", "25.0 - 29.9 กก./ม.²", Color(0xFFD32F2F), "เข้าเกณฑ์โรคอ้วน เสี่ยงสูงต่อโรคหัวใจ หลอดเลือด และข้อเข่าเสื่อม")
        BmiCard("อ้วนอันตราย / ระดับ 3", "≥ 30.0 กก./ม.²", Color(0xFFB71C1C), "โรคอ้วนอันตราย เสี่ยงต่อภาวะแทรกซ้อนรุนแรง ควรปรึกษาแพทย์")
    }
}

@Composable
fun BmiCard(title: String, range: String, color: Color, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
                Badge(containerColor = color) {
                    Text(range, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AgeGroupKnowledgeContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ความรู้และคำแนะนำสุขภาพแยกตามกลุ่มวัย", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)

        AgeGroupCard(
            title = "👶 กลุ่มเด็กเล็ก (0 - 5 ปี)",
            focus = "พัฒนาการ สมส่วน และวัคซีน",
            details = listOf(
                "• ตรวจสมุดบันทึกสุขภาพและติดตามการรับวัคซีนตามวัย",
                "• ส่งเสริมการเลี้ยงลูกด้วยนมแม่อย่างเดียวอย่างน้อย 6 เดือน",
                "• ประเมินพัฒนาการด้านการเคลื่อนไหว ภาษา และการช่วยเหลือตัวเอง",
                "• ป้องกันอุบัติเหตุในเด็ก เช่น การพลัดตกหกคะเมน และการสำลักสิ่งแปลกปลอม"
            )
        )

        AgeGroupCard(
            title = "👦 กลุ่มวัยเรียนและวัยรุ่น (6 - 21 ปี)",
            focus = "โภชนาการ สายตา ทันตสุขภาพ และสุขภาพจิต",
            details = listOf(
                "• ส่งเสริมการรับประทานอาหารครบ 5 หมู่ หลีกเลี่ยงอาหารหวานจัด เค็มจัด มันจัด",
                "• ตรวจคัดกรองสายตา ทันตสุขภาพ และการแปรงฟันที่ถูกวิธี",
                "• ป้องกันภาวะซึมเศร้า การติดเกม และสารเสพติด",
                "• ส่งเสริมการออกกำลังกายสม่ำเสมออย่างน้อย 60 นาที/วัน"
            )
        )

        AgeGroupCard(
            title = "👩‍🦰 กลุ่มวัยทำงาน (22 - 59 ปี)",
            focus = "โรค NCDs (เบาหวาน ความดัน) และออฟฟิศซินโดรม",
            details = listOf(
                "• ตรวจคัดกรองความดันโลหิตและระดับน้ำตาลในเลือดประจำปี",
                "• ควบคุมน้ำหนักและดัชนีมวลกาย (BMI) ให้อยู่ในเกณฑ์ปกติ",
                "• หลีกเลี่ยงความเครียด สูบบุหรี่ และดื่มสุราเกินขนาด",
                "• ระวังโรคจากการทำงาน เช่น ออฟฟิศซินโดรม และอาการปวดหลังเรื้อรัง"
            )
        )

        AgeGroupCard(
            title = "👵 กลุ่มผู้สูงอายุ (60 ปีขึ้นไป)",
            focus = "การพลัดตกหกคะเมน ข้อเข่าเสื่อม และสมองเสื่อม",
            details = listOf(
                "• ประเมินความเสี่ยงต่อการพลัดตกหกคะเมนและปรับสภาพแวดล้อมในบ้าน",
                "• ตรวจคัดกรองภาวะสมองเสื่อม (AD8) และภาวะซึมเศร้าในผู้สูงอายุ",
                "• ตรวจสุขภาพช่องปาก ฟันเทียม และภาวะโภชนาการ",
                "• ส่งเสริมการทำกิจกรรมทางกายที่เหมาะสม เช่น การเดิน และรำไทเก็ก"
            )
        )
    }
}

@Composable
fun AgeGroupCard(title: String, focus: String, details: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 16.sp)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MintAccent.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "จุดเน้น: $focus",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            details.forEach { detail ->
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DenguePreventionContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "แนวทางป้องกันไข้เลือดออก (Dengue Fever Prevention)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
        )

        Text(
            "โรคไข้เลือดออกเกิดจากเชื้อไวรัสเดงกี (Dengue Virus) มียุงลายเป็นพาหะนำโรค การควบคุมแหล่งเพาะพันธุ์ยุงลายคือหัวใจสำคัญในการป้องกันที่ดีที่สุด",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Campaign: 3 เก็บ 5 ส.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "มาตรการ “3 เก็บ ป้องกัน 3 โรค”",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
                Text(
                    "1. เก็บบ้าน: ให้สะอาด โปร่ง โล่ง ไม่ให้ยุงลายเกาะพัก\n" +
                    "2. เก็บขยะ: เศษภาชนะที่จะเป็นแหล่งน้ำขังรอบบ้าน\n" +
                    "3. เก็บน้ำ: ปิดฝาภาชนะเก็บน้ำให้มิดชิด หรือใส่ทรายอะเบท",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            "เช็คลิสต์สำรวจลูกน้ำยุงลายสำหรับ อสม. (Interactive Survey)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Interactive survey checklist
        var survey1 by remember { mutableStateOf(false) }
        var survey2 by remember { mutableStateOf(false) }
        var survey3 by remember { mutableStateOf(false) }
        var survey4 by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ใช้ประกอบการตรวจเยี่ยมหลังบ้านเพื่อประเมินความปลอดภัย:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = survey1, onCheckedChange = { survey1 = it })
                Text("ตรวจโอ่งน้ำ/ถังน้ำ (มีฝาปิดมิดชิด / ใส่ทรายกำจัดลูกน้ำ)", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = survey2, onCheckedChange = { survey2 = it })
                Text("ตรวจจานรองกระถางต้นไม้ (ไม่มีน้ำขังค้างสะสม)", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = survey3, onCheckedChange = { survey3 = it })
                Text("ตรวจยางรถยนต์เก่าหรือเศษกระป๋อง (จัดเก็บในร่ม ไม่โดนฝน)", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = survey4, onCheckedChange = { survey4 = it })
                Text("ตรวจรางน้ำฝนรอบบ้าน (โปร่ง สะอาด ไม่มีใบไม้อุดตัน)", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Warning Symptoms
        Text(
            "อาการสงสัยไข้เลือดออกที่ต้องเฝ้าระวัง",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "• มีไข้สูงลอยเฉียบพลัน (38.5 - 40 °C) นานประมาณ 2-7 วัน\n" +
                    "• ปวดศีรษะ ปวดกระบอกตา ปวดเมื่อยตามตัว\n" +
                    "• มีจุดเลือดออกสีแดงเล็กๆ ตามแขน ขา ลำตัว\n" +
                    "• คลื่นไส้ อาเจียน เบื่ออาหาร ปวดท้องบริเวณใต้ชายโครงขวา",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "⚠️ ห้ามทานยากลุ่ม Aspirin หรือ Ibuprofen เด็ดขาด เพราะเสี่ยงเลือดออกในทางเดินอาหารรุนแรง ให้ใช้ยาพาราเซตามอลแทน",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MedicalEmergencyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "การประเมินภาวะฉุกเฉินและการกู้ชีพเบื้องต้น (Medical Emergency Guide)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
        )

        // FAST Stroke protocol
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.04f)),
            border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "สงสัยโรคหลอดเลือดสมอง (Stroke) ประเมินด้วย FAST",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)
                    )
                }

                Text(
                    "หากพบคนไข้มีอาการเหล่านี้ ให้รีบประสานส่งต่อทันที เพราะทุกวินาทีคือเซลล์สมอง:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
                            Text("F", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Face (หน้าเบี้ยว / ปากเบี้ยว)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("ยิ้มแล้วมุมปากตกข้างหนึ่ง หรือหน้าเบี้ยวซีกใดซีกหนึ่ง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
                            Text("A", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Arm (แขนขาอ่อนแรง)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("ยกแขนไม่ขึ้น หรือขาไม่มีแรงเฉียบพลันซีกเดียว", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
                            Text("S", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Speech (พูดลำบาก / พูดไม่ชัด)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("พูดอ้อแอ้ พูดไม่ชัด พูดไม่ออก หรือฟังคนอื่นไม่รู้เรื่อง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(24.dp).background(Color(0xFFD32F2F), CircleShape), contentAlignment = Alignment.Center) {
                            Text("T", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Time (เวลาวิกฤต / รีบด่วน)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            Text("โทรเรียกสายด่วน 1669 ทันที ต้องไปโรงพยาบาลภายใน 4.5 ชม.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Basic CPR Guidelines
        Text(
            "ขั้นตอนการช่วยฟื้นคืนชีพเบื้องต้น (CPR สำหรับประชาชน)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val steps = listOf(
                "1. ประเมินความปลอดภัย" to "ดูสิ่งแวดล้อมรอบตัวคนไข้ให้ปลอดภัยก่อนเข้าไปช่วยเหลือเสมอ",
                "2. ปลุกเรียก" to "ตบไหล่ทั้งสองข้างแรงๆ แล้วเรียก 'คุณๆ เป็นอย่างไรบ้าง!' สังเกตการหายใจ",
                "3. โทร 1669" to "หากไม่ตอบสนอง ไม่หายใจ หรือหายใจเฮือก โทร 1669 ทันที แจ้งพิกัดและอาการ",
                "4. ปั๊มหัวใจ (Chest Compression)" to "วางส้นมือบริเวณกึ่งกลางหน้าอก (กระดูกหน้าอก) กดลึก 5-6 ซม. ความเร็ว 100-120 ครั้งต่อนาที ปล่อยหน้าอกคืนสุดทุกครั้ง",
                "5. ใช้เครื่อง AED" to "หากมีเครื่องช็อกจากกระแสไฟฟ้าอัตโนมัติ (AED) ให้เปิดเครื่องและปฏิบัติตามเสียงสั่งทันที"
            )

            steps.forEach { (stepTitle, stepDesc) ->
                Column {
                    Text(stepTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    Text(stepDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Hotline action button
        val context = LocalContext.current

        Button(
            onClick = {
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1669"))
                    context.startActivity(dialIntent)
                } catch (e: Exception) {
                    // fallback
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("โทรสายด่วน 1669 การแพทย์ฉุกเฉิน", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
