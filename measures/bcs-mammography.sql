-- Measure BCS_MAMMOGRAPHY — Breast cancer screening (simplified HEDIS-style).
-- Denominator: women 50–74.
-- Numerator: a mammography procedure within 27 months before @run_date.
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
denominator AS (
  SELECT pl.id AS patient_id
  FROM patient_latest pl
  WHERE pl.gender = 'female'
    AND DATE_DIFF(@run_date, SAFE_CAST(pl.birthDate AS DATE), YEAR) BETWEEN 50 AND 74
),
numerator AS (
  SELECT
    pr.subject.patientId AS patient_id,
    MAX(DATE(SAFE_CAST(pr.performed.dateTime AS TIMESTAMP))) AS last_evidence_date
  FROM procedure_latest pr
  WHERE EXISTS (
      SELECT 1 FROM UNNEST(pr.code.coding) coding
      -- mammography / screening mammography (Synthea uses 726551006)
      WHERE coding.code IN ('241055006', '71651007', '726551006', '24623002'))
    AND DATE(SAFE_CAST(pr.performed.dateTime AS TIMESTAMP))
        BETWEEN DATE_SUB(@run_date, INTERVAL 27 MONTH) AND @run_date
  GROUP BY 1
)
SELECT
  d.patient_id,
  n.patient_id IS NOT NULL AS in_numerator,
  n.last_evidence_date
FROM denominator d
LEFT JOIN numerator n USING (patient_id)
