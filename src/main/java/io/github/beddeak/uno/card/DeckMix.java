package io.github.beddeak.uno.card;

/** Copies of the adjustable cards in one shuffled pack. Colored counts are spread over the four colors. */
public record DeckMix(int sixes, int drawTwos, int drawFours, int drawTens, int drawNinetyNines) {

    public static final int MAX = 40;
    public static final DeckMix STANDARD = new DeckMix(8, 8, 4, 0, 0);

    public DeckMix {
        for (int count : new int[] {sixes, drawTwos, drawFours, drawTens, drawNinetyNines}) {
            if (count < 0 || count > MAX) {
                throw new IllegalArgumentException("deck counts must be 0.." + MAX);
            }
        }
    }
}
