-- Execute uma vez, depois das migrações de turmas e participantes.
begin;

create table public.lessons (
    id uuid primary key,
    classroom_id uuid not null references public.classrooms(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 100),
    starts_at timestamptz not null,
    status text not null default 'SCHEDULED' check (status in ('SCHEDULED','OPEN','CLOSED','CANCELLED')),
    created_at timestamptz not null default now()
);
create index lessons_classroom_idx on public.lessons(classroom_id, starts_at);
create table private.qr_sessions (
    id uuid primary key,
    lesson_id uuid not null references public.lessons(id) on delete cascade,
    token text not null unique,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy_m double precision not null check (accuracy_m > 0 and accuracy_m <= 50),
    radius_m integer not null check (radius_m between 20 and 500),
    expires_at timestamptz not null,
    is_active boolean not null default true
);
create unique index qr_one_active_idx on private.qr_sessions(lesson_id) where is_active;
create table public.lesson_roster (
    lesson_id uuid not null references public.lessons(id) on delete cascade,
    student_id uuid not null references public.profiles(id) on delete cascade,
    primary key (lesson_id, student_id)
);
create table public.attendances (
    lesson_id uuid not null references public.lessons(id) on delete cascade,
    student_id uuid not null references public.profiles(id) on delete cascade,
    recorded_at timestamptz not null default now(),
    distance_m double precision not null,
    accuracy_m double precision not null,
    primary key (lesson_id, student_id)
);
create table public.classroom_invitations (
    id uuid primary key default extensions.gen_random_uuid(),
    classroom_id uuid not null references public.classrooms(id) on delete cascade,
    student_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'PENDING' check (status in ('PENDING','ACCEPTED','REFUSED','CANCELLED')),
    created_at timestamptz not null default now(),
    responded_at timestamptz
);
create unique index invitations_pending_idx on public.classroom_invitations(classroom_id, student_id) where status = 'PENDING';
create index invitations_student_idx on public.classroom_invitations(student_id, created_at);
create table public.notifications (
    id uuid primary key default extensions.gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    message text not null,
    created_at timestamptz not null default now(),
    read_at timestamptz
);
create index notifications_user_idx on public.notifications(user_id, created_at);

alter table public.lessons enable row level security;
alter table public.lesson_roster enable row level security;
alter table public.attendances enable row level security;
alter table public.classroom_invitations enable row level security;
alter table public.notifications enable row level security;
alter table private.qr_sessions enable row level security;
revoke all on public.lessons, public.lesson_roster, public.attendances,
    public.classroom_invitations, public.notifications, private.qr_sessions from public, anon, authenticated;
-- As novas tabelas são acessadas exclusivamente pelas funções autorizadas abaixo.

create function private.require_teacher(p_classroom_id uuid)
returns public.classrooms language plpgsql security definer set search_path = '' as $$
declare v_class public.classrooms;
begin
    select * into v_class from public.classrooms where id = p_classroom_id for update;
    if not found or auth.uid() is null or v_class.teacher_id <> auth.uid()
       or not exists (select 1 from public.profiles where id = auth.uid() and role = 'TEACHER') then
        raise exception 'Classroom owner required' using errcode = '42501';
    end if;
    return v_class;
end; $$;

create function private.can_read_classroom(p_classroom_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and exists (
        select 1 from public.classrooms c where c.id = p_classroom_id and (
            (c.teacher_id = auth.uid() and exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'TEACHER'))
            or exists (select 1 from public.classroom_members m where m.classroom_id = c.id and m.student_id = auth.uid())
        )
    );
$$;
revoke all on function private.require_teacher(uuid), private.can_read_classroom(uuid) from public, anon, authenticated;

create function public.update_classroom(p_classroom_id uuid, p_name text, p_subject text, p_semester text, p_password text default null)
returns setof public.classrooms language plpgsql security definer set search_path = '' as $$
declare v_class public.classrooms;
begin
    v_class := private.require_teacher(p_classroom_id);
    if not v_class.is_active then raise exception 'Classroom closed' using errcode = 'P2002'; end if;
    if p_name is null or char_length(btrim(p_name)) not between 1 and 100
       or p_subject is null or char_length(btrim(p_subject)) not between 1 and 100
       or p_semester is null or char_length(btrim(p_semester)) not between 1 and 30 then
        raise exception 'Invalid fields' using errcode = '22023';
    end if;
    if p_password is not null then
        if char_length(btrim(p_password)) < 6 or octet_length(p_password) > 72 then
            raise exception 'Invalid password' using errcode = '22023';
        end if;
        update private.classroom_credentials set password_hash = extensions.crypt(p_password, extensions.gensalt('bf',10)) where classroom_id = p_classroom_id;
    end if;
    return query update public.classrooms set name = btrim(p_name), subject = btrim(p_subject), semester = btrim(p_semester)
        where id = p_classroom_id returning *;
end; $$;

create function public.close_classroom(p_classroom_id uuid)
returns setof public.classrooms language plpgsql security definer set search_path = '' as $$
begin
    perform private.require_teacher(p_classroom_id);
    update private.qr_sessions q set is_active = false from public.lessons l where q.lesson_id = l.id and l.classroom_id = p_classroom_id;
    update public.lessons set status = case when status = 'OPEN' then 'CLOSED' else 'CANCELLED' end
        where classroom_id = p_classroom_id and status in ('OPEN','SCHEDULED');
    update public.classroom_invitations set status = 'CANCELLED', responded_at = now()
        where classroom_id = p_classroom_id and status = 'PENDING';
    return query update public.classrooms set is_active = false where id = p_classroom_id returning *;
end; $$;

create function public.remove_classroom_participant(p_classroom_id uuid, p_student_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    perform private.require_teacher(p_classroom_id);
    delete from public.classroom_members where classroom_id = p_classroom_id and student_id = p_student_id;
    -- O histórico e a lista de chamada já aberta são preservados.
end; $$;

create function public.list_lessons(p_classroom_id uuid)
returns setof public.lessons language plpgsql security definer set search_path = '' as $$
begin
    if not private.can_read_classroom(p_classroom_id) then raise exception 'Membership required' using errcode = '42501'; end if;
    return query select * from public.lessons where classroom_id = p_classroom_id and status <> 'CANCELLED' order by starts_at desc, id;
end; $$;

create function public.save_lesson(p_id uuid, p_classroom_id uuid, p_title text, p_starts_at timestamptz)
returns setof public.lessons language plpgsql security definer set search_path = '' as $$
declare v_class public.classrooms; v_existing public.lessons;
begin
    v_class := private.require_teacher(p_classroom_id);
    if not v_class.is_active then raise exception 'Classroom closed' using errcode = 'P2002'; end if;
    if p_title is null or char_length(btrim(p_title)) not between 1 and 100 or p_starts_at is null then
        raise exception 'Invalid lesson' using errcode = '22023';
    end if;
    select * into v_existing from public.lessons where id = p_id;
    if found then
        if v_existing.classroom_id <> p_classroom_id then raise exception 'Lesson owner required' using errcode = '42501'; end if;
        if v_existing.status <> 'SCHEDULED' then raise exception 'Lesson already started' using errcode = 'P2009'; end if;
        return query update public.lessons set title = btrim(p_title), starts_at = p_starts_at where id = p_id returning *;
    else
        return query insert into public.lessons(id, classroom_id, title, starts_at) values (p_id, p_classroom_id, btrim(p_title), p_starts_at) returning *;
    end if;
end; $$;

create function public.cancel_lesson(p_lesson_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_lesson public.lessons;
begin
    select * into v_lesson from public.lessons where id = p_lesson_id;
    if not found then raise exception 'Lesson unavailable' using errcode = 'P2002'; end if;
    perform private.require_teacher(v_lesson.classroom_id);
    if exists (select 1 from public.attendances where lesson_id = p_lesson_id) then
        raise exception 'Attendance exists; close lesson instead' using errcode = 'P2009';
    end if;
    update private.qr_sessions set is_active = false where lesson_id = p_lesson_id;
    update public.lessons set status = 'CANCELLED' where id = p_lesson_id;
end; $$;

create function public.generate_attendance_qr(p_id uuid, p_lesson_id uuid, p_latitude double precision,
    p_longitude double precision, p_accuracy double precision, p_duration integer, p_radius integer)
returns table (id uuid, lesson_id uuid, token text, expires_at timestamptz, radius_m integer, is_active boolean)
language plpgsql security definer set search_path = '' as $$
declare v_lesson public.lessons; v_class public.classrooms; v_qr private.qr_sessions;
begin
    select * into v_lesson from public.lessons where public.lessons.id = p_lesson_id;
    if not found then raise exception 'Lesson unavailable' using errcode = 'P2002'; end if;
    v_class := private.require_teacher(v_lesson.classroom_id);
    -- Releitura depois do bloqueio impede reabrir uma aula encerrada em outra requisição.
    select * into v_lesson from public.lessons where public.lessons.id = p_lesson_id;
    if not v_class.is_active or v_lesson.status not in ('SCHEDULED','OPEN') then
        raise exception 'Lesson closed' using errcode = 'P2002';
    end if;
    if p_latitude is null or p_latitude not between -90 and 90 or p_longitude is null or p_longitude not between -180 and 180
       or p_accuracy is null or p_accuracy <= 0 or p_accuracy > least(50, p_radius / 2.0)
       or p_duration is null or p_duration not between 30 and 600 or p_radius is null or p_radius not between 20 and 500 then
        raise exception 'Invalid location or QR settings' using errcode = 'P2008';
    end if;
    select * into v_qr from private.qr_sessions q where q.id = p_id;
    if found then
        if v_qr.lesson_id <> p_lesson_id then raise exception 'QR owner required' using errcode = '42501'; end if;
    else
        update private.qr_sessions q set is_active = false where q.lesson_id = p_lesson_id and q.is_active;
        insert into private.qr_sessions(id, lesson_id, token, latitude, longitude, accuracy_m, radius_m, expires_at)
        values (p_id, p_lesson_id, 'presence:v1:' || encode(extensions.gen_random_bytes(32),'hex'),
            p_latitude, p_longitude, p_accuracy, p_radius, clock_timestamp() + make_interval(secs => p_duration)) returning * into v_qr;
        update public.lessons set status = 'OPEN' where public.lessons.id = p_lesson_id;
        insert into public.lesson_roster(lesson_id, student_id)
            select p_lesson_id, m.student_id from public.classroom_members m where m.classroom_id = v_class.id on conflict do nothing;
    end if;
    return query select v_qr.id, v_qr.lesson_id, v_qr.token, v_qr.expires_at, v_qr.radius_m, v_qr.is_active;
end; $$;

create function public.invalidate_attendance_qr(p_lesson_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_class_id uuid;
begin
    select classroom_id into v_class_id from public.lessons where id = p_lesson_id;
    perform private.require_teacher(v_class_id);
    update private.qr_sessions set is_active = false where lesson_id = p_lesson_id;
end; $$;

create function public.close_lesson(p_lesson_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_lesson public.lessons;
begin
    select * into v_lesson from public.lessons where id = p_lesson_id;
    perform private.require_teacher(v_lesson.classroom_id);
    if v_lesson.status = 'SCHEDULED' then
        insert into public.lesson_roster(lesson_id, student_id)
            select p_lesson_id, student_id from public.classroom_members where classroom_id = v_lesson.classroom_id on conflict do nothing;
    end if;
    update private.qr_sessions set is_active = false where lesson_id = p_lesson_id;
    update public.lessons set status = 'CLOSED' where id = p_lesson_id and status in ('SCHEDULED','OPEN');
end; $$;

create function public.record_attendance(p_classroom_id uuid, p_token text, p_latitude double precision,
    p_longitude double precision, p_accuracy double precision)
returns table (lesson_id uuid, already_recorded boolean, distance_m double precision)
language plpgsql security definer set search_path = '' as $$
declare v_user uuid := auth.uid(); v_qr private.qr_sessions; v_lesson public.lessons;
    v_distance double precision; v_class public.classrooms; v_existing public.attendances;
begin
    if v_user is null or not exists (select 1 from public.profiles where id = v_user and role = 'STUDENT') then
        raise exception 'Student required' using errcode = '42501';
    end if;
    select * into v_class from public.classrooms where id = p_classroom_id for update;
    if not found or not exists (select 1 from public.classroom_members where classroom_id = p_classroom_id and student_id = v_user) then
        raise exception 'Membership required' using errcode = 'P2003';
    end if;
    select * into v_qr from private.qr_sessions where token = p_token;
    if not found then raise exception 'QR unavailable' using errcode = 'P2001'; end if;
    select * into v_lesson from public.lessons where id = v_qr.lesson_id;
    if v_lesson.classroom_id <> p_classroom_id then raise exception 'QR belongs to another classroom' using errcode = 'P2003'; end if;
    -- Repetir uma requisição confirmada devolve o mesmo resultado, sem duplicar.
    select * into v_existing from public.attendances a where a.lesson_id = v_lesson.id and a.student_id = v_user;
    if found then return query select v_lesson.id, true, v_existing.distance_m; return; end if;
    if not v_class.is_active or v_lesson.status <> 'OPEN' or not v_qr.is_active or v_qr.expires_at <= clock_timestamp() then
        raise exception 'QR expired or invalidated' using errcode = 'P2001';
    end if;
    if p_latitude is null or p_latitude not between -90 and 90 or p_longitude is null or p_longitude not between -180 and 180
       or p_accuracy is null or p_accuracy <= 0 or p_accuracy > least(50, v_qr.radius_m / 2.0) then
        raise exception 'Location accuracy insufficient' using errcode = 'P2008';
    end if;
    -- Haversine em metros; posições do aluno não são armazenadas.
    v_distance := 6371000 * 2 * asin(sqrt(least(1.0, greatest(0.0,
        power(sin(radians(p_latitude - v_qr.latitude) / 2), 2)
        + cos(radians(v_qr.latitude)) * cos(radians(p_latitude)) * power(sin(radians(p_longitude - v_qr.longitude) / 2), 2)))));
    if v_distance > v_qr.radius_m then raise exception 'Outside attendance radius' using errcode = 'P2004'; end if;
    insert into public.lesson_roster(lesson_id, student_id) values (v_lesson.id, v_user) on conflict do nothing;
    insert into public.attendances(lesson_id, student_id, distance_m, accuracy_m) values (v_lesson.id, v_user, v_distance, p_accuracy);
    return query select v_lesson.id, false, v_distance;
end; $$;

create function public.list_lesson_attendance(p_lesson_id uuid)
returns table (student_id uuid, name text, recorded_at timestamptz, distance_m double precision)
language plpgsql security definer set search_path = '' as $$
declare v_class_id uuid;
begin
    select classroom_id into v_class_id from public.lessons where id = p_lesson_id;
    perform private.require_teacher(v_class_id);
    return query select r.student_id, p.name, a.recorded_at, a.distance_m
        from public.lesson_roster r join public.profiles p on p.id = r.student_id
        left join public.attendances a on a.lesson_id = r.lesson_id and a.student_id = r.student_id
        where r.lesson_id = p_lesson_id order by p.name, r.student_id;
end; $$;

create function public.student_attendance_history(p_classroom_id uuid)
returns table (lesson_id uuid, title text, starts_at timestamptz, lesson_status text, attendance_status text, recorded_at timestamptz)
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from public.profiles where id = auth.uid() and role = 'STUDENT')
       or not private.can_read_classroom(p_classroom_id) then raise exception 'Student membership required' using errcode = '42501'; end if;
    return query select l.id, l.title, l.starts_at, l.status,
        case when a.student_id is not null then 'PRESENT' when l.status = 'CLOSED' then 'ABSENT' else 'PENDING' end,
        a.recorded_at from public.lessons l
        left join public.attendances a on a.lesson_id = l.id and a.student_id = auth.uid()
        where l.classroom_id = p_classroom_id and l.status <> 'CANCELLED'
          and (l.status = 'SCHEDULED' or exists (select 1 from public.lesson_roster r where r.lesson_id = l.id and r.student_id = auth.uid()))
        order by l.starts_at desc, l.id;
end; $$;

create function public.invite_student(p_classroom_id uuid, p_email text)
returns void language plpgsql security definer set search_path = '' as $$
declare v_class public.classrooms; v_student uuid; v_invite uuid;
begin
    v_class := private.require_teacher(p_classroom_id);
    if not v_class.is_active then raise exception 'Classroom closed' using errcode = 'P2002'; end if;
    select p.id into v_student from auth.users u join public.profiles p on p.id = u.id
        where lower(u.email) = lower(btrim(p_email)) and p.role = 'STUDENT';
    if not found then raise exception 'Student account not found' using errcode = 'P2005'; end if;
    if exists (select 1 from public.classroom_members where classroom_id = p_classroom_id and student_id = v_student) then
        raise exception 'Already a member' using errcode = 'P2006';
    end if;
    insert into public.classroom_invitations(classroom_id, student_id) values (p_classroom_id, v_student)
        on conflict (classroom_id, student_id) where status = 'PENDING' do nothing returning id into v_invite;
    if v_invite is null then raise exception 'Invitation already pending' using errcode = 'P2006'; end if;
    insert into public.notifications(user_id, message) values (v_student, 'Você recebeu um convite para a turma ' || v_class.name || '. Abra Convites para responder.');
end; $$;

create function public.list_my_invitations()
returns table (id uuid, classroom_id uuid, classroom_name text, teacher_name text, status text, created_at timestamptz)
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'STUDENT') then raise exception 'Student required' using errcode = '42501'; end if;
    return query select i.id, i.classroom_id, c.name, p.name, i.status, i.created_at
        from public.classroom_invitations i join public.classrooms c on c.id = i.classroom_id join public.profiles p on p.id = c.teacher_id
        where i.student_id = auth.uid() order by i.created_at desc;
end; $$;

create function public.respond_invitation(p_invitation_id uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare v_invite public.classroom_invitations; v_class public.classrooms; v_name text;
begin
    select * into v_invite from public.classroom_invitations where id = p_invitation_id and student_id = auth.uid();
    if not found or not exists (select 1 from public.profiles where id = auth.uid() and role = 'STUDENT') then
        raise exception 'Invitation recipient required' using errcode = '42501';
    end if;
    select * into v_class from public.classrooms where id = v_invite.classroom_id for update;
    select * into v_invite from public.classroom_invitations where id = p_invitation_id;
    if v_invite.status = (case when p_accept then 'ACCEPTED' else 'REFUSED' end) then return; end if;
    if v_invite.status <> 'PENDING' or not v_class.is_active or p_accept is null then
        raise exception 'Invitation unavailable' using errcode = 'P2007';
    end if;
    if p_accept then
        insert into public.classroom_members(classroom_id, student_id) values (v_class.id, auth.uid()) on conflict do nothing;
    end if;
    update public.classroom_invitations set status = case when p_accept then 'ACCEPTED' else 'REFUSED' end, responded_at = now() where id = p_invitation_id;
    select name into v_name from public.profiles where id = auth.uid();
    insert into public.notifications(user_id, message) values (v_class.teacher_id,
        v_name || case when p_accept then ' aceitou' else ' recusou' end || ' o convite para ' || v_class.name || '.');
end; $$;

create function public.list_my_notifications()
returns setof public.notifications language sql security definer set search_path = '' as $$
    select * from public.notifications where user_id = auth.uid() order by created_at desc;
$$;
create function public.mark_notification_read(p_id uuid)
returns void language sql security definer set search_path = '' as $$
    update public.notifications set read_at = coalesce(read_at, now()) where id = p_id and user_id = auth.uid();
$$;

-- Funções novas não ficam executáveis por PUBLIC/anon. Não altera grants das migrações anteriores.
do $$
declare v_function text;
begin
    for v_function in select p.oid::regprocedure::text from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'public' and p.proname = any(array['update_classroom','close_classroom','remove_classroom_participant',
        'list_lessons','save_lesson','cancel_lesson','generate_attendance_qr','invalidate_attendance_qr','close_lesson',
        'record_attendance','list_lesson_attendance','student_attendance_history','invite_student','list_my_invitations',
        'respond_invitation','list_my_notifications','mark_notification_read'])
    loop
        execute 'revoke all on function ' || v_function || ' from public, anon, authenticated';
        execute 'grant execute on function ' || v_function || ' to authenticated';
    end loop;
end; $$;
notify pgrst, 'reload schema';
commit;
