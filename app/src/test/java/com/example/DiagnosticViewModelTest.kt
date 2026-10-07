package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.DiagnosticViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testInitialDiagnosticStateIsEmpty() {
        val viewModel = DiagnosticViewModel(repository, null)
        val state = viewModel.state.value

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.orphanedInRoomHouseholds.isEmpty())
        assertTrue(state.orphanedInRoomPersons.isEmpty())
        assertTrue(state.orphanedInFirestoreHouseholds.isEmpty())
        assertTrue(state.orphanedInFirestorePersons.isEmpty())
    }

    @Test
    fun testDiagnosticFailsGracefullyWhenFirebaseNull() {
        val viewModel = DiagnosticViewModel(repository, null)
        viewModel.runDiagnostic(context)

        val state = viewModel.state.value
        assertEquals("Firebase is not configured.", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun testSharedPreferencesActiveVillageNoFallback() {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("surveyor_village_no", "10").apply()

        val activeVillageNo = prefs.getString("surveyor_village_no", "8")
        assertEquals("10", activeVillageNo)
    }
}
