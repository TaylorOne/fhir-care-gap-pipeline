-- Operational care-gap schema. Owned by gap-analysis-service (the writer);
-- care-gap-api consumes it read-mostly and validates against it.

CREATE TABLE measure (
    id           TEXT PRIMARY KEY,
    display_name TEXT NOT NULL
);

CREATE TABLE measure_run (
    id           UUID PRIMARY KEY,
    run_date     DATE NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status       TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    error        TEXT,
    gaps_open    INTEGER,
    gaps_closed  INTEGER
);

CREATE TABLE care_gap (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_id         TEXT NOT NULL REFERENCES measure (id),
    patient_id         TEXT NOT NULL,
    status             TEXT NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    last_evidence_date DATE,
    first_identified_at TIMESTAMPTZ NOT NULL,
    last_evaluated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (measure_id, patient_id)
);

-- The dashboard's dominant query: open gaps filtered by measure.
CREATE INDEX idx_care_gap_status_measure ON care_gap (status, measure_id);
CREATE INDEX idx_care_gap_patient ON care_gap (patient_id);
