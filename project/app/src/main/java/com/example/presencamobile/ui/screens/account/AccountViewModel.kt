package com.example.presencamobile.ui.screens.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.repository.AuthRepository
import com.example.presencamobile.data.repository.ProfileRepository
import com.example.presencamobile.ui.auth.authErrorMessage
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class AccountUiState(
    val name: String = "",
    val email: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AccountViewModel(
    private val profile: UserProfile,
    email: String?,
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {
    var uiState by mutableStateOf(AccountUiState(name = profile.name, email = email.orEmpty()))
        private set
    private var job: Job? = null
    private var version = 0

    fun onNameChange(value: String) = edit(uiState.copy(name = value))
    fun onNewPasswordChange(value: String) = edit(uiState.copy(newPassword = value))
    fun onConfirmPasswordChange(value: String) = edit(uiState.copy(confirmPassword = value))

    private fun edit(state: AccountUiState) {
        if (!uiState.isBusy) uiState = state.copy(errorMessage = null, successMessage = null)
    }
    private fun reject(message: String) { uiState = uiState.copy(errorMessage = message, successMessage = null) }
    private fun checkAccount(userId: String? = authRepository.currentUserId) {
        if (userId != profile.id) throw AccountUnavailable("Sua sessão mudou. Entre novamente na sua conta.")
    }
    fun refresh(onProfileUpdated: (UserProfile) -> Unit) {
        perform {
            checkAccount()
            val user = authRepository.getCurrentUser()
            val updated = profileRepository.getProfile(profile.id)
                ?: throw AccountUnavailable("Seu perfil não foi encontrado. Saia e entre novamente.")
            currentCoroutineContext().ensureActive()
            checkAccount(updated.id)
            checkAccount(user.id)
            uiState = uiState.copy(name = updated.name, email = user.email.orEmpty())
            onProfileUpdated(updated)
        }
    }
    fun saveName(onProfileUpdated: (UserProfile) -> Unit) {
        if (uiState.isBusy) return
        val name = uiState.name.trim()
        if (name.length !in 1..100) { reject("Informe um nome com até 100 caracteres."); return }
        perform {
            checkAccount()
            val updated = profileRepository.updateName(name)
            currentCoroutineContext().ensureActive()
            checkAccount(updated.id)
            uiState = uiState.copy(name = updated.name, successMessage = "Nome atualizado.")
            onProfileUpdated(updated)
        }
    }
    fun savePassword() {
        if (uiState.isBusy) return
        val password = uiState.newPassword
        if (password.length < 8 || password.isBlank()) { reject("A nova senha deve ter pelo menos 8 caracteres."); return }
        if (password != uiState.confirmPassword) { reject("As senhas informadas não coincidem."); return }
        uiState = uiState.copy(newPassword = "", confirmPassword = "")
        perform {
            checkAccount()
            val user = authRepository.updatePassword(password)
            currentCoroutineContext().ensureActive()
            checkAccount(user.id)
            uiState = uiState.copy(successMessage = "Senha alterada. Use a nova senha no próximo login.")
        }
    }
    private fun perform(action: suspend () -> Unit) {
        if (uiState.isBusy) return
        val currentVersion = ++version
        uiState = uiState.copy(isBusy = true, errorMessage = null, successMessage = null)
        job = viewModelScope.launch {
            try { action() }
            catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) {
                if (currentVersion == version) uiState = uiState.copy(errorMessage = accountError(exception))
            }
            finally { if (currentVersion == version) uiState = uiState.copy(isBusy = false) }
        }
    }
    fun cancelWork() {
        version++
        job?.cancel()
        job = null
        uiState = uiState.copy(newPassword = "", confirmPassword = "", isBusy = false, errorMessage = null, successMessage = null)
    }
}

private class AccountUnavailable(message: String) : Exception(message)
private fun accountError(exception: Exception): String = when (exception) {
    is AccountUnavailable -> exception.message.orEmpty()
    is PostgrestRestException -> when (exception.code) {
        "42501" -> "Sua conta não tem acesso a esta operação. Entre novamente."
        "22023" -> "Informe um nome com até 100 caracteres."
        "PGRST202" -> "A edição do nome ainda não está disponível. Tente novamente mais tarde."
        else -> "Não foi possível salvar o nome. Confira a conexão e tente novamente."
    }
    else -> authErrorMessage(exception)
}
