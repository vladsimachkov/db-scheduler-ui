create table sql_job_connections (
    id              VARCHAR(50)      not null primary key,
    name            VARCHAR(200)     not null,
    dialect         VARCHAR(20)      not null,
    jdbc_url        VARCHAR(2000)    not null,
    driver_class    VARCHAR(300),
    username        VARCHAR(300),
    password        VARCHAR(1000),
    properties_json CLOB,
    created_time    timestamp with time zone not null,
    updated_time    timestamp with time zone not null
);

create table sql_job_executions (
    id                  BIGINT                   not null primary key,
    job_id              VARCHAR(50)              not null,
    task_instance       VARCHAR(250)             not null,
    connection_id       VARCHAR(50)              not null,
    dialect             VARCHAR(20),
    script_text         CLOB,
    time_started        timestamp with time zone not null,
    time_finished       timestamp with time zone not null,
    succeeded           BOOLEAN                  not null,
    statements_executed INT                      not null,
    rows_affected       BIGINT                   not null,
    rows_capped         BOOLEAN                  not null,
    duration_ms         BIGINT                   not null,
    error_class         VARCHAR(1000),
    error_message       CLOB,
    triggered_by        VARCHAR(20)              not null
);

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
