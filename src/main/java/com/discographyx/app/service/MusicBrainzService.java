package com.discographyx.app.service;

import com.discographyx.app.service.dto.ArtistSearchDTO;
import com.discographyx.app.service.dto.DiscographyDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Read-only catalogue with shared pacing, timeouts and bounded metadata caching. */
@Service
public class MusicBrainzService {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Cache<String, JsonNode> cache = Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(10)).build();
    private long nextRequest = System.nanoTime();

    @Autowired
    public MusicBrainzService(ObjectMapper mapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(), mapper);
    }

    MusicBrainzService(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public ArtistSearchDTO search(String query, int offset) {
        String name = query.strip();
        if (name.length() < 2 || name.length() > 100 || offset != 0) throw new MusicBrainzException(
            400,
            "Use a name of 2 to 100 characters; search results are limited to six matches."
        );
        // A quoted phrase prevents OR matching of independent words in a name.
        String literal = name.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("\"", "\\\"");
        JsonNode root = fetch("artist?fmt=json&limit=100&query=" + encode("artist:\"" + literal + "\""));
        if (!root.path("artists").isArray() || !root.path("count").isNumber()) throw invalidResponse();
        var candidates = new ArrayList<ArtistSearchDTO.Artist>();
        String normalized = normalize(name);
        for (JsonNode node : root.path("artists")) {
            if (
                text(node, "id") != null && text(node, "name") != null && normalize(text(node, "name")).contains(normalized)
            ) candidates.add(artist(node));
        }
        var exact = candidates
            .stream()
            .filter(item -> normalize(item.name()).equals(normalized))
            .toList();
        var matches = (exact.isEmpty() ? candidates : exact).stream().limit(6).toList();
        return new ArtistSearchDTO(matches, matches.size(), 0, 6);
    }

    public DiscographyDTO discography(UUID id, int offset) {
        if (offset < 0 || offset > 10000) throw new MusicBrainzException(400, "Invalid discography offset.");
        JsonNode detail = fetch("artist/" + id + "?fmt=json&inc=genres");
        if (text(detail, "name") == null) throw invalidResponse();
        JsonNode releases = fetch("release-group?fmt=json&artist=" + id + "&limit=100&offset=" + offset);
        if (!releases.path("release-groups").isArray() || !releases.path("release-group-count").isNumber()) throw invalidResponse();
        var albums = new ArrayList<DiscographyDTO.Album>();
        for (JsonNode item : releases.path("release-groups")) {
            if (text(item, "id") != null && text(item, "title") != null) albums.add(album(item));
        }
        albums.sort(Comparator.comparing(item -> Objects.requireNonNullElse(item.firstReleaseDate(), "9999")));
        var genres = new ArrayList<String>();
        for (JsonNode genre : detail.path("genres")) if (text(genre, "name") != null) genres.add(text(genre, "name"));
        return new DiscographyDTO(
            artist(detail),
            List.copyOf(genres),
            List.copyOf(albums),
            releases.path("release-group-count").asInt(),
            offset
        );
    }

    public DiscographyDTO.AlbumDetail albumDetail(UUID id) {
        JsonNode group = fetch("release-group/" + id + "?fmt=json&inc=releases+artist-credits+url-rels");
        if (text(group, "title") == null) throw invalidResponse();
        JsonNode edition = null;
        // Prefer an official edition with a date, then the earliest available edition.
        for (JsonNode release : group.path("releases")) {
            if (text(release, "id") == null) continue;
            if (edition == null || editionRank(release).compareTo(editionRank(edition)) < 0) edition = release;
        }
        var links = new ArrayList<DiscographyDTO.Link>();
        addListenLinks(group, links);
        String artistName = credits(group);
        if (edition == null) return new DiscographyDTO.AlbumDetail(
            album(group),
            artistName,
            null,
            null,
            null,
            List.of(),
            0,
            List.copyOf(links)
        );
        JsonNode release = fetch("release/" + text(edition, "id") + "?fmt=json&inc=recordings+url-rels");
        if (!release.path("media").isArray()) throw invalidResponse();
        addListenLinks(release, links);
        var tracks = new ArrayList<DiscographyDTO.Track>();
        int count = 0;
        for (JsonNode medium : release.path("media")) {
            count += medium.path("track-count").asInt();
            for (JsonNode track : medium.path("tracks")) {
                String title = text(track, "title");
                if (title == null) title = text(track.path("recording"), "title");
                JsonNode length = track.path("length").isNumber() ? track.path("length") : track.path("recording").path("length");
                tracks.add(
                    new DiscographyDTO.Track(
                        medium.path("position").asInt(1),
                        text(track, "number"),
                        title,
                        length.isNumber() ? length.asLong() : null
                    )
                );
            }
        }
        return new DiscographyDTO.AlbumDetail(
            album(group),
            artistName,
            text(edition, "id"),
            text(release, "date"),
            text(release, "country"),
            List.copyOf(tracks),
            Math.max(count, tracks.size()),
            List.copyOf(links)
        );
    }

    private static String editionRank(JsonNode release) {
        return ("Official".equals(text(release, "status")) ? "0" : "1") + Objects.requireNonNullElse(text(release, "date"), "9999");
    }

    private static void addListenLinks(JsonNode node, List<DiscographyDTO.Link> links) {
        for (JsonNode relation : node.path("relations")) {
            String type = text(relation, "type");
            String url = text(relation.path("url"), "resource");
            if (url == null || type == null || !type.contains("stream")) continue;
            try {
                URI uri = URI.create(url);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null) continue;
                var link = new DiscographyDTO.Link(uri.getHost().replaceFirst("^www\\.", ""), url);
                if (links.stream().noneMatch(existing -> existing.url().equals(url))) links.add(link);
            } catch (IllegalArgumentException ignored) {
                // Broken community-maintained links must not break an album.
            }
        }
    }

    private static String credits(JsonNode node) {
        var names = new ArrayList<String>();
        for (JsonNode credit : node.path("artist-credit")) {
            String name = text(credit, "name");
            if (name != null) names.add(name);
        }
        return String.join(" / ", names);
    }

    private static DiscographyDTO.Album album(JsonNode node) {
        var types = new ArrayList<String>();
        for (JsonNode type : node.path("secondary-types")) if (type.isString()) types.add(type.asString());
        String id = text(node, "id");
        return new DiscographyDTO.Album(
            id,
            text(node, "title"),
            text(node, "first-release-date"),
            text(node, "primary-type"),
            List.copyOf(types),
            "https://coverartarchive.org/release-group/" + id + "/front-500"
        );
    }

    private static ArtistSearchDTO.Artist artist(JsonNode node) {
        return new ArtistSearchDTO.Artist(
            text(node, "id"),
            text(node, "name"),
            text(node, "type"),
            text(node, "country"),
            text(node.path("life-span"), "begin"),
            text(node, "disambiguation")
        );
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .strip();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static MusicBrainzException invalidResponse() {
        return new MusicBrainzException(502, "MusicBrainz returned an invalid response.");
    }

    /** Sequential requests share pacing, including requests made while opening a discography. */
    private synchronized JsonNode fetch(String path) {
        JsonNode cached = cache.getIfPresent(path);
        if (cached != null) return cached;
        try {
            long delay = nextRequest - System.nanoTime();
            if (delay > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(delay);
            nextRequest = System.nanoTime() + Duration.ofMillis(1100).toNanos();
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://musicbrainz.org/ws/2/" + path))
                .header("User-Agent", "DiscographyX/0.0.1 (https://github.com/cesararamayo3068/discographyx)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new MusicBrainzException(404, "This artist or release was not found.");
            if (response.statusCode() != 200) throw new MusicBrainzException(503, "MusicBrainz is temporarily unavailable.");
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isObject()) throw invalidResponse();
            boolean valid = path.startsWith("artist?")
                ? root.path("artists").isArray() && root.path("count").isNumber()
                : path.startsWith("artist/")
                  ? text(root, "name") != null
                  : path.startsWith("release-group?")
                    ? root.path("release-groups").isArray() && root.path("release-group-count").isNumber()
                    : path.startsWith("release-group/")
                      ? text(root, "title") != null
                      : root.path("media").isArray();
            if (!valid) throw invalidResponse();
            cache.put(path, root);
            return root;
        } catch (HttpTimeoutException exception) {
            throw new MusicBrainzException(504, "MusicBrainz took too long to respond.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MusicBrainzException(503, "MusicBrainz request interrupted.");
        } catch (IOException exception) {
            throw new MusicBrainzException(503, "Unable to reach MusicBrainz.");
        } catch (JacksonException exception) {
            throw invalidResponse();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }
}
