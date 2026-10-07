package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;

/** What changed for a seat at one version: the new view, and that save's events. */
public record UpdateView(GameView view, List<EventView> events) {

    public int version() {
        return view.version();
    }
}
