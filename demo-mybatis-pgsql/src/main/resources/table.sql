create table public.users
(
    id         bigserial
        primary key,
    name       varchar(100)            not null,
    email      varchar(255)            not null
        unique,
    created_at timestamp default now() not null
);

alter table public.users
    owner to ujutech;

create table public.user_profiles
(
    id               bigserial
        primary key,
    user_id          bigint                  not null
        unique
        references public.users,
    phone_number     varchar(20),
    birth_date       date,
    address_line1    varchar(200),
    marketing_opt_in boolean   default false not null,
    created_at       timestamp default now() not null
);

alter table public.user_profiles
    owner to ujutech;

create table public.orders
(
    id               bigserial
        primary key,
    user_id          bigint                                              not null
        references public.users,
    customer_name    varchar(100)                                        not null,
    customer_email   varchar(255)                                        not null,
    customer_phone   varchar(20),
    shipping_address varchar(300)                                        not null,
    status           varchar(20)    default 'PENDING'::character varying not null,
    total_amount     numeric(10, 2) default 0                            not null,
    created_at       timestamp      default now()                        not null
);

alter table public.orders
    owner to ujutech;

create table public.emergency_contacts
(
    id            bigserial
        primary key,
    user_id       bigint                not null
        references public.users,
    contact_name  varchar(100)          not null,
    contact_phone varchar(20)           not null,
    relation      varchar(50),
    is_primary    boolean default false not null
);

alter table public.emergency_contacts
    owner to ujutech;

