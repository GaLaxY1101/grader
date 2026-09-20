ALTER TABLE users
    ADD COLUMN keycloak_id VARCHAR(36);

CREATE UNIQUE INDEX ux_users_keycloak_id ON users(keycloak_id);
