package fr.daliush.shardbound.core.rules.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Every way to pick some items, keeping their order: the combinations come in lexicographic order (spec §6.2). */
final class Combinations {

    private Combinations() {
    }

    static <T> List<List<T>> of(List<T> items, int size) {
        return withTotal(items, item -> 1, size);
    }

    /** The picks whose weights add up to exactly {@code total}. */
    static <T> List<List<T>> withTotal(List<T> items, ToIntFunction<T> weight, int total) {
        List<List<T>> found = new ArrayList<>();
        collect(items, weight, 0, total, new ArrayList<>(), found);
        return found;
    }

    private static <T> void collect(List<T> items, ToIntFunction<T> weight, int from, int left, List<T> picked,
                                    List<List<T>> found) {
        if (left == 0) {
            found.add(List.copyOf(picked));
            return;
        }
        for (int index = from; index < items.size(); index++) {
            T item = items.get(index);
            if (weight.applyAsInt(item) <= left) {
                picked.add(item);
                collect(items, weight, index + 1, left - weight.applyAsInt(item), picked, found);
                picked.removeLast();
            }
        }
    }
}
