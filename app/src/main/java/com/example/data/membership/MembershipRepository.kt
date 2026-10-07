package com.example.data.membership

import android.content.Context
import android.util.Log
import com.example.data.firestore.FirestoreManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository interface and implementation managing User Memberships and Area Permissions.
 *
 * Implements the architecture pipeline:
 * Google Account -> Firebase Auth (user.uid) -> Membership / Permission -> villageId -> Household -> Person
 */
open class MembershipRepository(
    private val context: Context? = null,
    private val firestoreProvider: () -> FirebaseFirestore? = { FirestoreManager.getInstance() }
) {
    companion object {
        private const val TAG = "MembershipRepository"
        const val COLLECTION_MEMBERSHIPS = "memberships"
        private const val PREFS_NAME = "membership_local_cache"
        private const val KEY_MEMBERSHIP_PREFIX = "membership_"
        private const val KEY_ACTIVE_VILLAGE_PREFIX = "active_village_"
    }

    private val memoryCache = mutableMapOf<String, UserMembership>()
    private val _activeMembershipFlow = MutableStateFlow<UserMembership?>(null)
    val activeMembershipFlow: StateFlow<UserMembership?> = _activeMembershipFlow.asStateFlow()

    /**
     * Resolves the active membership for a given user.
     * Checks memory cache, then local persistent storage, and generates a default baseline if absent.
     */
    open suspend fun getActiveMembership(uid: String): UserMembership? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null

        // 1. Check in-memory cache
        val cached = memoryCache.values.firstOrNull { it.uid == uid && it.status == MembershipStatus.ACTIVE }
        if (cached != null) {
            _activeMembershipFlow.value = cached
            return@withContext cached
        }

        // 2. Check local SharedPreferences (Local-First fallback)
        val loadedFromPrefs = loadFromLocalPrefs(uid)
        if (loadedFromPrefs != null) {
            memoryCache["${uid}_${loadedFromPrefs.villageId}"] = loadedFromPrefs
            _activeMembershipFlow.value = loadedFromPrefs
            return@withContext loadedFromPrefs
        }

        // 3. Fallback to default active membership for village 8
        val defaultMembership = createDefaultMembership(uid)
        saveMembership(defaultMembership)
        _activeMembershipFlow.value = defaultMembership
        defaultMembership
    }

    /**
     * Retrieves all memberships for a user across different areas.
     */
    open suspend fun getUserMemberships(uid: String): List<UserMembership> = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext emptyList()
        val active = getActiveMembership(uid)
        if (active != null) listOf(active) else emptyList()
    }

    /**
     * Saves a user membership locally and attempts to push to Firestore.
     */
    open suspend fun saveMembership(membership: UserMembership): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Identity security check
            require(membership.uid.isNotBlank()) { "Membership must have a non-blank user.uid" }
            require(membership.villageId.isNotBlank()) { "Membership must have a non-blank villageId" }

            // Cache in memory
            val key = "${membership.uid}_${membership.villageId}"
            memoryCache[key] = membership
            _activeMembershipFlow.value = membership

            // Persist locally in SharedPreferences (local-first)
            saveToLocalPrefs(membership)

            // Sync to Firestore if available
            val firestore = firestoreProvider()
            if (firestore != null) {
                try {
                    val map = membershipToMap(membership)
                    firestore.collection(COLLECTION_MEMBERSHIPS)
                        .document(key)
                        .set(map, SetOptions.merge())
                        .await()
                    Log.d(TAG, "Membership for ${membership.uid} synced to Firestore")
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud sync for membership deferred: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save membership", e)
            Result.failure(e)
        }
    }

    /**
     * Validates whether a user has a specific permission for an area.
     * Throws SecurityException or returns Result.failure if unauthorized.
     */
    open suspend fun validateAccess(
        uid: String?,
        villageId: String?,
        requiredPermission: AreaPermission
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isNullOrBlank()) {
            return@withContext Result.failure(SecurityException("Unauthorized: No authenticated user.uid"))
        }

        val membership = getActiveMembership(uid)
            ?: return@withContext Result.failure(SecurityException("Forbidden: No active membership found for user.uid: $uid"))

        if (villageId != null && membership.villageId != villageId && membership.villageNo != villageId) {
            return@withContext Result.failure(
                SecurityException("Area Access Denied: User $uid is assigned to ${membership.villageId} but attempted to access $villageId")
            )
        }

        if (!membership.hasPermission(requiredPermission)) {
            return@withContext Result.failure(
                SecurityException("Permission Denied: User $uid lacks ${requiredPermission.name} in ${membership.villageId}")
            )
        }

        Result.success(Unit)
    }

    /**
     * Pulls latest memberships for a user from Firestore.
     */
    open suspend fun syncMembershipsFromCloud(uid: String): Result<List<UserMembership>> = withContext(Dispatchers.IO) {
        try {
            if (uid.isBlank()) return@withContext Result.success(emptyList())
            val firestore = firestoreProvider()
                ?: return@withContext Result.success(getUserMemberships(uid))

            val snapshot = firestore.collection(COLLECTION_MEMBERSHIPS)
                .whereEqualTo("uid", uid)
                .get()
                .await()

            val remoteMemberships = snapshot.documents.mapNotNull { docToMembership(it.data) }
            for (m in remoteMemberships) {
                memoryCache["${m.uid}_${m.villageId}"] = m
                saveToLocalPrefs(m)
            }

            val active = remoteMemberships.firstOrNull { it.status == MembershipStatus.ACTIVE }
            if (active != null) {
                _activeMembershipFlow.value = active
            }

            Result.success(remoteMemberships)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync memberships from cloud: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Creates standard default membership for village 8 Nakhon Nayok.
     */
    fun createDefaultMembership(uid: String, villageNo: String = "8"): UserMembership {
        return UserMembership(
            uid = uid,
            villageId = "village_nayok_m$villageNo",
            villageNo = villageNo,
            villageName = "หมู่ $villageNo บ้านกร่างประดู่วัง",
            subdistrict = "ต.ป่าขะ",
            district = "อ.บ้านนา",
            province = "จ.นครนายก",
            role = MembershipRole.VHV_MEMBER,
            status = MembershipStatus.ACTIVE,
            permissions = setOf(
                AreaPermission.READ_POPULATION,
                AreaPermission.WRITE_POPULATION,
                AreaPermission.HEALTH_SCREENING,
                AreaPermission.SYNC_CLOUD,
                AreaPermission.IMPORT_EXPORT
            )
        )
    }

    private fun saveToLocalPrefs(membership: UserMembership) {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put("uid", membership.uid)
                put("villageId", membership.villageId)
                put("villageNo", membership.villageNo)
                put("villageName", membership.villageName)
                put("subdistrict", membership.subdistrict)
                put("district", membership.district)
                put("province", membership.province)
                put("role", membership.role.name)
                put("status", membership.status.name)
                put("permissions", JSONArray(membership.permissions.map { it.name }))
                put("assignedHouseholds", JSONArray(membership.assignedHouseholds))
                put("grantedAt", membership.grantedAt)
                put("grantedByUid", membership.grantedByUid)
                put("notes", membership.notes)
            }
            prefs.edit()
                .putString("$KEY_MEMBERSHIP_PREFIX${membership.uid}", json.toString())
                .putString("$KEY_ACTIVE_VILLAGE_PREFIX${membership.uid}", membership.villageId)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving membership to prefs", e)
        }
    }

    private fun loadFromLocalPrefs(uid: String): UserMembership? {
        val ctx = context ?: return null
        return try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("$KEY_MEMBERSHIP_PREFIX$uid", null) ?: return null
            val obj = JSONObject(jsonStr)

            val perms = mutableSetOf<AreaPermission>()
            val permArray = obj.optJSONArray("permissions")
            if (permArray != null) {
                for (i in 0 until permArray.length()) {
                    try {
                        perms.add(AreaPermission.valueOf(permArray.getString(i)))
                    } catch (_: Exception) {}
                }
            }

            val assignedList = mutableListOf<String>()
            val assignedArray = obj.optJSONArray("assignedHouseholds")
            if (assignedArray != null) {
                for (i in 0 until assignedArray.length()) {
                    assignedList.add(assignedArray.getString(i))
                }
            }

            UserMembership(
                uid = obj.getString("uid"),
                villageId = obj.optString("villageId", "village_nayok_m8"),
                villageNo = obj.optString("villageNo", "8"),
                villageName = obj.optString("villageName", "หมู่ 8 บ้านกร่างประดู่วัง"),
                subdistrict = obj.optString("subdistrict", "ต.ป่าขะ"),
                district = obj.optString("district", "อ.บ้านนา"),
                province = obj.optString("province", "จ.นครนายก"),
                role = try { MembershipRole.valueOf(obj.optString("role", MembershipRole.VHV_MEMBER.name)) } catch (_: Exception) { MembershipRole.VHV_MEMBER },
                status = try { MembershipStatus.valueOf(obj.optString("status", MembershipStatus.ACTIVE.name)) } catch (_: Exception) { MembershipStatus.ACTIVE },
                permissions = if (perms.isNotEmpty()) perms else setOf(AreaPermission.READ_POPULATION, AreaPermission.WRITE_POPULATION, AreaPermission.SYNC_CLOUD),
                assignedHouseholds = assignedList,
                grantedAt = obj.optLong("grantedAt", System.currentTimeMillis()),
                grantedByUid = obj.optString("grantedByUid").takeIf { it.isNotBlank() },
                notes = obj.optString("notes").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error reading membership from prefs", e)
            null
        }
    }

    private fun membershipToMap(m: UserMembership): Map<String, Any?> {
        return mapOf(
            "uid" to m.uid,
            "villageId" to m.villageId,
            "villageNo" to m.villageNo,
            "villageName" to m.villageName,
            "subdistrict" to m.subdistrict,
            "district" to m.district,
            "province" to m.province,
            "role" to m.role.name,
            "status" to m.status.name,
            "permissions" to m.permissions.map { it.name },
            "assignedHouseholds" to m.assignedHouseholds,
            "grantedAt" to m.grantedAt,
            "grantedByUid" to m.grantedByUid,
            "notes" to m.notes
        )
    }

    private fun docToMembership(data: Map<String, Any?>?): UserMembership? {
        if (data == null) return null
        return try {
            val uid = data["uid"] as? String ?: return null
            val villageId = data["villageId"] as? String ?: "village_nayok_m8"
            val permsList = (data["permissions"] as? List<*>)?.mapNotNull {
                try { AreaPermission.valueOf(it.toString()) } catch (_: Exception) { null }
            }?.toSet() ?: emptySet()

            val assignedList = (data["assignedHouseholds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

            UserMembership(
                uid = uid,
                villageId = villageId,
                villageNo = data["villageNo"] as? String ?: "8",
                villageName = data["villageName"] as? String ?: "หมู่ 8 บ้านกร่างประดู่วัง",
                subdistrict = data["subdistrict"] as? String ?: "ต.ป่าขะ",
                district = data["district"] as? String ?: "อ.บ้านนา",
                province = data["province"] as? String ?: "จ.นครนายก",
                role = try { MembershipRole.valueOf(data["role"]?.toString() ?: "") } catch (_: Exception) { MembershipRole.VHV_MEMBER },
                status = try { MembershipStatus.valueOf(data["status"]?.toString() ?: "") } catch (_: Exception) { MembershipStatus.ACTIVE },
                permissions = if (permsList.isNotEmpty()) permsList else setOf(AreaPermission.READ_POPULATION, AreaPermission.WRITE_POPULATION, AreaPermission.SYNC_CLOUD),
                assignedHouseholds = assignedList,
                grantedAt = (data["grantedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                grantedByUid = data["grantedByUid"] as? String,
                notes = data["notes"] as? String
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping doc to membership", e)
            null
        }
    }
}
