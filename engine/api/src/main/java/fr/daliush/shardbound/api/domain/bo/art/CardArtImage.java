package fr.daliush.shardbound.api.domain.bo.art;

/** A card's art as the server sends it: the image, its media type, and a version that changes with the image. */
public record CardArtImage(String cardId, String mediaType, String version, byte[] bytes) {
}
