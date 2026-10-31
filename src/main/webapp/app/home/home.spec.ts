import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { provideTranslateService } from '@ngx-translate/core';

import { AccountService } from 'app/core/auth';
import { of, throwError } from 'rxjs';
import { ArtistSearchService } from './artist-search.service';

import Home from './home';

describe('Home Component', () => {
  let comp: Home;
  let fixture: ComponentFixture<Home>;
  let mockRouter: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: ArtistSearchService, useValue: { search: vi.fn() } },
        {
          provide: AccountService,
        },
      ],
    });
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(Home);
    comp = fixture.componentInstance;

    mockRouter = TestBed.inject(Router);
    vi.spyOn(mockRouter, 'navigate');
  });

  describe('login', () => {
    it('should navigate to /login on login', () => {
      // WHEN
      comp.login();

      // THEN
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  it('searches real artists through the backend service', () => {
    const service = TestBed.inject(ArtistSearchService);
    const page = {
      artists: [{ musicBrainzId: 'mbid', name: 'Death', type: 'Group', country: null, beginDate: null, disambiguation: null }],
      total: 1,
      offset: 0,
      limit: 20,
    };
    vi.mocked(service.search).mockReturnValue(of(page));
    comp.query = ' Death ';
    comp.search();
    fixture.detectChanges();
    expect(service.search).toHaveBeenCalledWith('Death');
    expect(comp.loading()).toBe(false);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/bandas', 'mbid']);
    expect(fixture.nativeElement.textContent).toContain('Death');
    expect(fixture.nativeElement.textContent).toContain('No disponible');
  });

  it('handles failures and allows retrying', () => {
    vi.mocked(TestBed.inject(ArtistSearchService).search).mockReturnValue(throwError(() => ({ status: 429 })));
    comp.query = 'Death';
    comp.search();
    expect(comp.error()).toContain('Esperá');
    expect(comp.loading()).toBe(false);
  });

  it('lets the user choose when two bands share a name', () => {
    const service = TestBed.inject(ArtistSearchService);
    const artist = { musicBrainzId: 'first', name: 'Death', type: 'Group', country: null, beginDate: null, disambiguation: null };
    vi.mocked(service.search).mockReturnValue(
      of({ artists: [artist, { ...artist, musicBrainzId: 'second' }], total: 2, offset: 0, limit: 6 }),
    );
    comp.query = 'Death';
    comp.search();
    expect(comp.result()?.artists).toHaveLength(2);
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('rejects short searches without requesting the backend', () => {
    comp.query = 'a';
    comp.search();
    expect(TestBed.inject(ArtistSearchService).search).not.toHaveBeenCalled();
  });
});
