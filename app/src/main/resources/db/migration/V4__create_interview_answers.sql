-- 面试回答表（Slice 3-3）
-- attempt 为 fencing 字段：重新提交回答时递增，评估任务携带提交时的 attempt，
-- 回写时 WHERE attempt=提交值，旧任务的结果自然落空，防止旧评估覆盖新回答
CREATE TABLE interview_answers (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id  BIGINT      NOT NULL REFERENCES interview_questions (id),
    session_id   BIGINT      NOT NULL REFERENCES interview_sessions (id),
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    answer_text  TEXT        NOT NULL,
    score        INT,
    feedback     TEXT,
    strengths    TEXT,
    improvements TEXT,
    status       VARCHAR(24) NOT NULL DEFAULT 'EVALUATING',
    attempt      INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 每道题保留最新一次回答（重新提交覆盖）
CREATE UNIQUE INDEX uk_interview_answers_question ON interview_answers (question_id);
CREATE INDEX idx_interview_answers_session ON interview_answers (session_id);
