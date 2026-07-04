-- Measure CDC_A1C — Diabetes: HbA1c testing (simplified HEDIS-style).
-- Denominator: patients 18–75 with a diabetes condition.
-- Numerator: an HbA1c result (LOINC 4548-4) within 12 months before @run_date.
--
-- Conventions shared by all measures in this directory:
--  * ${dataset} is replaced by the service with "project.dataset" (validated).
--  * @run_date is a BigQuery named DATE parameter.
--  * Streaming export appends a row per resource VERSION; the *_latest CTEs
--    keep only the newest version of each resource.
--  * SNOMED/LOINC code lists are illustrative, not licensed HEDIS value sets.
--  * Result contract: patient_id STRING, in_numerator BOOL, last_evidence_date DATE.
WITH patient_latest AS (
  SELECT * FROM (
    SELECT p.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Patient` p
  ) WHERE rn = 1
),
condition_latest AS (
  SELECT * FROM (
    SELECT c.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Condition` c
  ) WHERE rn = 1
),
observation_latest AS (
  SELECT * FROM (
    SELECT o.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Observation` o
  ) WHERE rn = 1
),
denominator AS (
  SELECT DISTINCT pl.id AS patient_id
  FROM patient_latest pl
  JOIN condition_latest c ON c.subject.patientId = pl.id
  WHERE EXISTS (
      SELECT 1 FROM UNNEST(c.code.coding) coding
      -- type 2, type 1, and common Synthea diabetes SNOMED codes
      WHERE coding.code IN ('44054006', '46635009', '73211009'))
    AND DATE_DIFF(@run_date, SAFE_CAST(pl.birthDate AS DATE), YEAR) BETWEEN 18 AND 75
),
numerator AS (
  SELECT
    o.subject.patientId AS patient_id,
    MAX(DATE(SAFE_CAST(o.effective.dateTime AS TIMESTAMP))) AS last_evidence_date
  FROM observation_latest o
  WHERE EXISTS (
      SELECT 1 FROM UNNEST(o.code.coding) coding
      WHERE coding.system = 'http://loinc.org' AND coding.code = '4548-4')
    AND DATE(SAFE_CAST(o.effective.dateTime AS TIMESTAMP))
        BETWEEN DATE_SUB(@run_date, INTERVAL 12 MONTH) AND @run_date
  GROUP BY 1
)
SELECT
  d.patient_id,
  n.patient_id IS NOT NULL AS in_numerator,
  n.last_evidence_date
FROM denominator d
LEFT JOIN numerator n USING (patient_id)
