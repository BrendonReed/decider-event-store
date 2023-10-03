
-- DB w:db = postgresql://postgres:password@localhost:5402/postgres

select now();

select * from event_persistance;

SELECT pg_notify('foo_channel', 'yoyo');
NOTIFY foo_channel, 'This is the payload yo!';
