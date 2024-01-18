CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table event_log (
  id bigint GENERATED BY DEFAULT as identity primary key,
  tenant_id bigint,
  stream_id uuid, 
  -- seq_no bigint not null,
  transaction_time timestamp default now(),
  event_type text,
  payload jsonb not null
  -- correlation_id uuid not null
);

create table command_log (
  id bigint GENERATED BY DEFAULT as identity primary key,
  request_id uuid not null,
  tenant_id bigint,
  stream_id uuid, 
  transaction_time timestamp default now(),
  command_type text,
  command jsonb not null
);

create table processed_command (
  command_id bigint primary key,
  event_log_id bigint, -- most recent event after processing
  disposition text
  , transaction_time timestamp default now()
  , CHECK (disposition != 'force failure')
  --, CHECK (command_id < 5)
);

create table counter_checkpoint (
  id bigint primary key,
  event_log_id bigint
);
insert into counter_checkpoint (id, event_log_id) values (1, 0);

create table counter_state (
  id uuid,
  total_count bigint
);

create function send_event_notification() returns trigger as 
$$
begin
  perform pg_notify('event_updated', row_to_json(new)::TEXT);
  return null;
end;
$$ lANGUAGE plpgsql;

create trigger notify_table_updated 
  after insert or update on event_log
  for each row
  execute function send_event_notification();

create function send_command_notification() returns trigger as
$$
begin
  perform pg_notify('command_logged', row_to_json(new)::TEXT);
  return null;
end;
$$ lANGUAGE plpgsql;

create trigger notify_table_updated 
  after insert or update on command_log
  for each row
  execute function send_command_notification();


