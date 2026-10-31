package com.discographyx.app.web.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.discographyx.app.service.MusicBrainzException;
import com.discographyx.app.service.MusicBrainzService;
import com.discographyx.app.service.dto.ArtistSearchDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MusicBrainzResourceTest {

    private final MusicBrainzService service = mock(MusicBrainzService.class);

    @Test
    void returnsSearchPage() throws Exception {
        when(service.search("Death", 0)).thenReturn(new ArtistSearchDTO(List.of(), 0, 0, 20));
        MockMvcBuilders.standaloneSetup(new MusicBrainzResource(service))
            .build()
            .perform(get("/api/music/artists").param("query", "Death"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void reportsThrottlingAndRetryHeader() throws Exception {
        when(service.search("Death", 0)).thenThrow(new MusicBrainzException(429, "Please wait."));
        MockMvcBuilders.standaloneSetup(new MusicBrainzResource(service))
            .build()
            .perform(get("/api/music/artists").param("query", "Death"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    void requiresQueryParameter() throws Exception {
        MockMvcBuilders.standaloneSetup(new MusicBrainzResource(service))
            .build()
            .perform(get("/api/music/artists"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
