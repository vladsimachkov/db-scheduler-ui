-- Upgrade for existing installs: adds the users/schemas tables and the
-- sql_job_connections.schema_id column introduced with the users feature.
ALTER TABLE sql_job_connections ADD COLUMN schema_id text;

create table sql_users (
    id            text                       not null primary key,
    username      text                       not null,
    password_hash text                       not null,
    admin         BOOLEAN                    not null,
    enabled       BOOLEAN                    not null,
    created_time  timestamp with time zone   not null,
    updated_time  timestamp with time zone   not null
);

create table sql_schemas (
    id           text                        not null primary key,
    name         text                        not null,
    description  text,
    created_time timestamp with time zone    not null,
    updated_time timestamp with time zone    not null
);

create table sql_schema_grants (
    schema_id text not null,
    user_id   text not null,
    primary key (schema_id, user_id)
);

CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
