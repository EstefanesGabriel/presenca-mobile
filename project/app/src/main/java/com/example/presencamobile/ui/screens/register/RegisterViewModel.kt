package com.example.presencamobile.ui.screens.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.util.Patterns
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.repository.AuthRepository
import com.example.presencamobile.data.model.UserRole
import com.example.presencamobile.ui.auth.authErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    var uiState by mutableStateOf(RegisterUiState())
        private set

    fun onNameChange(newName: String) {
        if (uiState.isLoading) return
        uiState = uiState.copy(name = newName, errorMessage = null, successMessage = null)
    }

    fun onEmailChange(newEmail: String) {
        if (uiState.isLoading) return
        uiState = uiState.copy(email = newEmail, errorMessage = null, successMessage = null)
    }

    fun onPasswordChange(newPassword: String) {
        if (uiState.isLoading) return
        uiState = uiState.copy(password = newPassword, errorMessage = null, successMessage = null)
    }

    fun onRoleChange(newRole: UserRole) {
        if (uiState.isLoading) return
        uiState = uiState.copy(role = newRole, errorMessage = null, successMessage = null)
    }

    fun onRegisterClick() {
        if (uiState.isLoading) return

        val form = uiState

        val validationError = when {
            form.name.isBlank() ->
                "Informe seu nome."

            form.name.trim().length > 100 ->
                "O nome deve ter até 100 caracteres."

            !Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches() ->
                "Informe um e-mail válido."

            form.password.length < 8 ->
                "A senha deve ter pelo menos 8 caracteres."

            else -> null
        }

        if (validationError != null) {
            uiState = uiState.copy(
                errorMessage = validationError,
                successMessage = null
            )
            return
        }

        uiState = uiState.copy(
            isLoading = true,
            errorMessage = null,
            successMessage = null
        )

        viewModelScope.launch {
            try {
                authRepository.signUp(
                    name = form.name,
                    email = form.email,
                    password = form.password,
                    role = form.role.name
                )

                uiState = uiState.copy(
                    password = "",
                    successMessage = "Solicitação de cadastro enviada."
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    errorMessage = authErrorMessage(exception)
                )
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }
}
