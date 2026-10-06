package fr.daliush.shardbound.api.domain.bo.view;

/** The target named by an event: a unit by instance and card id, or a player by side. */
public record EventTargetView(String kind, Integer id, String card, String player) {

    public static EventTargetView unit(CardRef unit) {
        return new EventTargetView("unit", unit.id(), unit.card(), null);
    }

    public static EventTargetView player(String side) {
        return new EventTargetView("player", null, null, side);
    }
}
