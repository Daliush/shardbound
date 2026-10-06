package fr.daliush.shardbound.core.content.json;

import fr.daliush.shardbound.core.content.ContentException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Reads one JSON object strictly: missing fields, wrong types and unknown fields are errors. */
final class JsonObjectReader {

    private final JsonNode node;
    private final String location;
    private final Set<String> readFields = new HashSet<>();

    JsonObjectReader(JsonNode node, String location) {
        this.node = node;
        this.location = location;
        if (!node.isObject()) {
            throw error("must be a JSON object");
        }
    }

    boolean has(String field) {
        return node.has(field);
    }

    String string(String field) {
        JsonNode value = required(field);
        if (!value.isString() || value.stringValue().isEmpty()) {
            throw error("'" + field + "' must be a non-empty string");
        }
        return value.stringValue();
    }

    Optional<String> optionalString(String field) {
        return has(field) ? Optional.of(string(field)) : Optional.empty();
    }

    int integer(String field, int min, int max) {
        JsonNode value = required(field);
        if (!value.isInt() || value.intValue() < min || value.intValue() > max) {
            throw error("'" + field + "' must be an integer from " + min + " to " + max);
        }
        return value.intValue();
    }

    OptionalInt optionalInteger(String field, int min, int max) {
        return has(field) ? OptionalInt.of(integer(field, min, max)) : OptionalInt.empty();
    }

    /** An optional boolean that can only be {@code true} when present. */
    boolean trueFlag(String field) {
        if (!has(field)) {
            return false;
        }
        JsonNode value = required(field);
        if (!value.isBoolean() || !value.booleanValue()) {
            throw error("'" + field + "' must be true when present");
        }
        return true;
    }

    <E extends Enum<E>> E enumValue(String field, Class<E> type) {
        return parseEnum(string(field), field, type);
    }

    <E extends Enum<E>> List<E> enumList(String field, Class<E> type) {
        List<E> values = new ArrayList<>();
        for (JsonNode item : array(field)) {
            if (!item.isString()) {
                throw error("'" + field + "' must only contain strings");
            }
            E value = parseEnum(item.stringValue(), field, type);
            if (values.contains(value)) {
                throw error("'" + field + "' lists " + item.stringValue() + " twice");
            }
            values.add(value);
        }
        return values;
    }

    List<JsonObjectReader> objects(String field) {
        if (!has(field)) {
            throw error("missing field '" + field + "'");
        }
        List<JsonObjectReader> objects = new ArrayList<>();
        int index = 0;
        for (JsonNode item : array(field)) {
            objects.add(new JsonObjectReader(item, location + "." + field + "[" + index++ + "]"));
        }
        return objects;
    }

    List<JsonObjectReader> optionalObjects(String field) {
        return has(field) ? objects(field) : List.of();
    }

    void finish() {
        for (String field : node.propertyNames()) {
            if (!readFields.contains(field)) {
                throw error("unknown field '" + field + "'");
            }
        }
    }

    ContentException error(String message) {
        return new ContentException(location + ": " + message);
    }

    private JsonNode required(String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw error("missing field '" + field + "'");
        }
        readFields.add(field);
        return value;
    }

    private List<JsonNode> array(String field) {
        if (!has(field)) {
            return List.of();
        }
        JsonNode value = required(field);
        if (!value.isArray()) {
            throw error("'" + field + "' must be an array");
        }
        return List.copyOf(value.values());
    }

    private <E extends Enum<E>> E parseEnum(String text, String field, Class<E> type) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(text)) {
                return constant;
            }
        }
        throw error("'" + field + "' has an unknown value '" + text + "'");
    }
}
