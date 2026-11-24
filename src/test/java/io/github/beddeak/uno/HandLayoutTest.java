package io.github.beddeak.uno;

import io.github.beddeak.uno.card.Card;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.beddeak.uno.card.CardColor.BLUE;
import static io.github.beddeak.uno.card.CardColor.GREEN;
import static io.github.beddeak.uno.card.CardColor.RED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandLayoutTest {

    private static final Card RED_1 = Card.number(RED, 1);
    private static final Card RED_5 = Card.number(RED, 5);
    private static final Card GREEN_2 = Card.number(GREEN, 2);
    private static final Card BLUE_7 = Card.number(BLUE, 7);

    @Test
    void firstLayoutIsCompactInGivenOrder() {
        assertArrayEquals(new Card[] {RED_1, RED_5, GREEN_2, null},
                HandLayout.arrange(null, List.of(RED_1, RED_5, GREEN_2), 4, -1));
    }

    @Test
    void playedCardLeavesHoleAndOthersStay() {
        Card[] before = {RED_1, RED_5, GREEN_2, null};
        assertArrayEquals(new Card[] {null, RED_5, GREEN_2, null},
                HandLayout.arrange(before, List.of(RED_5, GREEN_2), 4, 0));
    }

    @Test
    void drawnCardFillsFirstHole() {
        Card[] before = {null, RED_5, GREEN_2, null};
        assertArrayEquals(new Card[] {BLUE_7, RED_5, GREEN_2, RED_1},
                HandLayout.arrange(before, List.of(BLUE_7, RED_5, GREEN_2, RED_1), 4, -1));
    }

    @Test
    void drawnCardSkipsHeldSlot() {
        Card[] before = {null, RED_5, GREEN_2, null};
        assertArrayEquals(new Card[] {null, RED_5, GREEN_2, BLUE_7},
                HandLayout.arrange(before, List.of(BLUE_7, RED_5, GREEN_2), 4, 0));
    }

    @Test
    void heldSlotIsUsedOnlyWhenNothingElseIsFree() {
        Card[] before = {null, RED_5, GREEN_2};
        assertArrayEquals(new Card[] {BLUE_7, RED_5, GREEN_2},
                HandLayout.arrange(before, List.of(BLUE_7, RED_5, GREEN_2), 3, 0));
    }

    @Test
    void stackedCardKeepsSlotWhileAnyCopyRemains() {
        Card[] before = {RED_1, RED_5, null};
        assertArrayEquals(new Card[] {RED_1, RED_5, null},
                HandLayout.arrange(before, List.of(RED_1, RED_5), 3, 0));
    }

    @Test
    void menuHasEveryColoredCardOnceGroupedByColor() {
        Set<Card> seen = new HashSet<>();
        for (int slot = 0; slot < HandLayout.MENU_SIZE; slot++) {
            Card card = HandLayout.menuCard(slot);
            if (slot == 44 || slot == 53) {
                assertNull(card, "wild slot " + slot);
                continue;
            }
            assertNotNull(card, "slot " + slot);
            assertTrue(seen.add(card), card + " twice");
        }
        Set<Card> colored = new HashSet<>(Card.distinct());
        colored.removeIf(Card::isWild);
        assertEquals(colored, seen);
        assertEquals(Card.number(BLUE, 6), HandLayout.menuCard(3 * 9 + 6));
        assertEquals(Card.number(RED, 9), HandLayout.menuCard(36));
        assertNull(HandLayout.menuCard(54));
    }

    @Test
    void wildSlotsShowWildKindsInHandStrongestFirst() {
        Card[] onlyWild = HandLayout.menu(Set.of(Card.wild(), RED_1));
        assertEquals(Card.wild(), onlyWild[53]);
        assertNull(onlyWild[44]);
        assertEquals(RED_1, onlyWild[1]);
        Card[] all = HandLayout.menu(Set.of(Card.wild(), Card.wildDrawFour(), Card.wildDrawTen()));
        assertEquals(Card.wildDrawTen(), all[53]);
        assertEquals(Card.wildDrawFour(), all[44]);
        Card[] strongest = HandLayout.menu(Set.of(Card.wild(), Card.wildDrawTen(), Card.wildDrawNinetyNine()));
        assertEquals(Card.wildDrawNinetyNine(), strongest[53]);
        assertEquals(Card.wildDrawTen(), strongest[44]);
        assertNull(HandLayout.menuCard(53));
    }

    @Test
    void cardsBeyondSlotCountAreDropped() {
        assertArrayEquals(new Card[] {RED_1, RED_5},
                HandLayout.arrange(null, List.of(RED_1, RED_5, GREEN_2), 2, -1));
    }
}
