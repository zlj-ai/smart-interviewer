-- 知识库表（Slice 4-1）
CREATE TABLE knowledge_bases (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_bases_user ON knowledge_bases (user_id);

-- 知识库文档表：extracted_text 存解析后的全文，向量化任务从它确定性重切，
-- 保证重试时切片结果一致
CREATE TABLE kb_documents (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kb_id          BIGINT       NOT NULL REFERENCES knowledge_bases (id),
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    filename       VARCHAR(256) NOT NULL,
    size_bytes     BIGINT       NOT NULL DEFAULT 0,
    char_count     INT          NOT NULL DEFAULT 0,
    chunk_count    INT          NOT NULL DEFAULT 0,
    extracted_text TEXT,
    status         VARCHAR(24)  NOT NULL DEFAULT 'UPLOADED',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_kb_documents_kb ON kb_documents (kb_id);

-- pgvector 扩展；vector_store 表结构由 Spring AI 启动时初始化
CREATE EXTENSION IF NOT EXISTS vector;
