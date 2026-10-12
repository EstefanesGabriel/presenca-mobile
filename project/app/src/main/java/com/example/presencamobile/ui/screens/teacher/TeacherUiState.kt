package com.example.presencamobile.ui.screens.teacher

import com.example.presencamobile.data.model.Classroom

data class ClassroomForm(
    val name: String = "",
    val subject: String = "",
    val semester: String = "",
    val accessPassword: String = "",
    val nameError: String? = null,
    val subjectError: String? = null,
    val semesterError: String? = null,
    val passwordError: String? = null
) {
    fun validated(): ClassroomForm = copy(
        nameError = if (name.trim().length !in 1..100) "Informe um nome com até 100 caracteres." else null,
        subjectError = if (subject.trim().length !in 1..100) "Informe uma disciplina com até 100 caracteres." else null,
        semesterError = if (semester.trim().length !in 1..30) "Informe o período com até 30 caracteres." else null,
        passwordError = if (accessPassword.length < 6 || accessPassword.toByteArray(Charsets.UTF_8).size > 72 || accessPassword.isBlank()) {
            "Use pelo menos 6 caracteres. Se a senha for muito longa, reduza seu tamanho."
        } else null
    )

    val hasErrors: Boolean
        get() = listOf(nameError, subjectError, semesterError, passwordError).any { it != null }
}

data class TeacherUiState(
    val classrooms: List<Classroom> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isSaving: Boolean = false,
    val form: ClassroomForm = ClassroomForm(),
    val listError: String? = null,
    val saveError: String? = null,
    val successMessage: String? = null,
    val selectedClassroom: Classroom? = null
)
