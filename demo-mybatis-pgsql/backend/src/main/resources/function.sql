create function public.mask_email(p_email character varying) returns character varying
    immutable
    language sql
as
$$
SELECT substring(p_email from 1 for 1) || '***@' || split_part(p_email, '@', 2)
           $$;

alter function public.mask_email(varchar) owner to ujutech;

create function public.find_contacts_by_name(p_name character varying)
    returns TABLE(id bigint, user_id bigint, contact_name character varying, contact_phone character varying)
    language sql
    as
$$
SELECT id, user_id, contact_name, contact_phone
FROM emergency_contacts
WHERE contact_name = p_name
    $$;

alter function public.find_contacts_by_name(varchar) owner to ujutech;

create procedure public.update_contact_phone(IN p_id bigint, IN p_phone character varying)
    language plpgsql
as
$$
BEGIN
UPDATE emergency_contacts SET contact_phone = p_phone WHERE id = p_id;
END;
$$;

alter procedure public.update_contact_phone(bigint, varchar) owner to ujutech;

