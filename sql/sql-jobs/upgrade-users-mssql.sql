-- Upgrade for existing installs: adds the users/schemas tables and the
-- sql_job_connections.schema_id column introduced with the users feature.
ALTER TABLE sql_job_connections ADD schema_id VARCHAR(50) NULL;

CREATE TABLE sql_users (
    id            VARCHAR(50)     NOT NULL PRIMARY KEY,
    username      NVARCHAR(100)   NOT NULL,
    password_hash NVARCHAR(500)   NOT NULL,
    admin         BIT             NOT NULL,
    enabled       BIT             NOT NULL,
    created_time  DATETIMEOFFSET  NOT NULL,
    updated_time  DATETIMEOFFSET  NOT NULL
);

CREATE TABLE sql_schemas (
    id           VARCHAR(50)      NOT NULL PRIMARY KEY,
    name         NVARCHAR(100)    NOT NULL,
    description  NVARCHAR(500),
    created_time DATETIMEOFFSET   NOT NULL,
    updated_time DATETIMEOFFSET   NOT NULL
);

CREATE TABLE sql_schema_grants (
    schema_id VARCHAR(50) NOT NULL,
    user_id   VARCHAR(50) NOT NULL,
    PRIMARY KEY (schema_id, user_id)
);

CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
