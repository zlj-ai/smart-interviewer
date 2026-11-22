-- 简历驱动出题（Agent 模式）：会话可关联一份简历，
-- 出题时模型通过工具自主查询候选人画像与技能短板，针对性生成题目
ALTER TABLE interview_sessions
    ADD COLUMN resume_id BIGINT REFERENCES resumes (id);
