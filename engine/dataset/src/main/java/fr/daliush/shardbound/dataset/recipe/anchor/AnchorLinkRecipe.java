package fr.daliush.shardbound.dataset.recipe.anchor;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Effect;
import fr.daliush.shardbound.core.content.Keyword;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.event.GameEvent;
import fr.daliush.shardbound.core.scenario.ScenarioBuilder;
import fr.daliush.shardbound.core.state.GameState;
import fr.daliush.shardbound.core.state.PlayerId;
import fr.daliush.shardbound.dataset.recipe.Answer;
import fr.daliush.shardbound.dataset.recipe.Cards;
import fr.daliush.shardbound.dataset.recipe.Category;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import fr.daliush.shardbound.dataset.recipe.Scenario;
import fr.daliush.shardbound.dataset.recipe.Side;
import fr.daliush.shardbound.dataset.recipe.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * An opponent's Anchor unit linked to another unit, and a Destroy spell. Destruction is not shared (11.5.3), the
 * protected unit stays (11.3.2) and the link breaks when either unit leaves the board (11.5.4). A spell that picks
 * its target aims at the Anchor unit, then at its partner.
 */
public final class AnchorLinkRecipe implements Recipe {

    private static final PlayerId ASKER = PlayerId.P1;

    private enum Aim {
        ANCHOR, PARTNER, ALL;

        String label() {
            return "aimed-at-" + name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public String interaction() {
        return "anchor+link";
    }

    @Override
    public Category category() {
        return Category.INTERACTION;
    }

    @Override
    public List<Scenario> scenarios(CardCatalog catalog) {
        List<Scenario> scenarios = new ArrayList<>();
        for (UnitCard anchored : Cards.unitsWith(catalog, Keyword.ANCHOR)) {
            for (Source source : Cards.sources(catalog, Effect.Destroy.class)) {
                if (Side.hitBy(source.target()).contains(Side.OPPONENTS)) {
                    for (Aim aim : aims(source)) {
                        for (Protection protection : Protection.values()) {
                            scenarios.add(scenario(catalog, anchored, source, aim, protection));
                        }
                    }
                }
            }
        }
        return scenarios;
    }

    /** The events that answer the question. */
    private static boolean answers(GameEvent event) {
        return event instanceof GameEvent.AnchorPrevented || event instanceof GameEvent.UnitDestroyed
                || event instanceof GameEvent.LinkBroken;
    }

    private Scenario scenario(CardCatalog catalog, UnitCard anchored, Source source, Aim aim,
            Protection protection) {
        String unit = anchored.id().value();
        String partner = Cards.fillerName(catalog);
        GameState start = source.prepare(ScenarioBuilder.of(catalog), ASKER)
                .unit(ASKER.opponent(), unit, setUp -> protection.setUp(setUp, Side.OPPONENTS))
                .unit(ASKER.opponent(), Cards.FILLER)
                .link(unit, Cards.FILLER)
                .build();
        String question = protection.situation(anchored.name(), Side.OPPONENTS) + ", and it is linked to their "
                + partner + ". If I " + source.phrase(aim == Aim.PARTNER ? partner : anchored.name())
                + ", what happens?";
        List<String> contextRules = Stream.concat(protection.contextRules().stream(), Stream.of("11.5.3")).toList();
        return new Scenario(String.join("/", interaction(), protection.label(), aim.label(), unit, source.id()),
                start, List.of(source.play(aim == Aim.PARTNER ? Cards.FILLER : unit)), ASKER, question,
                new Answer.Events(AnchorLinkRecipe::answers), contextRules);
    }

    private static List<Aim> aims(Source source) {
        return source.target().isChosen() ? List.of(Aim.ANCHOR, Aim.PARTNER) : List.of(Aim.ALL);
    }
}
