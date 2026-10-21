package fr.daliush.shardbound.api.adapter.art;

import fr.daliush.shardbound.api.dao.art.CardArtDao;
import fr.daliush.shardbound.api.dao.entities.art.CardArtEntity;
import fr.daliush.shardbound.api.dao.entities.art.CardArtImageEntity;
import fr.daliush.shardbound.api.domain.bo.art.CardArtImage;
import fr.daliush.shardbound.api.domain.ports.CardArtPort;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The cards' art through the art DAO. A browser cannot download from a folder, so the server sends the folder's
 * images itself, and an image's address is the server's own, {@code GET /api/cards/{id}/art}, with the image's
 * version: a browser keeps the image until it changes. Online, a bucket serves its images, and the address handed
 * out becomes the bucket's.
 */
@Component
public class CardArtAdapter implements CardArtPort {

    private static final String SERVED_AT = "/api/cards/%s/art?v=%s";

    private final CardArtDao dao;

    public CardArtAdapter(CardArtDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<URI> downloadUrl(String cardId) {
        return dao.find(cardId).map(CardArtAdapter::servedAt);
    }

    @Override
    public Optional<CardArtImage> image(String cardId) {
        return dao.read(cardId).map(CardArtAdapter::toImage);
    }

    private static URI servedAt(CardArtEntity art) {
        return URI.create(SERVED_AT.formatted(art.cardId(), art.version()));
    }

    private static CardArtImage toImage(CardArtImageEntity entity) {
        CardArtEntity art = entity.art();
        return new CardArtImage(art.cardId(), art.mediaType(), art.version(), entity.bytes());
    }
}
