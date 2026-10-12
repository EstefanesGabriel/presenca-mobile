package com.example.presencamobile.ui.screens.teacher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.model.NewClassroom
import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.model.UserRole
import com.example.presencamobile.data.repository.ClassroomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

class TeacherViewModel(
    val profile: UserProfile,
    private val repository: ClassroomRepository = ClassroomRepository()
) : ViewModel() {
    var uiState by mutableStateOf(TeacherUiState())
        private set

    private var requestId = UUID.randomUUID().toString()

    init { refresh() }

    fun openParticipants(classroom: Classroom) {
        if (profile.role != UserRole.TEACHER || classroom.teacherId != profile.id || uiState.isSaving) return
        uiState = uiState.copy(selectedClassroom = classroom)
    }

    fun closeParticipants() {
        uiState = uiState.copy(selectedClassroom = null)
    }

    fun refresh() {
        if (uiState.isLoading || uiState.isSaving) return
        if (profile.role != UserRole.TEACHER) {
            uiState = uiState.copy(listError = "Esta área está disponível apenas para professores.")
            return
        }
        uiState = uiState.copy(isLoading = true, listError = null)
        viewModelScope.launch {
            try {
                val classrooms = repository.listClassrooms(profile.id)
                uiState = uiState.copy(classrooms = classrooms)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(listError = "Não foi possível carregar suas turmas. Confira a conexão e tente novamente.")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun openCreate() {
        if (profile.role != UserRole.TEACHER || uiState.isLoading || uiState.isSaving) return
        requestId = UUID.randomUUID().toString()
        uiState = uiState.copy(isCreating = true, form = ClassroomForm(), saveError = null, successMessage = null)
    }

    fun cancelCreate() {
        if (uiState.isSaving) return
        uiState = uiState.copy(isCreating = false, form = ClassroomForm(), saveError = null)
    }

    fun onNameChange(value: String) = updateForm(uiState.form.copy(name = value, nameError = null))
    fun onSubjectChange(value: String) = updateForm(uiState.form.copy(subject = value, subjectError = null))
    fun onSemesterChange(value: String) = updateForm(uiState.form.copy(semester = value, semesterError = null))
    fun onPasswordChange(value: String) = updateForm(uiState.form.copy(accessPassword = value, passwordError = null))

    private fun updateForm(form: ClassroomForm) {
        if (uiState.isSaving) return
        uiState = uiState.copy(form = form, saveError = null)
    }

    fun createClassroom() {
        if (profile.role != UserRole.TEACHER || !uiState.isCreating || uiState.isSaving) return
        val form = uiState.form.validated()
        uiState = uiState.copy(form = form, saveError = null)
        if (form.hasErrors) return

        val request = NewClassroom(requestId, form.name.trim(), form.subject.trim(), form.semester.trim(), form.accessPassword)
        uiState = uiState.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val created = repository.createClassroom(request)
                uiState = uiState.copy(
                    classrooms = listOf(created) + uiState.classrooms.filterNot { it.id == created.id },
                    isCreating = false,
                    form = ClassroomForm(),
                    listError = null,
                    successMessage = "Turma ${created.name} criada. Código: ${created.accessCode}."
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(saveError = "Não foi possível confirmar a criação. Confira a conexão e tente novamente; a mesma solicitação não criará duas turmas.")
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}
