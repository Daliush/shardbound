package fr.daliush.shardbound.api.domain.services.content;

import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.Deck;
import fr.daliush.shardbound.core.text.CardText;
import fr.daliush.shardbound.core.text.CardTextRenderer;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** The game content a client loads once: every card with its rules text, and the decks by id. */
@Service
public class ContentService {

    private final Content content;
    private final CardTextRenderer texts;

    public ContentService(Content content) {
        this.content = content;
        this.texts = new CardTextRenderer(content.catalog(), content.textTemplates());
    }

    public List<CardDefinition> cards() {
        return List.copyOf(content.catalog().all());
    }

    /** Written from the card's data by the engine (spec §9). */
    public CardText text(CardDefinition card) {
        return texts.render(card);
    }

    public List<Deck> decks() {
        return content.decks().values().stream()
                .sorted(Comparator.comparing((Deck deck) -> deck.id().value()))
                .toList();
    }
}
