import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Gap, GapPage, Measure, MeasureRun, MeasureSummary } from './models';

/**
 * The API base URL is runtime configuration, not build configuration: the same
 * static bundle runs locally and in GCP. env.js (loaded before the app) sets
 * window.__env.apiUrl; the container entrypoint writes it from the API_URL
 * environment variable.
 */
declare global {
  interface Window {
    __env?: { apiUrl?: string };
  }
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = window.__env?.apiUrl || 'http://localhost:8080';

  summary(): Observable<MeasureSummary[]> {
    return this.http.get<MeasureSummary[]>(`${this.baseUrl}/api/gaps/summary`);
  }

  measures(): Observable<Measure[]> {
    return this.http.get<Measure[]>(`${this.baseUrl}/api/measures`);
  }

  runs(): Observable<MeasureRun[]> {
    return this.http.get<MeasureRun[]>(`${this.baseUrl}/api/runs`);
  }

  gaps(filter: {
    status?: 'OPEN' | 'CLOSED';
    measureId?: string;
    patientId?: string;
    page: number;
    size: number;
  }): Observable<GapPage> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.measureId) params = params.set('measureId', filter.measureId);
    if (filter.patientId) params = params.set('patientId', filter.patientId);
    return this.http.get<GapPage>(`${this.baseUrl}/api/gaps`, { params });
  }
}
