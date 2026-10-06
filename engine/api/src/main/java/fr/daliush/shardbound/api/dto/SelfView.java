package fr.daliush.shardbound.api.dto;

import java.util.List;

/** The viewer's side: everything but the order of their deck. */
public record SelfView(
        String faction,
        int hp,
        int maxHp,
        int shards,
        int maxShards,
        int lockedNextTurn,
        int fatigue,
        int deckCount,
        List<HandCardView> hand,
        boolean mulliganDecided,
        List<UnitView> units,
        List<RelicView> relics,
        List<CardRef> graveyard) {
}
