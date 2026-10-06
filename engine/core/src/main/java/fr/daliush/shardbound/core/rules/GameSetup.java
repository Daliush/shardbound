package fr.daliush.shardbound.core.rules;

import fr.daliush.shardbound.core.content.Deck;

/** Everything a game depends on: with the same setup and the same actions, a game replays exactly. */
public record GameSetup(Deck p1Deck, Deck p2Deck, long seed) {
}
