-- SOMENTE para um projeto Supabase novo, antes das migrações.
-- Se profiles e o trigger de cadastro já existem, não execute este arquivo.
begin;
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    name text not null check (char_length(btrim(name)) between 1 and 100),
    role text not null check (role in ('STUDENT','TEACHER')),
    created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;
revoke all on public.profiles from public, anon, authenticated;
grant select on public.profiles to authenticated;
create policy "Users read own profile" on public.profiles for select to authenticated using (id = (select auth.uid()));
create function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id, name, role)
    values (new.id, btrim(new.raw_user_meta_data ->> 'name'), new.raw_user_meta_data ->> 'role');
    return new;
end; $$;
revoke all on function public.handle_new_user() from public, anon, authenticated;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();
notify pgrst, 'reload schema';
commit;
