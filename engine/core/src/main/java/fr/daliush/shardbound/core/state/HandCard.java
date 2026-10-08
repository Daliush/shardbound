package fr.daliush.shardbound.core.state;

/**
 * A card in hand. {@code fractureStep} is the next Fracture step, 0-based (11.2);
 * {@code lastFractureTurn} is the turn its last step was played, 0 if never (11.2.3).
 */
public record HandCard(CardInstance card, int fractureStep, int lastFractureTurn) {

    public static HandCard fresh(CardInstance card) {
        return new HandCard(card, 0, 0);
    }

    public InstanceId id() {
        return card.id();
    }

    /** 11.2.2, 11.2.3: back in hand for its next step, which must wait for another turn. */
    public HandCard advancedOn(int turn) {
        return new HandCard(card, fractureStep + 1, turn);
    }
}
