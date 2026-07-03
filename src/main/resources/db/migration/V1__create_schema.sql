CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);


CREATE TABLE repositories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    github_url VARCHAR(500) NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    indexed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE code_entities (
    id BIGSERIAL PRIMARY KEY,
    repo_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    vector_doc_id VARCHAR(36),
    entity_type VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    start_line INT,
    end_line INT,
    source_code TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE dependencies (
    from_entity_id BIGINT NOT NULL REFERENCES code_entities(id) ON DELETE CASCADE,
    to_entity_id BIGINT NOT NULL REFERENCES code_entities(id) ON DELETE CASCADE,
    dependency_type VARCHAR(50),
    PRIMARY KEY (from_entity_id, to_entity_id)
);

CREATE INDEX idx_deps_from ON dependencies(from_entity_id);