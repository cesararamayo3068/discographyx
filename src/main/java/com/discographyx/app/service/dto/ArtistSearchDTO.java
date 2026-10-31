package com.discographyx.app.service.dto;

import java.util.List;

/** A page of live MusicBrainz artists; no local musical entities are persisted. */
public record ArtistSearchDTO(List<Artist> artists, int total, int offset, int limit) {
    public record Artist(String musicBrainzId, String name, String type, String country, String beginDate, String disambiguation) {}
}
