CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table event_persistance (
  event_id bigint GENERATED BY DEFAULT as identity primary key,
  stream_id uuid not null,
  -- seq_no bigint not null,
  transaction_time timestamp not null,
  event_type text,
  payload jsonb not null
  -- correlation_id uuid not null
);

create table counter_checkpoint (
  event_id bigint
);
insert into counter_checkpoint (event_id) values (0);

create table counter_state (
  id uuid,
  total_count bigint
);

insert into counter_state  
values ('4498a039-ce94-49b2-aff9-3ca12a8623d5', 0);

create function send_notification() returns trigger as 
$$
begin
  perform pg_notify('event_updated', row_to_json(new)::TEXT);
  return null;
end;
$$ lANGUAGE plpgsql;

create trigger notify_table_updated 
  after insert or update on event_persistance
  for each row
  execute function send_notification();