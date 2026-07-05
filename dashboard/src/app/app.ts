import { Component, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { Measure, MeasureRun, MeasureSummary } from './models';
import { SummaryCards } from './summary-cards';
import { GapsTable } from './gaps-table';
import { RunsPanel } from './runs-panel';

@Component({
  selector: 'app-root',
  imports: [SummaryCards, GapsTable, RunsPanel],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly api = inject(ApiService);

  readonly summary = signal<MeasureSummary[]>([]);
  readonly measures = signal<Measure[]>([]);
  readonly runs = signal<MeasureRun[]>([]);
  readonly loadFailed = signal(false);

  constructor() {
    this.api.summary().subscribe({
      next: (s) => this.summary.set(s),
      error: () => this.loadFailed.set(true),
    });
    this.api.measures().subscribe({
      next: (m) => this.measures.set(m),
      error: () => this.loadFailed.set(true),
    });
    this.api.runs().subscribe({ next: (r) => this.runs.set(r) });
  }
}
