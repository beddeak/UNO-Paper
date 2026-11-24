package io.github.beddeak.uno.game;

import io.github.beddeak.uno.card.DeckMix;

import java.util.Objects;

public record Rules(int handSize, boolean stacking, boolean drawUntilPlayable, int callPenalty, DeckMix deck) {

    public static final Rules DEFAULT = new Rules(7, true, true, 2);

    public Rules {
        if (handSize < 1 || handSize > 20) {
            throw new IllegalArgumentException("hand-size must be 1..20");
        }
        if (callPenalty < 0 || callPenalty > 10) {
            throw new IllegalArgumentException("call-penalty must be 0..10");
        }
        Objects.requireNonNull(deck, "deck");
    }

    public Rules(int handSize, boolean stacking, boolean drawUntilPlayable, int callPenalty) {
        this(handSize, stacking, drawUntilPlayable, callPenalty, DeckMix.STANDARD);
    }
}
