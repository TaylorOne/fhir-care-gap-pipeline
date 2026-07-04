-- Measure COL_SCREENING — Colorectal cancer screening (simplified HEDIS-style).
-- Denominator: adults 45–75.
-- Numerator: colonoscopy within 10 years OR stool blood test (FIT/FOBT)
-- within 1 year before @run_date.
-- See cdc-a1c.sql for the shared conventions and result contract.
WITH patient_latest AS (
  SELECT * FROM (
    SELECT p.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Patient` p
  ) WHERE rn = 1
),
procedure_latest AS (
  SELECT * FROM (
    SELECT pr.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Procedure` pr
  ) WHERE rn = 1
),
observation_latest AS (
  SELECT * FROM (
    SELECT o.*, ROW_NUMBER() OVER (PARTITION BY id ORDER BY meta.lastUpdated DESC) AS rn
    FROM `${dataset}.Observation` o
  ) WHERE rn = 1
),
denominator AS (
  SELECT pl.id AS patient_id
  FROM patient_latest pl
  WHERE DATE_DIFF(@run_date, SAFE_CAST(pl.birthDate AS DATE), YEAR) BETWEEN 45 AND 75
),
colonoscopy AS (
  SELECT
    pr.subject.patientId AS patient_id,
    MAX(DATE(SAFE_CAST(pr.performed.dateTime AS TIMESTAMP))) AS last_evidence_date
  FROM procedure_latest pr
  WHERE EXISTS (
      SELECT 1 FROM UNNEST(pr.code.coding) coding
      WHERE coding.code IN ('73761001', '444783004'))  -- colonoscopy / screening colonoscopy
    AND DATE(SAFE_CAST(pr.performed.dateTime AS TIMESTAMP))
        BETWEEN DATE_SUB(@run_date, INTERVAL 10 YEAR) AND @run_date
  GROUP BY 1
),
stool_test AS (
  SELECT
    o.subject.patientId AS patient_id,
    MAX(DATE(SAFE_CAST(o.effective.dateTime AS TIMESTAMP))) AS last_evidence_date
  FROM observation_latest o
  WHERE EXISTS (
      SELECT 1 FROM UNNEST(o.code.coding) coding
      -- FIT / FOBT LOINC codes
      WHERE coding.system = 'http://loinc.org' AND coding.code IN ('2335-8', '57905-2'))
    AND DATE(SAFE_CAST(o.effective.dateTime AS TIMESTAMP))
        BETWEEN DATE_SUB(@run_date, INTERVAL 1 YEAR) AND @run_date
  GROUP BY 1
),
numerator AS (
  SELECT patient_id, MAX(last_evidence_date) AS last_evidence_date
  FROM (SELECT * FROM colonoscopy UNION ALL SELECT * FROM stool_test)
  GROUP BY 1
)
SELECT
  d.patient_id,
  n.patient_id IS NOT NULL AS in_numerator,
  n.last_evidence_date
FROM denominator d
LEFT JOIN numerator n USING (patient_id)
