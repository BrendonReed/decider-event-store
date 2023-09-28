CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table event_persistance (
  id uuid not null primary key,
  transaction_time timestamp not null,
  event_type text,
  payload jsonb not null
);

create table events_later (
  id bigint primary key,
  stream_id uuid not null,
  seq_no bigint not null,
  transaction_time timestamp not null,
  correlation_id uuid not null,
  payload jsonb not null
);

create table sandbox (
  id uuid primary key,
  payload text
)
