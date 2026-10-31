import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { serverApiUrl } from 'app/config';

export interface Artist {
  musicBrainzId: string;
  name: string;
  type: string | null;
  country: string | null;
  beginDate: string | null;
  disambiguation: string | null;
}

export interface ArtistSearchResult {
  artists: Artist[];
  total: number;
  offset: number;
  limit: number;
}

export interface Album {
  musicBrainzId: string;
  title: string;
  firstReleaseDate: string | null;
  primaryType: string | null;
  secondaryTypes: string[];
  coverUrl: string;
}

export interface Discography {
  artist: Artist;
  genres: string[];
  albums: Album[];
  total: number;
  offset: number;
}

export interface AlbumDetail {
  album: Album;
  artistName: string;
  releaseId: string | null;
  editionDate: string | null;
  editionCountry: string | null;
  tracks: { disc: number; number: string | null; title: string | null; length: number | null }[];
  trackCount: number;
  listenLinks: { label: string; url: string }[];
}

@Service()
export class ArtistSearchService {
  private readonly http = inject(HttpClient);

  search(query: string, offset = 0): Observable<ArtistSearchResult> {
    return this.http.get<ArtistSearchResult>(`${serverApiUrl}api/music/artists`, { params: { query, offset } });
  }

  discography(id: string, offset = 0): Observable<Discography> {
    return this.http.get<Discography>(`${serverApiUrl}api/music/artists/${id}`, { params: { offset } });
  }

  album(id: string): Observable<AlbumDetail> {
    return this.http.get<AlbumDetail>(`${serverApiUrl}api/music/albums/${id}`);
  }
}
