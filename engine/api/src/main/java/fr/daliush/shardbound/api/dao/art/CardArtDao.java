package fr.daliush.shardbound.api.dao.art;

import fr.daliush.shardbound.api.dao.entities.art.CardArtEntity;
import fr.daliush.shardbound.api.dao.entities.art.CardArtImageEntity;
import java.util.Optional;

/** Where the cards' art lives. Implementations: {@code CardArtDaoFile}, later a bucket. */
public interface CardArtDao {

    /** The card's art, without its bytes; none for a card that has no art. */
    Optional<CardArtEntity> find(String cardId);

    /** The card's art with its bytes; none for a card that has no art. */
    Optional<CardArtImageEntity> read(String cardId);
}
