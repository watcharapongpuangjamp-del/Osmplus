package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Gender
import com.example.data.Household
import com.example.data.Person
import com.example.data.firestore.FirestorePopulationRepository
import com.example.data.membership.AreaPermission
import com.example.data.membership.MembershipRepository
import com.example.data.membership.MembershipRole
import com.example.data.membership.MembershipStatus
import com.example.data.membership.UserMembership
import com.example.domain.ExcelImportUseCase
import com.example.domain.ImportAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityAndMembershipArchitectureTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    // 1. User Identity (user.uid) MUST NOT substitute personUuid or householdUuid
    @Test
    fun test1_userIdentityCannotSubstitutePersonUuidOrHouseholdUuid() {
        val userUid = "firebase_auth_uid_12345"
        val villageId = "village_nayok_m8"

        val membership = UserMembership(
            uid = userUid,
            villageId = villageId,
            villageNo = "8",
            role = MembershipRole.VHV_MEMBER,
            status = MembershipStatus.ACTIVE
        )

        // Valid distinct UUIDs must succeed
        membership.assertIdentitySeparation(
            personUuid = "p-uuid-abc-111",
            householdUuid = "h-uuid-xyz-222"
        )

        // If personUuid == user.uid, assertion MUST throw
        try {
            membership.assertIdentitySeparation(
                personUuid = userUid,
                householdUuid = "h-uuid-xyz-222"
            )
            fail("Expected IllegalArgumentException when personUuid equals user.uid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot substitute personUuid") == true)
        }

        // If householdUuid == user.uid, assertion MUST throw
        try {
            membership.assertIdentitySeparation(
                personUuid = "p-uuid-abc-111",
                householdUuid = userUid
            )
            fail("Expected IllegalArgumentException when householdUuid equals user.uid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot substitute householdUuid") == true)
        }
    }

    // 2. Firestore mappers enforce Identity Separation
    @Test
    fun test2_firestoreMappersEnforceIdentitySeparation() {
        val userUid = "google_user_999"
        val repo = FirestorePopulationRepository()

        val validHousehold = Household(
            householdUuid = "H-GENUINE-UUID",
            houseNo = "10/1",
            villageNo = "8"
        )
        val hMap = repo.householdToMap(validHousehold, userUid = userUid)
        assertEquals("H-GENUINE-UUID", hMap["householdUuid"])
        assertEquals(userUid, hMap["lastUpdatedBy"])

        // Attempting to save a household where householdUuid == user.uid must be blocked
        val invalidHousehold = Household(
            householdUuid = userUid,
            houseNo = "10/1",
            villageNo = "8"
        )
        try {
            repo.householdToMap(invalidHousehold, userUid = userUid)
            fail("Expected failure when householdUuid is replaced with user.uid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot substitute householdUuid") == true)
        }
    }

    // 3. houseNo is NOT the primary identity of Household
    @Test
    fun test3_houseNoIsNotPrimaryIdentityOfHousehold() = runBlocking {
        // Two distinct households can have the exact same houseNo (e.g. across villages or sub-units)
        // Each has its own distinct, immutable householdUuid
        val h1 = Household(
            householdUuid = "H-UUID-VILLAGE-1",
            houseNo = "99/1",
            villageNo = "1",
            subdistrict = "ป่าขะ"
        )
        val h2 = Household(
            householdUuid = "H-UUID-VILLAGE-2",
            houseNo = "99/1", // Same houseNo!
            villageNo = "2",
            subdistrict = "ป่าขะ"
        )

        val id1 = db.householdDao().insert(h1)
        val id2 = db.householdDao().insert(h2)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)

        // Updating houseNo does NOT alter householdUuid
        val updatedH1 = h1.copy(id = id1, houseNo = "99/1 หมู่บ้านใหม่")
        db.householdDao().update(updatedH1)

        val retrievedH1 = db.householdDao().getHouseholdByUuid("H-UUID-VILLAGE-1")
        assertNotNull(retrievedH1)
        assertEquals("H-UUID-VILLAGE-1", retrievedH1?.householdUuid)
        assertEquals("99/1 หมู่บ้านใหม่", retrievedH1?.houseNo)
    }

    // 4. Membership & Area Permission Hierarchy: user.uid -> Membership -> villageId -> Household -> Person
    @Test
    fun test4_membershipAndPermissionHierarchy() = runBlocking {
        val userUid = "vhv_officer_somchai"
        val villageId = "village_nayok_m8"
        val repo = MembershipRepository(context)

        val membership = UserMembership(
            uid = userUid,
            villageId = villageId,
            villageNo = "8",
            role = MembershipRole.VHV_MEMBER,
            status = MembershipStatus.ACTIVE,
            permissions = setOf(AreaPermission.READ_POPULATION, AreaPermission.WRITE_POPULATION)
        )
        repo.saveMembership(membership)

        // Valid access to assigned village with assigned permission
        val accessResult = repo.validateAccess(userUid, villageId, AreaPermission.READ_POPULATION)
        assertTrue(accessResult.isSuccess)

        // Unauthorized permission check
        val deniedPermission = repo.validateAccess(userUid, villageId, AreaPermission.DELETE_POPULATION)
        assertTrue(deniedPermission.isFailure)

        // Access to wrong village area must be rejected
        val wrongVillageAccess = repo.validateAccess(userUid, "village_other_m99", AreaPermission.READ_POPULATION)
        assertTrue(wrongVillageAccess.isFailure)
    }

    // 5. Excel / Google Sheets Import Plan validation and operator audit tracking
    @Test
    fun test5_excelImportPlanEnforcesReviewAndTracksOperatorUid() = runBlocking {
        val operatorUid = "firebase_auth_operator_555"
        val operatorName = "สมศรี อสม.ดีเด่น"

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("ImportReviewTest")
        val hRow = sheet.createRow(0)
        hRow.createCell(0).setCellValue("houseNo")
        hRow.createCell(1).setCellValue("villageNo")
        hRow.createCell(2).setCellValue("fullName")
        hRow.createCell(3).setCellValue("birthDate")

        val dataRow = sheet.createRow(1)
        dataRow.createCell(0).setCellValue("77/2")
        dataRow.createCell(1).setCellValue("8")
        dataRow.createCell(2).setCellValue("นายบุญมี สุขภาพดี")
        dataRow.createCell(3).setCellValue("10/10/2530")

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()

        val useCase = ExcelImportUseCase(db)

        // Step 1: Must create ImportPlan first (Schema Validation & Planning stage)
        val plan = useCase.createImportPlan(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, plan.totalRows)
        assertEquals(1, plan.insertCount)
        assertEquals(ImportAction.INSERT, plan.plannedItems[0].action)

        // Step 2: Commit plan with operator identity
        val commitResult = useCase.commitImportPlan(plan, operatorUid = operatorUid, operatorName = operatorName)
        assertEquals(1, commitResult.successCount)

        // Verify PersonHistory records operatorUid as user.uid without substituting personUuid
        val historyList = db.personHistoryDao().getAllHistory().first()
        assertTrue(historyList.isNotEmpty())
        val history = historyList.first()
        assertEquals(operatorUid, history.operatorId)
        assertEquals(operatorName, history.operatorName)

        val insertedPerson = db.personDao().getAllPersonsList().first()
        assertNotEquals(operatorUid, insertedPerson.personUuid)
    }

    // 6. Duplicate UUID in file must be marked NEEDS_REVIEW and NEVER regenerated
    @Test
    fun test6_duplicateUuidPreservesExactUuidAndMarksNeedsReview() = runBlocking {
        val fixedDupUuid = "duplicate-uuid-constant-12345"

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("DupUuidSafety")
        val hRow = sheet.createRow(0)
        hRow.createCell(0).setCellValue("personUuid")
        hRow.createCell(1).setCellValue("houseNo")
        hRow.createCell(2).setCellValue("fullName")
        hRow.createCell(3).setCellValue("birthDate")

        val r1 = sheet.createRow(1)
        r1.createCell(0).setCellValue(fixedDupUuid)
        r1.createCell(1).setCellValue("12")
        r1.createCell(2).setCellValue("คนแรก A")
        r1.createCell(3).setCellValue("01/01/2530")

        val r2 = sheet.createRow(2)
        r2.createCell(0).setCellValue(fixedDupUuid) // Duplicate in file
        r2.createCell(1).setCellValue("13")
        r2.createCell(2).setCellValue("คนสอง B")
        r2.createCell(3).setCellValue("01/01/2532")

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()

        val useCase = ExcelImportUseCase(db)
        val plan = useCase.createImportPlan(ByteArrayInputStream(out.toByteArray()))

        assertEquals(2, plan.plannedItems.size)
        val secondItem = plan.plannedItems[1]

        // Must be marked NEEDS_REVIEW
        assertEquals(ImportAction.NEEDS_REVIEW, secondItem.action)
        assertTrue(secondItem.isDuplicateUuid)
        // Must preserve the exact duplicate UUID (DO NOT generate a new random UUID)
        assertEquals(fixedDupUuid, secondItem.personData.personUuid)
    }
}
