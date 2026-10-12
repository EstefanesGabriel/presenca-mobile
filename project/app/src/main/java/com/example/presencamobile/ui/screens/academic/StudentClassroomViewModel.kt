package com.example.presencamobile.ui.screens.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.presencamobile.data.location.LocationProvider
import com.example.presencamobile.data.model.*
import com.example.presencamobile.data.repository.AcademicRepository

class StudentClassroomViewModel(
    private val profile: UserProfile,
    classroom: Classroom,
    private val positionProvider: LocationProvider,
    private val repository: AcademicRepository = AcademicRepository()
) : AcademicViewModel() {
    var classroom by mutableStateOf(classroom); private set
    var history by mutableStateOf<List<AttendanceHistory>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var receipt by mutableStateOf<AttendanceReceipt?>(null); private set
    val summary get() = AttendanceSummary.from(history)

    fun refresh() {
        if (profile.role != UserRole.STUDENT) return
        perform { reloadHistory() }
    }
    private suspend fun reloadHistory() {
        val current = repository.classroom(classroom.id) ?: throw ClassroomUnavailable()
        val records = repository.history(classroom.id)
        classroom = current; history = records; loaded = true
    }
    fun scan(token: String) {
        if (profile.role != UserRole.STUDENT || busy) return
        if (!AttendanceToken.isValid(token)) { showError("Este QR Code não é uma chamada do Presença Mobile."); return }
        receipt = null
        perform {
            val position = positionProvider.currentPosition()
            receipt = repository.recordAttendance(classroom.id, token, position)
            notice = if (receipt?.alreadyRecorded == true) "Sua presença já estava registrada nesta aula." else "Presença confirmada!"
            // A confirmação é mantida mesmo se a atualização posterior do histórico falhar.
            reloadHistory()
        }
    }
}
