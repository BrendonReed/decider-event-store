CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table events (
  id bigint primary key,
  stream_id uuid not null,
  seq_no bigint not null,
  transaction_time timestamp not null,
  correlation_id uuid not null,
  payload jsonb not null
)
