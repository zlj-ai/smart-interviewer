-- 面试会话表（Slice 3-1）
-- user_id 实现数据隔离；request_id 为客户端幂等键，唯一索引是三级短路的最后兜底
CREATE TABLE interview_sessions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    request_id  VARCHAR(64)  NOT NULL,
    direction   VARCHAR(32)  NOT NULL,
    difficulty  VARCHAR(16)  NOT NULL,
    status      VARCHAR(24)  NOT NULL DEFAULT 'CREATED',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_interview_sessions_request_id ON interview_sessions (request_id);
CREATE INDEX idx_interview_sessions_user_id ON interview_sessions (user_id);
