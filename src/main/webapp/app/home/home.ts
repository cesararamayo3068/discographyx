import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AccountService } from 'app/core/auth';
import { ArtistSearchService, ArtistSearchResult } from './artist-search.service';

@Component({
  selector: 'jhi-home',
  templateUrl: './home.html',
  styleUrl: './home.scss',
  imports: [FormsModule, RouterLink],
})
export default class Home {
  public readonly account = inject(AccountService).account;
  readonly loading = signal(false);
  readonly result = signal<ArtistSearchResult | null>(null);
  readonly error = signal('');
  query = '';

  private readonly artists = inject(ArtistSearchService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly router = inject(Router);

  search(): void {
    if (this.loading()) return;
    const query = this.query.trim();
    if (query.length < 2 || query.length > 100) {
      this.error.set('Ingresá entre 2 y 100 caracteres para buscar.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.artists
      .search(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.result.set(result);
          this.loading.set(false);
          if (result.artists.length === 1) this.router.navigate(['/bandas', result.artists[0].musicBrainzId]);
        },
        error: (error: { status: number }) => {
          this.error.set(
            error.status === 429
              ? 'Esperá un momento y volvé a buscar.'
              : 'No pudimos consultar MusicBrainz. Intentá de nuevo en unos segundos.',
          );
          this.loading.set(false);
        },
      });
  }

  login(): void {
    this.router.navigate(['/login']);
  }
}
