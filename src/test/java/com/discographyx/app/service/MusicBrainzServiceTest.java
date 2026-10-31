package com.discographyx.app.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class MusicBrainzServiceTest {

    private HttpClient client;
    private HttpResponse<String> response;
    private MusicBrainzService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        service = new MusicBrainzService(client, JsonMapper.builder().build());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
    }

    @Test
    void mapsPartialArtistsAndCachesNormalizedSearches() throws Exception {
        when(response.body()).thenReturn(
            """
            {"count":1,"artists":[{"id":"test-mbid","name":"Death","life-span":{"begin":"1984"}}]}
            """
        );
        var result = service.search(" Death ", 0);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.artists().getFirst().country()).isNull();
        assertThat(result.artists().getFirst().beginDate()).isEqualTo("1984");
        assertThat(service.search("death", 0)).isEqualTo(result);
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().headers().firstValue("User-Agent")).hasValueSatisfying(value ->
            assertThat(value).contains("DiscographyX/")
        );
        assertThat(request.getValue().uri().getQuery()).contains("artist:\"death\"");
    }

    @Test
    void handlesEmptyResults() {
        when(response.body()).thenReturn("{\"count\":0,\"artists\":[]}");
        assertThat(service.search("Unknown", 0).artists()).isEmpty();
    }

    @Test
    void rejectsInvalidQueriesBeforeCallingUpstream() {
        assertThatThrownBy(() -> service.search("  ", 0)).isInstanceOf(MusicBrainzException.class);
        assertThatThrownBy(() -> service.search("Death", -1)).isInstanceOf(MusicBrainzException.class);
        verifyNoInteractions(client);
    }

    @Test
    void translatesUpstreamFailures() {
        when(response.statusCode()).thenReturn(503);
        assertThatThrownBy(() -> service.search("Death", 0)).isInstanceOfSatisfying(MusicBrainzException.class, error ->
            assertThat(error.getStatus()).isEqualTo(503)
        );
    }

    @Test
    void rejectsMalformedResponses() {
        when(response.body()).thenReturn("{\"error\":\"bad response\"}");
        assertThatThrownBy(() -> service.search("Death", 0)).isInstanceOfSatisfying(MusicBrainzException.class, error ->
            assertThat(error.getStatus()).isEqualTo(502)
        );
    }

    @Test
    void translatesTimeouts() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        assertThatThrownBy(() -> service.search("Death", 0)).isInstanceOfSatisfying(MusicBrainzException.class, error ->
            assertThat(error.getStatus()).isEqualTo(504)
        );
    }

    @Test
    void prioritizesFullNamesAndRemovesIndependentWordMatches() {
        when(response.body()).thenReturn(
            """
            {"count":195,"artists":[
              {"id":"1","name":"Malevolent Creation","country":"US"},
              {"id":"2","name":"The Creation"},
              {"id":"3","name":"Creation Rebel"},
              {"id":"4","name":"Malevolent Creation","country":"GB"},
              {"id":"5","name":"Malevolent Creation Tribute"}]}
            """
        );
        var result = service.search("MALEVOLENT CREATION", 0);
        assertThat(result.artists())
            .extracting(item -> item.musicBrainzId())
            .containsExactly("1", "4");
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void limitsPartialNameMatchesToSix() {
        when(response.body()).thenReturn(
            """
            {"count":8,"artists":[{"id":"1","name":"Death One"},{"id":"2","name":"Death Two"},
            {"id":"3","name":"Death Three"},{"id":"4","name":"Death Four"},{"id":"5","name":"Death Five"},
            {"id":"6","name":"Death Six"},{"id":"7","name":"Death Seven"},{"id":"8","name":"Death Eight"}]}
            """
        );
        assertThat(service.search("Death", 0).artists()).hasSize(6);
    }

    @Test
    void returnsChronologicalReleaseGroupsWithoutInventingMissingData() {
        when(response.body()).thenReturn(
            """
            {"id":"artist-id","name":"Death","genres":[{"name":"death metal"}]}
            """,
            """
            {"release-group-count":2,"release-groups":[
              {"id":"later","title":"Human","first-release-date":"1991","primary-type":"Album","secondary-types":[]},
              {"id":"earlier","title":"Scream Bloody Gore","first-release-date":"1987","primary-type":"Album","secondary-types":[]}]}
            """
        );
        var result = service.discography(UUID.randomUUID(), 0);
        assertThat(result.artist().country()).isNull();
        assertThat(result.genres()).containsExactly("death metal");
        assertThat(result.albums())
            .extracting(item -> item.title())
            .containsExactly("Scream Bloody Gore", "Human");
        assertThat(result.albums().getFirst().coverUrl()).contains("/release-group/earlier/front-500");
    }

    @Test
    void loadsOneOfficialEditionAndPreservesDiscPositionsAndDurations() {
        when(response.body()).thenReturn(
            """
            {"id":"group-id","title":"The Ten Commandments","first-release-date":"1991",
            "artist-credit":[{"name":"Malevolent Creation"}],"primary-type":"Album",
            "releases":[{"id":"bootleg","date":"1990","status":"Bootleg"},{"id":"official","date":"1991","status":"Official"}],
            "relations":[{"type":"free streaming","url":{"resource":"https://band.example/album"}},
            {"type":"free streaming","url":{"resource":"javascript:alert(1)"}}]}
            """,
            """
            {"date":"1991","country":"US","media":[{"position":1,"track-count":1,"tracks":[{"number":"1","title":"Memorial Arrangements","length":148000}]},
            {"position":2,"track-count":1,"tracks":[{"number":"1","recording":{"title":"Premature Burial"}}]}]}
            """
        );
        var detail = service.albumDetail(UUID.randomUUID());
        assertThat(detail.releaseId()).isEqualTo("official");
        assertThat(detail.trackCount()).isEqualTo(2);
        assertThat(detail.tracks().get(1).disc()).isEqualTo(2);
        assertThat(detail.tracks().get(1).length()).isNull();
        assertThat(detail.listenLinks()).hasSize(1);
    }
}
