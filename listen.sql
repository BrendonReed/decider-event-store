
CREATE TABLE notifications (
    id SERIAL PRIMARY KEY,
    channel TEXT,
    payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION process_notification() RETURNS TRIGGER AS $$
BEGIN
    -- Insert the received notification into the notifications table
    INSERT INTO notifications (channel, payload) VALUES (TG_RELNAME, TG_ARGV[0]);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER notify_trigger AFTER INSERT ON notify
FOR EACH ROW EXECUTE FUNCTION process_notification();


NOTIFY test_channel, 'Hello from PostgreSQL!';

LISTEN test_channel;


CREATE OR REPLACE FUNCTION handle_notification() RETURNS TRIGGER AS $$
BEGIN
    -- Your logic here to handle the notification
    RAISE NOTICE 'Notification received on channel: %, Payload: %', TG_ARGV[0], TG_ARGV[1];

    -- You can call other stored procedures or execute any other logic here

    RETURN NULL; -- Returning NULL indicates that the trigger function does not modify the data being processed
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER notify_trigger
AFTER NOTIFY
ON test_channel
EXECUTE FUNCTION handle_notification();

