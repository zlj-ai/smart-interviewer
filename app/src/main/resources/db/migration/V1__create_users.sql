-- 用户表：系统所有业务数据的归属主体（Slice 1 数据隔离基座）
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_username ON users (username);

-- 邮箱可选但唯一；部分索引允许多个 NULL
CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE email IS NOT NULL;
