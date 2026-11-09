CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' CHECK (role IN ('STUDENT', 'CLASS_REP', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_created_at ON users (created_at DESC);

-- Only a SHA-256 of the refresh token is stored; the raw value lives in an HttpOnly cookie.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE courses (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(160) NOT NULL,
    course_code VARCHAR(40),
    description VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_courses_user ON courses (user_id, created_at DESC);

CREATE TABLE class_folders (
    id          UUID PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_name VARCHAR(160) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_class_folders_owner ON class_folders (owner_id);

CREATE TABLE folder_members (
    id         UUID PRIMARY KEY,
    folder_id  UUID        NOT NULL REFERENCES class_folders (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission VARCHAR(20) NOT NULL DEFAULT 'VIEW' CHECK (permission IN ('VIEW', 'MANAGE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (folder_id, user_id)
);
CREATE INDEX idx_folder_members_user ON folder_members (user_id);

-- A document lives either in a personal course or in a shared class folder.
CREATE TABLE documents (
    id            UUID PRIMARY KEY,
    course_id     UUID REFERENCES courses (id) ON DELETE CASCADE,
    folder_id     UUID REFERENCES class_folders (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename      VARCHAR(255) NOT NULL,
    storage_key   VARCHAR(255) NOT NULL UNIQUE,
    file_size     BIGINT       NOT NULL CHECK (file_size >= 0),
    page_count    INT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    error_message VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT documents_single_parent CHECK (num_nonnulls(course_id, folder_id) = 1)
);
CREATE INDEX idx_documents_user ON documents (user_id, created_at DESC);
CREATE INDEX idx_documents_course ON documents (course_id);
CREATE INDEX idx_documents_folder ON documents (folder_id);
CREATE INDEX idx_documents_status ON documents (status);

-- The vector dimension is a Flyway placeholder so it always matches the configured model.
CREATE TABLE chunks (
    id          UUID PRIMARY KEY,
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number INT         NOT NULL CHECK (page_number >= 1),
    chunk_index INT         NOT NULL CHECK (chunk_index >= 0),
    content     TEXT        NOT NULL,
    embedding   VECTOR(${embedding_dimension}) NOT NULL,
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_chunks_document_page ON chunks (document_id, page_number);
CREATE INDEX idx_chunks_tsv ON chunks USING gin (content_tsv);
CREATE INDEX idx_chunks_embedding_hnsw ON chunks USING hnsw (embedding vector_cosine_ops);

-- A chat is always private to its user and scoped to one course or one shared folder.
CREATE TABLE chats (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_id  UUID REFERENCES courses (id) ON DELETE CASCADE,
    folder_id  UUID REFERENCES class_folders (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chats_single_scope CHECK (num_nonnulls(course_id, folder_id) = 1)
);
CREATE INDEX idx_chats_user_updated ON chats (user_id, updated_at DESC);
CREATE INDEX idx_chats_course ON chats (course_id);
CREATE INDEX idx_chats_folder ON chats (folder_id);

CREATE TABLE messages (
    id         UUID PRIMARY KEY,
    chat_id    UUID        NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content    TEXT        NOT NULL,
    sources    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_chat_created ON messages (chat_id, created_at);

-- Quizzes survive deletion of their source material, so the references are SET NULL.
CREATE TABLE quizzes (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    course_id   UUID REFERENCES courses (id) ON DELETE SET NULL,
    title       VARCHAR(200) NOT NULL,
    difficulty  VARCHAR(10)  NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    questions   JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_quizzes_user ON quizzes (user_id, created_at DESC);

CREATE TABLE quiz_attempts (
    id         UUID PRIMARY KEY,
    quiz_id    UUID        NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answers    JSONB       NOT NULL,
    score      INT         NOT NULL CHECK (score >= 0),
    total      INT         NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quiz_attempts_quiz ON quiz_attempts (quiz_id, created_at DESC);
CREATE INDEX idx_quiz_attempts_user ON quiz_attempts (user_id);
