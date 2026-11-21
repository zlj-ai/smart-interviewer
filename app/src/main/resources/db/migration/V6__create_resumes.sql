-- 简历表（Slice 5）
-- attempt 为 fencing 字段：重新提交分析时递增，旧分析任务回写落空
CREATE TABLE resumes (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    filename      VARCHAR(256) NOT NULL,
    object_key    VARCHAR(512) NOT NULL,
    size_bytes    BIGINT       NOT NULL DEFAULT 0,
    char_count    INT          NOT NULL DEFAULT 0,
    parsed_text   TEXT,
    analysis_json TEXT,
    status        VARCHAR(24)  NOT NULL DEFAULT 'UPLOADED',
    attempt       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_user ON resumes (user_id);
