import { Component, effect, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from './api.service';
import { GapPage, Measure } from './models';
import { input } from '@angular/core';

/**
 * Filterable, paginated gap list. Status is never conveyed by color alone:
 * every status cell pairs the colored dot with its text label.
 */
@Component({
  selector: 'app-gaps-table',
  imports: [DatePipe],
  template: `
    <div class="filters" role="search">
      <label>
        Measure
        <select (change)="setMeasure($event)">
          <option value="">All measures</option>
          @for (m of measures(); track m.id) {
            <option [value]="m.id">{{ m.displayName }}</option>
          }
        </select>
      </label>
      <label>
        Status
        <select (change)="setStatus($event)">
          <option value="">All</option>
          <option value="OPEN">Open</option>
          <option value="CLOSED">Closed</option>
        </select>
      </label>
      <label>
        Patient ID
        <input type="search" placeholder="exact id…" (change)="setPatient($event)" />
      </label>
    </div>

    @if (page(); as p) {
      <table>
        <thead>
          <tr>
            <th scope="col">Patient</th>
            <th scope="col">Measure</th>
            <th scope="col">Status</th>
            <th scope="col">Last evidence</th>
            <th scope="col">Last evaluated</th>
          </tr>
        </thead>
        <tbody>
          @for (gap of p.content; track gap.id) {
            <tr>
              <td class="mono">{{ gap.patientId }}</td>
              <td>{{ measureName(gap.measureId) }}</td>
              <td>
                <span class="status" [class.open]="gap.status === 'OPEN'">
                  <i class="dot"></i>{{ gap.status === 'OPEN' ? 'Open' : 'Closed' }}
                </span>
              </td>
              <td>{{ gap.lastEvidenceDate ? (gap.lastEvidenceDate | date: 'mediumDate') : '—' }}</td>
              <td>{{ gap.lastEvaluatedAt | date: 'medium' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="empty">No gaps match the current filters.</td></tr>
          }
        </tbody>
      </table>
      <nav class="pager" aria-label="Gap list pages">
        <button (click)="prev()" [disabled]="p.page === 0">‹ Previous</button>
        <span>Page {{ p.page + 1 }} of {{ p.totalPages === 0 ? 1 : p.totalPages }} · {{ p.totalElements }} gaps</span>
        <button (click)="next()" [disabled]="p.page >= p.totalPages - 1">Next ›</button>
      </nav>
    } @else {
      <p class="empty">Loading gaps…</p>
    }
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-secondary); }
    select, input { padding: 6px 8px; border: 1px solid var(--border); border-radius: 6px;
                    background: var(--surface-2); color: var(--text-primary); font-size: 14px; min-width: 160px; }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { text-align: left; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em;
         color: var(--text-secondary); font-weight: 600; padding: 8px; border-bottom: 1px solid var(--border); }
    td { padding: 8px; border-bottom: 1px solid var(--border); color: var(--text-primary); }
    .mono { font-family: ui-monospace, monospace; font-size: 13px; }
    .status { display: inline-flex; align-items: center; gap: 6px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--status-closed); display: inline-block; }
    .status.open .dot { background: var(--status-open); }
    .pager { display: flex; align-items: center; gap: 12px; margin-top: 12px;
             font-size: 13px; color: var(--text-secondary); }
    button { padding: 6px 12px; border: 1px solid var(--border); border-radius: 6px;
             background: var(--surface-2); color: var(--text-primary); cursor: pointer; }
    button:disabled { opacity: 0.45; cursor: default; }
    .empty { color: var(--text-secondary); padding: 16px 8px; }
  `,
})
export class GapsTable {
  private readonly api = inject(ApiService);

  readonly measures = input.required<Measure[]>();

  readonly page = signal<GapPage | null>(null);
  private readonly status = signal<'OPEN' | 'CLOSED' | undefined>(undefined);
  private readonly measureId = signal<string | undefined>(undefined);
  private readonly patientId = signal<string | undefined>(undefined);
  private readonly pageIndex = signal(0);

  constructor() {
    effect(() => {
      const filter = {
        status: this.status(),
        measureId: this.measureId(),
        patientId: this.patientId(),
        page: this.pageIndex(),
        size: 25,
      };
      this.api.gaps(filter).subscribe((p) => this.page.set(p));
    });
  }

  measureName(id: string): string {
    return this.measures().find((m) => m.id === id)?.displayName ?? id;
  }

  setStatus(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.pageIndex.set(0);
    this.status.set(value === '' ? undefined : (value as 'OPEN' | 'CLOSED'));
  }

  setMeasure(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.pageIndex.set(0);
    this.measureId.set(value === '' ? undefined : value);
  }

  setPatient(event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    this.pageIndex.set(0);
    this.patientId.set(value === '' ? undefined : value);
  }

  prev(): void {
    this.pageIndex.update((i) => Math.max(0, i - 1));
  }

  next(): void {
    this.pageIndex.update((i) => i + 1);
  }
}
