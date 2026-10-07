package fr.daliush.shardbound.api.controller.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A card as the client shows it. Its rendered {@code text} arrives with slice 3. */
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
        String flavor,
        List<Step> fracture) {

    public record Step(int step, int cost) {}
}
