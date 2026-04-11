CREATE TABLE courses
(
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    gitlab_group_id  INTEGER,
    created_by       BIGINT       NOT NULL,
    academic_year    INTEGER      NOT NULL,
    semester         INTEGER      NOT NULL,
    start_date       DATE,
    end_date         DATE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_courses_teachers FOREIGN KEY (created_by) REFERENCES teachers (id)
);

CREATE INDEX idx_courses_is_active   ON courses (is_active);
CREATE INDEX idx_courses_created_by  ON courses (created_by);

CREATE TABLE course_enrollments
(
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT       NOT NULL,
    student_id  BIGINT       NOT NULL,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    final_grade INTEGER,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_course_enrollments_courses  FOREIGN KEY (course_id)  REFERENCES courses (id),
    CONSTRAINT fk_course_enrollments_students FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT uq_course_enrollments_course_student UNIQUE (course_id, student_id)
);

CREATE INDEX idx_course_enrollments_course_id   ON course_enrollments (course_id);
CREATE INDEX idx_course_enrollments_student_id  ON course_enrollments (student_id);

CREATE TABLE course_teachers
(
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT      NOT NULL,
    teacher_id BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'LECTURER',
    CONSTRAINT fk_course_teachers_courses  FOREIGN KEY (course_id)  REFERENCES courses (id),
    CONSTRAINT fk_course_teachers_teachers FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT uq_course_teachers_course_teacher UNIQUE (course_id, teacher_id)
);

CREATE INDEX idx_course_teachers_course_id  ON course_teachers (course_id);
CREATE INDEX idx_course_teachers_teacher_id ON course_teachers (teacher_id);

CREATE TABLE assignments
(
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    max_score   INTEGER      NOT NULL DEFAULT 100,
    deadline    TIMESTAMP WITH TIME ZONE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_assignments_courses  FOREIGN KEY (course_id)  REFERENCES courses (id),
    CONSTRAINT fk_assignments_teachers FOREIGN KEY (created_by) REFERENCES teachers (id)
);

CREATE INDEX idx_assignments_course_id  ON assignments (course_id);
CREATE INDEX idx_assignments_is_active  ON assignments (is_active);

CREATE TABLE programming_tasks
(
    id                       BIGSERIAL PRIMARY KEY,
    assignment_id            BIGINT       NOT NULL,
    language                 VARCHAR(50)  NOT NULL,
    gitlab_project_template  VARCHAR(500),
    ci_config_template       TEXT,
    CONSTRAINT fk_programming_tasks_assignments FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT uq_programming_tasks_assignment_id UNIQUE (assignment_id)
);

CREATE TABLE file_upload_tasks
(
    id                   BIGSERIAL PRIMARY KEY,
    assignment_id        BIGINT   NOT NULL,
    allowed_extensions   TEXT[],
    max_file_size        INTEGER,
    allowed_file_count   INTEGER  NOT NULL DEFAULT 1,
    CONSTRAINT fk_file_upload_tasks_assignments FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT uq_file_upload_tasks_assignment_id UNIQUE (assignment_id)
);
