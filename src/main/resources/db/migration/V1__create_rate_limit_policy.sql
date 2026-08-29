CREATE TABLE rate_limit_policy (
    client_id VARCHAR(255) PRIMARY KEY,
    max_requests INTEGER NOT NULL CHECK (max_requests > 0),
    window_duration_millis BIGINT NOT NULL CHECK (window_duration_millis > 0)
);

INSERT INTO rate_limit_policy (client_id, max_requests, window_duration_millis)
VALUES
    ('customerA', 100, 60000),
    ('customerB', 1000, 60000),
    ('customerC', 10, 1000);