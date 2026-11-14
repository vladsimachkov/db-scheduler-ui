-- Upgrade for existing installs: adds the users/schemas tables and the
-- sql_job_connections.schema_id column introduced with the users feature.
ALTER TABLE sql_job_connections ADD (schema_id VARCHAR2(50));

CREATE TABLE sql_users (
    id            VARCHAR2(50)                    NOT NULL PRIMARY KEY,
    username      VARCHAR2(100)                   NOT NULL,
    password_hash VARCHAR2(500)                   NOT NULL,
    admin         NUMBER(1, 0)                    NOT NULL,
    enabled       NUMBER(1, 0)                    NOT NULL,
    created_time  TIMESTAMP(6) WITH TIME ZONE     NOT NULL,
    updated_time  TIMESTAMP(6) WITH TIME ZONE     NOT NULL
);

CREATE TABLE sql_schemas (
    id           VARCHAR2(50)                     NOT NULL PRIMARY KEY,
    name         VARCHAR2(100)                    NOT NULL,
    description  VARCHAR2(500),
    created_time TIMESTAMP(6) WITH TIME ZONE      NOT NULL,
    updated_time TIMESTAMP(6) WITH TIME ZONE      NOT NULL
);

CREATE TABLE sql_schema_grants (
    schema_id VARCHAR2(50) NOT NULL,
    user_id   VARCHAR2(50) NOT NULL,
    PRIMARY KEY (schema_id, user_id)
);

CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
