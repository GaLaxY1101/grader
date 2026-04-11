CREATE TABLE academic_groups
(
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    faculty          VARCHAR(100),
    speciality       VARCHAR(100),
    year_of_creation INTEGER      NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_academic_groups_code UNIQUE (code)
);

CREATE INDEX idx_academic_groups_is_active ON academic_groups (is_active);

CREATE TABLE group_students
(
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT NOT NULL,
    student_id   BIGINT NOT NULL,
    enrolled_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    graduated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_group_students_academic_groups FOREIGN KEY (group_id) REFERENCES academic_groups (id),
    CONSTRAINT fk_group_students_students FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT uq_group_students_group_student UNIQUE (group_id, student_id)
);

CREATE INDEX idx_group_students_group_id   ON group_students (group_id);
CREATE INDEX idx_group_students_student_id ON group_students (student_id);
