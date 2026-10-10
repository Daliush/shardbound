package fr.daliush.shardbound.dataset.card;

import static fr.daliush.shardbound.dataset.Phrases.count;
import static fr.daliush.shardbound.dataset.Phrases.shards;

import fr.daliush.shardbound.core.content.AttackAbility;
import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.CardDefinition;
import fr.daliush.shardbound.core.content.FractureStep;
import fr.daliush.shardbound.core.content.RelicCard;
import fr.daliush.shardbound.core.content.SpellCard;
import fr.daliush.shardbound.core.content.TextTemplates;
import fr.daliush.shardbound.core.content.UnitCard;
import fr.daliush.shardbound.core.text.CardText;
import fr.daliush.shardbound.core.text.CardTextRenderer;
import fr.daliush.shardbound.dataset.Phrases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The factual questions: for each card, its faction, cost, keywords, sacrifice cost, defense, attacks, effects,
 * Fracture steps and abilities, each answered from the card's data. What a card does is quoted as
 * {@link CardTextRenderer} prints it, so the key facts match the card text the Arbiter retrieves.
 */
public final class CardQuestions {

    public static final String CATEGORY = "factual";

    private final CardCatalog catalog;
    private final TextTemplates templates;
    private final CardTextRenderer renderer;

    public CardQuestions(CardCatalog catalog, TextTemplates templates) {
        this.catalog = catalog;
        this.templates = templates;
        this.renderer = new CardTextRenderer(catalog, templates);
    }

    /** Every card's questions, tokens excluded since no deck holds them (2.4), sorted by card id. */
    public List<CardQuestion> all() {
        return catalog.all().stream()
                .filter(card -> !card.isToken())
                .sorted(Comparator.comparing(card -> card.id().value()))
                .flatMap(card -> of(card).stream())
                .toList();
    }

    List<CardQuestion> of(CardDefinition card) {
        List<CardQuestion> questions = new ArrayList<>();
        questions.add(faction(card));
        questions.add(cost(card));
        if (!keywords(card).isEmpty()) {
            questions.add(question(card, "keywords", "What keywords does " + card.name() + " have?",
                    keywords(card).stream().map(keyword -> card.name() + " has " + keyword + ".").toList()));
        }
        if (card.sacrificeCost() > 0) {
            questions.add(question(card, "sacrifice-cost", "Do I have to sacrifice a unit to play " + card.name()
                    + "?", List.of("To play " + card.name() + ", you must sacrifice "
                    + count(card.sacrificeCost(), "unit", "units") + ".")));
        }
        switch (card) {
            case UnitCard unit -> questions.addAll(unitQuestions(unit));
            case SpellCard spell -> questions.addAll(spellQuestions(spell));
            case RelicCard relic -> questions.add(abilities(relic, "What does " + relic.name() + " do?"));
        }
        return questions;
    }

    private CardQuestion faction(CardDefinition card) {
        return question(card, "faction", "Which faction does " + card.name() + " belong to?",
                List.of(card.name() + " is " + Phrases.withArticle(Phrases.faction(card.faction())) + " card."));
    }

    /** A Fracture spell has no cost of its own: each step has its own (11.2.1). */
    private CardQuestion cost(CardDefinition card) {
        List<String> facts = card instanceof SpellCard spell && spell.isFracture()
                ? Stream.concat(Stream.of(card.name() + " has Fracture " + spell.fracture().size() + "."),
                        steps(spell).map(step -> "Step " + step.number() + " of " + spell.name() + " costs "
                                + shards(step.step().cost()) + ".")).toList()
                : List.of(card.name() + " costs " + shards(printedCost(card)) + ".");
        return question(card, "cost", "How much does " + card.name() + " cost?", facts);
    }

    private List<CardQuestion> unitQuestions(UnitCard unit) {
        List<CardQuestion> questions = new ArrayList<>();
        questions.add(question(unit, "defense", "How much defense does " + unit.name() + " have?",
                List.of(unit.name() + " has " + unit.defense() + " defense.")));
        String names = unit.attacks().stream().map(CardQuestions::name).collect(Collectors.joining(" and "));
        questions.add(question(unit, "attacks", "What attack abilities does " + unit.name() + " have?",
                List.of(unit.name() + " has " + count(unit.attacks().size(), "attack ability", "attack abilities")
                        + ": " + names + ".")));
        for (int index = 0; index < unit.attacks().size(); index++) {
            questions.add(attack(unit, index));
        }
        if (!unit.abilities().isEmpty()) {
            questions.add(abilities(unit, "Besides its " + (unit.attacks().size() == 1 ? "attack" : "attacks")
                    + ", what does " + unit.name() + " do?"));
        }
        return questions;
    }

    private CardQuestion attack(UnitCard unit, int index) {
        AttackAbility attack = unit.attacks().get(index);
        String attackName = unit.name() + "'s " + name(attack);
        List<String> facts = new ArrayList<>(List.of(
                attackName + " costs " + shards(attack.cost()) + ".",
                attackName + ": \"" + renderer.effects(attack.effects(), unit) + "\""));
        attack.echo().ifPresent(echo -> facts.add(attackName + " has Echo " + echo + "."));
        return question(unit, "attack-" + (index + 1), "What does " + attackName + " do?", facts);
    }

    private List<CardQuestion> spellQuestions(SpellCard spell) {
        if (!spell.isFracture()) {
            return List.of(question(spell, "effects", "What does " + spell.name() + " do?",
                    List.of(spell.name() + ": \"" + renderer.effects(spell.effects(), spell) + "\"")));
        }
        return steps(spell).map(step -> {
            String stepName = "Step " + step.number() + " of " + spell.name();
            return question(spell, "step-" + step.number(), "What does step " + step.number() + " of "
                    + spell.name() + " do?", List.of(stepName + " costs " + shards(step.step().cost()) + ".",
                    stepName + ": \"" + renderer.effects(step.step().effects(), spell) + "\""));
        }).toList();
    }

    /** One key fact per ability, as the card prints it: "Death: Deal 2 damage to your opponent." */
    private CardQuestion abilities(CardDefinition card, String template) {
        List<String> facts = renderer.render(card).lines().stream()
                .filter(line -> line.kind() == CardText.Kind.ABILITY)
                .map(line -> card.name() + ": \"" + line.text() + "\"")
                .toList();
        return question(card, "abilities", template, facts);
    }

    /** "Anchor", "Fracture 3": the card's keywords as its text names them. */
    private List<String> keywords(CardDefinition card) {
        Stream<String> printed = card.keywords().stream()
                .map(keyword -> templates.get("keywords." + keyword.name().toLowerCase(Locale.ROOT)));
        Stream<String> fracture = card instanceof SpellCard spell && spell.isFracture()
                ? Stream.of("Fracture " + spell.fracture().size()) : Stream.empty();
        return Stream.concat(printed, fracture).toList();
    }

    private static Stream<NumberedStep> steps(SpellCard spell) {
        return Stream.iterate(0, index -> index < spell.fracture().size(), index -> index + 1)
                .map(index -> new NumberedStep(index + 1, spell.fracture().get(index)));
    }

    private static int printedCost(CardDefinition card) {
        return switch (card) {
            case UnitCard unit -> unit.cost().orElseThrow();
            case SpellCard spell -> spell.cost().orElseThrow();
            case RelicCard relic -> relic.cost();
        };
    }

    /** An attack without a name is printed "Attack" (the card text's layout). */
    private static String name(AttackAbility attack) {
        return attack.name().orElse("Attack");
    }

    private static CardQuestion question(CardDefinition card, String kind, String template, List<String> facts) {
        String id = card.id().value();
        return new CardQuestion("card/" + id + "/" + kind, CATEGORY, id, template, facts);
    }

    private record NumberedStep(int number, FractureStep step) {}
}
