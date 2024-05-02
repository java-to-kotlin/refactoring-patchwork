set database transaction control mvcc;
set database transaction rollback on conflict true;


create sequence if not exists RIDER_IDS start with 1;

create table if not exists RIDER
(
    id   integer generated by default as sequence RIDER_IDS primary key,
    name longvarchar not null
);


create sequence if not exists RACE_IDS start with 1;

create table if not exists RACE
(
    id   integer generated by default as sequence RACE_IDS primary key,
    name longvarchar not null
);


create table if not exists RESULT
(
    race_id  integer       not null references RACE (id) on delete cascade,
    rider_id integer       not null references RIDER (id) on delete cascade,
    distance numeric(8, 4) not null check (distance >= 0),

    primary key (race_id, rider_id)
);