package io.github.beddeak.uno;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.github.beddeak.uno.card.CardType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HandLayout {

    static final int MENU_SIZE = 54;
    private static final int[] WILD_SLOTS = {53, 44};
    private static final List<Card> WILD_PRIORITY = List.of(Card.wildDrawNinetyNine(), Card.wildDrawTen(), Card.wildDrawFour(), Card.wild());
    private static final Card[] MENU = buildMenu();

    private HandLayout() {
    }

    static Card menuCard(int slot) {
        return slot >= 0 && slot < MENU_SIZE ? MENU[slot] : null;
    }

    /** Colored cards keep fixed slots; the two wild slots show the wild kinds in hand, strongest first. */
    static Card[] menu(Set<Card> hand) {
        Card[] menu = MENU.clone();
        int next = 0;
        for (Card wild : WILD_PRIORITY) {
            if (hand.contains(wild) && next < WILD_SLOTS.length) {
                menu[WILD_SLOTS[next++]] = wild;
            }
        }
        return menu;
    }

    private static Card[] buildMenu() {
        Card[] menu = new Card[MENU_SIZE];
        CardColor[] colors = CardColor.values();
        for (int c = 0; c < colors.length; c++) {
            for (int number = 0; number <= 8; number++) {
                menu[c * 9 + number] = Card.number(colors[c], number);
            }
            int base = 36 + c / 2 * 9 + c % 2 * 4;
            menu[base] = Card.number(colors[c], 9);
            menu[base + 1] = Card.action(colors[c], CardType.SKIP);
            menu[base + 2] = Card.action(colors[c], CardType.REVERSE);
            menu[base + 3] = Card.action(colors[c], CardType.DRAW_TWO);
        }
        return menu;
    }

    static Card[] arrange(Card[] previous, Collection<Card> cards, int slots, int avoid) {
        Card[] layout = new Card[slots];
        Set<Card> placed = new HashSet<>();
        if (previous != null) {
            for (int i = 0; i < Math.min(slots, previous.length); i++) {
                Card card = previous[i];
                if (card != null && cards.contains(card) && placed.add(card)) {
                    layout[i] = card;
                }
            }
        }
        int next = 0;
        for (Card card : cards) {
            if (placed.contains(card)) {
                continue;
            }
            while (next < slots && (layout[next] != null || next == avoid)) {
                next++;
            }
            int slot = next < slots ? next : avoid >= 0 && avoid < slots && layout[avoid] == null ? avoid : -1;
            if (slot < 0) {
                break;
            }
            layout[slot] = card;
            placed.add(card);
        }
        return layout;
    }
}
