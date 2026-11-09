-- Edição do nome exibido pelo aplicativo. A senha e o e-mail são alterados
-- pela API do Supabase Auth, sem dar acesso administrativo ao Android.
begin;

create or replace function public.update_profile_name(p_name text)
returns table (id uuid, name text, role text)
language plpgsql security definer set search_path = '' as $$
declare
    v_user_id uuid := auth.uid();
    v_name text := btrim(p_name);
begin
    if v_user_id is null then
        raise exception 'Authenticated account required' using errcode = '42501';
    end if;
    if v_name is null or char_length(v_name) not between 1 and 100 or v_name ~ '^[[:space:]]*$' then
        raise exception 'Invalid profile name' using errcode = '22023';
    end if;
    -- Somente o nome da própria conta pode ser alterado. O perfil aluno/professor
    -- e os identificadores continuam iguais, preservando turmas e presenças.
    return query update public.profiles p set name = v_name
        where p.id = v_user_id returning p.id, p.name, p.role;
    if not found then
        raise exception 'Profile unavailable' using errcode = '42501';
    end if;
end; $$;

revoke all on function public.update_profile_name(text) from public, anon, authenticated;
grant execute on function public.update_profile_name(text) to authenticated;
notify pgrst, 'reload schema';
commit;
