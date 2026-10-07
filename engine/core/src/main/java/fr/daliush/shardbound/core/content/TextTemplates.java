package fr.daliush.shardbound.core.content;

import java.util.Map;
import java.util.Set;

/**
 * The wording of card texts, from {@code content/cards/text-templates.json}, by dotted key:
 * {@code "layout.attack_named"}, {@code "targets.ally_unit"}, {@code "effects.heal.player"}…
 */
public record TextTemplates(Map<String, String> wording) {

    public TextTemplates {
        wording = Map.copyOf(wording);
    }

    public String get(String key) {
        String template = wording.get(key);
        if (template == null) {
            throw new ContentException("text-templates.json has no wording for '" + key + "'");
        }
        return template;
    }

    public Set<String> keys() {
        return wording.keySet();
    }
}
