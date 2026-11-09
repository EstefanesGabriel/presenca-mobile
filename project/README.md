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
| 6 | [202610120001_delete_classrooms_and_lessons.sql](supabase/migrations/202610120001_delete_classrooms_and_lessons.sql) | Exclusão permanente de turmas e aulas pelo professor responsável |
| 7 | [202610130001_account_profile.sql](supabase/migrations/202610130001_account_profile.sql) | Edição do nome da própria conta |
| 8 | [202610130002_leave_and_adjust_attendance.sql](supabase/migrations/202610130002_leave_and_adjust_attendance.sql) | Saída da turma com exclusão do histórico e correção de presença com justificativa |

Aguarde `COMMIT` em cada execução. Em caso de erro, execute `ROLLBACK;` e confira a causa antes de continuar. Não repita etapas já aplicadas. Os scripts não são executados automaticamente pelo Android.

Se o banco já foi preparado pelo antigo script de reconstrução, as etapas 1 a 5 já estão incluídas nele.

A etapa 6 é nova e deve ser aplicada também em bancos preparados pelo antigo script de reconstrução. Ela cria as funções de exclusão e não apaga dados durante sua aplicação.

A etapa 7 habilita Salvar nome na tela Minha conta. Não execute novamente o setup de profiles em um banco existente.

A etapa 8 habilita Sair da turma e a correção de presença. Execute o arquivo inteiro uma vez no Query Tool e aguarde COMMIT. A migração preserva os dados existentes: o histórico de um aluno só é apagado quando ele confirma a saída da turma.

## Funcionalidades

Professor: criar, editar, encerrar e excluir turmas; listar e remover participantes; convidar alunos por e-mail da conta; criar, editar e excluir aulas; gerar QR temporário com localização e raio; desativar QR; acompanhar presenças e encerrar a chamada; ler notificações de resposta aos convites.

Para excluir uma turma, abra a aba Turma e toque em Excluir turma. Para excluir uma aula, abra a aula e toque em Excluir aula. As duas opções pedem confirmação. Excluir turma apaga também todas as aulas e seus QR, listas de chamada e presenças, além dos vínculos, credenciais e convites da turma. Excluir aula apaga os registros daquela aula e a retira do cálculo de frequência. A exclusão é permanente e está disponível também para turmas encerradas e aulas com presenças. Encerrar turma ou chamada continua preservando o histórico. As contas dos usuários e as notificações já recebidas permanecem.

Aluno: entrar por código/senha ou aceitar convite; recusar convites; escanear QR pela câmera e registrar presença com GPS; consultar presença, falta e frequência por turma; ler e marcar notificações.

Para sair, o aluno abre Presença e histórico e toca em Sair da turma. Após a confirmação do servidor, são removidos o vínculo, as presenças, faltas, ajustes e convites daquele aluno naquela turma. As aulas e os registros dos outros alunos permanecem. Entrar novamente não recupera o histórico anterior. É possível sair também de uma turma encerrada. A remoção pelo professor continua preservando o histórico.

Para corrigir, o professor abre uma aula e usa Marcar presença ou Marcar falta na lista de chamada. Basta preencher uma justificativa de 1 a 250 caracteres. São permitidos ajustes em aulas abertas ou encerradas, inclusive de turmas encerradas. O último motivo, autor e data aparecem para o professor e o aluno; todos os ajustes ficam registrados no banco em `attendance_adjustments`. A frequência considera o estado corrigido das aulas encerradas. O QR não sobrescreve uma falta manual. Excluir a aula/turma ou o aluno sair da turma também apaga os ajustes correspondentes.

Notificações ficam dentro do aplicativo e são atualizadas ao abrir a tela ou tocar em Atualizar. Presenças são oficiais somente depois da confirmação do servidor; é necessária conexão com a internet.

## Minha conta

Aluno e professor acessam Minha conta no topo da área autenticada. A tela mostra nome, e-mail e perfil, permite salvar o nome e alterar a senha de login. O e-mail é exibido apenas para consulta. O nome é mantido em `public.profiles`, que é a fonte usada pelas telas e listas de participantes. O perfil aluno/professor não é alterado pela edição da conta.

A nova senha deve ter pelo menos 8 caracteres e coincidir com sua confirmação. Os campos de senha são limpos ao enviar a alteração e ao sair da tela. Não há recuperação de senha.

Se o servidor pedir autenticação recente para trocar a senha, saia e entre novamente na conta.

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
