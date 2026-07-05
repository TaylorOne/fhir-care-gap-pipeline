export interface MeasureSummary {
  measureId: string;
  status: 'OPEN' | 'CLOSED';
  gaps: number;
}

export interface Measure {
  id: string;
  displayName: string;
}

export interface Gap {
  id: number;
  measureId: string;
  patientId: string;
  status: 'OPEN' | 'CLOSED';
  lastEvidenceDate: string | null;
  firstIdentifiedAt: string;
  lastEvaluatedAt: string;
}

export interface GapPage {
  content: Gap[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MeasureRun {
  id: string;
  runDate: string;
  startedAt: string;
  completedAt: string | null;
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  error: string | null;
  gapsOpen: number | null;
  gapsClosed: number | null;
}
