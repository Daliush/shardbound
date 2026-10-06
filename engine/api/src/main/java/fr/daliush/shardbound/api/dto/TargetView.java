package fr.daliush.shardbound.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A target of an action: a unit, a relic or a graveyard card by instance id, or a player by side. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TargetView(String kind, Integer id, String player) {

    public static TargetView card(String kind, int id) {
        return new TargetView(kind, id, null);
    }

    public static TargetView player(String side) {
        return new TargetView("player", null, side);
    }
}
