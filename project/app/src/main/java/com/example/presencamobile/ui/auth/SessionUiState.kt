package com.example.presencamobile.ui.auth

import com.example.presencamobile.data.model.UserProfile

data class SessionUiState(
    val isInitializing: Boolean = true,
    val isAuthenticated: Boolean = false,
    val email: String? = null,
    val isSigningOut: Boolean = false,
    val errorMessage: String? = null,
    val userId: String? = null,
    val profile: UserProfile? = null,
    val isProfileLoading: Boolean = false,
    val profileError: String? = null
)
