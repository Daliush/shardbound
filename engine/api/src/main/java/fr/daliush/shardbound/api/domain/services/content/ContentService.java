package fr.daliush.shardbound.api.domain.services.content;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** The game content a client loads once: every card, and the decks by id. */
@Service
public class ContentService {

    private final Content content;

    public ContentService(Content content) {
        this.content = content;
    }

    public List<CardDefinition> cards() {
        return List.copyOf(content.catalog().all());
    }

    public List<Deck> decks() {
        return content.decks().values().stream()
                .sorted(Comparator.comparing((Deck deck) -> deck.id().value()))
                .toList();
    }
}
