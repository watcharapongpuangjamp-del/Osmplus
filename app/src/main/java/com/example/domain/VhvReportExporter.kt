package com.example.domain

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.Period
import java.util.*

object VhvReportExporter {

    private val thaiDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("th", "TH"))
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH"))

    /**
     * Data class holding calculated report summaries for a given village or all villages.
     */
    data class Osm1Summary(
        val villageFilter: String,
        val totalHouseholds: Int,
        val householdsWithGps: Int,
        val householdsNeedsReview: Int,
        val totalPopulation: Int,
        val alivePopulation: Int,
        val maleCount: Int,
        val femaleCount: Int,
        val deceasedCount: Int,
        val movedCount: Int,
        val earlyChildCount: Int,  // 0-5
        val schoolAgeCount: Int,   // 6-12
        val teenagerCount: Int,    // 13-20
        val workingAgeCount: Int,  // 21-59
        val elderlyCount: Int,     // 60+
        // Screenings summary
        val screenedCount: Int,
        val bpNormal: Int,
        val bpRisk: Int,
        val bpHigh: Int,
        val dtxNormal: Int,
        val dtxRisk: Int,
        val dtxHigh: Int,
        val bmiUnderweight: Int,
        val bmiNormal: Int,
        val bmiOverweight: Int,
        val bmiObese: Int
    )

    /**
     * Compute summary statistics from filtered dataset.
     */
    fun computeOsm1Summary(
        villageFilter: String,
        households: List<Household>,
        persons: List<Person>,
        screenings: List<HealthScreening>
    ): Osm1Summary {
        val filteredHouseholds = if (villageFilter == "ALL") households else households.filter { it.villageNo == villageFilter }
        val filteredHouseholdIds = filteredHouseholds.map { it.id }.toSet()
        val filteredPersons = persons.filter { it.householdId in filteredHouseholdIds }

        val alivePersons = filteredPersons.filter { it.personStatus == PersonStatus.ALIVE }
        val now = LocalDate.now()

        var earlyChild = 0
        var schoolAge = 0
        var teenager = 0
        var workingAge = 0
        var elderly = 0

        alivePersons.forEach { p ->
            val age = p.birthDate?.let { Period.between(it, now).years }
            when (VhvAgeGroup.fromAge(age)) {
                VhvAgeGroup.EARLY_CHILD -> earlyChild++
                VhvAgeGroup.SCHOOL_AGE -> schoolAge++
                VhvAgeGroup.TEENAGER -> teenager++
                VhvAgeGroup.WORKING_AGE -> workingAge++
                VhvAgeGroup.ELDERLY -> elderly++
                else -> {}
            }
        }

        // Screenings stats (latest screening per person)
        val filteredPersonIds = filteredPersons.map { it.id }.toSet()
        val relevantScreenings = screenings.filter { it.personId in filteredPersonIds }
        val latestScreeningByPerson = relevantScreenings.groupBy { it.personId }
            .mapValues { entry -> entry.value.maxByOrNull { it.timestamp } }
            .values.filterNotNull()

        var bpNormal = 0
        var bpRisk = 0
        var bpHigh = 0
        var dtxNormal = 0
        var dtxRisk = 0
        var dtxHigh = 0
        var bmiUnder = 0
        var bmiNorm = 0
        var bmiOver = 0
        var bmiObese = 0

        latestScreeningByPerson.forEach { sc ->
            val sys = sc.systolic
            val dia = sc.diastolic
            if (sys != null && dia != null) {
                if (sys >= 140 || dia >= 90) bpHigh++
                else if (sys >= 130 || dia >= 85) bpRisk++
                else bpNormal++
            }

            val dtx = sc.bloodSugar
            if (dtx != null) {
                if (dtx >= 126) dtxHigh++
                else if (dtx >= 100) dtxRisk++
                else dtxNormal++
            }

            val bmi = sc.bmi
            if (bmi != null) {
                if (bmi < 18.5) bmiUnder++
                else if (bmi < 23.0) bmiNorm++
                else if (bmi < 25.0) bmiOver++
                else bmiObese++
            }
        }

        return Osm1Summary(
            villageFilter = villageFilter,
            totalHouseholds = filteredHouseholds.size,
            householdsWithGps = filteredHouseholds.count { it.latitude != null && it.longitude != null },
            householdsNeedsReview = filteredHouseholds.count { it.dataStatus == DataStatus.NEEDS_REVIEW },
            totalPopulation = filteredPersons.size,
            alivePopulation = alivePersons.size,
            maleCount = filteredPersons.count { it.gender == Gender.MALE },
            femaleCount = filteredPersons.count { it.gender == Gender.FEMALE },
            deceasedCount = filteredPersons.count { it.personStatus == PersonStatus.DEAD },
            movedCount = filteredPersons.count { it.personStatus == PersonStatus.MOVED },
            earlyChildCount = earlyChild,
            schoolAgeCount = schoolAge,
            teenagerCount = teenager,
            workingAgeCount = workingAge,
            elderlyCount = elderly,
            screenedCount = latestScreeningByPerson.size,
            bpNormal = bpNormal,
            bpRisk = bpRisk,
            bpHigh = bpHigh,
            dtxNormal = dtxNormal,
            dtxRisk = dtxRisk,
            dtxHigh = dtxHigh,
            bmiUnderweight = bmiUnder,
            bmiNormal = bmiNorm,
            bmiOverweight = bmiOver,
            bmiObese = bmiObese
        )
    }

    /**
     * Exports official VHV Report OSM 1 as formatted Microsoft Excel (.xlsx) file.
     */
    fun generateOsm1Excel(
        context: Context,
        villageFilter: String,
        households: List<Household>,
        persons: List<Person>,
        screenings: List<HealthScreening>
    ): Uri {
        val summary = computeOsm1Summary(villageFilter, households, persons, screenings)
        val filteredHouseholds = if (villageFilter == "ALL") households else households.filter { it.villageNo == villageFilter }
        val householdMap = filteredHouseholds.associateBy { it.id }
        val filteredPersons = persons.filter { it.householdId in householdMap.keys }
        val latestScreeningMap = screenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }

        val workbook = XSSFWorkbook()

        // Styles
        val headerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
            color = IndexedColors.WHITE.index
        }
        val subHeaderFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 11
            color = IndexedColors.BLACK.index
        }
        val boldFont = workbook.createFont().apply { bold = true }

        val emeraldHeaderStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
        val sectionHeaderStyle = workbook.createCellStyle().apply {
            setFont(subHeaderFont)
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
        }
        val tableHeaderStyle = workbook.createCellStyle().apply {
            setFont(boldFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            alignment = HorizontalAlignment.CENTER
        }
        val normalBorderedStyle = workbook.createCellStyle().apply {
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        val numberCenterStyle = workbook.createCellStyle().apply {
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            alignment = HorizontalAlignment.CENTER
        }

        // ================= SHEET 1: สรุปรายงาน อสม. 1 =================
        val sheet1 = workbook.createSheet("สรุปรายงาน อสม.1")
        sheet1.isDisplayGridlines = true

        var r = 0
        // Header Title
        val titleRow = sheet1.createRow(r++)
        titleRow.heightInPoints = 32f
        val titleCell = titleRow.createCell(0)
        val villageLabel = if (villageFilter == "ALL") "ทุกหมู่บ้าน (13 หมู่บ้าน)" else "หมู่ที่ $villageFilter"
        titleCell.setCellValue("แบบรายงานผลการปฏิบัติงานของ อสม. ประจำเดือน (รายงาน อสม. 1)")
        titleCell.cellStyle = emeraldHeaderStyle
        sheet1.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

        val subTitleRow = sheet1.createRow(r++)
        subTitleRow.createCell(0).setCellValue("พื้นที่: ตำบลป่าขะ อำเภอบ้านนา จังหวัดนครนายก | $villageLabel")
        sheet1.addMergedRegion(CellRangeAddress(1, 1, 0, 5))

        val dateRow = sheet1.createRow(r++)
        dateRow.createCell(0).setCellValue("ข้อมูล ณ วันที่: ${thaiDateFormat.format(Date())}")
        sheet1.addMergedRegion(CellRangeAddress(2, 2, 0, 5))
        r++ // blank row

        // หมวด 1: ข้อมูลประชากรและครัวเรือน
        val sec1Row = sheet1.createRow(r++)
        sec1Row.createCell(0).setCellValue("หมวดที่ 1: ข้อมูลครัวเรือนและประชากรทั่วไป")
        sec1Row.getCell(0).cellStyle = sectionHeaderStyle
        sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 5))

        val demoItems = listOf(
            "จำนวนครัวเรือนทั้งหมด" to "${summary.totalHouseholds} ครัวเรือน",
            "ครัวเรือนที่มีพิกัดแผนที่ GPS" to "${summary.householdsWithGps} ครัวเรือน",
            "ครัวเรือนที่รอการตรวจสอบ" to "${summary.householdsNeedsReview} ครัวเรือน",
            "ประชากรที่ลงทะเบียนทั้งหมด" to "${summary.totalPopulation} คน",
            "ประชากรที่มีชีวิต (ปัจจุบัน)" to "${summary.alivePopulation} คน",
            "เพศชาย / เพศหญิง" to "${summary.maleCount} คน / ${summary.femaleCount} คน",
            "ประชากรเสียชีวิตแล้ว" to "${summary.deceasedCount} คน",
            "ประชากรย้ายถิ่นออก" to "${summary.movedCount} คน"
        )
        demoItems.forEach { (lbl, valStr) ->
            val row = sheet1.createRow(r++)
            val c0 = row.createCell(0); c0.setCellValue(lbl); c0.cellStyle = normalBorderedStyle
            sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 2))
            val c1 = row.createCell(3); c1.setCellValue(valStr); c1.cellStyle = numberCenterStyle
            sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 3, 5))
        }
        r++ // blank row

        // หมวด 2: การคัดแยกกลุ่มอายุตามเกณฑ์ อสม. (5 กลุ่ม)
        val sec2Row = sheet1.createRow(r++)
        sec2Row.createCell(0).setCellValue("หมวดที่ 2: การคัดแยกกลุ่มอายุตามเกณฑ์ อสม. (5 กลุ่มเป้าหมาย)")
        sec2Row.getCell(0).cellStyle = sectionHeaderStyle
        sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 5))

        val ageHeadRow = sheet1.createRow(r++)
        listOf("ลำดับ", "กลุ่มเป้าหมาย อสม.", "ช่วงอายุ", "จำนวน (คน)", "ร้อยละ (%)", "ภารกิจดูแล อสม.").forEachIndexed { idx, txt ->
            val cell = ageHeadRow.createCell(idx)
            cell.setCellValue(txt)
            cell.cellStyle = tableHeaderStyle
        }

        val totalAlive = summary.alivePopulation.coerceAtLeast(1)
        val ageTable = listOf(
            Triple("1", "เด็กปฐมวัย", Pair("0 - 5 ปี", Pair(summary.earlyChildCount, "วัคซีนครบ, พัฒนาการสมวัย"))),
            Triple("2", "เด็กวัยเรียน", Pair("6 - 12 ปี", Pair(summary.schoolAgeCount, "อนามัยโรงเรียน, สุขภาพช่องปาก"))),
            Triple("3", "วัยรุ่น", Pair("13 - 20 ปี", Pair(summary.teenagerCount, "สุขภาพจิต, ทักษะชีวิต, สารเสพติด"))),
            Triple("4", "วัยทำงาน", Pair("21 - 59 ปี", Pair(summary.workingAgeCount, "คัดกรอง NCDs ความดัน/เบาหวาน"))),
            Triple("5", "ผู้สูงอายุ", Pair("60 ปีขึ้นไป", Pair(summary.elderlyCount, "เยี่ยมบ้าน, ดูแลกลุ่มติดบ้าน/ติดเตียง")))
        )

        ageTable.forEach { (no, name, pair) ->
            val range = pair.first
            val count = pair.second.first
            val mission = pair.second.second
            val pct = String.format(Locale.US, "%.1f%%", (count.toDouble() * 100.0) / totalAlive)

            val row = sheet1.createRow(r++)
            val c0 = row.createCell(0); c0.setCellValue(no); c0.cellStyle = numberCenterStyle
            val c1 = row.createCell(1); c1.setCellValue(name); c1.cellStyle = normalBorderedStyle
            val c2 = row.createCell(2); c2.setCellValue(range); c2.cellStyle = numberCenterStyle
            val c3 = row.createCell(3); c3.setCellValue(count.toDouble()); c3.cellStyle = numberCenterStyle
            val c4 = row.createCell(4); c4.setCellValue(pct); c4.cellStyle = numberCenterStyle
            val c5 = row.createCell(5); c5.setCellValue(mission); c5.cellStyle = normalBorderedStyle
        }
        r++ // blank row

        // หมวด 3: สรุปผลการคัดกรองสุขภาพ (NCDs & BMI)
        val sec3Row = sheet1.createRow(r++)
        sec3Row.createCell(0).setCellValue("หมวดที่ 3: สรุปผลการคัดกรองสุขภาพชุมชนและโรคไม่ติดต่อ (NCDs)")
        sec3Row.getCell(0).cellStyle = sectionHeaderStyle
        sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 5))

        val healthItems = listOf(
            "ประชากรที่ได้รับการคัดกรองสุขภาพแล้ว" to "${summary.screenedCount} คน",
            "ความดันโลหิต ปกติ (<130/85)" to "${summary.bpNormal} คน",
            "ความดันโลหิต กลุ่มเสี่ยง (130-139/85-89)" to "${summary.bpRisk} คน",
            "ความดันโลหิต สงสัยป่วย/สูง (≥140/90)" to "${summary.bpHigh} คน",
            "ระดับน้ำตาลในเลือด ปกติ (<100 mg/dL)" to "${summary.dtxNormal} คน",
            "ระดับน้ำตาลในเลือด กลุ่มเสี่ยง (100-125 mg/dL)" to "${summary.dtxRisk} คน",
            "ระดับน้ำตาลในเลือด สงสัยป่วย/สูง (≥126 mg/dL)" to "${summary.dtxHigh} คน",
            "ภาวะโภชนาการ BMI ปกติ (18.5 - 22.9)" to "${summary.bmiNormal} คน",
            "ภาวะโภชนาการ BMI น้ำหนักเกิน/อ้วน (≥23.0)" to "${summary.bmiOverweight + summary.bmiObese} คน"
        )
        healthItems.forEach { (lbl, valStr) ->
            val row = sheet1.createRow(r++)
            val c0 = row.createCell(0); c0.setCellValue(lbl); c0.cellStyle = normalBorderedStyle
            sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 3))
            val c1 = row.createCell(4); c1.setCellValue(valStr); c1.cellStyle = numberCenterStyle
            sheet1.addMergedRegion(CellRangeAddress(r - 1, r - 1, 4, 5))
        }

        for (i in 0..5) sheet1.autoSizeColumn(i)

        // ================= SHEET 2: ทะเบียนประชากรและสุขภาพรายบุคคล =================
        val sheet2 = workbook.createSheet("ทะเบียนประชากรรายคน")
        sheet2.isDisplayGridlines = true

        val s2Headers = listOf(
            "ลำดับ", "บ้านเลขที่", "หมู่ที่", "ชื่อ - นามสกุล", "เลขประจำตัวประชาชน",
            "เพศ", "อายุ (ปี)", "กลุ่มอายุ อสม.", "สถานะในบ้าน", "สถานะบุคคล",
            "สิทธิการรักษา", "เบอร์โทร", "ความดัน (mmHg)", "น้ำตาล (mg/dL)", "BMI", "สถานะข้อมูล"
        )
        val s2HeaderRow = sheet2.createRow(0)
        s2Headers.forEachIndexed { idx, txt ->
            val cell = s2HeaderRow.createCell(idx)
            cell.setCellValue(txt)
            cell.cellStyle = tableHeaderStyle
        }

        filteredPersons.sortedWith(compareBy({ householdMap[it.householdId]?.houseNo }, { it.fullName })).forEachIndexed { idx, p ->
            val h = householdMap[p.householdId]
            val row = sheet2.createRow(idx + 1)
            val age = p.birthDate?.let { Period.between(it, LocalDate.now()).years }
            val ageGroup = VhvAgeGroup.fromAge(age)
            val sc = latestScreeningMap[p.id]

            row.createCell(0).apply { setCellValue((idx + 1).toDouble()); cellStyle = numberCenterStyle }
            row.createCell(1).apply { setCellValue(h?.houseNo ?: ""); cellStyle = numberCenterStyle }
            row.createCell(2).apply { setCellValue(h?.villageNo ?: ""); cellStyle = numberCenterStyle }
            row.createCell(3).apply { setCellValue(p.fullName); cellStyle = normalBorderedStyle }
            row.createCell(4).apply { setCellValue(p.nationalId ?: "-"); cellStyle = numberCenterStyle }
            row.createCell(5).apply { setCellValue(p.gender.value); cellStyle = numberCenterStyle }
            row.createCell(6).apply { setCellValue(age?.toDouble() ?: 0.0); cellStyle = numberCenterStyle }
            row.createCell(7).apply { setCellValue(ageGroup.value); cellStyle = normalBorderedStyle }
            row.createCell(8).apply { setCellValue(p.houseStatus.value); cellStyle = normalBorderedStyle }
            row.createCell(9).apply { setCellValue(p.personStatus.value); cellStyle = numberCenterStyle }
            row.createCell(10).apply { setCellValue(p.healthInsurance ?: "-"); cellStyle = normalBorderedStyle }
            row.createCell(11).apply { setCellValue(p.phoneNumber ?: "-"); cellStyle = numberCenterStyle }

            val bpStr = if (sc?.systolic != null && sc.diastolic != null) "${sc.systolic}/${sc.diastolic}" else "-"
            val dtxStr = sc?.bloodSugar?.toString() ?: "-"
            val bmiStr = sc?.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "-"

            row.createCell(12).apply { setCellValue(bpStr); cellStyle = numberCenterStyle }
            row.createCell(13).apply { setCellValue(dtxStr); cellStyle = numberCenterStyle }
            row.createCell(14).apply { setCellValue(bmiStr); cellStyle = numberCenterStyle }
            row.createCell(15).apply { setCellValue(p.dataStatus.value); cellStyle = numberCenterStyle }
        }

        for (i in 0..15) sheet2.setColumnWidth(i, 4000)

        // Write to Cache Directory
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val fileName = "รายงาน_อสม1_${villageLabel.replace(" ", "_")}_${System.currentTimeMillis()}.xlsx"
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { out ->
            workbook.write(out)
        }
        workbook.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Exports official VHV Report OSM 2 (Elderly & Vulnerable Care Plan) as formatted Excel (.xlsx).
     */
    fun generateOsm2Excel(
        context: Context,
        villageFilter: String,
        households: List<Household>,
        persons: List<Person>,
        screenings: List<HealthScreening>
    ): Uri {
        val filteredHouseholds = if (villageFilter == "ALL") households else households.filter { it.villageNo == villageFilter }
        val householdMap = filteredHouseholds.associateBy { it.id }
        val now = LocalDate.now()

        val elderlyPersons = persons.filter { p ->
            p.householdId in householdMap.keys &&
                    p.personStatus == PersonStatus.ALIVE &&
                    p.birthDate != null &&
                    Period.between(p.birthDate, now).years >= 60
        }.sortedByDescending { Period.between(it.birthDate, now).years }

        val latestScreeningMap = screenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }

        val workbook = XSSFWorkbook()

        val headerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
            color = IndexedColors.WHITE.index
        }
        val boldFont = workbook.createFont().apply { bold = true }

        val blueHeaderStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
        val tableHeaderStyle = workbook.createCellStyle().apply {
            setFont(boldFont)
            fillForegroundColor = IndexedColors.PALE_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            alignment = HorizontalAlignment.CENTER
        }
        val normalBorderedStyle = workbook.createCellStyle().apply {
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        val numberCenterStyle = workbook.createCellStyle().apply {
            borderTop = BorderStyle.THIN
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            alignment = HorizontalAlignment.CENTER
        }

        val sheet = workbook.createSheet("รายงานผู้สูงอายุ อสม.2")
        sheet.isDisplayGridlines = true

        var r = 0
        val titleRow = sheet.createRow(r++)
        titleRow.heightInPoints = 32f
        val villageLabel = if (villageFilter == "ALL") "ทุกหมู่บ้าน" else "หมู่ที่ $villageFilter"
        titleRow.createCell(0).apply {
            setCellValue("แบบรายงานผู้สูงอายุและกลุ่มเปราะบาง (รายงาน อสม. 2)")
            cellStyle = blueHeaderStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 11))

        val subRow = sheet.createRow(r++)
        subRow.createCell(0).setCellValue("พื้นที่: ตำบลป่าขะ อำเภอบ้านนา จังหวัดนครนายก | $villageLabel | ผู้สูงอายุทั้งหมด: ${elderlyPersons.size} คน")
        sheet.addMergedRegion(CellRangeAddress(1, 1, 0, 11))
        r++ // blank row

        val headers = listOf(
            "ลำดับ", "บ้านเลขที่", "หมู่ที่", "ชื่อ - นามสกุล", "อายุ (ปี)", "ช่วงวัยสูงอายุ",
            "สิทธิการรักษา", "เบอร์โทร", "ความดัน (mmHg)", "ระดับน้ำตาล (mg/dL)", "ระดับความเสี่ยงสุขภาพ", "การดูแลโดย อสม."
        )
        val hRow = sheet.createRow(r++)
        headers.forEachIndexed { idx, txt ->
            hRow.createCell(idx).apply {
                setCellValue(txt)
                cellStyle = tableHeaderStyle
            }
        }

        elderlyPersons.forEachIndexed { idx, p ->
            val h = householdMap[p.householdId]
            val age = Period.between(p.birthDate, now).years
            val ageBracket = when {
                age >= 80 -> "วัยปลาย (80+ ปี)"
                age >= 70 -> "วัยกลาง (70-79 ปี)"
                else -> "วัยต้น (60-69 ปี)"
            }
            val sc = latestScreeningMap[p.id]
            val sys = sc?.systolic
            val dia = sc?.diastolic
            val dtx = sc?.bloodSugar

            val isHighBp = sys != null && dia != null && (sys >= 140 || dia >= 90)
            val isHighDtx = dtx != null && dtx >= 126
            val riskLevel = when {
                isHighBp && isHighDtx -> "เสี่ยงสูงมาก (BP & DTX สูง)"
                isHighBp -> "เสี่ยงความดันโลหิตสูง"
                isHighDtx -> "เสี่ยงเบาหวาน"
                sc != null -> "ปกติ/ความเสี่ยงต่ำ"
                else -> "ยังไม่ได้รับการคัดกรอง"
            }
            val carePlan = when {
                age >= 80 || isHighBp || isHighDtx -> "เยี่ยมบ้านสัปดาห์ละ 1-2 ครั้ง"
                else -> "เยี่ยมบ้านเดือนละ 1 ครั้ง"
            }

            val row = sheet.createRow(r++)
            row.createCell(0).apply { setCellValue((idx + 1).toDouble()); cellStyle = numberCenterStyle }
            row.createCell(1).apply { setCellValue(h?.houseNo ?: ""); cellStyle = numberCenterStyle }
            row.createCell(2).apply { setCellValue(h?.villageNo ?: ""); cellStyle = numberCenterStyle }
            row.createCell(3).apply { setCellValue(p.fullName); cellStyle = normalBorderedStyle }
            row.createCell(4).apply { setCellValue(age.toDouble()); cellStyle = numberCenterStyle }
            row.createCell(5).apply { setCellValue(ageBracket); cellStyle = normalBorderedStyle }
            row.createCell(6).apply { setCellValue(p.healthInsurance ?: "-"); cellStyle = normalBorderedStyle }
            row.createCell(7).apply { setCellValue(p.phoneNumber ?: "-"); cellStyle = numberCenterStyle }
            row.createCell(8).apply { setCellValue(if (sys != null && dia != null) "$sys/$dia" else "-"); cellStyle = numberCenterStyle }
            row.createCell(9).apply { setCellValue(dtx?.toString() ?: "-"); cellStyle = numberCenterStyle }
            row.createCell(10).apply { setCellValue(riskLevel); cellStyle = normalBorderedStyle }
            row.createCell(11).apply { setCellValue(carePlan); cellStyle = normalBorderedStyle }
        }

        for (i in 0..11) sheet.setColumnWidth(i, 4500)

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val fileName = "รายงาน_อสม2_ผู้สูงอายุ_${villageLabel.replace(" ", "_")}_${System.currentTimeMillis()}.xlsx"
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { out ->
            workbook.write(out)
        }
        workbook.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Generates a fully printable A4 HTML Document for Official OSM 1 Report.
     */
    fun generateOsm1Html(
        summary: Osm1Summary,
        villageFilter: String
    ): String {
        val villageLabel = if (villageFilter == "ALL") "ทุกหมู่บ้าน (13 หมู่บ้าน)" else "หมู่ที่ $villageFilter"
        val totalAlive = summary.alivePopulation.coerceAtLeast(1)

        val p1 = String.format(Locale.US, "%.1f", (summary.earlyChildCount.toDouble() * 100) / totalAlive)
        val p2 = String.format(Locale.US, "%.1f", (summary.schoolAgeCount.toDouble() * 100) / totalAlive)
        val p3 = String.format(Locale.US, "%.1f", (summary.teenagerCount.toDouble() * 100) / totalAlive)
        val p4 = String.format(Locale.US, "%.1f", (summary.workingAgeCount.toDouble() * 100) / totalAlive)
        val p5 = String.format(Locale.US, "%.1f", (summary.elderlyCount.toDouble() * 100) / totalAlive)

        return """
            <!DOCTYPE html>
            <html lang="th">
            <head>
                <meta charset="UTF-8">
                <title>รายงานผลการปฏิบัติงาน อสม. 1</title>
                <style>
                    @page { size: A4 portrait; margin: 12mm 10mm; }
                    body { font-family: 'Sarabun', 'Prompt', -apple-system, sans-serif; color: #1e293b; line-height: 1.4; padding: 10px; }
                    .header-box { text-align: center; border-bottom: 2px solid #059669; padding-bottom: 12px; margin-bottom: 16px; }
                    .title { font-size: 18pt; font-weight: bold; color: #065f46; margin: 0; }
                    .subtitle { font-size: 11pt; color: #475569; margin: 4px 0; }
                    .meta { font-size: 9.5pt; color: #64748b; }
                    .section-title { font-size: 12pt; font-weight: bold; color: #047857; margin-top: 16px; margin-bottom: 6px; border-left: 4px solid #059669; padding-left: 8px; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 14px; font-size: 10pt; }
                    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; }
                    th { background-color: #f1f5f9; font-weight: bold; text-align: center; }
                    .center { text-align: center; }
                    .right { text-align: right; }
                    .badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 8.5pt; font-weight: bold; }
                    .badge-green { background-color: #d1fae5; color: #065f46; }
                    .badge-yellow { background-color: #fef3c7; color: #92400e; }
                    .badge-red { background-color: #fee2e2; color: #991b1b; }
                    .signature-box { margin-top: 28px; display: flex; justify-content: space-between; page-break-inside: avoid; }
                    .sign-col { text-align: center; width: 45%; }
                    .sign-line { border-bottom: 1px dotted #64748b; margin: 36px 20px 8px 20px; }
                </style>
            </head>
            <body>
                <div class="header-box">
                    <h1 class="title">แบบรายงานผลการปฏิบัติงานของ อสม. ประจำเดือน (อสม. 1)</h1>
                    <p class="subtitle">ระบบสารสนเทศสุขภาพชุมชน Smart OSM • ตำบลป่าขะ อำเภอบ้านนา จังหวัดนครนายก</p>
                    <p class="meta">พื้นที่รายงาน: <b>$villageLabel</b> | ข้อมูล ณ วันที่ ${thaiDateFormat.format(Date())}</p>
                </div>

                <div class="section-title">หมวดที่ 1: ข้อมูลประชากรและครัวเรือนทั่วไป</div>
                <table>
                    <tr>
                        <th width="35%">รายการสำรวจ</th>
                        <th width="15%">จำนวน</th>
                        <th width="35%">รายการสำรวจ</th>
                        <th width="15%">จำนวน</th>
                    </tr>
                    <tr>
                        <td>ครัวเรือนทั้งหมดในระบบ</td>
                        <td class="center"><b>${summary.totalHouseholds}</b> หลัง</td>
                        <td>ประชากรทั้งหมดที่ลงทะเบียน</td>
                        <td class="center"><b>${summary.totalPopulation}</b> คน</td>
                    </tr>
                    <tr>
                        <td>ครัวเรือนที่มีพิกัด GPS</td>
                        <td class="center">${summary.householdsWithGps} หลัง</td>
                        <td>ประชากรที่มีชีวิต (ปัจจุบัน)</td>
                        <td class="center"><span class="badge badge-green">${summary.alivePopulation} คน</span></td>
                    </tr>
                    <tr>
                        <td>ครัวเรือนรอตรวจสอบ</td>
                        <td class="center">${summary.householdsNeedsReview} หลัง</td>
                        <td>สัดส่วนเพศ (ชาย / หญิง)</td>
                        <td class="center">${summary.maleCount} / ${summary.femaleCount} คน</td>
                    </tr>
                    <tr>
                        <td>ประชากรย้ายถิ่นออก</td>
                        <td class="center">${summary.movedCount} คน</td>
                        <td>ประชากรเสียชีวิตแล้ว</td>
                        <td class="center">${summary.deceasedCount} คน</td>
                    </tr>
                </table>

                <div class="section-title">หมวดที่ 2: สรุปการคัดแยกกลุ่มอายุตามเกณฑ์ อสม. (5 กลุ่มเป้าหมาย)</div>
                <table>
                    <thead>
                        <tr>
                            <th width="8%">ลำดับ</th>
                            <th width="24%">กลุ่มเป้าหมาย อสม.</th>
                            <th width="18%">ช่วงอายุ</th>
                            <th width="15%">จำนวน (คน)</th>
                            <th width="15%">ร้อยละ (%)</th>
                            <th width="20%">ภารกิจดูแล อสม.</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td class="center">1</td>
                            <td><b>เด็กปฐมวัย</b></td>
                            <td class="center">0 - 5 ปี</td>
                            <td class="center"><b>${summary.earlyChildCount}</b></td>
                            <td class="center">$p1%</td>
                            <td>วัคซีนครบ, พัฒนาการสมวัย</td>
                        </tr>
                        <tr>
                            <td class="center">2</td>
                            <td><b>เด็กวัยเรียน</b></td>
                            <td class="center">6 - 12 ปี</td>
                            <td class="center"><b>${summary.schoolAgeCount}</b></td>
                            <td class="center">$p2%</td>
                            <td>อนามัยโรงเรียน, สุขภาพฟัน</td>
                        </tr>
                        <tr>
                            <td class="center">3</td>
                            <td><b>วัยรุ่น</b></td>
                            <td class="center">13 - 20 ปี</td>
                            <td class="center"><b>${summary.teenagerCount}</b></td>
                            <td class="center">$p3%</td>
                            <td>สุขภาพจิต, สารเสพติด</td>
                        </tr>
                        <tr>
                            <td class="center">4</td>
                            <td><b>วัยทำงาน</b></td>
                            <td class="center">21 - 59 ปี</td>
                            <td class="center"><b>${summary.workingAgeCount}</b></td>
                            <td class="center">$p4%</td>
                            <td>คัดกรอง NCDs ความดัน/เบาหวาน</td>
                        </tr>
                        <tr>
                            <td class="center">5</td>
                            <td><b>ผู้สูงอายุ</b></td>
                            <td class="center">60 ปีขึ้นไป</td>
                            <td class="center"><b><span class="badge badge-yellow">${summary.elderlyCount}</span></b></td>
                            <td class="center">$p5%</td>
                            <td>เยี่ยมบ้าน, ติดบ้าน/ติดเตียง</td>
                        </tr>
                        <tr style="background-color: #f8fafc; font-weight: bold;">
                            <td colspan="3" class="center">รวมประชากรที่มีชีวิตทั้งหมด</td>
                            <td class="center">${summary.alivePopulation}</td>
                            <td class="center">100.0%</td>
                            <td>-</td>
                        </tr>
                    </tbody>
                </table>

                <div class="section-title">หมวดที่ 3: สรุปผลการคัดกรองสุขภาพและโรคไม่ติดต่อเรื้อรัง (NCDs)</div>
                <table>
                    <tr>
                        <th width="40%">การคัดกรองความดันโลหิต</th>
                        <th width="10%">คน</th>
                        <th width="40%">การคัดกรองระดับน้ำตาลในเลือด</th>
                        <th width="10%">คน</th>
                    </tr>
                    <tr>
                        <td>• ปกติ (&lt;130/85 mmHg)</td>
                        <td class="center"><span class="badge badge-green">${summary.bpNormal}</span></td>
                        <td>• ปกติ (&lt;100 mg/dL)</td>
                        <td class="center"><span class="badge badge-green">${summary.dtxNormal}</span></td>
                    </tr>
                    <tr>
                        <td>• กลุ่มเสี่ยง (130-139 / 85-89 mmHg)</td>
                        <td class="center"><span class="badge badge-yellow">${summary.bpRisk}</span></td>
                        <td>• กลุ่มเสี่ยง (100-125 mg/dL)</td>
                        <td class="center"><span class="badge badge-yellow">${summary.dtxRisk}</span></td>
                    </tr>
                    <tr>
                        <td>• กลุ่มสงสัยป่วย/สูง (≥140/90 mmHg)</td>
                        <td class="center"><span class="badge badge-red">${summary.bpHigh}</span></td>
                        <td>• กลุ่มสงสัยป่วย/สูง (≥126 mg/dL)</td>
                        <td class="center"><span class="badge badge-red">${summary.dtxHigh}</span></td>
                    </tr>
                    <tr>
                        <td colspan="3"><b>จำนวนประชากรที่ได้รับการตรวจคัดกรองสุขภาพแล้ว</b></td>
                        <td class="center"><b>${summary.screenedCount} คน</b></td>
                    </tr>
                </table>

                <div class="signature-box">
                    <div class="sign-col">
                        <div class="sign-line"></div>
                        <p style="margin: 0;">ลงชื่อ ..............................................................</p>
                        <p style="margin: 4px 0; font-size: 9pt; color: #64748b;">( ตัวแทน อสม. ผู้จัดทำรายงาน )</p>
                    </div>
                    <div class="sign-col">
                        <div class="sign-line"></div>
                        <p style="margin: 0;">ลงชื่อ ..............................................................</p>
                        <p style="margin: 4px 0; font-size: 9pt; color: #64748b;">( เจ้าหน้าที่สาธารณสุข รพ.สต. ผู้รับรายงาน )</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates a fully printable A4 HTML Document for Official OSM 2 Report (Elderly & Vulnerable Care).
     */
    fun generateOsm2Html(
        villageFilter: String,
        households: List<Household>,
        persons: List<Person>,
        screenings: List<HealthScreening>
    ): String {
        val villageLabel = if (villageFilter == "ALL") "ทุกหมู่บ้าน (13 หมู่บ้าน)" else "หมู่ที่ $villageFilter"
        val filteredHouseholds = if (villageFilter == "ALL") households else households.filter { it.villageNo == villageFilter }
        val householdMap = filteredHouseholds.associateBy { it.id }
        val now = LocalDate.now()

        val elderlyPersons = persons.filter { p ->
            p.householdId in householdMap.keys &&
                    p.personStatus == PersonStatus.ALIVE &&
                    p.birthDate != null &&
                    Period.between(p.birthDate, now).years >= 60
        }.sortedByDescending { Period.between(it.birthDate, now).years }

        val latestScreeningMap = screenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }

        var countEarly = 0
        var countMid = 0
        var countLate = 0
        var countHighRisk = 0
        var countScreened = 0

        val rows = elderlyPersons.mapIndexed { idx, p ->
            val h = householdMap[p.householdId]
            val age = Period.between(p.birthDate, now).years
            val ageBracket = when {
                age >= 80 -> { countLate++; "วัยปลาย (80+)" }
                age >= 70 -> { countMid++; "วัยกลาง (70-79)" }
                else -> { countEarly++; "วัยต้น (60-69)" }
            }

            val sc = latestScreeningMap[p.id]
            if (sc != null) countScreened++

            val sys = sc?.systolic
            val dia = sc?.diastolic
            val dtx = sc?.bloodSugar

            val bpStr = if (sys != null && dia != null) "$sys/$dia" else "-"
            val dtxStr = dtx?.toString() ?: "-"

            val isHighBp = sys != null && dia != null && (sys >= 140 || dia >= 90)
            val isHighDtx = dtx != null && dtx >= 126

            val riskBadge = when {
                isHighBp && isHighDtx -> {
                    countHighRisk++
                    "<span class='badge badge-red'>เสี่ยงสูง BP&DTX</span>"
                }
                isHighBp -> {
                    countHighRisk++
                    "<span class='badge badge-red'>เสี่ยงความดัน</span>"
                }
                isHighDtx -> {
                    countHighRisk++
                    "<span class='badge badge-red'>เสี่ยงเบาหวาน</span>"
                }
                sc != null -> "<span class='badge badge-green'>ปกติ</span>"
                else -> "<span class='badge badge-yellow'>รอคัดกรอง</span>"
            }

            val carePlan = when {
                age >= 80 || isHighBp || isHighDtx -> "เยี่ยมบ้านสัปดาห์ละ 1-2 ครั้ง"
                else -> "เยี่ยมบ้านเดือนละ 1 ครั้ง"
            }

            """
                <tr>
                    <td class="center">${idx + 1}</td>
                    <td class="center">${h?.houseNo ?: "-"}</td>
                    <td class="center">${h?.villageNo ?: "-"}</td>
                    <td><b>${p.fullName}</b></td>
                    <td class="center"><b>$age</b></td>
                    <td class="center">$ageBracket</td>
                    <td>${p.healthInsurance ?: "บัตรทอง"}</td>
                    <td class="center">${p.phoneNumber ?: "-"}</td>
                    <td class="center">$bpStr</td>
                    <td class="center">$dtxStr</td>
                    <td class="center">$riskBadge</td>
                    <td>$carePlan</td>
                </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
            <!DOCTYPE html>
            <html lang="th">
            <head>
                <meta charset="UTF-8">
                <title>รายงานผู้สูงอายุและกลุ่มเปราะบาง อสม. 2</title>
                <style>
                    @page { size: A4 landscape; margin: 10mm 10mm; }
                    body { font-family: 'Sarabun', 'Prompt', -apple-system, sans-serif; color: #1e293b; line-height: 1.35; padding: 10px; }
                    .header-box { text-align: center; border-bottom: 2px solid #2563eb; padding-bottom: 10px; margin-bottom: 12px; }
                    .title { font-size: 16pt; font-weight: bold; color: #1e40af; margin: 0; }
                    .subtitle { font-size: 10.5pt; color: #475569; margin: 2px 0; }
                    .meta { font-size: 9pt; color: #64748b; }
                    .stat-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-bottom: 12px; }
                    .stat-card { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px; padding: 6px 10px; text-align: center; }
                    .stat-card .num { font-size: 14pt; font-weight: bold; color: #1d4ed8; }
                    .stat-card .lbl { font-size: 8.5pt; color: #64748b; }
                    table { width: 100%; border-collapse: collapse; font-size: 9pt; }
                    th, td { border: 1px solid #cbd5e1; padding: 5px 6px; }
                    th { background-color: #eff6ff; font-weight: bold; text-align: center; color: #1e3a8a; }
                    .center { text-align: center; }
                    .badge { display: inline-block; padding: 2px 5px; border-radius: 4px; font-size: 8pt; font-weight: bold; }
                    .badge-green { background-color: #d1fae5; color: #065f46; }
                    .badge-yellow { background-color: #fef3c7; color: #92400e; }
                    .badge-red { background-color: #fee2e2; color: #991b1b; }
                    .signature-box { margin-top: 24px; display: flex; justify-content: space-between; page-break-inside: avoid; }
                    .sign-col { text-align: center; width: 45%; }
                    .sign-line { border-bottom: 1px dotted #64748b; margin: 30px 20px 6px 20px; }
                </style>
            </head>
            <body>
                <div class="header-box">
                    <h1 class="title">แบบรายงานผู้สูงอายุและกลุ่มเปราะบาง (รายงาน อสม. 2)</h1>
                    <p class="subtitle">ระบบสารสนเทศสุขภาพชุมชน Smart OSM • ตำบลป่าขะ อำเภอบ้านนา จังหวัดนครนายก</p>
                    <p class="meta">พื้นที่: <b>$villageLabel</b> | พิมพ์เมื่อ: ${dateTimeFormat.format(Date())}</p>
                </div>

                <div class="stat-grid">
                    <div class="stat-card">
                        <div class="num">${elderlyPersons.size}</div>
                        <div class="lbl">ผู้สูงอายุทั้งหมด (คน)</div>
                    </div>
                    <div class="stat-card">
                        <div class="num">$countEarly</div>
                        <div class="lbl">วัยต้น (60-69 ปี)</div>
                    </div>
                    <div class="stat-card">
                        <div class="num">$countMid</div>
                        <div class="lbl">วัยกลาง (70-79 ปี)</div>
                    </div>
                    <div class="stat-card">
                        <div class="num">$countLate</div>
                        <div class="lbl">วัยปลาย (80+ ปี)</div>
                    </div>
                    <div class="stat-card">
                        <div class="num" style="color: #dc2626;">$countHighRisk</div>
                        <div class="lbl">กลุ่มเสี่ยงสูง NCDs</div>
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th width="3%">ที่</th>
                            <th width="7%">บ้านเลขที่</th>
                            <th width="5%">หมู่ที่</th>
                            <th width="18%">ชื่อ - นามสกุล</th>
                            <th width="5%">อายุ</th>
                            <th width="10%">ช่วงวัยสูงอายุ</th>
                            <th width="10%">สิทธิการรักษา</th>
                            <th width="9%">เบอร์โทร</th>
                            <th width="8%">ความดัน</th>
                            <th width="7%">น้ำตาล</th>
                            <th width="9%">ระดับความเสี่ยง</th>
                            <th width="12%">การดูแลโดย อสม.</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>

                <div class="signature-box">
                    <div class="sign-col">
                        <div class="sign-line"></div>
                        <p style="margin: 0; font-size: 9.5pt;">ลงชื่อ ..............................................................</p>
                        <p style="margin: 4px 0; font-size: 8.5pt; color: #64748b;">( ตัวแทน อสม. ผู้จัดทำรายงาน )</p>
                    </div>
                    <div class="sign-col">
                        <div class="sign-line"></div>
                        <p style="margin: 0; font-size: 9.5pt;">ลงชื่อ ..............................................................</p>
                        <p style="margin: 4px 0; font-size: 8.5pt; color: #64748b;">( เจ้าหน้าที่สาธารณสุข รพ.สต. ผู้รับรายงาน )</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates a fully printable A4 HTML Document for Family Health Folder (แฟ้มประวัติสุขภาพประจำบ้าน).
     */
    fun generateFamilyFolderHtml(
        household: Household,
        persons: List<Person>,
        screenings: List<HealthScreening>
    ): String {
        val now = LocalDate.now()
        val latestScreeningMap = screenings.groupBy { it.personId }.mapValues { it.value.maxByOrNull { s -> s.timestamp } }
        val headPerson = persons.find { it.houseStatus == HouseholdRole.HEAD }

        val memberRows = persons.mapIndexed { idx, p ->
            val age = p.birthDate?.let { Period.between(it, now).years }
            val ageStr = age?.toString() ?: "-"
            val sc = latestScreeningMap[p.id]
            val bpStr = if (sc?.systolic != null && sc.diastolic != null) "${sc.systolic}/${sc.diastolic}" else "-"
            val dtxStr = sc?.bloodSugar?.toString() ?: "-"
            val bmiStr = sc?.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "-"

            val isHighBp = sc?.systolic != null && sc.systolic >= 140
            val isHighDtx = sc?.bloodSugar != null && sc.bloodSugar >= 126
            val riskBadge = when {
                isHighBp || isHighDtx -> "<span class='badge badge-red'>กลุ่มสงสัยป่วย/เสี่ยงสูง</span>"
                sc != null -> "<span class='badge badge-green'>ปกติ</span>"
                else -> "<span class='badge badge-yellow'>รอคัดกรอง</span>"
            }

            """
                <tr>
                    <td class="center">${idx + 1}</td>
                    <td><b>${p.fullName}</b></td>
                    <td class="center">${p.nationalId ?: "-"}</td>
                    <td class="center">${p.gender.value}</td>
                    <td class="center">$ageStr</td>
                    <td>${p.houseStatus.value}</td>
                    <td>${p.healthInsurance ?: "บัตรทอง"}</td>
                    <td class="center">${p.phoneNumber ?: "-"}</td>
                    <td class="center">$bpStr</td>
                    <td class="center">$dtxStr</td>
                    <td class="center">$bmiStr</td>
                    <td class="center">$riskBadge</td>
                </tr>
            """.trimIndent()
        }.joinToString("\n")

        val gpsText = if (household.latitude != null && household.longitude != null) {
            "${String.format(Locale.US, "%.5f", household.latitude)}, ${String.format(Locale.US, "%.5f", household.longitude)}"
        } else "ยังไม่ได้ระบุพิกัด"

        return """
            <!DOCTYPE html>
            <html lang="th">
            <head>
                <meta charset="UTF-8">
                <title>แฟ้มประวัติสุขภาพประจำบ้าน Family Folder</title>
                <style>
                    @page { size: A4 landscape; margin: 10mm 10mm; }
                    body { font-family: 'Sarabun', 'Prompt', -apple-system, sans-serif; color: #1e293b; line-height: 1.35; padding: 10px; }
                    .header-box { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #059669; padding-bottom: 10px; margin-bottom: 12px; }
                    .title { font-size: 16pt; font-weight: bold; color: #065f46; margin: 0; }
                    .subtitle { font-size: 10.5pt; color: #475569; margin: 2px 0; }
                    .house-badge { background-color: #059669; color: white; padding: 6px 14px; border-radius: 8px; font-size: 14pt; font-weight: bold; text-align: center; }
                    .info-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; background: #f8fafc; border: 1px solid #cbd5e1; padding: 10px; border-radius: 8px; margin-bottom: 12px; font-size: 9.5pt; }
                    .info-item b { color: #047857; }
                    table { width: 100%; border-collapse: collapse; font-size: 9pt; margin-top: 8px; }
                    th, td { border: 1px solid #cbd5e1; padding: 5px 6px; }
                    th { background-color: #e2e8f0; font-weight: bold; text-align: center; }
                    .center { text-align: center; }
                    .badge { display: inline-block; padding: 2px 5px; border-radius: 4px; font-size: 8pt; font-weight: bold; }
                    .badge-green { background-color: #d1fae5; color: #065f46; }
                    .badge-yellow { background-color: #fef3c7; color: #92400e; }
                    .badge-red { background-color: #fee2e2; color: #991b1b; }
                    .footer-note { font-size: 8.5pt; color: #64748b; margin-top: 14px; display: flex; justify-content: space-between; }
                </style>
            </head>
            <body>
                <div class="header-box">
                    <div>
                        <h1 class="title">แฟ้มประวัติสุขภาพประจำบ้าน (Family Health Folder)</h1>
                        <p class="subtitle">ระบบสารสนเทศสุขภาพชุมชน Smart OSM • โรงพยาบาลส่งเสริมสุขภาพตำบล (รพ.สต.)</p>
                    </div>
                    <div class="house-badge">
                        บ้านเลขที่ ${household.houseNo}
                    </div>
                </div>

                <div class="info-grid">
                    <div class="info-item"><b>รหัสประจำบ้าน:</b> ${household.houseId ?: "-"}</div>
                    <div class="info-item"><b>หมู่ที่:</b> ${household.villageNo.ifBlank { "-" }}</div>
                    <div class="info-item"><b>ตำบล:</b> ${household.subdistrict.ifBlank { "ต.ป่าขะ" }}</div>
                    <div class="info-item"><b>อำเภอ/จังหวัด:</b> ${household.district.ifBlank { "อ.บ้านนา" }} จ.${household.province.ifBlank { "นครนายก" }}</div>
                    <div class="info-item"><b>หัวหน้าครัวเรือน:</b> ${headPerson?.fullName ?: "ไม่ระบุ"}</div>
                    <div class="info-item"><b>เบอร์โทรติดต่อ:</b> ${headPerson?.phoneNumber ?: "-"}</div>
                    <div class="info-item"><b>จำนวนสมาชิก:</b> ${persons.size} คน</div>
                    <div class="info-item"><b>พิกัด GPS:</b> $gpsText</div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th width="3%">ที่</th>
                            <th width="15%">ชื่อ - นามสกุล</th>
                            <th width="12%">เลขประจำตัว ปชช.</th>
                            <th width="5%">เพศ</th>
                            <th width="5%">อายุ</th>
                            <th width="8%">สถานะในบ้าน</th>
                            <th width="10%">สิทธิการรักษา</th>
                            <th width="9%">เบอร์โทร</th>
                            <th width="9%">ความดัน (mmHg)</th>
                            <th width="8%">น้ำตาล (mg%)</th>
                            <th width="6%">BMI</th>
                            <th width="10%">สถานะสุขภาพ</th>
                        </tr>
                    </thead>
                    <tbody>
                        $memberRows
                    </tbody>
                </table>

                <div class="footer-note">
                    <span>ข้อมูล ณ วันที่: ${dateTimeFormat.format(Date())}</span>
                    <span>ผู้รับผิดชอบดูแล: อาสาสมัครสาธารณสุขประจำหมู่บ้าน (อสม.) ชุมชนตำบลป่าขะ</span>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Prints HTML document via Android PrintManager (Native A4 print and Save-as-PDF).
     */
    fun printHtmlDocument(activity: Activity, htmlContent: String, jobName: String) {
        activity.runOnUiThread {
            val webView = WebView(activity)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager?.print(jobName, printAdapter, printAttributes)
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html; charset=utf-8", "UTF-8", null)
        }
    }

    /**
     * Shares file via Android System Chooser (LINE, Drive, Gmail, Files).
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    /**
     * Shares summary text directly to LINE / chat.
     */
    fun shareText(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
