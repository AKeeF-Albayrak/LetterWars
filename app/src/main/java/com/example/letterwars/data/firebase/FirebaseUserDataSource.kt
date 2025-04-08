package com.example.letterwars.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.example.letterwars.data.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

class FirebaseUserDataSource(
    private val database: DatabaseReference = FirebaseDatabase
        .getInstance("https://letterwars-f8384-default-rtdb.europe-west1.firebasedatabase.app/")
        .reference

) {

    fun saveUser(user: User) {
        database.child("users").child(user.uid).setValue(user)
            .addOnSuccessListener {
                println("FirebaseSave Kullanıcı başarıyla kaydedildi.")
            }
            .addOnFailureListener { e ->
                println("FirebaseSave Hata oluştu: ${e.message}")
            }
    }





    fun getUser(uid: String, onComplete: (User?) -> Unit) {
        database.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    onComplete(user)
                }

                override fun onCancelled(error: DatabaseError) {
                    onComplete(null)
                }
            })
    }
}
