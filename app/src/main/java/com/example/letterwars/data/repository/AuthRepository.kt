package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource()
) {

    suspend fun registerUser(email: String, password: String, username: String, age: Int): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                val newUser = User(
                    uid = it.uid,
                    email = it.email ?: "",
                    username = username,
                    age = age
                )
                userDataSource.saveUser(newUser)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun loginUser(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
