package fr.daliush.shardbound.core.view;

import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Shards;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** The opponent's side: public zones, and only the size of their hand (3.3). */
public record OpponentState(Faction faction, int hp, int maxHp, Shards shards, int fatigue, int deckCount,
                            int handCount, List<Unit> units, List<Relic> relics, List<CardInstance> graveyard) {
}
