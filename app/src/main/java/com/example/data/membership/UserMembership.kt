package com.example.data.membership

import com.example.data.Household

/**
 * Role of the user within an assigned village/area.
 */
enum class MembershipRole(val titleThai: String) {
    ADMIN("ผู้ดูแลระบบ (Admin)"),
    VHV_LEADER("ประธาน อสม. หมู่บ้าน"),
    VHV_MEMBER("อสม. ประจำหมู่บ้าน"),
    AUDITOR("เจ้าหน้าที่ตรวจสอบข้อมูล"),
    VIEWER("ผู้สังเกตการณ์")
}

/**
 * Granular permissions governing area-level operations.
 */
enum class AreaPermission(val descriptionThai: String) {
    READ_POPULATION("อ่านและค้นหาข้อมูลประชากร"),
    WRITE_POPULATION("บันทึกและแก้ไขข้อมูลประชากร"),
    DELETE_POPULATION("ลบข้อมูลประชากรและครัวเรือน"),
    HEALTH_SCREENING("บันทึกผลการคัดกรองสุขภาพ NCDs"),
    IMPORT_EXPORT("นำเข้า/ส่งออก Excel หรือ Google Sheets"),
    SYNC_CLOUD("ซิงค์ข้อมูลกับ Cloud Firestore"),
    MANAGE_MEMBERSHIP("จัดการสมาชิกและกำหนดสิทธิ์ในพื้นที่")
}

/**
 * Approval status of a user's membership in a village.
 */
enum class MembershipStatus(val labelThai: String) {
    ACTIVE("อนุมัติแล้ว (Active)"),
    PENDING_APPROVAL("รอการอนุมัติ (Pending)"),
    SUSPENDED("ระงับชั่วคราว (Suspended)"),
    REVOKED("เพิกถอนสิทธิ์ (Revoked)")
}

/**
 * Core Identity & Permission model linking:
 * Google Account -> Firebase Auth user.uid -> UserMembership -> villageId -> Household -> Person
 *
 * Rules:
 * - user.uid identifies the authenticated user
 * - villageId identifies the assigned geographical area
 * - householdUuid identifies households within that area
 * - personUuid identifies citizens within those households
 * - user.uid MUST NEVER substitute householdUuid or personUuid
 */
data class UserMembership(
    val uid: String,                               // Firebase Authentication user.uid
    val villageId: String,                         // Area Identity (e.g. "village_nayok_m08")
    val villageNo: String = "8",                   // Village Number (e.g. "8")
    val villageName: String = "หมู่ 8 บ้านกร่างประดู่วัง", // Village Name
    val subdistrict: String = "ต.ป่าขะ",           // Subdistrict
    val district: String = "อ.บ้านนา",              // District
    val province: String = "จ.นครนายก",            // Province
    val role: MembershipRole = MembershipRole.VHV_MEMBER,
    val permissions: Set<AreaPermission> = setOf(
        AreaPermission.READ_POPULATION,
        AreaPermission.WRITE_POPULATION,
        AreaPermission.HEALTH_SCREENING,
        AreaPermission.SYNC_CLOUD,
        AreaPermission.IMPORT_EXPORT
    ),
    val status: MembershipStatus = MembershipStatus.ACTIVE,
    val assignedHouseholds: List<String> = emptyList(), // Filtered householdUuids (empty means all in village)
    val grantedAt: Long = System.currentTimeMillis(),
    val grantedByUid: String? = null,
    val notes: String? = null
) {
    /**
     * Verifies if this membership grants a specific operational permission.
     */
    fun hasPermission(permission: AreaPermission): Boolean {
        return status == MembershipStatus.ACTIVE && permissions.contains(permission)
    }

    /**
     * Checks if this membership has authority over a specific household.
     */
    fun canAccessHousehold(household: Household): Boolean {
        if (status != MembershipStatus.ACTIVE) return false
        // Must match villageNo/villageId
        val matchesVillage = household.villageNo.isBlank() || household.villageNo == villageNo
        if (!matchesVillage) return false

        // If specific households are assigned, check householdUuid
        return if (assignedHouseholds.isNotEmpty()) {
            assignedHouseholds.contains(household.householdUuid)
        } else {
            true
        }
    }

    /**
     * Security assertion ensuring user.uid is completely distinct from entities.
     */
    fun assertIdentitySeparation(personUuid: String?, householdUuid: String?) {
        if (uid.isNotBlank()) {
            if (!personUuid.isNullOrBlank()) {
                require(personUuid != uid) {
                    "Security Breach: user.uid ($uid) cannot substitute personUuid"
                }
            }
            if (!householdUuid.isNullOrBlank()) {
                require(householdUuid != uid) {
                    "Security Breach: user.uid ($uid) cannot substitute householdUuid"
                }
            }
        }
    }
}
