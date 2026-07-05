import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MeasureRun } from './models';

/** Data freshness: recent measure runs with outcome. Icon + label, never color alone. */
@Component({
  selector: 'app-runs-panel',
  imports: [DatePipe],
  template: `
    <ul>
      @for (run of runs(); track run.id) {
        <li>
          <span class="outcome" [class.failed]="run.status === 'FAILED'">
            {{ run.status === 'SUCCEEDED' ? '✓' : run.status === 'FAILED' ? '✕' : '…' }}
            {{ run.status }}
          </span>
          <span class="when">{{ run.startedAt | date: 'medium' }}</span>
          @if (run.status === 'SUCCEEDED') {
            <span class="counts">{{ run.gapsOpen }} open / {{ run.gapsClosed }} closed</span>
          } @else if (run.error) {
            <span class="error">{{ run.error }}</span>
          }
        </li>
      } @empty {
        <li class="empty">No measure runs recorded yet.</li>
      }
    </ul>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; font-size: 13px; }
    li { display: flex; flex-wrap: wrap; gap: 10px; padding: 6px 0; border-bottom: 1px solid var(--border);
         color: var(--text-secondary); align-items: baseline; }
    .outcome { font-weight: 600; color: var(--text-primary); }
    .outcome.failed { color: var(--status-open); }
    .counts { font-variant-numeric: tabular-nums; }
    .error { font-family: ui-monospace, monospace; font-size: 12px; }
    .empty { color: var(--text-secondary); }
  `,
})
export class RunsPanel {
  readonly runs = input.required<MeasureRun[]>();
}
