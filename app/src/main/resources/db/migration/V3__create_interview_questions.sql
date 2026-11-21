-- 面试题目表（Slice 3-2）
-- user_id 冗余归属字段，隔离查询免 join；重新生成时按 session_id 批量删除后重建
CREATE TABLE interview_questions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id       BIGINT      NOT NULL REFERENCES interview_sessions (id),
    user_id          BIGINT      NOT NULL REFERENCES users (id),
    seq              INT         NOT NULL,
    question_text    TEXT        NOT NULL,
    reference_answer TEXT,
    skill_tag        VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_interview_questions_session ON interview_questions (session_id, seq);
