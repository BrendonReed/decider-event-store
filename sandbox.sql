
-- DB w:db = postgresql://postgres:password@localhost:5402/postgres

select now();

delete from event_persistance;

select * from counter_state;

select * from event_persistance
order by event_id;

delete from counter_state;


SELECT pg_notify('event_updated', 'time: ' || now());
NOTIFY foo_channel, 'This is the payload yo!';
