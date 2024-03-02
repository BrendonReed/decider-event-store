
-- DB w:db = postgresql://postgres:password@localhost:5402/postgres

select now();
select * from 

SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_type = 'BASE TABLE';

select now();

delete from event_log;

select * from counter_state;
delete from counter_state where id is null;

select * from event_log
order by id;

delete from counter_state;


SELECT pg_notify('event_updated', 'time: ' || now());
NOTIFY foo_channel, 'This is the payload yo!';

select * from processed_command order by command_id desc limit 10;

explain analyze
select *
from command_log
left join processed_command on processed_command.command_id = command_log.id
where 1=1
and processed_command.command_id is null
order by command_log.id desc

analyze command_log
analyze processed_command
explain analyze

select *
from command_log
where not exists (
    select * 
    from processed_command 
    where command_id = command_log.id)
order by command_log.id

select command_log.id, count(*)
from command_log
left join processed_command on processed_command.command_id = command_log.id
where 1=1
group by 1 having count(*) > 1

select *
from command_log
left join processed_command on processed_command.command_id = command_log.id
where 1=1
and id = 6884
order by command_log.id desc

select count(*) from command_log;
select count(*) from processed_command;
select count(*) from event_log;

select * from command_log order by id;
select * from processed_command order by command_id;
select * from event_log order by id;
select * from counter_state;
select * from counter_checkpoint;

select now()

select min(time_to_process), max(time_to_process), avg(time_to_process)
from (
select c.id, c.transaction_time command_time, pc.transaction_time processed_time, e.transaction_time event_time
,extract (millisecond from (pc.transaction_time - c.transaction_time)) time_to_process
,((pc.transaction_time - c.transaction_time)) time_to_process2
from command_log c
join processed_command pc on c.id = pc.command_id
join event_log e on e.id = pc.event_log_id
) processing_times;

select min(transaction_time), max(transaction_time), max(transaction_time) - min(transaction_time) d, count(*), count(*) / extract (second from (max(transaction_time) - min(transaction_time)))
from command_log c;

truncate event_log;
truncate processed_command;
truncate command_log;

--decider.event.store.Decider$Increment
INSERT INTO command_log (request_id, command_type, command)
SELECT
    uuid_generate_v4(),
    'domain.CounterDecider$Increment',
    ('{"amount": ' || generate_series || ', "streamId": "3BE87B37-B538-40BC-A53C-24A630BFFA2A", "tenantId": 1 }')::jsonb
FROM generate_series(1, 10);

select uuid_generate_v4() command_id, generate_series id
FROM generate_series(11, 20);
SELECT
    '4498a039-ce94-49b2-aff9-3ca12a8623d5',
    now(),
    'decider.event.store.CounterDecider$Increment',
    ('{"amount": ' || '1' || '}')::jsonb id

SELECT event_log.*
FROM event_log
WHERE event_log.id > (SELECT event_log_id FROM counter_checkpoint LIMIT 1)
order by event_log.id
