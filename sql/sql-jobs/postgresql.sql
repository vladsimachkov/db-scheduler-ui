create table sql_job_connections (
    id              text                     not null primary key,
    name            text                     not null,
    schema_id       text,
    dialect         text                     not null,
    jdbc_url        text                     not null,
    driver_class    text,
    username        text,
    password        text,
    properties_json text,
    created_time    timestamp with time zone not null,
    updated_time    timestamp with time zone not null
);

create table sql_job_executions (
    id                  bigint                     not null primary key,
    job_id              text                       not null,
    task_instance       text                       not null,
    connection_id       text                       not null,
    dialect             text,
    script_text         text,
    time_started        timestamp with time zone   not null,
    time_finished       timestamp with time zone   not null,
    succeeded           BOOLEAN                    not null,
    statements_executed INT                        not null,
    rows_affected       bigint                     not null,
    rows_capped         BOOLEAN                    not null,
    duration_ms         bigint                     not null,
    error_class         text,
    error_message       text,
    triggered_by        text                       not null
);

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

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
