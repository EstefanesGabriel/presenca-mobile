package com.example.presencamobile.ui.screens.academic

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.location.LocationProvider
import com.example.presencamobile.data.model.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Composable
fun TeacherClassroomRoute(profile: UserProfile, classroom: Classroom, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: TeacherClassroomViewModel = viewModel(key = "teacher-class-${profile.id}-${classroom.id}", factory = viewModelFactory {
        initializer { TeacherClassroomViewModel(profile, classroom, LocationProvider(context)) }
    })
    DisposableEffect(vm) { vm.refresh(); onDispose { vm.cancelWork() } }
    val state = vm.state
    val back = { if (!vm.busy) { if (state.selectedLesson != null) vm.backToClassroom() else onBack() } }
    BackHandler { back() }
    TeacherClassroomScreen(vm, back)
}

@Composable
fun TeacherClassroomScreen(vm: TeacherClassroomViewModel, onBack: () -> Unit) {
    val state = vm.state
    val classroom = state.classroom
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var participantId by rememberSaveable { mutableStateOf("") }
    var lessonId by rememberSaveable { mutableStateOf("") }
    var lessonTitle by rememberSaveable { mutableStateOf("") }
    var lessonDate by rememberSaveable { mutableStateOf("") }
    val lesson = state.selectedLesson
    AcademicPage(lesson?.title ?: classroom.name, "${classroom.subject} • ${classroom.semester}", vm, onBack) {
        if (!classroom.isActive) Text("Turma encerrada — histórico disponível.")
        if (lesson != null) {
            TeacherLessonPanel(vm,
                onEdit = { lessonId = lesson.id; lessonTitle = lesson.title; lessonDate = AcademicDates.display(lesson.startsAt); dialog = "lesson" },
                onRemove = { dialog = "removeLesson" }, onClose = { dialog = "closeLesson" })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Aulas", "Alunos", "Turma").forEachIndexed { index, label ->
                    OutlinedButton(onClick = { tab = index }, enabled = !vm.busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        Text(if (tab == index) "• $label" else label)
                    }
                }
            }
            OutlinedButton(onClick = vm::refresh, enabled = !vm.busy) { Text("Atualizar") }
            when (tab) {
                0 -> {
                    if (classroom.isActive) Button(onClick = {
                        lessonId = UUID.randomUUID().toString(); lessonTitle = ""; lessonDate = AcademicDates.defaultInput(); dialog = "lesson"
                    }, enabled = !vm.busy) { Text("Criar aula") }
                    if (state.loaded && state.lessons.isEmpty()) Text("Nenhuma aula cadastrada.")
                    state.lessons.forEach { item -> AcademicCard {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(AcademicDates.display(item.startsAt)); Text(item.status.label())
                        OutlinedButton(onClick = { vm.openLesson(item) }, enabled = !vm.busy) { Text("Abrir aula e chamada") }
                    } }
                }
                1 -> {
                    Text("${state.participants.size} participante(s)")
                    if (classroom.isActive) Button(onClick = { dialog = "invite" }, enabled = !vm.busy) { Text("Convidar aluno por e-mail") }
                    if (state.loaded && state.participants.isEmpty()) Text("Nenhum aluno nesta turma.")
                    state.participants.forEach { student -> AcademicCard {
                        Text(student.name, style = MaterialTheme.typography.titleMedium)
                        Text("Entrou em ${AcademicDates.display(student.joinedAt)}")
                        TextButton(onClick = { participantId = student.studentId; dialog = "removeParticipant" }, enabled = !vm.busy) { Text("Remover da turma") }
                    } }
                }
                2 -> {
                    SelectionContainer { Text("Código da turma: ${classroom.accessCode}") }
                    Text("Compartilhe o código e a senha com os alunos. A senha não é exibida; você pode definir outra em Editar turma.")
                    if (classroom.isActive) {
                        Button(onClick = { dialog = "classroom" }, enabled = !vm.busy) { Text("Editar turma") }
                        OutlinedButton(onClick = { dialog = "closeClassroom" }, enabled = !vm.busy) { Text("Encerrar turma") }
                    }
                }
            }
        }
    }
    when (dialog) {
        "classroom" -> ClassroomEditDialog(classroom, vm, { dialog = null })
        "lesson" -> LessonEditDialog(lessonId, lessonTitle, lessonDate, vm, { dialog = null })
        "invite" -> InvitationDialog(vm, { dialog = null })
        "closeClassroom" -> ConfirmAction("Encerrar turma?", "Bloqueia novas entradas e chamadas, encerra aulas abertas e cancela aulas agendadas e convites pendentes. O histórico será preservado.", vm, { dialog = null }) { vm.closeClassroom { dialog = null } }
        "removeParticipant" -> ConfirmAction("Remover participante?", "O aluno perderá acesso à turma. As presenças já registradas serão preservadas.", vm, { dialog = null }) { vm.removeParticipant(participantId) { dialog = null } }
        "removeLesson" -> ConfirmAction("Remover aula?", "A aula será cancelada e deixará de contar na frequência. Aulas com presenças devem ser encerradas.", vm, { dialog = null }) { vm.cancelLesson { dialog = null } }
        "closeLesson" -> ConfirmAction("Encerrar chamada?", "O QR Code será desativado. Alunos da lista sem registro receberão falta. Esta aula não poderá ser reaberta.", vm, { dialog = null }) { vm.closeLesson { dialog = null } }
    }
}

@Composable
private fun TeacherLessonPanel(vm: TeacherClassroomViewModel, onEdit: () -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    val state = vm.state
    val lesson = state.selectedLesson ?: return
    var duration by rememberSaveable(lesson.id) { mutableStateOf("300") }
    var radius by rememberSaveable(lesson.id) { mutableStateOf("100") }
    val generate = rememberLocationAction({ vm.generateQr(duration.toIntOrNull(), radius.toIntOrNull()) }, vm::showError)
    Text(AcademicDates.display(lesson.startsAt)); Text(lesson.status.label())
    if (state.classroom.isActive && lesson.status in listOf(LessonStatus.SCHEDULED, LessonStatus.OPEN)) {
        if (lesson.status == LessonStatus.SCHEDULED) TextButton(onClick = onEdit, enabled = !vm.busy) { Text("Editar título e data") }
        Text("Ao gerar, o professor deve estar no local da aula. A localização precisa será usada como referência.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(duration, { duration = it }, label = { Text("Validade (s)") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !vm.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(radius, { radius = it }, label = { Text("Raio (m)") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !vm.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Text("Validade: 30–600 s. Raio: 20–500 m. Precisão do GPS: até 50 m e metade do raio.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = generate, enabled = !vm.busy) { Text(if (state.qr == null) "Gerar QR Code" else "Gerar novo QR Code") }
        PermissionSettingsButton()
        state.qr?.let { qr ->
            AttendanceQrImage(qr)
        }
        if (state.qr != null || lesson.status == LessonStatus.OPEN) OutlinedButton(onClick = vm::invalidateQr, enabled = !vm.busy) { Text("Desativar QR Code") }
        Button(onClick = onClose, enabled = !vm.busy) { Text("Encerrar chamada") }
        if (state.attendance.none { it.recordedAt != null }) TextButton(onClick = onRemove, enabled = !vm.busy) { Text("Remover aula") }
    }
    Text("Lista de chamada", style = MaterialTheme.typography.titleLarge)
    Text("${state.attendance.count { it.recordedAt != null }} presença(s) / ${state.attendance.size} aluno(s)")
    OutlinedButton(onClick = vm::refresh, enabled = !vm.busy) { Text("Atualizar presenças") }
    if (state.attendance.isEmpty()) Text("A lista é formada ao abrir ou encerrar a chamada.")
    state.attendance.forEach { student -> AcademicCard {
        Text(student.name, style = MaterialTheme.typography.titleMedium)
        Text(if (student.recordedAt != null) "Presente — ${AcademicDates.display(student.recordedAt)}" else if (lesson.status == LessonStatus.CLOSED) "Falta" else "Ainda não registrou")
    } }
}

@Composable
private fun AttendanceQrImage(qr: AttendanceQr) {
    val remaining by produceState(0L, qr.expiresAt, qr.isActive) {
        while (true) {
            value = if (qr.isActive) runCatching { Duration.between(Instant.now(), AcademicDates.instant(qr.expiresAt)).seconds.coerceAtLeast(0) }.getOrDefault(0) else 0
            delay(1000)
        }
    }
    if (remaining > 0) {
        val bitmap = remember(qr.token) {
            val matrix = QRCodeWriter().encode(qr.token, BarcodeFormat.QR_CODE, 600, 600)
            val pixels = IntArray(600 * 600) { index -> if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 600, 600, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        Image(bitmap, "QR Code para registrar presença nesta aula", Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(1f).background(Color.White))
        Text("Expira em ${remaining / 60}min ${remaining % 60}s • raio ${qr.radiusMeters} m")
    } else Text("QR Code expirado ou desativado. Gere outro se a chamada continuar aberta.")
}

@Composable
private fun ClassroomEditDialog(classroom: Classroom, vm: TeacherClassroomViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(classroom.name) }
    var subject by rememberSaveable { mutableStateOf(classroom.subject) }
    var semester by rememberSaveable { mutableStateOf(classroom.semester) }
    var password by rememberSaveable { mutableStateOf("") }
    FormDialog("Editar turma", vm, onDismiss, { vm.saveClassroom(name, subject, semester, password, onDismiss) }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, enabled = !vm.busy)
        OutlinedTextField(subject, { subject = it }, label = { Text("Disciplina") }, singleLine = true, enabled = !vm.busy)
        OutlinedTextField(semester, { semester = it }, label = { Text("Período") }, singleLine = true, enabled = !vm.busy)
        OutlinedTextField(password, { password = it }, label = { Text("Nova senha (opcional)") }, singleLine = true, enabled = !vm.busy, visualTransformation = PasswordVisualTransformation())
        Text("Deixe a senha vazia para manter a atual.")
    }
}

@Composable
private fun LessonEditDialog(id: String, initialTitle: String, initialDate: String, vm: TeacherClassroomViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable(id) { mutableStateOf(initialTitle) }
    var date by rememberSaveable(id) { mutableStateOf(initialDate) }
    FormDialog("Salvar aula", vm, onDismiss, { vm.saveLesson(id, title, date, onDismiss) }) {
        OutlinedTextField(title, { title = it }, label = { Text("Título da aula") }, singleLine = true, enabled = !vm.busy)
        OutlinedTextField(date, { date = it }, label = { Text("Data e hora") }, placeholder = { Text("07/10/2026 19:30") }, singleLine = true, enabled = !vm.busy)
        Text("Formato: dd/MM/aaaa HH:mm. Horário local do celular.")
    }
}

@Composable
private fun InvitationDialog(vm: TeacherClassroomViewModel, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    FormDialog("Convidar aluno", vm, onDismiss, { vm.invite(email, onDismiss) }) {
        Text("O aluno precisa já ter uma conta. O convite será recebido dentro do aplicativo.")
        OutlinedTextField(email, { email = it }, label = { Text("E-mail do aluno") }, singleLine = true, enabled = !vm.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    }
}

@Composable
private fun FormDialog(title: String, vm: AcademicViewModel, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = { if (!vm.busy) onDismiss() }, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content(); AcademicFeedback(vm) } },
        confirmButton = { TextButton(onClick = onSave, enabled = !vm.busy) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancelar") } })
}
