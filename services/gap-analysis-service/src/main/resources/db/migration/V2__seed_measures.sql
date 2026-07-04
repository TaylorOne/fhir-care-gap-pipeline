-- Measure ids match MeasureCatalog; the catalog fails startup if its SQL is
-- missing, and the care_gap FK fails the run if a catalog id is not seeded.
INSERT INTO measure (id, display_name) VALUES
    ('CDC_A1C', 'Diabetes: HbA1c testing'),
    ('BCS_MAMMOGRAPHY', 'Breast cancer screening'),
    ('COL_SCREENING', 'Colorectal cancer screening');
