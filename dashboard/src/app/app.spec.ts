import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('renders the summary card from API data', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url.endsWith('/api/gaps/summary')).flush([
      { measureId: 'CDC_A1C', status: 'OPEN', gaps: 4 },
      { measureId: 'CDC_A1C', status: 'CLOSED', gaps: 6 },
    ]);
    http.expectOne((r) => r.url.endsWith('/api/measures')).flush([
      { id: 'CDC_A1C', displayName: 'Diabetes: HbA1c testing' },
    ]);
    http.expectOne((r) => r.url.endsWith('/api/runs')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/gaps')).flush({
      content: [], page: 0, size: 25, totalElements: 0, totalPages: 0,
    });
    http.verify();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Diabetes: HbA1c testing');
    expect(text).toContain('open gaps');
    expect(text).toContain('60% closure');
  });

  it('shows an error banner when the API is unreachable', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url.endsWith('/api/gaps/summary'))
        .error(new ProgressEvent('error'));
    http.expectOne((r) => r.url.endsWith('/api/measures')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/runs')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/gaps')).flush({
      content: [], page: 0, size: 25, totalElements: 0, totalPages: 0,
    });
    http.verify();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Could not reach');
  });
});
