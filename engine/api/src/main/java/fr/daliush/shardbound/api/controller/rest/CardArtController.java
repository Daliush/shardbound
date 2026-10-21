package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.domain.bo.art.CardArtImage;
import fr.daliush.shardbound.api.domain.services.content.CardArtService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A card's art, for a store the server serves itself: the address {@code GET /api/cards} hands out names the image's
 * version, so the image at that address never changes and a browser keeps it. Without the current version, the
 * browser asks again each time, and gets a 304 while its copy is still the current one.
 */
@RestController
@RequestMapping("/api/cards")
public class CardArtController {

    private static final CacheControl FOREVER = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final CardArtService art;

    public CardArtController(CardArtService art) {
        this.art = art;
    }

    @GetMapping("/{id}/art")
    public ResponseEntity<byte[]> findArt(@PathVariable String id,
                                          @RequestParam(name = "v", required = false) String version) {
        CardArtImage image = art.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(image.version().equals(version) ? FOREVER : CacheControl.noCache())
                .eTag(image.version())
                .body(image.bytes());
    }
}
