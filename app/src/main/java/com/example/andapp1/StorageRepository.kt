package com.example.andapp1

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor() {
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadImage(roomId: String, imageUri: Uri): String {
        val storageRef = storage.reference.child("chat_images/$roomId/${System.currentTimeMillis()}")
        val uploadTask = storageRef.putFile(imageUri).await()
        return storageRef.downloadUrl.await().toString()
    }

    fun uploadImageFlow(roomId: String, imageUri: Uri): Flow<Result<String>> = callbackFlow {
        val storageRef = storage.reference.child("chat_images/$roomId/${System.currentTimeMillis()}")
        
        val uploadTask = storageRef.putFile(imageUri)
        
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                trySend(Result.success(uri.toString()))
            }.addOnFailureListener { exception ->
                trySend(Result.failure(exception))
            }
        }.addOnFailureListener { exception ->
            trySend(Result.failure(exception))
        }
        
        awaitClose()
    }
} 