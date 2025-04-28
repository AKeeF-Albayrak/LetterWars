package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User

class UserRepository(
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource()
) {
    suspend fun getUser(uid: String): User? {
        return userDataSource.getUser(uid)
    }
}
