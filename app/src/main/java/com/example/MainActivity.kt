package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.PersonRepository
import com.example.data.firestore.FirestoreManager
import com.example.data.sync.HouseholdSyncScheduler
import com.example.data.sync.OsmSyncScheduler
import com.example.data.sync.RoomFirestoreSyncHelper
import com.example.domain.ExcelImportUseCase
import com.example.ui.DiagnosticViewModel
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AppThemeProvider
import com.example.viewmodel.PersonViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        // Initialize Firestore with offline cache and retry settings
        FirestoreManager.initialize(applicationContext)

        // Schedule periodic background sync with Firestore
        HouseholdSyncScheduler.schedulePeriodicSync(applicationContext)
        OsmSyncScheduler.schedulePeriodicSync(applicationContext)

        val db = AppDatabase.getInstance(applicationContext)
        val repository = PersonRepository(
            db,
            db.personDao(),
            db.householdDao(),
            db.personHistoryDao(),
            db.populationEventDao()
        )
        val excelImportUseCase = ExcelImportUseCase(db)
        val syncHelper = RoomFirestoreSyncHelper(
            applicationContext,
            repository,
            firestoreProvider = { FirestoreManager.getInstance() }
        )

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PersonViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PersonViewModel(repository, excelImportUseCase, syncHelper) as T
                }
                if (modelClass.isAssignableFrom(DiagnosticViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return DiagnosticViewModel(
                        repository,
                        FirestoreManager.getInstance()
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        setContent {
            AppThemeProvider {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: PersonViewModel = viewModel(factory = factory)
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        viewModel = viewModel,
                        repository = repository,
                        firestore = FirestoreManager.getInstance(),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
