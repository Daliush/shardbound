/**
 * Game sessions (spec §13.4). No instance owns a game: sessions sit behind {@link GameRepository} with versioned
 * saves, and {@link GameUpdates} tells every instance that a game moved. Nothing here depends on a transport.
 */
package fr.daliush.shardbound.api.session;
