package fr.daliush.shardbound.core.content.json;

import fr.daliush.shardbound.core.content.ContentException;
import fr.daliush.shardbound.core.content.TextTemplates;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Reads {@code text-templates.json}: nested objects of strings, flattened to dotted keys; {@code $comment} skipped. */
public final class TextTemplatesParser {

    private static final String COMMENT = "$comment";

    public TextTemplates parse(JsonNode node, String location) {
        Map<String, String> wording = new LinkedHashMap<>();
        flatten(node, "", location, wording);
        return new TextTemplates(wording);
    }

    private static void flatten(JsonNode node, String prefix, String location, Map<String, String> wording) {
        if (!node.isObject()) {
            throw new ContentException(location + ": '" + prefix + "' must be an object of wordings");
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (prefix.isEmpty() && field.getKey().equals(COMMENT)) {
                continue;
            }
            String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            if (field.getValue().isString()) {
                wording.put(key, field.getValue().stringValue());
            } else {
                flatten(field.getValue(), key, location, wording);
            }
        }
    }
}
