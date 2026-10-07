package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.PersonRepository
import com.example.data.firestore.FirestoreManager
import kotlinx.coroutines.CancellationException

class HouseholdSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HouseholdSyncWorker"
        const val WORK_NAME = "HouseholdPeriodicSyncWork"
    }

    override suspend fun doWork(): Result {
        return try {
            if (isStopped) {
                return Result.success()
            }
            Log.d(TAG, "Starting periodic background sync of household registration data...")
            val appContext = applicationContext
            
            FirestoreManager.initialize(appContext)
            if (FirestoreManager.getInstance() == null) {
                Log.w(TAG, "Firestore is not configured. Skipping background sync.")
                return Result.success()
            }

            val db = AppDatabase.getInstance(appContext)

            val repository = PersonRepository(db, db.personDao(), db.householdDao(), db.personHistoryDao(), db.populationEventDao())
            val syncHelper = RoomFirestoreSyncHelper(
                appContext,
                repository,
                firestoreProvider = { FirestoreManager.getInstance() }
            )

            val syncResult = syncHelper.syncRoomToFirestore()
            if (syncResult.isSuccess) {
                val result = syncResult.getOrNull()
                Log.i(TAG, "Background sync completed successfully: ${result?.householdsSynced} households, ${result?.personsSynced} persons synced.")
                Result.success()
            } else {
                Log.w(TAG, "Background sync non-critical failure: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (ce: CancellationException) {
            Log.d(TAG, "HouseholdSyncWorker cancelled normally.")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Exception during background sync worker execution", e)
            Result.retry()
        }
    }
}
