package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;
import java.util.Map;

/**
 * One event as a seat may see it, one to one with the engine's record: its name in snake_case, its rule IDs,
 * the engine's sentence, then the record's components in {@code fields}, in order.
 */
public record EventView(String type, List<String> rules, String text, Map<String, Object> fields) {
}
