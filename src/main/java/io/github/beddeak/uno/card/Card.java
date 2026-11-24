package io.github.beddeak.uno.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Card(CardType type, CardColor color, int number) {

    public static final Comparator<Card> HAND_ORDER = Comparator
            .comparingInt((Card card) -> card.color == null ? CardColor.values().length : card.color.ordinal())
            .thenComparingInt(card -> card.type.ordinal())
            .thenComparingInt(Card::number);

    private static final List<Card> DISTINCT = buildDistinct();

    public Card {
        Objects.requireNonNull(type, "type");
        if (type.isWild()) {
            if (color != null) {
                throw new IllegalArgumentException("wild cards have no color");
            }
        } else if (color == null) {
            throw new IllegalArgumentException("colored card needs a color");
        }
        if (type == CardType.NUMBER ? number < 0 || number > 9 : number != 0) {
            throw new IllegalArgumentException("bad number " + number + " for " + type);
        }
    }

    public static Card number(CardColor color, int number) {
        return new Card(CardType.NUMBER, color, number);
    }

    public static Card action(CardColor color, CardType type) {
        return new Card(type, color, 0);
    }

    public static Card wild() {
        return new Card(CardType.WILD, null, 0);
    }

    public static Card wildDrawFour() {
        return new Card(CardType.WILD_DRAW_FOUR, null, 0);
    }

    public static Card wildDrawTen() {
        return new Card(CardType.WILD_DRAW_TEN, null, 0);
    }

    public static Card wildDrawNinetyNine() {
        return new Card(CardType.WILD_DRAW_NINETY_NINE, null, 0);
    }

    public boolean isWild() {
        return type.isWild();
    }

    public String id() {
        if (type.isWild()) {
            return type.id();
        }
        return color.id() + "_" + (type == CardType.NUMBER ? Integer.toString(number) : type.id());
    }

    public static Card fromId(String id) {
        for (Card card : DISTINCT) {
            if (card.id().equals(id)) {
                return card;
            }
        }
        return null;
    }

    public int points() {
        return switch (type) {
            case NUMBER -> number;
            case SKIP, REVERSE, DRAW_TWO -> 20;
            case WILD, WILD_DRAW_FOUR, WILD_DRAW_TEN -> 50;
            case WILD_DRAW_NINETY_NINE -> 99;
        };
    }

    public String symbol() {
        return switch (type) {
            case NUMBER -> Integer.toString(number);
            case SKIP -> "스킵";
            case REVERSE -> "리버스";
            case DRAW_TWO -> "+2";
            case WILD -> "와일드";
            case WILD_DRAW_FOUR -> "와일드 +4";
            case WILD_DRAW_TEN -> "와일드 +10";
            case WILD_DRAW_NINETY_NINE -> "와일드 +99";
        };
    }

    public String koreanName() {
        return isWild() ? symbol() : color.koreanName() + " " + symbol();
    }

    public static List<Card> distinct() {
        return DISTINCT;
    }

    public static List<Card> standardDeck() {
        return deck(DeckMix.STANDARD);
    }

    public static List<Card> deck(DeckMix mix) {
        List<Card> deck = new ArrayList<>();
        CardColor[] colors = CardColor.values();
        for (int c = 0; c < colors.length; c++) {
            CardColor color = colors[c];
            deck.add(number(color, 0));
            for (int copy = 0; copy < 2; copy++) {
                for (int n = 1; n <= 9; n++) {
                    if (n != 6) {
                        deck.add(number(color, n));
                    }
                }
                deck.add(action(color, CardType.SKIP));
                deck.add(action(color, CardType.REVERSE));
            }
            repeat(deck, number(color, 6), share(mix.sixes(), c, colors.length));
            repeat(deck, action(color, CardType.DRAW_TWO), share(mix.drawTwos(), c, colors.length));
        }
        repeat(deck, wild(), 4);
        repeat(deck, wildDrawFour(), mix.drawFours());
        repeat(deck, wildDrawTen(), mix.drawTens());
        repeat(deck, wildDrawNinetyNine(), mix.drawNinetyNines());
        return deck;
    }

    private static int share(int total, int index, int parts) {
        return total / parts + (index < total % parts ? 1 : 0);
    }

    private static void repeat(List<Card> deck, Card card, int copies) {
        for (int i = 0; i < copies; i++) {
            deck.add(card);
        }
    }

    private static List<Card> buildDistinct() {
        List<Card> cards = new ArrayList<>();
        for (CardColor color : CardColor.values()) {
            for (int n = 0; n <= 9; n++) {
                cards.add(number(color, n));
            }
            cards.add(action(color, CardType.SKIP));
            cards.add(action(color, CardType.REVERSE));
            cards.add(action(color, CardType.DRAW_TWO));
        }
        cards.add(wild());
        cards.add(wildDrawFour());
        cards.add(wildDrawTen());
        cards.add(wildDrawNinetyNine());
        return Collections.unmodifiableList(cards);
    }
}
