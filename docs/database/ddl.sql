-- Estado implementado pela E0-S1.
-- Fonte: backend/src/main/resources/db/migration/V1__initialize_platform.sql

CREATE TABLE application_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL
);

INSERT INTO application_metadata (metadata_key, metadata_value)
VALUES ('schema_version', '1');
