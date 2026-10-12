package com.example.presencamobile.ui.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import java.io.IOException

fun authErrorMessage(exception: Exception): String {
    return when (exception) {
        is AuthRestException -> authErrorMessage(exception.errorCode)
        is IOException -> "Não foi possível conectar. Confira sua conexão e tente novamente."
        else -> "Não foi possível concluir a solicitação. Tente novamente."
    }
}

internal fun authErrorMessage(code: AuthErrorCode?): String = when (code) {
    AuthErrorCode.InvalidCredentials -> "E-mail ou senha incorretos."
    AuthErrorCode.EmailNotConfirmed -> "Confirme seu e-mail antes de entrar."
    AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists ->
        "Já existe uma conta com esse e-mail. Tente entrar."
    AuthErrorCode.WeakPassword -> "A senha não atende aos requisitos. Use uma senha mais forte."
    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
        "Muitas tentativas em pouco tempo. Aguarde antes de tentar novamente."
    AuthErrorCode.EmailAddressNotAuthorized ->
        "O envio de confirmação ainda não está habilitado para este e-mail."
    else -> "Não foi possível concluir a solicitação. Tente novamente."
}
