package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Household
import com.example.data.Person
import com.example.data.PersonRepository
import com.example.data.sync.RoomFirestoreSyncHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DiagnosticResult(
    val orphanedInRoomHouseholds: List<String> = emptyList(),
    val orphanedInRoomPersons: List<String> = emptyList(),
    val orphanedInFirestoreHouseholds: List<String> = emptyList(),
    val orphanedInFirestorePersons: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DiagnosticViewModel(
    private val repository: PersonRepository,
    private val firestore: FirebaseFirestore?
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticResult())
    val state: StateFlow<DiagnosticResult> = _state

    fun runDiagnostic(context: Context) {
        if (firestore == null) {
            _state.value = _state.value.copy(error = "ระบบ Cloud (Firebase) ยังไม่ได้เชื่อมต่อในระบบนี้ (ใช้งานฐานข้อมูลภายใน Room ได้ปกติ)")
            return
        }

        viewModelScope.launch {
            _state.value = DiagnosticResult(isLoading = true)

            try {
                // Ensure Firebase Auth session before querying
                try {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    if (auth.currentUser == null) {
                        auth.signInAnonymously().await()
                    }
                } catch (authEx: Exception) {
                    Log.d("DiagnosticVM", "Anonymous auth note: ${authEx.message}")
                }

                // Get the surveyor's active villageNo for regional query partitioning
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val activeVillageNo = prefs.getString("surveyor_village_no", "8") ?: "8"

                // Fetch local matching activeVillageNo
                val localHouseholds = repository.getAllHouseholds().filter { it.villageNo == activeVillageNo }
                val localHouseholdIds = localHouseholds.map { it.id }.toSet()
                val localPersons = repository.getAllPersonsList().filter { it.householdId in localHouseholdIds }
                
                val localHouseholdUuids = localHouseholds.map { it.householdUuid }.toSet()
                val localPersonUuids = localPersons.map { it.personUuid }.toSet()

                // Fetch cloud matching activeVillageNo (complying with security rules partitioning)
                val cloudHouseholdsSnapshot = firestore.collection(RoomFirestoreSyncHelper.COLLECTION_HOUSEHOLDS)
                    .whereEqualTo("villageNo", activeVillageNo)
                    .get().await()
                
                val cloudPersonsSnapshot = firestore.collection(RoomFirestoreSyncHelper.COLLECTION_PERSONS)
                    .whereEqualTo("villageNo", activeVillageNo)
                    .get().await()

                val cloudHouseholdUuids = cloudHouseholdsSnapshot.documents.map { it.id }.toSet()
                val cloudPersonUuids = cloudPersonsSnapshot.documents.map { it.id }.toSet()

                // Identify orphans within the authorized partition
                val orphanedInRoomHouseholds = localHouseholdUuids.filter { it !in cloudHouseholdUuids }
                val orphanedInRoomPersons = localPersonUuids.filter { it !in cloudPersonUuids }
                val orphanedInFirestoreHouseholds = cloudHouseholdUuids.filter { it !in localHouseholdUuids }
                val orphanedInFirestorePersons = cloudPersonUuids.filter { it !in localPersonUuids }

                _state.value = DiagnosticResult(
                    orphanedInRoomHouseholds = orphanedInRoomHouseholds,
                    orphanedInRoomPersons = orphanedInRoomPersons,
                    orphanedInFirestoreHouseholds = orphanedInFirestoreHouseholds,
                    orphanedInFirestorePersons = orphanedInFirestorePersons,
                    isLoading = false
                )
            } catch (e: Exception) {
                val errorMsg = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                    "สิทธิ์การเข้าถึง Cloud Firestore ถูกปฏิเสธ (PERMISSION_DENIED): โปรดตรวจสอบ Firebase Security Rules หรือเข้าสู่ระบบ Google (ข้อมูลในเครื่อง Room Database ปลอดภัยและใช้งานได้ปกติ)"
                } else {
                    e.message ?: "เกิดข้อผิดพลาดในการตรวจสอบข้อมูล"
                }
                _state.value = DiagnosticResult(error = errorMsg, isLoading = false)
            }
        }
    }
}
