package com.example.letterwars.data.firebase

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.example.letterwars.data.model.User

class FirebaseUserDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun saveUser(user: User) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(user)
    }


    fun getUser(uid: String, onComplete: (User?) -> Unit) {
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                onComplete(user)
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }
}
