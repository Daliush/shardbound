package fr.daliush.shardbound.api.dao.entities.art;

/** A card's art with the bytes of its image. */
public record CardArtImageEntity(CardArtEntity art, byte[] bytes) {
}
