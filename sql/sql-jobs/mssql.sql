CREATE TABLE sql_job_connections (
    id              VARCHAR(50)      NOT NULL PRIMARY KEY,
    name            NVARCHAR(200)    NOT NULL,
    dialect         VARCHAR(20)      NOT NULL,
    jdbc_url        NVARCHAR(2000)   NOT NULL,
    driver_class    NVARCHAR(300),
    username        NVARCHAR(300),
    password        NVARCHAR(1000),
    properties_json NVARCHAR(MAX),
    created_time    DATETIMEOFFSET   NOT NULL,
    updated_time    DATETIMEOFFSET   NOT NULL
);

CREATE TABLE sql_job_executions (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    job_id              VARCHAR(50)     NOT NULL,
    task_instance       VARCHAR(250)    NOT NULL,
    connection_id       VARCHAR(50)     NOT NULL,
    dialect             VARCHAR(20),
    script_text         NVARCHAR(MAX),
    time_started        DATETIMEOFFSET  NOT NULL,
    time_finished       DATETIMEOFFSET  NOT NULL,
    succeeded           BIT             NOT NULL,
    statements_executed INT             NOT NULL,
    rows_affected       BIGINT          NOT NULL,
    rows_capped         BIT             NOT NULL,
    duration_ms         BIGINT          NOT NULL,
    error_class         NVARCHAR(1000),
    error_message       NVARCHAR(MAX),
    triggered_by        VARCHAR(20)     NOT NULL
);

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
