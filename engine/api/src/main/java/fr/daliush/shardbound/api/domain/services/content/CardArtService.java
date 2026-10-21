package fr.daliush.shardbound.api.domain.services.content;

import fr.daliush.shardbound.api.domain.bo.art.CardArtImage;
import fr.daliush.shardbound.api.domain.bo.error.ContentException;
import fr.daliush.shardbound.api.domain.ports.CardArtPort;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** The cards' art: where a client downloads it, and the image when the server is the one serving it. */
@Service
public class CardArtService {

    private final CardArtPort art;

    public CardArtService(CardArtPort art) {
        this.art = art;
    }

    public Optional<URI> downloadUrl(String cardId) {
        return art.downloadUrl(cardId);
    }

    public CardArtImage image(String cardId) {
        return art.image(cardId).orElseThrow(() -> new ContentException.UnknownArt(cardId));
    }
}
