-- Corrige o nome da função pgcrypto sem modificar uma migração anterior.
begin;
create or replace function public.update_classroom(p_classroom_id uuid, p_name text, p_subject text, p_semester text, p_password text default null)
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
        update private.classroom_credentials set password_hash = extensions.crypt(p_password, extensions.gen_salt('bf',10)) where classroom_id = p_classroom_id;
    end if;
    return query update public.classrooms set name = btrim(p_name), subject = btrim(p_subject), semester = btrim(p_semester)
        where id = p_classroom_id returning *;
end; $$;
revoke all on function public.update_classroom(uuid,text,text,text,text) from public, anon, authenticated;
grant execute on function public.update_classroom(uuid,text,text,text,text) to authenticated;
notify pgrst, 'reload schema';
commit;
