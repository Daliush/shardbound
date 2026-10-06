package fr.daliush.shardbound.api.dto;

import java.util.List;

/** What the viewer must decide now. A client answers with the {@code index} of one of the actions. */
public record DecisionView(String id, String kind, String prompt, List<ActionView> actions) {
}
