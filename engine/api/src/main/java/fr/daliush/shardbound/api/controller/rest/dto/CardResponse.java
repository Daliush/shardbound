package fr.daliush.shardbound.api.controller.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A card as the client shows it, with its rules text line by line, each line with its kind (spec §9). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardResponse(
        String id,
        String name,
        String faction,
        String type,
        Integer cost,
        Integer defense,
        boolean token,
        List<String> keywords,
        List<TextLine> text,
        String flavor,
        List<Step> fracture) {

    public record Step(int step, int cost) {}

    /** {@code kind}: keywords, sacrifice_cost, attack, fracture, effect or ability. */
    public record TextLine(String kind, String text) {}
}
