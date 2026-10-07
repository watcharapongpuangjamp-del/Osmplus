package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.firestore.FirestoreManager
import com.example.data.vhv.OsmRp00002Data
import kotlinx.coroutines.CancellationException

/**
 * Background WorkManager worker responsible for syncing Public Health Volunteer (OSM)
 * data from the OSMRP00002 dataset into Firestore database for offline persistence and access.
 */
class OsmSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "OsmSyncWorker"
        const val WORK_NAME = "OsmRp00002PeriodicSyncWork"
        const val ONE_TIME_WORK_NAME = "OsmRp00002OneTimeSyncWork"
    }

    override suspend fun doWork(): Result {
        return try {
            if (isStopped) {
                return Result.success()
            }
            Log.d(TAG, "Starting background sync worker for OSM (Public Health Volunteer) data...")
            val appContext = applicationContext

            // Initialize Cloud Firestore with offline persistence
            FirestoreManager.initialize(appContext)
            val firestore = FirestoreManager.getInstance()
            if (firestore == null) {
                Log.w(TAG, "Firestore instance unavailable. Skipping background OSM sync.")
                return Result.success()
            }

            // Use AppDatabase singleton instance with full migration chain
            val db = AppDatabase.getInstance(appContext)
            val vhvMemberDao = db.vhvMemberDao()

            // Seed local database if empty
            if (vhvMemberDao.getVhvCount() == 0) {
                Log.i(TAG, "Seeding OSMRP00002 VHV dataset into Room...")
                vhvMemberDao.insertAll(OsmRp00002Data.PA_KHA_VHV_MEMBERS)
            }

            val syncHelper = VhvFirestoreSyncHelper(
                context = appContext,
                vhvMemberDao = vhvMemberDao,
                firestoreProvider = { firestore }
            )

            val syncResult = syncHelper.syncOsmData()
            if (syncResult.isSuccess) {
                val res = syncResult.getOrNull()
                Log.i(TAG, "OSM background sync finished successfully: ${res?.personsSynced} records processed.")
                Result.success()
            } else {
                Log.w(TAG, "OSM background sync non-critical failure: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (ce: CancellationException) {
            Log.d(TAG, "OsmSyncWorker cancelled normally.")
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Exception in OsmSyncWorker execution", e)
            Result.retry()
        }
    }
}
