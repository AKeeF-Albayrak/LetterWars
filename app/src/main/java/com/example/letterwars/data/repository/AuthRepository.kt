package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource()
) {

    private val dbRef = FirebaseDatabase
        .getInstance("https://letterwars-f8384-default-rtdb.europe-west1.firebasedatabase.app/")
        .reference


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
            val snapshot = dbRef.child("users")
                .orderByChild("username")
                .equalTo(username)
                .get()
                .await()

            val user = snapshot.children.firstOrNull()?.getValue(User::class.java)
                ?: return Result.failure(Exception("Kullanıcı bulunamadı"))

            auth.signInWithEmailAndPassword(user.email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val snapshot = dbRef.child("users")
                .orderByChild("username")
                .equalTo(username)
                .get()
                .await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }
}
