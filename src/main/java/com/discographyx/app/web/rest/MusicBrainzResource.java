package com.discographyx.app.web.rest;

import com.discographyx.app.service.MusicBrainzException;
import com.discographyx.app.service.MusicBrainzService;
import com.discographyx.app.service.dto.ArtistSearchDTO;
import com.discographyx.app.service.dto.DiscographyDTO;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
public class MusicBrainzResource {

    private final MusicBrainzService service;

    public MusicBrainzResource(MusicBrainzService service) {
        this.service = service;
    }

    @GetMapping("/artists")
    public ArtistSearchDTO search(@RequestParam String query, @RequestParam(defaultValue = "0") int offset) {
        return service.search(query, offset);
    }

    @GetMapping("/artists/{id}")
    public DiscographyDTO discography(@PathVariable UUID id, @RequestParam(defaultValue = "0") int offset) {
        return service.discography(id, offset);
    }

    @GetMapping("/albums/{id}")
    public DiscographyDTO.AlbumDetail album(@PathVariable UUID id) {
        return service.albumDetail(id);
    }

    @ExceptionHandler(MusicBrainzException.class)
    public ResponseEntity<ProblemDetail> handleMusicBrainzFailure(MusicBrainzException exception) {
        var response = ResponseEntity.status(exception.getStatus());
        if (exception.getStatus() == 429 || exception.getStatus() == 503) response.header("Retry-After", "2");
        return response.body(
            ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(exception.getStatus()), exception.getMessage())
        );
    }
}
