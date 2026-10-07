package com.example

import android.app.Application
import android.util.Log
import com.example.data.AppContainer
import com.example.data.firestore.FirestoreManager
import com.google.firebase.FirebaseApp

class SmartOsmApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            FirestoreManager.initialize(this)
            
            // Ensure Firebase Auth session is active for Firestore security rules
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    auth.signInAnonymously().addOnSuccessListener {
                        Log.i("SmartOsmApp", "Firebase anonymous auth initialized: ${it.user?.uid}")
                    }.addOnFailureListener {
                        Log.d("SmartOsmApp", "Firebase anonymous auth note: ${it.message}")
                    }
                }
            } catch (authEx: Exception) {
                Log.d("SmartOsmApp", "FirebaseAuth init note: ${authEx.message}")
            }
            
            Log.i("SmartOsmApp", "Firebase and Firestore successfully initialized")
        } catch (e: Exception) {
            Log.w("SmartOsmApp", "Firebase initialization deferred: ${e.message}")
        }
    }
}
