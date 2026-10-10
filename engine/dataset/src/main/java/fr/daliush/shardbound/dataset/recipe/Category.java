package fr.daliush.shardbound.dataset.recipe;

/** The question categories that recipes produce (design doc §6.1); a tag for balance and per-category scores. */
public enum Category {
    INTERACTION("interaction"),
    MULTI_STEP("multi-step");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
