package fr.daliush.shardbound.core.view;

import fr.daliush.shardbound.core.content.Faction;
import fr.daliush.shardbound.core.state.CardInstance;
import fr.daliush.shardbound.core.state.HandCard;
import fr.daliush.shardbound.core.state.Relic;
import fr.daliush.shardbound.core.state.Shards;
import fr.daliush.shardbound.core.state.Unit;
import java.util.List;

/** The viewer's own side: everything but the order of their deck. */
public record SelfState(Faction faction, int hp, int maxHp, Shards shards, int fatigue, int deckCount,
                        List<HandCard> hand, List<Unit> units, List<Relic> relics, List<CardInstance> graveyard,
                        boolean mulliganDecided) {
}
