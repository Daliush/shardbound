package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;

/** The opponent's side: public zones, and only the size of their hand. */
public record OpponentView(
        String faction,
        int hp,
        int maxHp,
        int shards,
        int maxShards,
        int lockedNextTurn,
        int fatigue,
        int deckCount,
        int handCount,
        List<UnitView> units,
        List<RelicView> relics,
        List<CardRef> graveyard) {
}
