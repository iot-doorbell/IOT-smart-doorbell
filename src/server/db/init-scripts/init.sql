CREATE TABLE IF NOT EXISTS recordings
(
    id            SERIAL PRIMARY KEY,
    conference_id VARCHAR(255),
    video_url     TEXT,
    img_url       TEXT,
    start_time    TIMESTAMPTZ NOT NULL,
    end_time      TIMESTAMPTZ NOT NULL,
    status        TEXT        NOT NULL
);

-- status: 'calling', 'missed', 'accepted', 'rejected'