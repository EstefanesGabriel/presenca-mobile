package com.example.presencamobile.ui.screens.student

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.model.UserRole
import com.example.presencamobile.data.repository.MembershipRepository
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StudentViewModel(
    private val profile: UserProfile,
    private val repository: MembershipRepository = MembershipRepository()
) : ViewModel() {
    var uiState by mutableStateOf(StudentUiState())
        private set

    init { refresh() }

    fun refresh() {
        if (uiState.isLoading || uiState.isSaving) return
        if (profile.role != UserRole.STUDENT) {
            uiState = uiState.copy(listError = "Esta área está disponível apenas para alunos.")
            return
        }
        uiState = uiState.copy(isLoading = true, listError = null)
        viewModelScope.launch {
            try {
                uiState = uiState.copy(classrooms = repository.listMyClassrooms())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(listError = "Não foi possível carregar suas turmas. Confira a conexão e tente novamente.")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun openJoin() {
        if (profile.role != UserRole.STUDENT || uiState.isLoading || uiState.isSaving) return
        uiState = uiState.copy(isJoining = true, form = JoinClassroomForm(), joinError = null, successMessage = null)
    }

    fun cancelJoin() {
        if (uiState.isSaving) return
        uiState = uiState.copy(isJoining = false, form = JoinClassroomForm(), joinError = null)
    }

    fun onCodeChange(code: String) {
        if (uiState.isSaving) return
        uiState = uiState.copy(form = uiState.form.copy(code = code, codeError = null), joinError = null)
    }

    fun onPasswordChange(password: String) {
        if (uiState.isSaving) return
        uiState = uiState.copy(form = uiState.form.copy(password = password, passwordError = null), joinError = null)
    }

    fun joinClassroom() {
        if (profile.role != UserRole.STUDENT || !uiState.isJoining || uiState.isSaving) return
        val form = uiState.form.validated()
        uiState = uiState.copy(form = form, joinError = null)
        if (form.hasErrors) return
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val result = repository.joinClassroom(form.normalizedCode, form.password)
                uiState = uiState.copy(
                    classrooms = listOf(result.classroom) + uiState.classrooms.filterNot { it.id == result.classroom.id },
                    isJoining = false,
                    form = JoinClassroomForm(),
                    listError = null,
                    successMessage = if (result.alreadyMember) "Você já participa da turma ${result.classroom.name}." else "Você entrou na turma ${result.classroom.name}."
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(joinError = joinClassroomError((exception as? PostgrestRestException)?.code))
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}

internal fun joinClassroomError(code: String?): String = when (code) {
    "P1001" -> "Código ou senha incorretos, ou turma indisponível. Confira com o professor."
    "42501" -> "Entre com uma conta de aluno para participar de uma turma."
    else -> "Não foi possível confirmar sua entrada. Confira a conexão e tente novamente."
}
