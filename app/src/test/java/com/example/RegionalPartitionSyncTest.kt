package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.sync.RoomFirestoreSyncHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegionalPartitionSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PersonRepository(
            db,
            db.personDao(),
            db.householdDao(),
            db.personHistoryDao(),
            db.populationEventDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testRoomDataFilteringByVillageNo() = runBlocking {
        // 1. Seed Household & Person for Village 8
        val h8Id = repository.insertHousehold(
            Household(
                householdUuid = "H-VILL8-001",
                houseNo = "88/1",
                villageNo = "8",
                subdistrict = "ป่าขะ",
                district = "บ้านนา",
                province = "นครนายก",
                dataStatus = DataStatus.VERIFIED
            )
        )
        repository.insert(
            Person(
                personUuid = "P-VILL8-001",
                householdId = h8Id,
                nationalId = "1111111111111",
                fullName = "นายแปด ใจดี",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1980, 1, 1),
                houseStatus = HouseholdRole.HEAD,
                personStatus = PersonStatus.ALIVE,
                dataStatus = DataStatus.VERIFIED
            )
        )

        // 2. Seed Household & Person for Village 9
        val h9Id = repository.insertHousehold(
            Household(
                householdUuid = "H-VILL9-002",
                houseNo = "99/1",
                villageNo = "9",
                subdistrict = "ป่าขะ",
                district = "บ้านนา",
                province = "นครนายก",
                dataStatus = DataStatus.VERIFIED
            )
        )
        repository.insert(
            Person(
                personUuid = "P-VILL9-002",
                householdId = h9Id,
                nationalId = "2222222222222",
                fullName = "นายเก้า ดีใจ",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1990, 2, 2),
                houseStatus = HouseholdRole.HEAD,
                personStatus = PersonStatus.ALIVE,
                dataStatus = DataStatus.VERIFIED
            )
        )

        // 3. Verify total households in Room
        val totalHouseholds = repository.getAllHouseholds()
        assertEquals(2, totalHouseholds.size)

        // 4. Test filtering of households and persons by activeVillageNo
        val village8Households = totalHouseholds.filter { it.villageNo == "8" }
        assertEquals(1, village8Households.size)
        assertEquals("H-VILL8-001", village8Households[0].householdUuid)

        val village8HouseholdMap = village8Households.associateBy { it.id }
        val village8Persons = repository.getAllPersonsList().filter { village8HouseholdMap.containsKey(it.householdId) }
        assertEquals(1, village8Persons.size)
        assertEquals("P-VILL8-001", village8Persons[0].personUuid)

        val village9Households = totalHouseholds.filter { it.villageNo == "9" }
        assertEquals(1, village9Households.size)
        assertEquals("H-VILL9-002", village9Households[0].householdUuid)

        val village9HouseholdMap = village9Households.associateBy { it.id }
        val village9Persons = repository.getAllPersonsList().filter { village9HouseholdMap.containsKey(it.householdId) }
        assertEquals(1, village9Persons.size)
        assertEquals("P-VILL9-002", village9Persons[0].personUuid)
    }

    @Test
    fun testSharedPreferencesVillageFallbacks() {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("surveyor_village_no", "12").apply()

        val activeVillageNo = prefs.getString("surveyor_village_no", null)
        assertEquals("12", activeVillageNo)
    }
}
