CREATE TYPE kubernetes_worker_jobs_status AS ENUM (
    'STARTING',
    'STARTED',
    'COMPLETED',
    'FAILED',
    'ERROR',
    'STOPPED',
    'NOT_AVAILABLE'
);

CREATE FUNCTION kubernetes_worker_jobs_status_cast(CHARACTER VARYING) RETURNS kubernetes_worker_jobs_status
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: kubernetes_worker_jobs_status $_$;

CREATE CAST (CHARACTER VARYING AS kubernetes_worker_jobs_status) WITH FUNCTION kubernetes_worker_jobs_status_cast(CHARACTER VARYING) AS IMPLICIT;

CREATE TYPE kubernetes_worker_jobs_job_type AS ENUM (
    'WORKFLOW',
    'INTERACTIVE_APPLICATION'
);

CREATE FUNCTION kubernetes_worker_jobs_job_type_cast(CHARACTER VARYING) RETURNS kubernetes_worker_jobs_job_type
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: kubernetes_worker_jobs_job_type $_$;

CREATE CAST (CHARACTER VARYING AS kubernetes_worker_jobs_job_type) WITH FUNCTION kubernetes_worker_jobs_job_type_cast(CHARACTER VARYING) AS IMPLICIT;

CREATE TABLE kubernetes_worker_jobs (
    job_id CHARACTER VARYING(255) NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE,
    int_job_id CHARACTER VARYING(255) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE,
    status kubernetes_worker_jobs_status NOT NULL,
    job_type kubernetes_worker_jobs_job_type,
    group_id BIGINT
);

ALTER TABLE ONLY kubernetes_worker_jobs
    ADD CONSTRAINT kubernetes_worker_jobs_pkey PRIMARY KEY (job_id);