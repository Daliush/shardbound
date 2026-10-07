package fr.daliush.shardbound.api.domain.bo.view;

import java.util.OptionalInt;

/** {@code cost} is what playing the card costs now, without Overcharge (6.8); {@code fractureStep} is 1-based. */
public record HandCardView(int id, String card, int cost, OptionalInt fractureStep) {
}
