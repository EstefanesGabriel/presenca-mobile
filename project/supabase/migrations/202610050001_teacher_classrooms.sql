-- Execute uma vez no Query Tool do pgAdmin conectado ao Supabase.
-- Pré-requisito: public.profiles(id uuid, name text, role text), criado na etapa anterior.
begin;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

-- Este schema deve permanecer fora dos schemas expostos pela Data API.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.classrooms (
    id uuid primary key,
    teacher_id uuid not null references public.profiles(id),
    name text not null check (char_length(btrim(name)) between 1 and 100),
    subject text not null check (char_length(btrim(subject)) between 1 and 100),
    semester text not null check (char_length(btrim(semester)) between 1 and 30),
    access_code text not null unique default upper(encode(extensions.gen_random_bytes(6), 'hex')),
    created_at timestamptz not null default now()
);
create index classrooms_teacher_created_idx on public.classrooms(teacher_id, created_at desc);

create table private.classroom_credentials (
    classroom_id uuid primary key references public.classrooms(id) on delete cascade,
    password_hash text not null
);

alter table public.classrooms enable row level security;
alter table private.classroom_credentials enable row level security;
revoke all on public.classrooms from public, anon, authenticated;
revoke all on private.classroom_credentials from public, anon, authenticated;
grant select on public.classrooms to authenticated;

create policy "Teachers read only their own classrooms"
on public.classrooms for select to authenticated
using (
    teacher_id = (select auth.uid())
    and exists (
        select 1 from public.profiles p
        where p.id = (select auth.uid()) and p.role = 'TEACHER'
    )
);

-- Somente esta função pode criar turmas pelo aplicativo. O dono vem da sessão,
-- nunca de um teacher_id fornecido pelo cliente. Senha e turma são gravadas juntas.
create function public.create_classroom(
    p_id uuid,
    p_name text,
    p_subject text,
    p_semester text,
    p_access_password text
)
returns setof public.classrooms
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_classroom public.classrooms;
begin
    if v_user_id is null or not exists (
        select 1 from public.profiles where id = v_user_id and role = 'TEACHER'
    ) then
        raise exception 'Only teachers can create classrooms' using errcode = '42501';
    end if;

    if p_id is null
       or p_name is null or char_length(btrim(p_name)) not between 1 and 100
       or p_subject is null or char_length(btrim(p_subject)) not between 1 and 100
       or p_semester is null or char_length(btrim(p_semester)) not between 1 and 30
       or p_access_password is null or char_length(p_access_password) < 6
       or char_length(btrim(p_access_password)) = 0 or octet_length(p_access_password) > 72 then
        raise exception 'Invalid classroom fields' using errcode = '22023';
    end if;

    -- Serializa reenvios da mesma solicitação, inclusive de conexões diferentes.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_id::text, 0));
    select * into v_classroom from public.classrooms where id = p_id;
    if found then
        if v_classroom.teacher_id <> v_user_id then
            raise exception 'Request ID unavailable' using errcode = '42501';
        end if;
        return next v_classroom;
        return;
    end if;

    insert into public.classrooms(id, teacher_id, name, subject, semester)
    values (p_id, v_user_id, btrim(p_name), btrim(p_subject), btrim(p_semester))
    returning * into v_classroom;

    insert into private.classroom_credentials(classroom_id, password_hash)
    values (p_id, extensions.crypt(p_access_password, extensions.gen_salt('bf', 10)));

    return next v_classroom;
end;
$$;

revoke all on function public.create_classroom(uuid, text, text, text, text) from public, anon, authenticated;
grant execute on function public.create_classroom(uuid, text, text, text, text) to authenticated;

notify pgrst, 'reload schema';
commit;
