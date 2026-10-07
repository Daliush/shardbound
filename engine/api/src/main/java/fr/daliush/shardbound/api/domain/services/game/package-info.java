/**
 * Creating, joining and playing games (spec §13.4). Every change loads the session, applies it and saves it
 * with an optimistic lock through {@link GameSaver}, so any instance can process any message.
 */
package fr.daliush.shardbound.api.domain.services.game;
