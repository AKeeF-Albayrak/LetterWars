package com.example.letterwars.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.letterwars.App
import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.content.edit

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val context: Context = App.instance
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    }

    suspend fun registerUser(email: String, password: String, username: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            auth.currentUser?.reload()

            auth.currentUser?.let {
                val newUser = User(
                    uid = it.uid,
                    email = it.email ?: "",
                    username = username,
                    wonGames = 0,
                    totalGames = 0
                )
                userDataSource.saveUser(newUser)
            }

            println("User oluşturuldu: ${result.user?.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUserWithUsername(username: String, password: String): Result<Unit> {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()

            val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
                ?: return Result.failure(Exception("Kullanıcı bulunamadı"))

            auth.signInWithEmailAndPassword(user.email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }
    suspend fun saveRememberedUser(username: String) {
        prefs.edit {
            putString("remembered_username", username)
            apply()
        }
    }

    fun getRememberedUsername(): String? {
        return prefs.getString("remembered_username", null)
    }

    fun clearRememberedUser() {
        prefs.edit {
            remove("remembered_username")
            apply()
        }
    }

}
