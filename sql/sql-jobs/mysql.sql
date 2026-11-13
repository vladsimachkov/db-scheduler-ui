CREATE TABLE sql_job_connections (
    id              VARCHAR(50)     NOT NULL PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    dialect         VARCHAR(20)     NOT NULL,
    jdbc_url        VARCHAR(2000)   NOT NULL,
    driver_class    VARCHAR(300),
    username        VARCHAR(300),
    password        VARCHAR(1000),
    properties_json TEXT,
    created_time    DATETIME(6)     NOT NULL,
    updated_time    DATETIME(6)     NOT NULL
);

CREATE TABLE sql_job_executions (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    job_id              VARCHAR(50)   NOT NULL,
    task_instance       VARCHAR(250)  NOT NULL,
    connection_id       VARCHAR(50)   NOT NULL,
    dialect             VARCHAR(20),
    script_text         MEDIUMTEXT,
    time_started        DATETIME(6)   NOT NULL,
    time_finished       DATETIME(6)   NOT NULL,
    succeeded           BOOLEAN       NOT NULL,
    statements_executed INT           NOT NULL,
    rows_affected       BIGINT        NOT NULL,
    rows_capped         BOOLEAN       NOT NULL,
    duration_ms         BIGINT        NOT NULL,
    error_class         VARCHAR(1000),
    error_message       TEXT,
    triggered_by        VARCHAR(20)   NOT NULL
);

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
