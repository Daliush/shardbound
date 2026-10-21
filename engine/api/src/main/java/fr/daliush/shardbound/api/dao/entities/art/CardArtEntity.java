package fr.daliush.shardbound.api.dao.entities.art;

/** A card's art in the store: its media type, and a version that changes whenever the image does. */
public record CardArtEntity(String cardId, String mediaType, String version) {
}
