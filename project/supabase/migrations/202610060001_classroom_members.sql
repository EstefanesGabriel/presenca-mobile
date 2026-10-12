-- Execute depois de 202610050001_teacher_classrooms.sql. Não repita a migração anterior.
begin;

alter table public.classrooms add column is_active boolean not null default true;

create table public.classroom_members (
    classroom_id uuid not null references public.classrooms(id) on delete cascade,
    student_id uuid not null references public.profiles(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (classroom_id, student_id)
);
create index classroom_members_student_idx on public.classroom_members(student_id, classroom_id);
alter table public.classroom_members enable row level security;
revoke all on public.classroom_members from public, anon, authenticated;
grant select on public.classroom_members to authenticated;

-- Sem referência de volta a classrooms: evita recursão entre as políticas.
create policy "Students read their own memberships"
on public.classroom_members for select to authenticated
using (student_id = (select auth.uid()));

create policy "Students read joined classrooms"
on public.classrooms for select to authenticated
using (
    exists (select 1 from public.profiles p where p.id = (select auth.uid()) and p.role = 'STUDENT')
    and exists (
        select 1 from public.classroom_members m
        where m.classroom_id = classrooms.id and m.student_id = (select auth.uid())
    )
);

-- A função precisa ler o hash privado e criar o vínculo. Por isso usa SECURITY DEFINER,
-- com identificação do aluno e conferência de perfil feitas dentro da função.
create function public.join_classroom(p_access_code text, p_access_password text)
returns table (classroom jsonb, already_member boolean)
language plpgsql security definer set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_classroom public.classrooms;
    v_hash text;
    v_inserted integer;
begin
    if v_user_id is null or not exists (
        select 1 from public.profiles where id = v_user_id and role = 'STUDENT'
    ) then
        raise exception 'Student account required' using errcode = '42501';
    end if;
    if p_access_code is null or upper(btrim(p_access_code)) !~ '^[0-9A-F]{12}$'
       or p_access_password is null or char_length(p_access_password) < 6
       or octet_length(p_access_password) > 72 then
        raise exception 'Invalid classroom credentials' using errcode = 'P1001';
    end if;

    select c.* into v_classroom from public.classrooms c
    where c.access_code = upper(btrim(p_access_code)) and c.is_active
    for share;
    if not found then
        raise exception 'Invalid classroom credentials' using errcode = 'P1001';
    end if;
    select password_hash into v_hash from private.classroom_credentials
    where classroom_id = v_classroom.id;
    if v_hash is null or extensions.crypt(p_access_password, v_hash) <> v_hash then
        raise exception 'Invalid classroom credentials' using errcode = 'P1001';
    end if;

    -- A chave primária também protege contra duas requisições simultâneas.
    insert into public.classroom_members(classroom_id, student_id)
    values (v_classroom.id, v_user_id)
    on conflict (classroom_id, student_id) do nothing;
    get diagnostics v_inserted = row_count;
    return query select to_jsonb(v_classroom), v_inserted = 0;
end;
$$;

-- Retorna somente os dados necessários à lista. Não abre acesso geral a profiles.
create function public.list_classroom_participants(p_classroom_id uuid)
returns table (student_id uuid, name text, joined_at timestamptz)
language plpgsql security definer set search_path = ''
as $$
begin
    if auth.uid() is null or not exists (
        select 1 from public.classrooms c join public.profiles p on p.id = c.teacher_id
        where c.id = p_classroom_id and c.teacher_id = auth.uid() and p.role = 'TEACHER'
    ) then
        raise exception 'Classroom owner required' using errcode = '42501';
    end if;
    return query
    select m.student_id, p.name, m.joined_at
    from public.classroom_members m join public.profiles p on p.id = m.student_id
    where m.classroom_id = p_classroom_id
    order by p.name, m.student_id;
end;
$$;

revoke all on function public.join_classroom(text, text) from public, anon, authenticated;
revoke all on function public.list_classroom_participants(uuid) from public, anon, authenticated;
grant execute on function public.join_classroom(text, text) to authenticated;
grant execute on function public.list_classroom_participants(uuid) to authenticated;

notify pgrst, 'reload schema';
commit;
