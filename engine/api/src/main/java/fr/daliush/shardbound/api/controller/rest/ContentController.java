package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.controller.mappers.rest.CardResponseMapper;
import fr.daliush.shardbound.api.controller.mappers.rest.DeckResponseMapper;
import fr.daliush.shardbound.api.controller.rest.dto.CardResponse;
import fr.daliush.shardbound.api.controller.rest.dto.DeckResponse;
import fr.daliush.shardbound.api.domain.services.bot.BotRoster;
import fr.daliush.shardbound.api.domain.services.content.ContentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a client loads once: the cards, the decks and the bots it can play against. */
@RestController
@RequestMapping("/api")
public class ContentController {

    private final ContentService content;
    private final BotRoster bots;
    private final CardResponseMapper cards;
    private final DeckResponseMapper decks;

    public ContentController(ContentService content, BotRoster bots, CardResponseMapper cards,
                             DeckResponseMapper decks) {
        this.content = content;
        this.bots = bots;
        this.cards = cards;
        this.decks = decks;
    }

    @GetMapping("/cards")
    public List<CardResponse> cards() {
        return content.cards().stream().map(card -> cards.toResponse(card, content.text(card))).toList();
    }

    @GetMapping("/decks")
    public List<DeckResponse> decks() {
        return content.decks().stream().map(decks::toResponse).toList();
    }

    @GetMapping("/bots")
    public List<String> bots() {
        return bots.names();
    }
}
