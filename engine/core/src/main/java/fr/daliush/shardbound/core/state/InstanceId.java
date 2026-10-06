package fr.daliush.shardbound.core.state;

/** One physical copy of a card in one game, such as {@code #61}. */
public record InstanceId(int value) implements Comparable<InstanceId> {

    public static InstanceId of(int value) {
        return new InstanceId(value);
    }

    @Override
    public int compareTo(InstanceId other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "#" + value;
    }
}
