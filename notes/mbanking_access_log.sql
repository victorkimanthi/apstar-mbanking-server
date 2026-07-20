CREATE TYPE mbanking_logs.enum_app_type AS ENUM ('USSD', 'MAPP', 'WAPP');
CREATE TYPE mbanking_logs.enum_identifier_type AS ENUM ('CUSTOMER_NO','MSISDN', 'EMAIL_ADDRESS', 'NATIONAL_ID','OTHER');

CREATE TABLE mbanking_logs.mbanking_access_logs (
    access_log_id SERIAL PRIMARY KEY,
    app_type mbanking_logs.enum_app_type NOT NULL,
    identifier_type mbanking_logs.enum_identifier_type NOT NULL,
    identifier VARCHAR(50) NOT NULL,
    date_accessed TIMESTAMP NOT NULL,
    date_created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX index_mbanking_access_logs_app_type ON mbanking_logs.mbanking_access_logs (app_type);
CREATE INDEX index_mbanking_access_logs_identifier_type ON mbanking_logs.mbanking_access_logs (identifier_type);
CREATE INDEX index_mbanking_access_logs_identifier ON mbanking_logs.mbanking_access_logs (identifier);
CREATE INDEX index_mbanking_access_logs_date_accessed ON mbanking_logs.mbanking_access_logs (date_accessed);
CREATE INDEX index_mbanking_access_logs_date_created ON mbanking_logs.mbanking_access_logs (date_created);
CREATE INDEX index_mbanking_access_logs_date_modified ON mbanking_logs.mbanking_access_logs (date_modified);