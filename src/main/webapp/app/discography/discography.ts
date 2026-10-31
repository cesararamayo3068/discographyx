import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, Subscription, catchError, combineLatest, of, startWith, switchMap } from 'rxjs';

import { Album, AlbumDetail, ArtistSearchService, Discography as DiscographyModel } from '../home/artist-search.service';

@Component({
  selector: 'jhi-discography',
  templateUrl: './discography.html',
  styleUrl: './discography.scss',
  imports: [RouterLink],
})
export default class Discography {
  readonly data = signal<DiscographyModel | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly loadingMore = signal(false);
  readonly filter = signal('studio');
  readonly activeAlbum = signal<Album | null>(null);
  readonly albumDetail = signal<AlbumDetail | null>(null);
  readonly albumLoading = signal(false);
  readonly albumError = signal('');
  readonly brokenCovers = signal(new Set<string>());
  readonly filters = [
    { id: 'studio', label: 'Álbumes de estudio' },
    { id: 'all', label: 'Todos' },
    { id: 'EP', label: 'EPs' },
    { id: 'Single', label: 'Singles' },
    { id: 'Live', label: 'En vivo' },
    { id: 'Compilation', label: 'Compilaciones' },
  ];
  readonly visibleAlbums = computed(() =>
    (this.data()?.albums ?? []).filter(album => {
      const filter = this.filter();
      if (filter === 'all') return true;
      if (filter === 'studio')
        return album.primaryType === 'Album' && !album.secondaryTypes.some(type => ['Live', 'Compilation', 'Remix', 'Demo'].includes(type));
      return album.primaryType === filter || album.secondaryTypes.includes(filter);
    }),
  );

  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(ArtistSearchService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly refresh = new Subject<void>();
  private albumRequest?: Subscription;
  private artistId = '';

  constructor() {
    combineLatest([this.route.paramMap, this.refresh.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) => {
          this.artistId = params.get('id') ?? '';
          this.loading.set(true);
          this.error.set('');
          this.data.set(null);
          this.loadingMore.set(false);
          this.closeAlbum();
          return this.catalog.discography(this.artistId).pipe(
            catchError(() => {
              this.error.set('No pudimos abrir esta discografía. Intentá de nuevo en unos segundos.');
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(data => {
        this.data.set(data);
        this.loading.set(false);
      });
  }

  retry(): void {
    this.refresh.next();
  }

  loadMore(): void {
    const current = this.data();
    if (!current || this.loadingMore()) return;
    const id = this.artistId;
    this.loadingMore.set(true);
    this.error.set('');
    this.catalog
      .discography(id, current.offset + 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          if (id !== this.artistId) return;
          const unique = new Map([...current.albums, ...page.albums].map(album => [album.musicBrainzId, album]));
          this.data.set({
            ...page,
            albums: [...unique.values()].sort((a, b) => (a.firstReleaseDate ?? '9999').localeCompare(b.firstReleaseDate ?? '9999')),
          });
          this.loadingMore.set(false);
        },
        error: () => {
          if (id !== this.artistId) return;
          this.error.set('No pudimos cargar los discos restantes. Podés volver a intentarlo.');
          this.loadingMore.set(false);
        },
      });
  }

  openAlbum(album: Album): void {
    this.albumRequest?.unsubscribe();
    this.activeAlbum.set(album);
    this.albumDetail.set(null);
    this.albumError.set('');
    this.albumLoading.set(true);
    setTimeout(() => {
      const panel = this.document.getElementById('dx-album-panel');
      if (panel && typeof panel.scrollIntoView === 'function') panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    this.albumRequest = this.catalog
      .album(album.musicBrainzId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => {
          this.albumDetail.set(detail);
          this.albumLoading.set(false);
        },
        error: () => {
          this.albumError.set('No pudimos cargar los temas de este disco. Volvé a intentarlo.');
          this.albumLoading.set(false);
        },
      });
  }

  closeAlbum(): void {
    this.albumRequest?.unsubscribe();
    this.activeAlbum.set(null);
    this.albumDetail.set(null);
  }

  coverFailed(id: string): void {
    this.brokenCovers.update(current => new Set([...current, id]));
  }

  year(date: string | null): string {
    return date?.slice(0, 4) ?? 'Sin fecha';
  }

  country(code: string | null): string {
    return code ? (new Intl.DisplayNames(['es'], { type: 'region' }).of(code) ?? code) : 'País no documentado';
  }

  type(album: Album): string {
    if (album.secondaryTypes.includes('Live')) return 'En vivo';
    if (album.secondaryTypes.includes('Compilation')) return 'Compilación';
    return (
      ({ Album: 'Álbum', Single: 'Single', EP: 'EP', Other: 'Otro lanzamiento' } as Record<string, string>)[album.primaryType ?? ''] ??
      'Lanzamiento'
    );
  }

  duration(length: number | null): string {
    if (length === null) return '—';
    const seconds = Math.floor(length / 1000);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  }

  listenSearch(title: string): string {
    return `https://music.youtube.com/search?q=${encodeURIComponent(`${this.data()?.artist.name ?? ''} ${title}`)}`;
  }
}
