package fr.daliush.shardbound.api.controller.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeckResponse(String id, String name, String description, String faction, List<Entry> cards) {

    public record Entry(String card, int count) {}
}
