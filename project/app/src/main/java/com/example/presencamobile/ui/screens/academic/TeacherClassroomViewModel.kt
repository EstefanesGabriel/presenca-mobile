package com.example.presencamobile.ui.screens.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.presencamobile.data.location.LocationProvider
import com.example.presencamobile.data.model.*
import com.example.presencamobile.data.repository.*
import java.util.UUID

data class TeacherClassroomState(
    val classroom: Classroom,
    val lessons: List<Lesson> = emptyList(),
    val participants: List<ClassroomParticipant> = emptyList(),
    val selectedLesson: Lesson? = null,
    val attendance: List<AttendanceRecord> = emptyList(),
    val qr: AttendanceQr? = null,
    val loaded: Boolean = false
)

class TeacherClassroomViewModel(
    private val profile: UserProfile,
    classroom: Classroom,
    private val positionProvider: LocationProvider,
    private val repository: AcademicRepository = AcademicRepository(),
    private val memberships: MembershipRepository = MembershipRepository()
) : AcademicViewModel() {
    var state by mutableStateOf(TeacherClassroomState(classroom)); private set
    private var qrRequestId: String? = null
    private fun allowed() = profile.role == UserRole.TEACHER && state.classroom.teacherId == profile.id

    fun refresh() {
        if (!allowed()) return
        perform { reload() }
    }
    private suspend fun reload() {
        val classroom = repository.classroom(state.classroom.id) ?: throw ClassroomUnavailable()
        val lessons = repository.lessons(state.classroom.id)
        val participants = memberships.listParticipants(state.classroom.id)
        val selected = state.selectedLesson?.let { current -> lessons.find { it.id == current.id } }
        val attendance = selected?.let { repository.attendance(it.id) } ?: emptyList()
        state = state.copy(classroom = classroom, lessons = lessons, participants = participants, selectedLesson = selected, attendance = attendance, loaded = true)
    }
    fun openLesson(lesson: Lesson) {
        if (!allowed() || busy || state.lessons.none { it.id == lesson.id }) return
        qrRequestId = null
        state = state.copy(selectedLesson = lesson, qr = null, attendance = emptyList())
        perform { state = state.copy(attendance = repository.attendance(lesson.id)) }
    }
    fun backToClassroom() {
        if (busy) return
        state = state.copy(selectedLesson = null, attendance = emptyList(), qr = null)
        qrRequestId = null
    }
    fun saveClassroom(name: String, subject: String, semester: String, password: String, onSaved: () -> Unit) {
        if (!allowed() || busy) return
        if (name.trim().length !in 1..100 || subject.trim().length !in 1..100 || semester.trim().length !in 1..30 ||
            (password.isNotEmpty() && (password.trim().length < 6 || password.toByteArray().size > 72))) {
            showError("Informe nome e disciplina (até 100 caracteres), período (até 30) e senha com 6 a 72 bytes, se desejar alterá-la."); return
        }
        perform {
            state = state.copy(classroom = repository.updateClassroom(state.classroom.copy(name = name.trim(), subject = subject.trim(), semester = semester.trim()), password.ifEmpty { null }))
            notice = "Turma atualizada."; onSaved()
        }
    }
    fun closeClassroom(onSaved: () -> Unit) {
        if (!allowed()) return
        perform {
            state = state.copy(classroom = repository.closeClassroom(state.classroom.id), qr = null)
            reload(); notice = "Turma encerrada. O histórico foi preservado."; onSaved()
        }
    }
    fun removeParticipant(studentId: String, onSaved: () -> Unit) {
        if (!allowed()) return
        perform { repository.removeParticipant(state.classroom.id, studentId); reload(); notice = "Participante removido. O histórico foi preservado."; onSaved() }
    }
    fun saveLesson(id: String, title: String, localDateTime: String, onSaved: () -> Unit) {
        if (!allowed() || busy) return
        val utc = AcademicDates.toUtc(localDateTime)
        if (title.trim().length !in 1..100 || utc == null) {
            showError("Informe um título de até 100 caracteres e uma data válida no formato dd/MM/aaaa HH:mm."); return
        }
        perform { repository.saveLesson(id, state.classroom.id, title.trim(), utc); reload(); notice = "Aula salva."; onSaved() }
    }
    fun cancelLesson(onSaved: () -> Unit) {
        if (!allowed()) return
        val lesson = state.selectedLesson ?: return
        perform { repository.cancelLesson(lesson.id); state = state.copy(selectedLesson = null, qr = null); reload(); notice = "Aula removida."; onSaved() }
    }
    fun closeLesson(onSaved: () -> Unit) {
        if (!allowed()) return
        val lesson = state.selectedLesson ?: return
        perform { repository.closeLesson(lesson.id); state = state.copy(qr = null); reload(); notice = "Chamada encerrada; as faltas agora aparecem no histórico."; onSaved() }
    }
    fun generateQr(duration: Int?, radius: Int?) {
        if (!allowed() || busy) return
        val lesson = state.selectedLesson ?: return
        if (duration == null || duration !in 30..600 || radius == null || radius !in 20..500) {
            showError("Use validade de 30 a 600 segundos e raio de 20 a 500 metros."); return
        }
        val requestId = qrRequestId ?: UUID.randomUUID().toString().also { qrRequestId = it }
        perform {
            val position = positionProvider.currentPosition()
            state = state.copy(qr = repository.generateQr(requestId, lesson.id, position, duration, radius))
            qrRequestId = null
            reload(); notice = "QR gerado. Mostre a imagem aos alunos e encerre a chamada ao terminar."
        }
    }
    fun invalidateQr() {
        if (!allowed()) return
        val lesson = state.selectedLesson ?: return
        perform { repository.invalidateQr(lesson.id); state = state.copy(qr = null); qrRequestId = null; notice = "QR desativado. Você pode gerar outro para esta aula." }
    }
    fun invite(email: String, onSaved: () -> Unit) {
        if (!allowed() || busy) return
        if (email.trim().length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())) { showError("Informe um e-mail válido."); return }
        perform { repository.invite(state.classroom.id, email.trim()); notice = "Convite enviado no aplicativo do aluno."; onSaved() }
    }
}
