package com.example.presencamobile.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.repository.AuthRepository
import com.example.presencamobile.data.repository.ProfileRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    var uiState by mutableStateOf(SessionUiState())
        private set

    private var profileJob: Job? = null
    private var profileVersion = 0

    init {
        viewModelScope.launch {
            authRepository.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = status.session.user?.id
                    val sameAccount = uiState.isAuthenticated && uiState.userId == userId
                    if (!sameAccount) {
                        profileVersion++
                        profileJob?.cancel()
                        profileJob = null
                    }
                    uiState = if (sameAccount) {
                        uiState.copy(email = status.session.user?.email)
                    } else {
                        SessionUiState(
                            isInitializing = false,
                            isAuthenticated = true,
                            email = status.session.user?.email,
                            userId = userId
                        )
                    }
                    if (!sameAccount) loadProfile()
                    return@collect
                }
                profileVersion++
                profileJob?.cancel()
                profileJob = null
                uiState = when (status) {
                    SessionStatus.Initializing -> SessionUiState()
                    is SessionStatus.Authenticated -> error("Tratado acima")
                    is SessionStatus.NotAuthenticated -> SessionUiState(
                        isInitializing = false
                    )
                    is SessionStatus.RefreshFailure -> SessionUiState(
                        isInitializing = false,
                        errorMessage = "Não foi possível renovar sua sessão. Confira a conexão e entre novamente."
                    )
                }
            }
        }
    }

    fun loadProfile() {
        if (!uiState.isAuthenticated || profileJob?.isActive == true) return
        val userId = uiState.userId
        if (userId == null) {
            uiState = uiState.copy(profileError = "Não foi possível identificar sua conta. Saia e entre novamente.")
            return
        }
        uiState = uiState.copy(isProfileLoading = true, profileError = null)
        val version = ++profileVersion
        profileJob = viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile(userId)
                if (profileVersion != version || !uiState.isAuthenticated) return@launch
                uiState = if (profile == null || profile.id != userId) {
                    uiState.copy(profile = null, profileError = "Seu perfil não foi encontrado. Verifique o cadastro antes de continuar.")
                } else {
                    uiState.copy(profile = profile)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (profileVersion == version) {
                    uiState = uiState.copy(profile = null, profileError = "Não foi possível carregar seu perfil. Confira a conexão e tente novamente.")
                }
            } finally {
                if (profileVersion == version) uiState = uiState.copy(isProfileLoading = false)
            }
        }
    }

    fun onSignOutClick() {
        if (!uiState.isAuthenticated || uiState.isSigningOut) return

        uiState = uiState.copy(isSigningOut = true, errorMessage = null)
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    errorMessage = "Não foi possível sair. Confira a conexão e tente novamente."
                )
            } finally {
                uiState = uiState.copy(isSigningOut = false)
            }
        }
    }
}
