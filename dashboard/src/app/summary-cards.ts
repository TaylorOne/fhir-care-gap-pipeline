import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Measure, MeasureSummary } from './models';

interface MeasureCard {
  measureId: string;
  name: string;
  open: number;
  closed: number;
  total: number;
  closureRate: number; // 0..1
}

/**
 * One stat tile per measure: the headline number is OPEN gaps (the actionable
 * quantity), with a compact open/closed split bar underneath. Direct labels on
 * both segments (the validated palette's relief rule) and a shared legend.
 */
@Component({
  selector: 'app-summary-cards',
  imports: [DecimalPipe],
  template: `
    <div class="legend" role="list" aria-label="Gap status legend">
      <span role="listitem"><i class="swatch open"></i>Open</span>
      <span role="listitem"><i class="swatch closed"></i>Closed</span>
    </div>
    <div class="cards">
      @for (card of cards(); track card.measureId) {
        <section class="card" [attr.aria-label]="card.name">
          <h3>{{ card.name }}</h3>
          <p class="headline">
            <span class="value">{{ card.open | number }}</span>
            <span class="unit">open gaps</span>
          </p>
          <p class="sub">{{ card.closed | number }} closed · {{ card.closureRate | number: '1.0-0' }}% closure</p>
          <div class="split" role="img"
               [attr.aria-label]="card.open + ' open, ' + card.closed + ' closed'">
            @if (card.open > 0) {
              <div class="seg open" [style.flex-grow]="card.open"
                   [title]="card.open + ' open'">
                <span class="seg-label">{{ card.open }}</span>
              </div>
            }
            @if (card.closed > 0) {
              <div class="seg closed" [style.flex-grow]="card.closed"
                   [title]="card.closed + ' closed'">
                <span class="seg-label">{{ card.closed }}</span>
              </div>
            }
          </div>
        </section>
      } @empty {
        <p class="empty">No gap data yet — run the pipeline to populate.</p>
      }
    </div>
  `,
  styles: `
    .legend { display: flex; gap: 16px; font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }
    .legend span { display: inline-flex; align-items: center; gap: 6px; }
    .swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
    .swatch.open { background: var(--status-open); }
    .swatch.closed { background: var(--status-closed); }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
    .card { background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 16px; }
    h3 { margin: 0 0 8px; font-size: 14px; font-weight: 600; color: var(--text-secondary); }
    .headline { margin: 0; display: flex; align-items: baseline; gap: 8px; }
    .value { font-size: 32px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }
    .unit { font-size: 13px; color: var(--text-secondary); }
    .sub { margin: 2px 0 12px; font-size: 13px; color: var(--text-secondary); }
    .split { display: flex; gap: 2px; height: 22px; border-radius: 4px; overflow: hidden; }
    .seg { display: flex; align-items: center; justify-content: center; min-width: 20px; }
    .seg.open { background: var(--status-open); }
    .seg.closed { background: var(--status-closed); }
    .seg-label { font-size: 12px; font-weight: 600; color: #fff; }
    .empty { color: var(--text-secondary); }
  `,
})
export class SummaryCards {
  readonly summary = input.required<MeasureSummary[]>();
  readonly measures = input.required<Measure[]>();

  readonly cards = computed<MeasureCard[]>(() => {
    const names = new Map(this.measures().map((m) => [m.id, m.displayName]));
    const byMeasure = new Map<string, MeasureCard>();
    for (const row of this.summary()) {
      const card = byMeasure.get(row.measureId) ?? {
        measureId: row.measureId,
        name: names.get(row.measureId) ?? row.measureId,
        open: 0,
        closed: 0,
        total: 0,
        closureRate: 0,
      };
      if (row.status === 'OPEN') card.open += row.gaps;
      else card.closed += row.gaps;
      byMeasure.set(row.measureId, card);
    }
    return [...byMeasure.values()]
      .map((c) => ({
        ...c,
        total: c.open + c.closed,
        closureRate: c.open + c.closed === 0 ? 0 : (100 * c.closed) / (c.open + c.closed),
      }))
      .sort((a, b) => a.measureId.localeCompare(b.measureId));
  });
}
