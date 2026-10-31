package com.discographyx.app.service.dto;

import java.util.List;

/** Release groups identify albums; releases identify editions. */
public record DiscographyDTO(ArtistSearchDTO.Artist artist, List<String> genres, List<Album> albums, int total, int offset) {
    public record Album(
        String musicBrainzId,
        String title,
        String firstReleaseDate,
        String primaryType,
        List<String> secondaryTypes,
        String coverUrl
    ) {}

    public record Link(String label, String url) {}

    public record Track(int disc, String number, String title, Long length) {}

    public record AlbumDetail(
        Album album,
        String artistName,
        String releaseId,
        String editionDate,
        String editionCountry,
        List<Track> tracks,
        int trackCount,
        List<Link> listenLinks
    ) {}
}
