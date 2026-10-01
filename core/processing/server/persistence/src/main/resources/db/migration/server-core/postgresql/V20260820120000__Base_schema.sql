
CREATE TYPE jobs_status AS ENUM (
    'CREATED',
    'RUNNING',
    'COMPLETED',
    'ERROR',
    'CANCELLED',
    'PENDING',
    'WAITING',
    'CONDITION_WAIT'
);


CREATE TYPE roles AS ENUM (
    'GUEST',
    'USER',
    'EXPERT_USER',
    'CONTENT_AUTHORITY',
    'ADMIN'
);


CREATE TYPE services_licence AS ENUM (
    'OPEN',
    'RESTRICTED'
);

CREATE TYPE services_status AS ENUM (
    'IN_DEVELOPMENT',
    'AVAILABLE',
    'DISABLED'
);


CREATE TYPE services_type AS ENUM (
    'PROCESSOR',
    'BULK_PROCESSOR',
    'APPLICATION',
    'PARALLEL_PROCESSOR'
);


 CREATE TYPE job_step AS ENUM (
    'CREATED',
    'DATA_FETCH',
    'PROCESSING',
    'OUTPUT_LIST'
);

CREATE FUNCTION jobs_status_cast(character varying) RETURNS jobs_status
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: jobs_status $_$;


CREATE FUNCTION roles_cast(character varying) RETURNS roles
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: roles $_$;


CREATE FUNCTION services_licence_cast(character varying) RETURNS services_licence
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: services_licence $_$;


CREATE FUNCTION services_status_cast(character varying) RETURNS services_status
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: services_status $_$;


CREATE FUNCTION services_type_cast(character varying) RETURNS services_type
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: services_type $_$;


CREATE FUNCTION job_step_cast(character varying) RETURNS job_step
    LANGUAGE sql IMMUTABLE
    AS $_$ SELECT ('' || $1) :: job_step $_$;


SET search_path = pg_catalog;


CREATE CAST (character varying AS public.jobs_status) WITH FUNCTION public.jobs_status_cast(character varying) AS IMPLICIT;

CREATE CAST (character varying AS public.roles) WITH FUNCTION public.roles_cast(character varying) AS IMPLICIT;

CREATE CAST (character varying AS public.services_licence) WITH FUNCTION public.services_licence_cast(character varying) AS IMPLICIT;

CREATE CAST (character varying AS public.services_status) WITH FUNCTION public.services_status_cast(character varying) AS IMPLICIT;

CREATE CAST (character varying AS public.services_type) WITH FUNCTION public.services_type_cast(character varying) AS IMPLICIT;

CREATE CAST (character varying AS public.job_step) WITH FUNCTION public.job_step_cast(character varying) AS IMPLICIT;


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;


CREATE TABLE job_configs (
    id bigint NOT NULL,
    inputs text,
    parent bigint,
    owner bigint NOT NULL,
    service bigint NOT NULL,
    systematic_parameter character varying(255),
    label character varying(255)
);

CREATE SEQUENCE job_configs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE job_configs_id_seq OWNED BY job_configs.id;


CREATE TABLE jobs (
    id bigint NOT NULL,
    end_time timestamp without time zone,
    ext_id character varying(255) NOT NULL,
    gui_url character varying(255),
    gui_endpoint character varying(255),
    is_parent boolean DEFAULT false,
    outputs text,
    stage character varying(255),
    start_time timestamp without time zone,
    status jobs_status,
    phase job_step,
    job_config bigint NOT NULL,
    owner bigint NOT NULL,
    parent_job_id bigint,
    queue_position integer,
    cost_quotation text,
    worker_id character varying(255),
    created timestamp with time zone,
    last_updated timestamp with time zone

);

CREATE SEQUENCE jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE jobs_id_seq OWNED BY jobs.id;


CREATE TABLE service_files (
    id bigint NOT NULL,
    service bigint NOT NULL,
    filename character varying(255),
    executable boolean DEFAULT false NOT NULL,
    content text
);

CREATE SEQUENCE service_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE service_files_id_seq OWNED BY service_files.id;


CREATE TABLE services (
    id bigint NOT NULL,
    description character varying(255),
    docker_tag character varying(255),
    licence services_licence NOT NULL,
    name character varying(255) NOT NULL,
    service_descriptor text,
    docker_build_info text,
    required_resources text,
    service_files_uri character varying(255),
    strip_proxy_path boolean DEFAULT true,
    external_uri character varying(255),
    status services_status NOT NULL,
    type services_type NOT NULL,
    port character varying(255),
    owner bigint NOT NULL,
    group_id bigint,
    cwl_url character varying(255),
    cwl_document text
);

CREATE SEQUENCE services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE services_id_seq OWNED BY services.id;


CREATE TABLE services_mounts (
    service_id bigint NOT NULL,
    user_mount_id bigint NOT NULL,
    target_mount_path character varying(255) NOT NULL
);


CREATE TABLE user_mounts (
    id bigint NOT NULL,
    owner bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    mount_path character varying(255) NOT NULL
);


CREATE SEQUENCE user_mounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE user_mounts_id_seq OWNED BY user_mounts.id;


CREATE TABLE users (
    uid bigint NOT NULL,
    mail character varying(255),
    uuid character varying(255),
    name character varying(255) NOT NULL,
    role roles DEFAULT 'GUEST'::roles NOT NULL
);

CREATE SEQUENCE users_uid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE users_uid_seq OWNED BY users.uid;


ALTER TABLE ONLY job_configs ALTER COLUMN id SET DEFAULT nextval('job_configs_id_seq'::regclass);

ALTER TABLE ONLY jobs ALTER COLUMN id SET DEFAULT nextval('jobs_id_seq'::regclass);

ALTER TABLE ONLY service_files ALTER COLUMN id SET DEFAULT nextval('service_files_id_seq'::regclass);

ALTER TABLE ONLY services ALTER COLUMN id SET DEFAULT nextval('services_id_seq'::regclass);

ALTER TABLE ONLY user_mounts ALTER COLUMN id SET DEFAULT nextval('user_mounts_id_seq'::regclass);

ALTER TABLE ONLY users ALTER COLUMN uid SET DEFAULT nextval('users_uid_seq'::regclass);


ALTER TABLE ONLY job_configs
    ADD CONSTRAINT job_configs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY service_files
    ADD CONSTRAINT service_files_pkey PRIMARY KEY (id);

ALTER TABLE ONLY services
    ADD CONSTRAINT services_pkey PRIMARY KEY (id);

ALTER TABLE ONLY user_mounts
    ADD CONSTRAINT user_mounts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (uid);

CREATE UNIQUE INDEX job_configs_unique_idx ON job_configs (owner, service, md5(inputs), parent, systematic_parameter);

CREATE INDEX job_configs_label_idx ON job_configs USING btree (label);

CREATE INDEX job_configs_owner_idx ON job_configs USING btree (owner);

CREATE INDEX job_configs_service_idx ON job_configs USING btree (service);


CREATE UNIQUE INDEX jobs_ext_id_idx ON jobs USING btree (ext_id);

CREATE INDEX jobs_job_config_idx ON jobs USING btree (job_config);

CREATE INDEX jobs_owner_idx ON jobs USING btree (owner);


CREATE INDEX service_files_filename_idx ON service_files USING btree (filename);

CREATE UNIQUE INDEX service_files_filename_service_idx ON service_files USING btree (filename, service);

CREATE INDEX service_files_service_idx ON service_files USING btree (service);


CREATE UNIQUE INDEX services_name_idx ON services USING btree (name);

CREATE INDEX services_owner_idx ON services USING btree (owner);


CREATE INDEX user_mount_name_idx ON user_mounts USING btree (name);

CREATE UNIQUE INDEX user_mount_name_owner_idx ON user_mounts USING btree (name);

CREATE INDEX user_mount_owner_idx ON user_mounts USING btree (owner);


CREATE UNIQUE INDEX users_name_idx ON users USING btree (name);



ALTER TABLE ONLY job_configs
    ADD CONSTRAINT job_configs_owner_fkey FOREIGN KEY (owner) REFERENCES users(uid);

ALTER TABLE ONLY job_configs
    ADD CONSTRAINT job_configs_parent_fkey FOREIGN KEY (parent) REFERENCES jobs(id);

ALTER TABLE ONLY job_configs
    ADD CONSTRAINT job_configs_service_fkey FOREIGN KEY (service) REFERENCES services(id);


ALTER TABLE ONLY jobs
    ADD CONSTRAINT jobs_job_config_fkey FOREIGN KEY (job_config) REFERENCES job_configs(id);

ALTER TABLE ONLY jobs
    ADD CONSTRAINT jobs_owner_fkey FOREIGN KEY (owner) REFERENCES users(uid);

ALTER TABLE ONLY jobs
    ADD CONSTRAINT jobs_parent_job_id_fkey FOREIGN KEY (parent_job_id) REFERENCES jobs(id);


ALTER TABLE ONLY service_files
    ADD CONSTRAINT service_files_service_fkey FOREIGN KEY (service) REFERENCES services(id);


ALTER TABLE ONLY services_mounts
    ADD CONSTRAINT services_mounts_service_id_fkey FOREIGN KEY (service_id) REFERENCES services(id);

ALTER TABLE ONLY services_mounts
    ADD CONSTRAINT services_mounts_user_mount_id_fkey FOREIGN KEY (user_mount_id) REFERENCES user_mounts(id);


ALTER TABLE ONLY services
    ADD CONSTRAINT services_owner_fkey FOREIGN KEY (owner) REFERENCES users(uid);


ALTER TABLE ONLY user_mounts
    ADD CONSTRAINT user_mounts_owner_fkey FOREIGN KEY (owner) REFERENCES users(uid);

