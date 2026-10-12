package com.example.presencamobile.ui.screens.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val formMessage: String? = null,
    val isLoading: Boolean = false
)
