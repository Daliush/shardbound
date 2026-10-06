package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;

/**
 * One option of a decision, described for display: the client filters these, never builds one.
 * Only the fields of the action's {@code type} are present.
 */
public record ActionView(
        int index,
        String type,
        String label,
        Integer card,
        Boolean overcharge,
        List<TargetView> targets,
        List<Integer> sacrificed,
        Integer attacker,
        Integer attackIndex,
        Integer interceptor,
        List<Integer> cards,
        List<Integer> order) {

    public static ActionView plain(int index, String type, String label) {
        return new ActionView(index, type, label, null, null, null, null, null, null, null, null, null);
    }

    public static ActionView play(int index, String label, int card, boolean overcharge, List<TargetView> targets,
                                  List<Integer> sacrificed) {
        return new ActionView(index, "play", label, card, overcharge, targets, sacrificed, null, null, null, null,
                null);
    }

    public static ActionView attack(int index, String label, int attacker, int attackIndex,
                                    List<TargetView> targets) {
        return new ActionView(index, "attack", label, null, null, targets, null, attacker, attackIndex, null, null,
                null);
    }

    public static ActionView intercept(int index, String label, int interceptor) {
        return new ActionView(index, "intercept", label, null, null, null, null, null, null, interceptor, null,
                null);
    }

    public static ActionView chooseTarget(int index, String label, TargetView target) {
        return new ActionView(index, "choose_target", label, null, null, List.of(target), null, null, null, null,
                null, null);
    }

    public static ActionView chooseCards(int index, String label, List<Integer> cards) {
        return new ActionView(index, "choose_cards", label, null, null, null, null, null, null, null, cards, null);
    }

    public static ActionView chooseOrder(int index, String label, List<Integer> order) {
        return new ActionView(index, "choose_order", label, null, null, null, null, null, null, null, null, order);
    }
}
