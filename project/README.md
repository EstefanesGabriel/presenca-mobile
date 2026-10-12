# Presença Mobile

Aplicativo Android de controle de presença por QR Code e localização, com perfis de professor e aluno. Kotlin, Jetpack Compose, Navigation Compose, ViewModel e Supabase (Auth + PostgreSQL via PostgREST).

## Preparar e executar

1. Abra esta pasta no Android Studio e sincronize o Gradle. O projeto usa o JDK do Android Studio e o SDK configurado em `local.properties`.
2. Confira a URL e a chave **publishable** do seu projeto em `app/src/main/java/com/example/presencamobile/data/remote/SupabaseProvider.kt`. Nunca coloque senha do PostgreSQL ou chave `service_role` no aplicativo.
3. Prepare o banco conforme a sequência abaixo. Em um banco existente, aplique apenas as etapas ainda pendentes.
4. Se deseja cadastro imediato, desative **Confirm email** na configuração do provedor Email do Supabase. Essa configuração pertence ao servidor.
5. Execute o aplicativo pelo Run do Android Studio e cadastre uma conta de professor e uma de aluno.

## Preparar o banco no pgAdmin 4

Conecte o pgAdmin ao PostgreSQL do seu projeto Supabase. Em um banco novo, abra o Query Tool e execute cada arquivo inteiro, nesta ordem:

| Ordem | Arquivo | Finalidade |
| --- | --- | --- |
| 1 | [profiles.sql](supabase/setup/profiles.sql) | Perfis e gatilho de cadastro |
| 2 | [202610050001_teacher_classrooms.sql](supabase/migrations/202610050001_teacher_classrooms.sql) | Turmas e senha de acesso |
| 3 | [202610060001_classroom_members.sql](supabase/migrations/202610060001_classroom_members.sql) | Vínculos dos alunos e participantes |
| 4 | [202610070001_complete_attendance.sql](supabase/migrations/202610070001_complete_attendance.sql) | Aulas, QR, presenças, convites e notificações |
| 5 | [202610090001_classroom_password_fix.sql](supabase/migrations/202610090001_classroom_password_fix.sql) | Correção da alteração de senha da turma |

Aguarde `COMMIT` em cada execução. Em caso de erro, execute `ROLLBACK;` e confira a causa antes de continuar. Não repita etapas já aplicadas. Os scripts não são executados automaticamente pelo Android.

Se o banco já foi preparado pelo antigo script de reconstrução, as etapas acima já estão incluídas nele. Esta limpeza de arquivos locais não modifica o banco remoto.

## Funcionalidades

Professor: criar, editar e encerrar turmas; listar e remover participantes; convidar alunos por e-mail da conta; criar, editar e remover aulas; gerar QR temporário com localização e raio; desativar QR; acompanhar presenças e encerrar a chamada; ler notificações de resposta aos convites.

Aluno: entrar por código/senha ou aceitar convite; recusar convites; escanear QR pela câmera e registrar presença com GPS; consultar presença, falta e frequência por turma; ler e marcar notificações.

Notificações ficam dentro do aplicativo e são atualizadas ao abrir a tela ou tocar em Atualizar. Presenças são oficiais somente depois da confirmação do servidor; é necessária conexão com a internet.

## Organização do código

O aplicativo utiliza MVVM em um único módulo:

- `ui`: telas Compose, estado e ViewModels.
- `data/model`: objetos recebidos e enviados ao servidor.
- `data/repository`: comunicação com o Supabase.
- `data/location`: obtenção da localização precisa.
- `data/remote`: configuração do cliente Supabase.
- `ui/scanner` e os layouts XML: câmera e leitura exclusiva de QR Code.

O fluxo é tela → ViewModel → repositório → Supabase. Os ViewModels utilizam os repositórios diretamente. Os scripts SQL preservam as verificações de perfil, vínculo, validade do QR e distância no servidor.

O professor abre uma turma nas abas Aulas, Alunos e Turma. Ao gerar o QR, precisa estar no local da aula e permitir localização precisa. O aluno abre Presença e histórico e permite câmera e localização precisa para registrar presença. A frequência considera as aulas encerradas da lista de chamada. Os convites são recebidos dentro do aplicativo, usando o e-mail para identificar a conta de aluno já cadastrada.

## Gerar o APK

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. O projeto não contém testes automatizados nem simulações de repositórios. As pastas `build`, `.gradle` e `.kotlin` são geradas pelas ferramentas de desenvolvimento e podem reaparecer ao compilar.
