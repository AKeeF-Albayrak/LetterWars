package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseUserDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun saveUser(user: User): Boolean {
        return try {
            firestore.collection("users").document(user.uid).set(user).await()
            Log.d("Firestore", "Kullanıcı başarıyla kaydedildi.")
            true
        } catch (e: Exception) {
            Log.e("Firestore", "Kullanıcı kaydedilirken hata oluştu: ${e.message}")
            false
        }
    }

    suspend fun getUser(uid: String): User? {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            if (document.exists()) {
                document.toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Kullanıcı getirilirken hata oluştu: ${e.message}")
            null
        }
    }

    suspend fun updateUserInDatabase(user: User) {
        val userRef = firestore.collection("users").document(user.uid)
        userRef.set(user)
    }

}
