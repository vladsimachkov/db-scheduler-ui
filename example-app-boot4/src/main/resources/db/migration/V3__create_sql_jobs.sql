create table sql_job_connections
(
    id              VARCHAR(50)                    not null primary key,
    name            text                           not null,
    dialect         text                           not null,
    jdbc_url        text                           not null,
    driver_class    text,
    username        text,
    password        text,
    properties_json text,
    created_time    timestamp with time zone       not null,
    updated_time    timestamp with time zone       not null
);

create table sql_job_executions
(
    id                  bigint                   not null primary key,
    job_id              varchar(50)              not null,
    task_instance       text                     not null,
    connection_id       varchar(50)              not null,
    dialect             text,
    script_text         text,
    time_started        timestamp with time zone not null,
    time_finished       timestamp with time zone not null,
    succeeded           BOOLEAN                  not null,
    statements_executed INT                      not null,
    rows_affected       bigint                   not null,
    rows_capped         BOOLEAN                  not null,
    duration_ms         bigint                   not null,
    error_class         text,
    error_message       text,
    triggered_by        text                     not null
);

CREATE INDEX sje_job_started_idx ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx        ON sql_job_connections (name);
