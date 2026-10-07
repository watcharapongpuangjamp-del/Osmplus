package com.example.data

import android.content.Context

class AppContainer(context: Context) {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val personDao by lazy { database.personDao() }
    val householdDao by lazy { database.householdDao() }
    val personHistoryDao by lazy { database.personHistoryDao() }
    val populationEventDao by lazy { database.populationEventDao() }
    val healthScreeningDao by lazy { database.healthScreeningDao() }
    val vhvMemberDao by lazy { database.vhvMemberDao() }
}
