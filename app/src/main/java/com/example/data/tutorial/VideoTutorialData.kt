package com.example.data.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Step detail in an interactive short video tutorial.
 */
data class TutorialStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val actionHint: String,
    val narrationText: String,
    val targetHotspotName: String,
    val tip: String? = null
)

/**
 * Video Tutorial Item model representing a bite-sized video guide.
 */
data class VideoTutorialItem(
    val id: String,
    val title: String,
    val shortDescription: String,
    val category: String,
    val durationSeconds: Int,
    val durationText: String,
    val difficulty: String = "ง่ายมาก",
    val icon: ImageVector,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isFeatured: Boolean = false,
    val relatedRoute: String? = null,
    val relatedActionTitle: String? = null,
    val steps: List<TutorialStep> = emptyList(),
    val youtubeUrl: String = "https://www.youtube.com/results?search_query=Smart+OSM+อสม"
)

object VideoTutorialData {
    val TUTORIALS = listOf(
        VideoTutorialItem(
            id = "vhv_setup",
            title = "1. เริ่มต้นใช้งานและลงทะเบียน อสม.",
            shortDescription = "วิธีลงทะเบียนข้อมูลผู้ปฏิบัติงาน อสม. ระบุหมู่บ้าน และตั้งรหัสผ่าน PIN ปลอดภัย",
            category = "เริ่มต้นใช้งาน",
            durationSeconds = 42,
            durationText = "0:42 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.HowToReg,
            primaryColorHex = 0xFF00897B,
            secondaryColorHex = 0xFF26A69A,
            isFeatured = true,
            relatedRoute = "vhv_registration",
            relatedActionTitle = "ไปที่หน้าลงทะเบียน อสม.",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "เลือกวิธีเข้าสู่ระบบ",
                    description = "สามารถลงชื่อเข้าใช้ด้วยบัญชี Google หรือกด 'ใช้งานทันที (ออฟไลน์)' เพื่อเริ่มใช้งานได้โดยไม่ต้องต่อเน็ต",
                    actionHint = "กดปุ่ม 'เข้าสู่ระบบด้วย Google' หรือ 'ใช้งานทันทีในเครื่อง'",
                    narrationText = "ยินดีต้อนรับสู่ Smart OSM ค่ะ สำหรับการเริ่มต้น ให้แตะเลือกเข้าสู่ระบบด้วย Google หรือกดเริ่มใช้งานทันทีแบบออฟไลน์",
                    targetHotspotName = "ปุ่มเข้าสู่ระบบ Google / โหมดออฟไลน์",
                    tip = "แม้ไม่มีสัญญาณอินเทอร์เน็ต แอปก็สามารถบันทึกข้อมูลในเครื่องได้ 100%"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "กรอกข้อมูล อสม. และพื้นที่",
                    description = "ใส่ชื่อ-นามสกุล เบอร์โทรศัพท์ เลขบัตร อสม. และเลือกหมู่บ้านที่ตนเองรับผิดชอบ (เช่น หมู่ 8 ต.ป่าขะ)",
                    actionHint = "พิมพ์ชื่อ และเลือกหมู่บ้านจากรายการ",
                    narrationText = "ขั้นตอนที่สอง กรอกชื่อ นามสกุล และเลือกหมู่บ้านในพื้นที่รับผิดชอบของท่านค่ะ",
                    targetHotspotName = "ช่องกรอกชื่อและเลือกหมู่บ้าน",
                    tip = "ข้อมูลหมู่บ้านจะถูกนำไปจัดกลุ่มบ้านและประชากรให้อัตโนมัติ"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "ตั้งรหัส PIN 4 หลัก",
                    description = "กำหนดรหัส PIN เพื่อความปลอดภัยในการเปิดเข้าใช้งานแอปในครั้งต่อไป",
                    actionHint = "แตะแป้นตัวเลขเพื่อใส่รหัส 4 หลัก",
                    narrationText = "สุดท้าย ตั้งรหัส PIN 4 หลัก เพื่อป้องกันข้อมูลส่วนบุคคลของประชาชนในพื้นที่ค่ะ",
                    targetHotspotName = "แป้นพิมพ์ตัวเลข PIN 4 หลัก",
                    tip = "สามารถใช้เลขเดียวกันเพื่อปลดล็อกเข้าสู่หน้าหลักได้สะดวกรวดเร็ว"
                )
            )
        ),
        VideoTutorialItem(
            id = "add_household",
            title = "2. วิธีสำรวจและปักหมุดบ้านใหม่ (GPS)",
            shortDescription = "บันทึกเลขที่บ้าน พิกัดแผนที่อัตโนมัติ และตรวจนับจำนวนผู้อาศัย",
            category = "สำรวจและบันทึก",
            durationSeconds = 55,
            durationText = "0:55 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.AddHomeWork,
            primaryColorHex = 0xFF2E7D32,
            secondaryColorHex = 0xFF66BB6A,
            isFeatured = true,
            relatedRoute = "household_form/-1",
            relatedActionTitle = "ไปที่หน้าเพิ่มบ้านใหม่",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "กดปุ่มเพิ่มครัวเรือน",
                    description = "ที่หน้าแรกหรือหน้าครัวเรือน ให้แตะปุ่ม '+' สีเขียวด้านล่างขวาเพื่อสร้างบ้านใหม่",
                    actionHint = "แตะปุ่ม FAB '+' สีเขียว",
                    narrationText = "เมื่อลงพื้นที่สำรวจบ้าน ให้แตะปุ่มเครื่องหมายบวกสีเขียว เพื่อเริ่มบันทึกบ้านใหม่ค่ะ",
                    targetHotspotName = "ปุ่ม + เพิ่มครัวเรือน (Floating Action Button)",
                    tip = "สามารถกดจากหน้าแผนที่เพื่อปักหมุดตรงจุดที่ยืนอยู่ได้ทันที"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "กรอกเลขที่บ้านและข้อมูลพื้นฐาน",
                    description = "ใส่บ้านเลขที่ หมู่ที่ และชื่อผู้นำหรือเจ้าบ้าน",
                    actionHint = "พิมพ์บ้านเลขที่ เช่น 45/2",
                    narrationText = "พิมพ์บ้านเลขที่ และเลือกหมู่ที่ต้องการสำรวจ",
                    targetHotspotName = "ช่องกรอกบ้านเลขที่",
                    tip = "ระบบจะป้องกันการบันทึกบ้านเลขที่ซ้ำซ้อนให้โดยอัตโนมัติ"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "ดึงพิกัด GPS อัตโนมัติ",
                    description = "แตะปุ่ม 'ดึงพิกัดปัจจุบัน' เพื่อให้ GPS จับตำแหน่งละติจูดและลองจิจูดของตัวบ้านอย่างแม่นยำ",
                    actionHint = "แตะปุ่มไอคอนหมุด 'ดึงพิกัด GPS'",
                    narrationText = "แตะปุ่มดึงพิกัด GPS เพื่อบันทึกตำแหน่งบ้านลงบนแผนที่สารสนเทศค่ะ",
                    targetHotspotName = "ปุ่มค้นหาพิกัด GPS อัตโนมัติ",
                    tip = "แนะนำให้ยืนบริเวณหน้าบ้านเพื่อให้สัญญาณดาวเทียมจับพิกัดได้แม่นยำที่สุด"
                ),
                TutorialStep(
                    stepNumber = 4,
                    title = "กดบันทึกข้อมูล",
                    description = "แตะ 'บันทึกครัวเรือน' ข้อมูลจะถูกจัดเก็บลงในเครื่องและพร้อมสำหรับเพิ่มสมาชิกในบ้าน",
                    actionHint = "แตะปุ่ม 'บันทึก'",
                    narrationText = "ตรวจทานข้อมูลเรียบร้อยแล้วแตะปุ่มบันทึก เป็นอันเสร็จสิ้นการเพิ่มบ้านค่ะ",
                    targetHotspotName = "ปุ่มบันทึกครัวเรือน",
                    tip = "หลังบันทึกเสร็จ สามารถพิมพ์ QR Code ประจำบ้านได้ทันที"
                )
            )
        ),
        VideoTutorialItem(
            id = "add_person",
            title = "3. บันทึกข้อมูลประชากร & สแกนบัตร ปชช.",
            shortDescription = "เพิ่มรายชื่อคนในบ้าน ตรวจสอบสิทธิ วันเกิด กลุ่มวัย และสถานะทางทะเบียน",
            category = "สำรวจและบันทึก",
            durationSeconds = 48,
            durationText = "0:48 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.PersonAdd,
            primaryColorHex = 0xFF0277BD,
            secondaryColorHex = 0xFF29B6F6,
            isFeatured = true,
            relatedRoute = "households",
            relatedActionTitle = "ไปที่รายการครัวเรือนเพื่อเลือกบ้าน",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "เปิดบ้านที่ต้องการเพิ่มสมาชิก",
                    description = "เลือกบ้านเป้าหมายจากรายการ จากนั้นแตะปุ่ม 'เพิ่มสมาชิกในบ้าน'",
                    actionHint = "แตะที่การ์ดบ้าน และกดปุ่ม 'เพิ่มประชากร'",
                    narrationText = "แตะเลือกบ้านที่ต้องการเพิ่มคน แล้วกดปุ่มเพิ่มสมาชิกในบ้านค่ะ",
                    targetHotspotName = "ปุ่ม + เพิ่มสมาชิกในบ้าน",
                    tip = "สมาชิกแต่ละคนจะถูกผูกเข้ากับบ้านเลขที่นั้นโดยอัตโนมัติ"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "กรอกเลขบัตร ปชช. 13 หลัก หรือ สแกน",
                    description = "พิมพ์เลขบัตรประชาชน หรือใช้กล้องสแกน OCR เพื่อดึงเลข 13 หลักและชื่ออัตโนมัติ",
                    actionHint = "กรอกเลข 13 หลัก หรือแตะปุ่มสแกนบัตร",
                    narrationText = "กรอกเลขประจำตัวประชาชน 13 หลัก หรือแตะปุ่มกล้องเพื่อสแกนจากบัตรได้ทันที",
                    targetHotspotName = "ช่องกรอกเลขบัตรประชาชน 13 หลัก",
                    tip = "ระบบมีอัลกอริทึมตรวจสอบความถูกต้องของเลข 13 หลักตามมาตรฐานกรมการปกครอง"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "เลือกวันเกิดและสถานะในบ้าน",
                    description = "เลือกวันเดือนปีเกิด (ระบบจะคำนวณอายุและกลุ่มวัย อสม. ให้อัตโนมัติ) และเลือกสถานะ เช่น เจ้าบ้าน, ผู้อาศัย",
                    actionHint = "เลือกวันเกิดจากปฏิทิน และเลือกสถานะในบ้าน",
                    narrationText = "ใส่วันเกิด ระบบจะจัดกลุ่มวัยเป็น เด็ก วัยรุ่น วัยทำงาน หรือผู้สูงอายุให้อัตโนมัติค่ะ",
                    targetHotspotName = "ตัวเลือกวันเดือนปีเกิดและสถานะในบ้าน",
                    tip = "หากจำวันเกิดไม่ได้ ให้ติ๊ก 'ทราบเฉพาะปีเกิด'"
                )
            )
        ),
        VideoTutorialItem(
            id = "health_screening",
            title = "4. การคัดกรองสุขภาพ (เบาหวาน/ความดัน/NCDs)",
            shortDescription = "บันทึกค่าวัดความดันโลหิต น้ำตาลในเลือด ประเมินความเสี่ยง และติดตามกลุ่มเสี่ยง",
            category = "สุขภาพและคัดกรอง",
            durationSeconds = 60,
            durationText = "1:00 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.MonitorHeart,
            primaryColorHex = 0xFFC2185B,
            secondaryColorHex = 0xFFEC407A,
            isFeatured = true,
            relatedRoute = "persons",
            relatedActionTitle = "ไปที่รายชื่อประชากรเพื่อเริ่มคัดกรอง",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "แตะไอคอนรูปหัวใจที่รายชื่อบุคคล",
                    description = "ค้นหาชื่อบุคคลที่ต้องการตรวจสุขภาพ แล้วแตะปุ่ม 'คัดกรองสุขภาพ'",
                    actionHint = "แตะปุ่มไอคอนหัวใจ (Monitor Heart) บนการ์ดประชากร",
                    narrationText = "สำหรับการตรวจสุขภาพประจำเดือน ให้แตะที่ไอคอนรูปหัวใจที่รายชื่อชาวบ้านค่ะ",
                    targetHotspotName = "ปุ่มคัดกรองสุขภาพ (Monitor Heart)",
                    tip = "สามารถค้นหาชื่อหรือเลขบัตรประชาชนเพื่อความรวดเร็ว"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "บันทึกค่าความดันโลหิต (SYS/DIA)",
                    description = "กรอกค่าความดันตัวบน (SYS) ตัวล่าง (DIA) และชีพจร ระบบจะแสดงแถบสีประเมินความเสี่ยงทันที (เขียว, เหลือง, แดง)",
                    actionHint = "พิมพ์ค่าความดัน เช่น 120 / 80",
                    narrationText = "กรอกค่าวัดความดันโลหิต ระบบจะแปลผลระดับความเสี่ยงเป็นแถบสีให้เข้าใจง่ายทันทีค่ะ",
                    targetHotspotName = "ช่องกรอกความดันโลหิต SYS และ DIA",
                    tip = "ค่าความดันปกติควรต่ำกว่า 120/80 mmHg"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "บันทึกระดับน้ำตาลในเลือด & BMI",
                    description = "ใส่น้ำหนัก ส่วนสูง และค่าน้ำตาลปลายนิ้ว (DTX) พร้อมบันทึกพฤติกรรมเสี่ยง",
                    actionHint = "กรอกน้ำหนัก ส่วนสูง และค่าน้ำตาล",
                    narrationText = "กรอกน้ำหนัก ส่วนสูง และค่าน้ำตาลปลายนิ้ว พร้อมคำแนะนำการดูแลสุขภาพเฉพาะบุคคล",
                    targetHotspotName = "ช่องกรอกน้ำตาล DTX และน้ำหนัก/ส่วนสูง",
                    tip = "ระบบจะคำนวณค่าดัชนีมวลกาย (BMI) ให้โดยไม่ต้องคำนวณเอง"
                )
            )
        ),
        VideoTutorialItem(
            id = "qr_scanner",
            title = "5. การสแกน QR Code ประจำบ้าน",
            shortDescription = "สแกนสติกเกอร์หน้าบ้านเพื่อเข้าถึงข้อมูลครัวเรือนและประชากรใน 1 วินาที",
            category = "สำรวจและบันทึก",
            durationSeconds = 35,
            durationText = "0:35 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.QrCodeScanner,
            primaryColorHex = 0xFF6A1B9A,
            secondaryColorHex = 0xFFAB47BC,
            isFeatured = false,
            relatedRoute = "qr_scanner",
            relatedActionTitle = "เปิดกล้องสแกน QR Code",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "เปิดโหมดสแกน QR Code",
                    description = "แตะที่ไอคอนกล้องสแกนที่มุมบนขวาของหน้าหลัก",
                    actionHint = "แตะไอคอน 'สแกน QR Code'",
                    narrationText = "เมื่อเดินถึงหน้าบ้านชาวบ้าน ให้แตะไอคอนกล้องสแกน QR Code ที่มุมบนขวาค่ะ",
                    targetHotspotName = "ไอคอนสแกน QR Code ใน Top Bar",
                    tip = "ใช้ได้กับ QR Code ที่พิมพ์ออกจากระบบ Smart OSM ทุกหลัง"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "ส่องกล้องไปที่ QR Code หน้าบ้าน",
                    description = "ส่องกล้องให้อยู่ในกรอบสี่เหลี่ยม ระบบจะอ่านรหัสและพาเข้าสู่ข้อมูลบ้านทันที",
                    actionHint = "วาง QR Code ให้อยู่กึ่งกลางกรอบสแกน",
                    narrationText = "ส่องกล้องไปที่ QR Code ระบบจะตรวจจับและเปิดข้อมูลบ้านพร้อมรายชื่อสมาชิกให้ทันทีใน 1 วินาทีค่ะ",
                    targetHotspotName = "กรอบสี่เหลี่ยมตรวจจับ QR Code",
                    tip = "สามารถเปิดไฟแฟลชได้หากสแกนในที่แสงน้อย"
                )
            )
        ),
        VideoTutorialItem(
            id = "cloud_sync",
            title = "6. การซิงค์ Cloud และส่งออกไฟล์ Excel",
            shortDescription = "สำรองข้อมูลขึ้นระบบ Cloud Firestore และส่งออกรายงานเข้า Excel / LINE ได้ทันที",
            category = "ระบบและรายงาน",
            durationSeconds = 48,
            durationText = "0:48 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.CloudSync,
            primaryColorHex = 0xFF1565C0,
            secondaryColorHex = 0xFF42A5F5,
            isFeatured = false,
            relatedRoute = "cloud_sync",
            relatedActionTitle = "ไปที่หน้าสำรองข้อมูลและซิงค์",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "เข้าสู่หน้าสำรองข้อมูล",
                    description = "ไปที่เมนู 'ข้อมูล อสม.' แล้วเลือก 'สำรองข้อมูลและซิงค์คลาวด์'",
                    actionHint = "แตะเมนู 'สำรองข้อมูลและซิงค์คลาวด์'",
                    narrationText = "เมื่อมีสัญญาณอินเทอร์เน็ต ให้เข้าสู่หน้าสำรองข้อมูลและซิงค์คลาวด์ค่ะ",
                    targetHotspotName = "การ์ดเมนูสำรองข้อมูลและซิงค์คลาวด์",
                    tip = "ระบบสามารถซิงค์ได้ทั้งสองทาง (ส่งข้อมูลขึ้น และดึงข้อมูลล่าสุดลงเครื่อง)"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "แตะปุ่ม 'ซิงค์ข้อมูลกับ Cloud'",
                    description = "ระบบจะส่งข้อมูลบ้าน ประชากร และผลการคัดกรองสุขภาพขึ้นสู่ Cloud อย่างปลอดภัย",
                    actionHint = "แตะปุ่ม 'ซิงค์ข้อมูล 2 ทาง (Sync All)'",
                    narrationText = "แตะปุ่มซิงค์ข้อมูล ระบบจะอัปโหลดข้อมูลทั้งหมดขึ้นสู่ระบบอย่างปลอดภัยในไม่กี่วินาที",
                    targetHotspotName = "ปุ่มซิงค์ข้อมูลกับ Cloud Firestore",
                    tip = "ข้อมูลจะถูกผูกกับรหัสประจำตัว อสม. ของท่านอย่างถูกต้อง"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "ส่งออกไฟล์ Excel ไปยัง LINE",
                    description = "แตะปุ่ม 'ส่งออกไฟล์ Excel (.xlsx)' เพื่อแชร์รายงานให้ รพ.สต. หรือส่งเข้ากลุ่ม LINE อสม.",
                    actionHint = "แตะปุ่ม 'ส่งออกไฟล์ Excel'",
                    narrationText = "หรือแตะปุ่มส่งออกไฟล์ Excel เพื่อนำรายงานประชากรไปใช้งานต่อหรือส่งให้ รพ.สต. ได้ทันทีค่ะ",
                    targetHotspotName = "ปุ่มส่งออกไฟล์ Excel (.xlsx)",
                    tip = "ไฟล์ Excel จะจัดรูปแบบตารางสรุปมาตรฐานสวยงามพร้อมพิมพ์"
                )
            )
        ),
        VideoTutorialItem(
            id = "monthly_report",
            title = "7. การทำรายงาน อสม. 1 ประจำเดือน",
            shortDescription = "สรุปผลงาน 9 กิจกรรมหลัก คำนวณอัตโนมัติ และพิมพ์รายงาน A4 ส่งเจ้าหน้าที่",
            category = "ระบบและรายงาน",
            durationSeconds = 52,
            durationText = "0:52 นาที",
            difficulty = "ง่ายมาก",
            icon = Icons.Filled.Assessment,
            primaryColorHex = 0xFFE65100,
            secondaryColorHex = 0xFFFF9800,
            isFeatured = false,
            relatedRoute = "vhv_monthly_report",
            relatedActionTitle = "ไปที่หน้ารายงาน อสม. 1",
            steps = listOf(
                TutorialStep(
                    stepNumber = 1,
                    title = "เปิดหน้ารายงาน อสม. 1 & 2",
                    description = "แตะเมนู 'รายงานการปฏิบัติงาน อสม.' จากหน้าแรกหรือหน้าโปรไฟล์",
                    actionHint = "แตะที่การ์ด 'รายงานการปฏิบัติงาน อสม.'",
                    narrationText = "สำหรับการส่งผลงานประจำเดือน ให้เปิดหน้ารายงานการปฏิบัติงาน อสม. 1 ค่ะ",
                    targetHotspotName = "เมนูรายงาน อสม. 1",
                    tip = "ระบบจะดึงยอดจำนวนบ้านและประชากรที่ท่านดูแลมาคำนวณให้อัตโนมัติ"
                ),
                TutorialStep(
                    stepNumber = 2,
                    title = "เลือกเดือนและบันทึกกิจกรรม",
                    description = "เลือกเดือนที่รายงาน และติ๊กผลงานการเยี่ยมบ้าน กิจกรรมป้องกันโรคไข้เลือดออก และดูแลผู้ป่วย",
                    actionHint = "เลือกเดือนและกรอกจำนวนครั้งที่ปฏิบัติงาน",
                    narrationText = "เลือกเดือนที่ต้องการรายงาน และติ๊กผลงานกิจกรรมสาธารณสุข 9 ด้านตามจริงค่ะ",
                    targetHotspotName = "ตารางกิจกรรม 9 หมวดงาน อสม.",
                    tip = "สามารถใช้ปุ่ม 'ดึงยอดจากสถิติจริง' เพื่อกรอกข้อมูลให้อัตโนมัติ"
                ),
                TutorialStep(
                    stepNumber = 3,
                    title = "พิมพ์เอกสาร A4 หรือส่งสรุป",
                    description = "แตะปุ่ม 'พิมพ์รายงาน A4' หรือส่งสรุปผลงานเข้ากลุ่มไลน์ อสม. ได้ทันที",
                    actionHint = "แตะปุ่ม 'พิมพ์เอกสาร A4' หรือ 'แชร์รายงาน'",
                    narrationText = "เสร็จแล้วแตะปุ่มพิมพ์เอกสาร A4 หรือแชร์ผลงานส่ง รพ.สต. ได้สะดวกรวดเร็วค่ะ",
                    targetHotspotName = "ปุ่มพิมพ์เอกสาร A4 และส่งออก",
                    tip = "เอกสารตรงตามแบบฟอร์มกระทรวงสาธารณสุข 100%"
                )
            )
        )
    )

    val CATEGORIES = listOf("ทั้งหมด", "เริ่มต้นใช้งาน", "สำรวจและบันทึก", "สุขภาพและคัดกรอง", "ระบบและรายงาน")
}
