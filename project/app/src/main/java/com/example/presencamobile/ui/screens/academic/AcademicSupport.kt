package com.example.presencamobile.ui.screens.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.presencamobile.data.location.LocationUnavailable
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Uma operação por vez. Erros de rede não são apresentados como sucesso. */
abstract class AcademicViewModel : ViewModel() {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); protected set
    private var job: Job? = null
    private var version = 0

    protected fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        val currentVersion = ++version
        job = viewModelScope.launch {
            try { action() }
            catch (exception: CancellationException) { throw exception }
            catch (exception: Exception) { error = academicError(exception) }
            finally { if (currentVersion == version) busy = false }
        }
    }
    fun showError(message: String) { if (!busy) error = message }
    fun cancelWork() { version++; job?.cancel(); job = null; busy = false }
}

internal fun academicError(exception: Exception): String {
    if (exception is LocationUnavailable) return exception.message ?: "Localização indisponível."
    if (exception is ClassroomUnavailable) return "Esta turma não está mais disponível para sua conta. Volte e atualize a lista de turmas."
    if (exception is SecurityException) return "Verifique as permissões de câmera e localização precisa do aplicativo."
    return when ((exception as? PostgrestRestException)?.code) {
        "42501" -> "Sua conta não tem acesso a esta operação. Atualize a tela ou entre novamente."
        "P2001" -> "QR Code inválido, expirado ou desativado. Peça um novo código ao professor."
        "P2002" -> "A aula ou a turma foi encerrada ou está indisponível. Atualize a tela."
        "P2003" -> "Você precisa participar da turma correspondente ao QR Code."
        "P2004" -> "Você está fora do raio de presença. Aproxime-se do professor e tente novamente."
        "P2005" -> "Não foi encontrada uma conta de aluno com esse e-mail. O aluno precisa se cadastrar primeiro."
        "P2006" -> "O aluno já está na turma ou já possui um convite pendente."
        "P2007" -> "Este convite não está mais disponível. Atualize a lista."
        "P2008" -> "Localização imprecisa ou configuração inválida. Aguarde o GPS melhorar; use um raio maior se necessário."
        "P2009" -> "Esta aula já começou ou tem presenças. Encerre a chamada para preservar o histórico."
        "22023" -> "Revise os campos informados e tente novamente."
        else -> "Não foi possível confirmar a operação. Confira a conexão e atualize a tela antes de tentar novamente."
    }
}

internal class ClassroomUnavailable : Exception()

object AcademicDates {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("pt-BR")).withResolverStyle(ResolverStyle.STRICT)
    fun instant(value: String): Instant = OffsetDateTime.parse(value.replace(' ', 'T')).toInstant()
    fun display(value: String): String = runCatching { formatter.format(instant(value).atZone(ZoneId.systemDefault())) }.getOrDefault("Data indisponível")
    fun toUtc(value: String): String? = runCatching { LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault()).toInstant().toString() }.getOrNull()
    fun defaultInput(): String = formatter.format(LocalDateTime.now().withSecond(0).withNano(0))
}
