-- Execute depois das migrações anteriores. Não apaga históricos ao ser aplicado.
begin;

-- Uma falta manual precisa ser distinguida de um registro ainda pendente.
alter table public.attendances add column status text not null default 'PRESENT'
    check (status in ('PRESENT', 'ABSENT'));
alter table public.attendances alter column recorded_at drop not null;
alter table public.attendances alter column distance_m drop not null;
alter table public.attendances alter column accuracy_m drop not null;
alter table public.attendances add constraint attendance_status_fields check (
    (status = 'PRESENT' and recorded_at is not null
        and ((distance_m is null and accuracy_m is null) or (distance_m is not null and accuracy_m is not null)))
    or (status = 'ABSENT' and recorded_at is null and distance_m is null and accuracy_m is null)
);
insert into public.lesson_roster(lesson_id, student_id)
    select lesson_id, student_id from public.attendances on conflict do nothing;
alter table public.attendances add constraint attendance_roster_fk
    foreign key (lesson_id, student_id) references public.lesson_roster(lesson_id, student_id) on delete cascade;

-- Cada alteração é preservada, mesmo quando o professor faz outro ajuste depois.
create table public.attendance_adjustments (
    id uuid primary key,
    lesson_id uuid not null,
    student_id uuid not null,
    teacher_id uuid not null references public.profiles(id),
    previous_status text not null check (previous_status in ('PRESENT', 'ABSENT', 'PENDING')),
    new_status text not null check (new_status in ('PRESENT', 'ABSENT')),
    previous_recorded_at timestamptz,
    reason text not null check (char_length(btrim(reason)) between 1 and 250 and reason !~ '^[[:space:]]*$'),
    created_at timestamptz not null default clock_timestamp(),
    foreign key (lesson_id, student_id) references public.lesson_roster(lesson_id, student_id) on delete cascade
);
create index attendance_adjustments_latest_idx on public.attendance_adjustments(lesson_id, student_id, created_at desc, id desc);
alter table public.attendance_adjustments enable row level security;
revoke all on public.attendance_adjustments from public, anon, authenticated;

create function public.leave_classroom(p_classroom_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_user uuid := auth.uid();
begin
    if v_user is null or not exists (select 1 from public.profiles where id = v_user and role = 'STUDENT') then
        raise exception 'Student required' using errcode = '42501';
    end if;
    if p_classroom_id is null then raise exception 'Classroom required' using errcode = '22023'; end if;
    -- Mesmo bloqueio usado pelo QR, convites e alterações do professor.
    perform 1 from public.classrooms where id = p_classroom_id for update;
    if not found then return; end if;
    delete from public.lesson_roster r using public.lessons l
        where r.lesson_id = l.id and l.classroom_id = p_classroom_id and r.student_id = v_user;
    -- A exclusão da lista remove presenças/faltas e ajustes pelas chaves com CASCADE.
    delete from public.classroom_invitations where classroom_id = p_classroom_id and student_id = v_user;
    delete from public.classroom_members where classroom_id = p_classroom_id and student_id = v_user;
end; $$;

-- O retorno agora inclui o estado explícito e o último ajuste, sem expor o histórico de outros alunos.
drop function public.list_lesson_attendance(uuid);
create function public.list_lesson_attendance(p_lesson_id uuid)
returns table (student_id uuid, name text, attendance_status text, recorded_at timestamptz, distance_m double precision,
    adjustment_reason text, adjusted_at timestamptz, adjusted_by_name text)
language plpgsql security definer set search_path = '' as $$
declare v_class_id uuid;
begin
    select classroom_id into v_class_id from public.lessons where id = p_lesson_id;
    perform private.require_teacher(v_class_id);
    return query select r.student_id, p.name,
        coalesce(a.status, case when l.status = 'CLOSED' then 'ABSENT' else 'PENDING' end),
        a.recorded_at, a.distance_m, j.reason, j.created_at, t.name
        from public.lesson_roster r join public.profiles p on p.id = r.student_id
        join public.lessons l on l.id = r.lesson_id
        left join public.attendances a on a.lesson_id = r.lesson_id and a.student_id = r.student_id
        left join lateral (select x.reason, x.created_at, x.teacher_id from public.attendance_adjustments x
            where x.lesson_id = r.lesson_id and x.student_id = r.student_id order by x.created_at desc, x.id desc limit 1) j on true
        left join public.profiles t on t.id = j.teacher_id
        where r.lesson_id = p_lesson_id order by p.name, r.student_id;
end; $$;

drop function public.student_attendance_history(uuid);
create function public.student_attendance_history(p_classroom_id uuid)
returns table (lesson_id uuid, title text, starts_at timestamptz, lesson_status text, attendance_status text,
    recorded_at timestamptz, adjustment_reason text, adjusted_at timestamptz, adjusted_by_name text)
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from public.profiles where id = auth.uid() and role = 'STUDENT')
       or not private.can_read_classroom(p_classroom_id) then raise exception 'Student membership required' using errcode = '42501'; end if;
    return query select l.id, l.title, l.starts_at, l.status,
        coalesce(a.status, case when l.status = 'CLOSED' then 'ABSENT' else 'PENDING' end),
        a.recorded_at, j.reason, j.created_at, t.name from public.lessons l
        left join public.attendances a on a.lesson_id = l.id and a.student_id = auth.uid()
        left join lateral (select x.reason, x.created_at, x.teacher_id from public.attendance_adjustments x
            where x.lesson_id = l.id and x.student_id = auth.uid() order by x.created_at desc, x.id desc limit 1) j on true
        left join public.profiles t on t.id = j.teacher_id
        where l.classroom_id = p_classroom_id and l.status <> 'CANCELLED'
          and (l.status = 'SCHEDULED' or exists (select 1 from public.lesson_roster r where r.lesson_id = l.id and r.student_id = auth.uid()))
        order by l.starts_at desc, l.id;
end; $$;

create function public.adjust_attendance(p_id uuid, p_lesson_id uuid, p_student_id uuid, p_status text, p_reason text)
returns table (student_id uuid, name text, attendance_status text, recorded_at timestamptz, distance_m double precision,
    adjustment_reason text, adjusted_at timestamptz, adjusted_by_name text)
language plpgsql security definer set search_path = '' as $$
declare v_lesson public.lessons; v_existing public.attendances; v_adjustment public.attendance_adjustments;
    v_previous text; v_reason text := btrim(p_reason);
begin
    select * into v_lesson from public.lessons where id = p_lesson_id;
    if not found then raise exception 'Lesson unavailable' using errcode = 'P2002'; end if;
    perform private.require_teacher(v_lesson.classroom_id);
    select * into v_lesson from public.lessons where id = p_lesson_id;
    if not found or v_lesson.status not in ('OPEN', 'CLOSED') then
        raise exception 'Open or closed lesson required' using errcode = 'P2002';
    end if;
    if p_id is null or p_student_id is null or p_status is null or p_status not in ('PRESENT', 'ABSENT')
       or v_reason is null or char_length(v_reason) not between 1 and 250 or v_reason ~ '^[[:space:]]*$' then
        raise exception 'Brief justification and status required' using errcode = '22023';
    end if;
    if not exists (select 1 from public.lesson_roster r where r.lesson_id = p_lesson_id and r.student_id = p_student_id) then
        raise exception 'Student no longer on roster' using errcode = 'P2011';
    end if;
    -- Reenviar a mesma operação após uma falha de rede não duplica o histórico.
    select * into v_adjustment from public.attendance_adjustments where id = p_id;
    if found then
        if v_adjustment.teacher_id <> auth.uid() or v_adjustment.lesson_id <> p_lesson_id or v_adjustment.student_id <> p_student_id
           or v_adjustment.new_status <> p_status or v_adjustment.reason <> v_reason then
            raise exception 'Adjustment request changed' using errcode = '22023';
        end if;
        return query select * from public.list_lesson_attendance(p_lesson_id); return;
    end if;
    select * into v_existing from public.attendances a where a.lesson_id = p_lesson_id and a.student_id = p_student_id;
    v_previous := coalesce(v_existing.status, case when v_lesson.status = 'CLOSED' then 'ABSENT' else 'PENDING' end);
    if v_previous = p_status then raise exception 'Attendance already has this status' using errcode = '22023'; end if;
    insert into public.attendance_adjustments(id, lesson_id, student_id, teacher_id, previous_status, new_status, previous_recorded_at, reason)
        values (p_id, p_lesson_id, p_student_id, auth.uid(), v_previous, p_status, v_existing.recorded_at, v_reason);
    insert into public.attendances as a (lesson_id, student_id, status, recorded_at, distance_m, accuracy_m)
        values (p_lesson_id, p_student_id, p_status, case when p_status = 'PRESENT' then clock_timestamp() end, null, null)
        on conflict on constraint attendances_pkey do update set status = excluded.status, recorded_at = excluded.recorded_at,
            distance_m = null, accuracy_m = null;
    return query select * from public.list_lesson_attendance(p_lesson_id);
end; $$;

create or replace function public.record_attendance(p_classroom_id uuid, p_token text, p_latitude double precision,
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
    select * into v_existing from public.attendances a where a.lesson_id = v_lesson.id and a.student_id = v_user;
    if found then
        if v_existing.status = 'ABSENT' then raise exception 'Absence adjusted by teacher' using errcode = 'P2010'; end if;
        return query select v_lesson.id, true, v_existing.distance_m; return;
    end if;
    if not v_class.is_active or v_lesson.status <> 'OPEN' or not v_qr.is_active or v_qr.expires_at <= clock_timestamp() then
        raise exception 'QR expired or invalidated' using errcode = 'P2001';
    end if;
    if p_latitude is null or p_latitude not between -90 and 90 or p_longitude is null or p_longitude not between -180 and 180
       or p_accuracy is null or p_accuracy <= 0 or p_accuracy > least(50, v_qr.radius_m / 2.0) then
        raise exception 'Location accuracy insufficient' using errcode = 'P2008';
    end if;
    v_distance := 6371000 * 2 * asin(sqrt(least(1.0, greatest(0.0,
        power(sin(radians(p_latitude - v_qr.latitude) / 2), 2)
        + cos(radians(v_qr.latitude)) * cos(radians(p_latitude)) * power(sin(radians(p_longitude - v_qr.longitude) / 2), 2)))));
    if v_distance > v_qr.radius_m then raise exception 'Outside attendance radius' using errcode = 'P2004'; end if;
    insert into public.lesson_roster(lesson_id, student_id) values (v_lesson.id, v_user) on conflict do nothing;
    insert into public.attendances(lesson_id, student_id, distance_m, accuracy_m) values (v_lesson.id, v_user, v_distance, p_accuracy);
    return query select v_lesson.id, false, v_distance;
end; $$;

-- Um convite apagado pela saída não pode ser aceito por uma requisição que já estava aguardando o bloqueio.
create or replace function public.respond_invitation(p_invitation_id uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare v_invite public.classroom_invitations; v_class public.classrooms; v_name text;
begin
    select * into v_invite from public.classroom_invitations where id = p_invitation_id and student_id = auth.uid();
    if not found or not exists (select 1 from public.profiles where id = auth.uid() and role = 'STUDENT') then
        raise exception 'Invitation recipient required' using errcode = '42501';
    end if;
    select * into v_class from public.classrooms where id = v_invite.classroom_id for update;
    if not found then raise exception 'Classroom unavailable' using errcode = 'P2007'; end if;
    select * into v_invite from public.classroom_invitations where id = p_invitation_id and student_id = auth.uid();
    if not found or p_accept is null then raise exception 'Invitation unavailable' using errcode = 'P2007'; end if;
    if v_invite.status = (case when p_accept then 'ACCEPTED' else 'REFUSED' end) then return; end if;
    if v_invite.status <> 'PENDING' or not v_class.is_active then
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

revoke all on function public.leave_classroom(uuid), public.adjust_attendance(uuid, uuid, uuid, text, text),
    public.list_lesson_attendance(uuid), public.student_attendance_history(uuid) from public, anon, authenticated;
grant execute on function public.leave_classroom(uuid), public.adjust_attendance(uuid, uuid, uuid, text, text),
    public.list_lesson_attendance(uuid), public.student_attendance_history(uuid) to authenticated;
notify pgrst, 'reload schema';
commit;
