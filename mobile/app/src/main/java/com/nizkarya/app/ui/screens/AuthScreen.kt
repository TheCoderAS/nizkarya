package com.nizkarya.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.nizkarya.app.R
import com.nizkarya.app.data.AuthRepo
import com.nizkarya.app.ui.components.GhostButton
import com.nizkarya.app.ui.components.IconAction
import com.nizkarya.app.ui.components.LabelledField
import com.nizkarya.app.ui.components.PrimaryCta
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.theme.heroGradient
import com.nizkarya.app.ui.theme.onHero
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            notice = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                loading = false
            }
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                error = "Google didn't return a sign-in token. Try again."
            } else {
                run { AuthRepo.signInWithGoogle(idToken) }
            }
        } catch (e: ApiException) {
            error = when (e.statusCode) {
                10 -> "Google sign-in is not set up yet. This app's SHA-1 " +
                    "fingerprint still needs adding in Firebase."
                12501 -> null // user cancelled, not an error worth showing
                else -> "Google sign-in failed (code ${e.statusCode}). Try again."
            }
        }
    }

    fun launchGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, options)
        client.signOut() // always show the account chooser
        googleLauncher.launch(client.signInIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(heroGradient())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column {
                Text(
                    text = "NizKarya",
                    style = MaterialTheme.typography.headlineLarge,
                    color = onHero()
                )
                Text(
                    text = "Own your day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero().copy(alpha = 0.85f)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(16.dp))

        if (isSignUp) {
            LabelledField(
                label = "First name",
                value = firstName,
                onValueChange = { firstName = it },
                placeholder = "Aalok"
            )
            Spacer(Modifier.height(12.dp))
            LabelledField(
                label = "Last name",
                value = lastName,
                onValueChange = { lastName = it },
                placeholder = "Sah"
            )
            Spacer(Modifier.height(12.dp))
        }
        LabelledField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            placeholder = "you@example.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        LabelledField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "At least 6 characters",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                // Typing a password blind on a phone keyboard is how people end
                // up locked out of their own account.
                IconAction(
                    icon = if (showPassword) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = if (showPassword) "Hide password"
                    else "Show password",
                    onClick = { showPassword = !showPassword },
                    diameter = 28.dp
                )
            }
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (notice != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = notice ?: "",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryCta(
            text = when {
                loading -> "One moment"
                isSignUp -> "Create account"
                else -> "Sign in"
            },
            enabled = !loading,
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    error = "Enter your email and password."
                } else if (isSignUp) {
                    run { AuthRepo.signUp(firstName, lastName, email, password) }
                } else {
                    run { AuthRepo.signIn(email, password) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Continue with Google",
            enabled = !loading,
            onClick = { launchGoogleSignIn() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        GhostButton(
            text = if (isSignUp) "Have an account? Sign in" else "New here? Create account",
            tint = MaterialTheme.colorScheme.primary,
            onClick = { isSignUp = !isSignUp; error = null }
        )
        if (!isSignUp) {
            GhostButton(
                text = "Forgot password?",
                onClick = {
                    if (email.isBlank()) {
                        error = "Enter your email to reset your password."
                    } else {
                        run {
                            AuthRepo.sendPasswordReset(email)
                            notice = "Reset link sent. Check your inbox."
                        }
                    }
                }
            )
        }
    }
}
