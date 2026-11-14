CREATE TABLE sql_job_connections (
    id              VARCHAR2(50)                   NOT NULL PRIMARY KEY,
    name            VARCHAR2(200)                  NOT NULL,
    schema_id       VARCHAR2(50),
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

CREATE INDEX sje_job_started_idx     ON sql_job_executions (job_id, time_started);
CREATE INDEX sjc_name_idx            ON sql_job_connections (name);
CREATE INDEX sjc_schema_idx          ON sql_job_connections (schema_id);
CREATE UNIQUE INDEX sql_users_username_idx ON sql_users (username);
CREATE UNIQUE INDEX sql_schemas_name_idx   ON sql_schemas (name);
CREATE INDEX ssg_user_idx            ON sql_schema_grants (user_id);
