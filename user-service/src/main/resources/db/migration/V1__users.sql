CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS users;

CREATE TABLE users.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100),
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login TIMESTAMP
);

CREATE TABLE users.auth_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users.users(id),
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO users.users (username, email, full_name, password_hash, role, status)
VALUES (
           'admin',
           'admin@jio.internal',
           'System Admin',
           '$2a$10$KQy4dhnwb0udnPpOxCVgh.gNg015aBcaO67xmeA59WOmXqvTvjpnC',
           'SUPER_ADMIN',
           'ACTIVE'
       );

