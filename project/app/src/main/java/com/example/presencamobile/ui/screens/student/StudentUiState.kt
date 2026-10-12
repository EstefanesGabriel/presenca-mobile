package com.example.presencamobile.ui.screens.student

import com.example.presencamobile.data.model.Classroom
import java.util.Locale

data class JoinClassroomForm(
    val code: String = "",
    val password: String = "",
    val codeError: String? = null,
    val passwordError: String? = null
) {
    val normalizedCode: String get() = code.trim().uppercase(Locale.ROOT)
    fun validated() = copy(
        codeError = if (!normalizedCode.matches(Regex("[0-9A-F]{12}"))) "Informe o código de 12 caracteres fornecido pelo professor." else null,
        passwordError = if (password.isBlank() || password.length < 6 || password.toByteArray(Charsets.UTF_8).size > 72) {
            "Confira a senha de acesso fornecida pelo professor."
        } else null
    )
    val hasErrors: Boolean get() = codeError != null || passwordError != null
}

data class StudentUiState(
    val classrooms: List<Classroom> = emptyList(),
    val isLoading: Boolean = false,
    val isJoining: Boolean = false,
    val isSaving: Boolean = false,
    val form: JoinClassroomForm = JoinClassroomForm(),
    val listError: String? = null,
    val joinError: String? = null,
    val successMessage: String? = null
)
