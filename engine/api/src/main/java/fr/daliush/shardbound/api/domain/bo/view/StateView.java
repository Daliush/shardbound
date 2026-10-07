package fr.daliush.shardbound.api.domain.bo.view;

import java.util.List;

/** Everything a seat needs to draw the game from scratch: the current view and the whole history. */
public record StateView(GameView view, List<EventView> history) {

    public int version() {
        return view.version();
    }
}
