import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AlbumDetail, ArtistSearchService, Discography as DiscographyModel } from '../home/artist-search.service';
import Discography from './discography';

const catalog: DiscographyModel = {
  artist: { musicBrainzId: 'artist', name: 'Malevolent Creation', country: 'US', type: 'Group', beginDate: '1987', disambiguation: null },
  genres: ['death metal'],
  total: 2,
  offset: 0,
  albums: [
    {
      musicBrainzId: 'studio',
      title: 'The Ten Commandments',
      firstReleaseDate: '1991',
      primaryType: 'Album',
      secondaryTypes: [],
      coverUrl: 'cover.jpg',
    },
    {
      musicBrainzId: 'live',
      title: 'Live',
      firstReleaseDate: '2008',
      primaryType: 'Album',
      secondaryTypes: ['Live'],
      coverUrl: 'live.jpg',
    },
  ],
};

describe('Discography', () => {
  const service = { discography: vi.fn(), album: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    service.discography.mockReturnValue(of(catalog));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'artist' })) } },
        { provide: ArtistSearchService, useValue: service },
      ],
    });
  });

  it('opens the artist and separates studio albums from live releases', () => {
    const fixture = TestBed.createComponent(Discography);
    fixture.detectChanges();
    expect(service.discography).toHaveBeenCalledWith('artist');
    expect(fixture.componentInstance.visibleAlbums().map(album => album.title)).toEqual(['The Ten Commandments']);
    fixture.componentInstance.filter.set('Live');
    expect(fixture.componentInstance.visibleAlbums().map(album => album.title)).toEqual(['Live']);
    expect(fixture.nativeElement.textContent).toContain('Malevolent Creation');
  });

  it('shows a recoverable error when the artist cannot be loaded', () => {
    service.discography.mockReturnValue(throwError(() => new Error('unavailable')));
    const component = TestBed.createComponent(Discography).componentInstance;
    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('No pudimos');
    service.discography.mockReturnValue(of(catalog));
    component.retry();
    expect(component.data()?.artist.name).toBe('Malevolent Creation');
    expect(component.error()).toBe('');
  });

  it('cancels the previous album so late responses cannot replace the selected record', () => {
    const first = new Subject<AlbumDetail>();
    const second = new Subject<AlbumDetail>();
    service.album.mockReturnValueOnce(first).mockReturnValueOnce(second);
    const component = TestBed.createComponent(Discography).componentInstance;
    component.openAlbum(catalog.albums[0]);
    component.openAlbum(catalog.albums[1]);
    expect(first.observed).toBe(false);
    expect(second.observed).toBe(true);
    expect(component.activeAlbum()?.musicBrainzId).toBe('live');
    component.closeAlbum();
    expect(second.observed).toBe(false);
  });

  it('handles missing cover art without repeatedly requesting the broken image', () => {
    const component = TestBed.createComponent(Discography).componentInstance;
    component.coverFailed('studio');
    expect(component.brokenCovers().has('studio')).toBe(true);
    expect(component.duration(148000)).toBe('2:28');
    expect(component.duration(null)).toBe('—');
  });
});
