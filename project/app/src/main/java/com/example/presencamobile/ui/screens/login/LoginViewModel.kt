package com.example.presencamobile.ui.screens.login

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.repository.AuthRepository
import com.example.presencamobile.ui.auth.authErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun onEmailChange(newEmail: String) {
        if (uiState.isLoading) return
        uiState = uiState.copy(
            email = newEmail,
            emailError = null,
            formMessage = null
        )
    }

    fun onPasswordChange(newPassword: String) {
        if (uiState.isLoading) return
        uiState = uiState.copy(
            password = newPassword,
            passwordError = null,
            formMessage = null
        )
    }

    fun onLoginClick() {
        if (uiState.isLoading) return
        val normalizedEmail = uiState.email.trim()
        val password = uiState.password

        val emailError = when {
            normalizedEmail.isEmpty() ->
                "Informe seu e-mail"

            !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() ->
                "Informe um e-mail válido"

            else -> null
        }

        val passwordError = if (uiState.password.isEmpty()) {
            "Informe sua senha"
        } else {
            null
        }

        uiState = uiState.copy(
            email = normalizedEmail,
            emailError = emailError,
            passwordError = passwordError,
            formMessage = null
        )

        if (emailError != null || passwordError != null) return

        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            try {
                authRepository.signIn(normalizedEmail, password)
                uiState = uiState.copy(password = "")
                // A navegação reage à sessão, não somente ao clique no botão.
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(formMessage = authErrorMessage(exception))
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }
}
