package fr.daliush.shardbound.api.domain.ports;

import fr.daliush.shardbound.api.domain.bo.art.CardArtImage;
import java.net.URI;
import java.util.Optional;

/** What the domain needs from the store of the cards' art, whatever it is: a folder today, a bucket online. */
public interface CardArtPort {

    /** Where a client downloads the card's art; none for a card without art, which the client draws itself. */
    Optional<URI> downloadUrl(String cardId);

    /** The card's art, for a store the server serves itself. */
    Optional<CardArtImage> image(String cardId);
}
