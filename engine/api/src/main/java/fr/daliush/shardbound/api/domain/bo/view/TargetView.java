package fr.daliush.shardbound.api.domain.bo.view;

/** A target of an action: a unit, a relic or a graveyard card by instance id, or a player by side. */
public record TargetView(String kind, Integer id, String player) {

    public static TargetView card(String kind, int id) {
        return new TargetView(kind, id, null);
    }

    public static TargetView player(String side) {
        return new TargetView("player", null, side);
    }
}
