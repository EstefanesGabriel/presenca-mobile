package com.example.presencamobile.ui.screens.register

import com.example.presencamobile.data.model.UserRole

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val role: UserRole = UserRole.STUDENT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
