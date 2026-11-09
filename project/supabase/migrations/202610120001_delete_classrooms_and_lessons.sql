-- Execute depois das migrações anteriores. Cria as operações de exclusão;
-- aplicar esta migração não exclui nenhuma turma ou aula existente.
begin;

create or replace function public.delete_classroom(p_classroom_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
    v_user_id uuid := auth.uid();
    v_teacher_id uuid;
begin
    if v_user_id is null or not exists (
        select 1 from public.profiles where id = v_user_id and role = 'TEACHER'
    ) then
        raise exception 'Teacher account required' using errcode = '42501';
    end if;
    if p_classroom_id is null then
        raise exception 'Classroom ID required' using errcode = '22023';
    end if;

    -- O mesmo bloqueio usado nas chamadas coordena exclusão e novas presenças.
    select teacher_id into v_teacher_id from public.classrooms
        where id = p_classroom_id for update;
    -- Reenviar uma exclusão confirmada não gera erro nem altera outra turma.
    if not found then return; end if;
    if v_teacher_id <> v_user_id then
        raise exception 'Classroom owner required' using errcode = '42501';
    end if;

    -- As chaves estrangeiras ON DELETE CASCADE já existentes apagam aulas,
    -- QR, listas de chamada, presenças, vínculos, credenciais e convites.
    delete from public.classrooms where id = p_classroom_id;
end; $$;

create or replace function public.delete_lesson(p_classroom_id uuid, p_lesson_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if p_classroom_id is null or p_lesson_id is null then
        raise exception 'Classroom and lesson IDs required' using errcode = '22023';
    end if;
    perform private.require_teacher(p_classroom_id);
    if exists (
        select 1 from public.lessons where id = p_lesson_id and classroom_id <> p_classroom_id
    ) then
        raise exception 'Lesson belongs to another classroom' using errcode = '42501';
    end if;

    -- Aulas encerradas ou com presenças também podem ser excluídas.
    -- A cascata remove seus QR, lista de chamada e registros de presença.
    -- Se a aula já foi excluída, a repetição não modifica outros registros.
    delete from public.lessons where id = p_lesson_id and classroom_id = p_classroom_id;
end; $$;

revoke all on function public.delete_classroom(uuid) from public, anon, authenticated;
revoke all on function public.delete_lesson(uuid,uuid) from public, anon, authenticated;
grant execute on function public.delete_classroom(uuid) to authenticated;
grant execute on function public.delete_lesson(uuid,uuid) to authenticated;

notify pgrst, 'reload schema';
commit;
