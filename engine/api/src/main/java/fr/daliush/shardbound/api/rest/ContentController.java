package fr.daliush.shardbound.api.rest;

import fr.daliush.shardbound.api.session.BotRoster;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a client loads once: the cards, the decks and the bots it can play against. */
@RestController
@RequestMapping("/api")
class ContentController {

    private final Content content;
    private final BotRoster bots;

    ContentController(Content content, BotRoster bots) {
        this.content = content;
        this.bots = bots;
    }

    @GetMapping("/cards")
    List<CardResponse> cards() {
        return content.catalog().all().stream().map(CardResponse::of).toList();
    }

    @GetMapping("/decks")
    List<DeckResponse> decks() {
        return content.decks().values().stream()
                .sorted(Comparator.comparing((Deck deck) -> deck.id().value()))
                .map(DeckResponse::of)
                .toList();
    }

    @GetMapping("/bots")
    List<String> bots() {
        return bots.names();
    }
}
