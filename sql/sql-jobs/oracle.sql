CREATE TABLE sql_job_connections (
    id              VARCHAR2(50)                   NOT NULL PRIMARY KEY,
    name            VARCHAR2(200)                  NOT NULL,
    dialect         VARCHAR2(20)                   NOT NULL,
    jdbc_url        VARCHAR2(2000)                 NOT NULL,
    driver_class    VARCHAR2(300),
    username        VARCHAR2(300),
    password        VARCHAR2(1000),
    properties_json CLOB,
    created_time    TIMESTAMP(6) WITH TIME ZONE    NOT NULL,
    updated_time    TIMESTAMP(6) WITH TIME ZONE    NOT NULL
);

CREATE TABLE sql_job_executions (
    id                  NUMBER                        NOT NULL PRIMARY KEY,
    job_id              VARCHAR2(50)                  NOT NULL,
    task_instance       VARCHAR2(250)                 NOT NULL,
    connection_id       VARCHAR2(50)                  NOT NULL,
    dialect             VARCHAR2(20),
    script_text         CLOB,
    time_started        TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    time_finished       TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    succeeded           NUMBER(1, 0)                  NOT NULL,
    statements_executed NUMBER                        NOT NULL,
    rows_affected       NUMBER                        NOT NULL,
    rows_capped         NUMBER(1, 0)                  NOT NULL,
    duration_ms         NUMBER                        NOT NULL,
    error_class         VARCHAR2(1000),
    error_message       CLOB,
    triggered_by        VARCHAR2(20)                  NOT NULL
);

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
