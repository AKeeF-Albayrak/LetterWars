package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource()
) {

    suspend fun registerUser(email: String, password: String, username: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                val newUser = User(
                    uid = it.uid,
                    email = it.email ?: "",
                    username = username
                )
                userDataSource.saveUser(newUser)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUserWithUsername(username: String, password: String): Result<Unit> {
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()

            val email = snapshot.documents.firstOrNull()?.getString("email")
                ?: return Result.failure(Exception("Kullanıcı bulunamadı"))

            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false // Hata varsa false döneriz, ama loglanabilir.
        }
    }

}
