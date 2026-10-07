package com.example.data

enum class Gender(val value: String) {
    MALE("ชาย"),
    FEMALE("หญิง"),
    UNKNOWN("ไม่ระบุ");

    companion object {
        fun fromString(value: String): Gender {
            return entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class HouseholdRole(val value: String) {
    HEAD("เจ้าบ้าน"),
    RESIDENT("ผู้อาศัย"),
    UNKNOWN("ไม่ระบุ");

    companion object {
        fun fromString(value: String): HouseholdRole {
            return entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class PersonStatus(val value: String) {
    ALIVE("มีชีวิต"),
    DEAD("เสียชีวิต"),
    MOVED("ย้ายออก"),
    UNKNOWN("ไม่ระบุ");

    companion object {
        fun fromString(value: String): PersonStatus {
            return entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class DataStatus(val value: String) {
    VERIFIED("ยืนยันแล้ว"),
    NEEDS_REVIEW("ต้องตรวจสอบ"),
    UNKNOWN("ไม่ระบุ");

    companion object {
        fun fromString(value: String): DataStatus {
            return entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class VhvAgeGroup(val value: String) {
    EARLY_CHILD("เด็กปฐมวัย (0-5 ปี)"),
    SCHOOL_AGE("เด็กวัยเรียน (6-12 ปี)"),
    TEENAGER("วัยรุ่น (13-20 ปี)"),
    WORKING_AGE("วัยทำงาน (21-59 ปี)"),
    ELDERLY("ผู้สูงอายุ (60 ปีขึ้นไป)"),
    UNKNOWN("ไม่ระบุ");

    companion object {
        fun fromAge(age: Int?): VhvAgeGroup {
            if (age == null) return UNKNOWN
            return when {
                age <= 5 -> EARLY_CHILD
                age <= 12 -> SCHOOL_AGE
                age <= 20 -> TEENAGER
                age <= 59 -> WORKING_AGE
                else -> ELDERLY
            }
        }
        
        fun fromString(value: String): VhvAgeGroup {
            return entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
