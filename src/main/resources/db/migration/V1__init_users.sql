-- Base user table shared by all roles
-- role column retained here for JWT auth (not in dbdiagram but required for MVP security)
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    date_of_birth DATE,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_is_active ON users (is_active);

-- Teacher-specific profile data
CREATE TABLE teachers
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    department      VARCHAR(100),
    academic_degree VARCHAR(100),
    gitlab_id       INTEGER,
    gitlab_token    VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_teachers_user_id  UNIQUE (user_id),
    CONSTRAINT uq_teachers_gitlab_id UNIQUE (gitlab_id),
    CONSTRAINT fk_teachers_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_teachers_user_id    ON teachers (user_id);
CREATE INDEX idx_teachers_department ON teachers (department);

-- Student-specific profile data
CREATE TABLE students
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    gitlab_id    INTEGER,
    gitlab_token VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_students_user_id   UNIQUE (user_id),
    CONSTRAINT uq_students_gitlab_id UNIQUE (gitlab_id),
    CONSTRAINT fk_students_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_students_user_id ON students (user_id);

-- Admin-specific profile data
CREATE TABLE admins
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_admins_user_id UNIQUE (user_id),
    CONSTRAINT fk_admins_users FOREIGN KEY (user_id) REFERENCES users (id)
);
