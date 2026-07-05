-- care-gap-api reads through its own least-privilege database user. The user
-- name is environment-specific (a Cloud SQL IAM user in GCP, the shared
-- 'caregap' user locally), injected as a Flyway placeholder:
--   spring.flyway.placeholders.apiuser  (env API_DB_USER)
GRANT SELECT ON measure, measure_run, care_gap TO "${apiuser}";
