package com.nizkarya.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val uid: String,
        val email: String,
        val displayName: String
    ) : AuthState
}

object AuthRepo {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val state: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(
                if (user == null) AuthState.SignedOut
                else AuthState.SignedIn(
                    uid = user.uid,
                    email = user.email ?: "",
                    displayName = user.displayName ?: ""
                )
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(firstName: String, lastName: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return
        val displayName = listOf(firstName, lastName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (displayName.isNotEmpty()) {
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            ).await()
        }
        // Mirror the web app's users/{uid} profile document.
        FirebaseFirestore.getInstance()
            .collection("users").document(user.uid)
            .set(
                mapOf(
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "email" to email.trim(),
                    "author_uid" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
    }

    /** Exchange a Google ID token for a Firebase session and sync the profile doc. */
    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: return
        val nameParts = (user.displayName ?: "")
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        FirebaseFirestore.getInstance()
            .collection("users").document(user.uid)
            .set(
                mapOf(
                    "firstName" to (nameParts.firstOrNull() ?: ""),
                    "lastName" to nameParts.drop(1).joinToString(" "),
                    "email" to (user.email ?: ""),
                    "author_uid" to user.uid
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        auth.signOut()
    }
}
