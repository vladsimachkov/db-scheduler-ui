-- Upgrade for existing installs: adds the users/schemas tables and the
-- sql_job_connections.schema_id column introduced with the users feature.
ALTER TABLE sql_job_connections ADD COLUMN schema_id VARCHAR(50);

create table sql_users (
    id            VARCHAR(50)      not null primary key,
    username      VARCHAR(100)     not null,
    password_hash VARCHAR(500)     not null,
    admin         BOOLEAN          not null,
    enabled       BOOLEAN          not null,
    created_time  timestamp with time zone not null,
    updated_time  timestamp with time zone not null
);

create table sql_schemas (
    id           VARCHAR(50)       not null primary key,
    name         VARCHAR(100)      not null,
    description  VARCHAR(500),
    created_time timestamp with time zone not null,
    updated_time timestamp with time zone not null
);

create table sql_schema_grants (
    schema_id VARCHAR(50) not null,
    user_id   VARCHAR(50) not null,
    primary key (schema_id, user_id)
);

CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
