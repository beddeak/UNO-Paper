package io.github.beddeak.uno.game;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.github.beddeak.uno.card.CardType;
import io.github.beddeak.uno.game.GameEvent.DrawReason;
import io.github.beddeak.uno.game.Result.Failure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class Game {

    public enum Phase { TURN, CHOOSING_COLOR, FINISHED }

    private static final int DRAW_UNTIL_PLAYABLE_LIMIT = 300;
    private static final int DISCARD_KEEP = 50;

    private final Rules rules;
    private final Random random;
    private final List<UUID> order;
    private final Map<UUID, List<Card>> hands = new HashMap<>();
    private final ArrayDeque<Card> drawPile;
    private final List<Card> discard = new ArrayList<>();
    private final Set<UUID> called = new HashSet<>();
    private final Set<UUID> exposed = new LinkedHashSet<>();
    private int current;
    private int direction = 1;
    private CardColor color;
    private int pendingDraw;
    private Phase phase = Phase.TURN;
    private Card drawnCard;
    private UUID winner;
    private int turnNumber;
    private boolean started;

    public Game(List<UUID> players, Rules rules, Random random) {
        this(players, rules, random, shuffled(Card.deck(rules.deck()), random));
    }

    Game(List<UUID> players, Rules rules, Random random, List<Card> deckTopFirst) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("need at least 2 players");
        }
        if (new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("duplicate player");
        }
        this.order = new ArrayList<>(players);
        this.rules = rules;
        this.random = random;
        this.drawPile = new ArrayDeque<>(deckTopFirst);
        for (UUID player : order) {
            hands.put(player, new ArrayList<>());
        }
    }

    private static List<Card> shuffled(List<Card> cards, Random random) {
        List<Card> copy = new ArrayList<>(cards);
        Collections.shuffle(copy, random);
        return copy;
    }

    public List<GameEvent> start() {
        if (started) {
            throw new IllegalStateException("already started");
        }
        started = true;
        List<GameEvent> events = new ArrayList<>();
        for (int round = 0; round < rules.handSize(); round++) {
            for (UUID player : order) {
                hands.get(player).add(takeFromPile(events));
            }
        }
        Card starter = null;
        for (int guard = 0; guard < 1000; guard++) {
            Card card = takeFromPile(events);
            if (card.type() == CardType.NUMBER) {
                starter = card;
                break;
            }
            drawPile.addLast(card);
        }
        if (starter == null) {
            throw new IllegalStateException("no number card left for the starter");
        }
        discard.add(starter);
        color = starter.color();
        events.add(new GameEvent.StarterFlipped(starter));
        beginTurn(events);
        return events;
    }

    public Result play(UUID player, Card card) {
        Failure failure = turnCheck(player);
        if (failure != null) {
            return Result.fail(failure);
        }
        List<Card> hand = hands.get(player);
        if (!hand.contains(card)) {
            return Result.fail(Failure.NOT_IN_HAND);
        }
        if (drawnCard != null && !drawnCard.equals(card)) {
            return Result.fail(Failure.ONLY_DRAWN_CARD);
        }
        if (!isPlayable(card)) {
            return Result.fail(pendingDraw > 0 ? Failure.MUST_STACK_OR_DRAW : Failure.NOT_MATCHING);
        }
        List<GameEvent> events = new ArrayList<>();
        exposed.clear();
        hand.remove(card);
        discard.add(card);
        if (discard.size() > DISCARD_KEEP) {
            discard.removeFirst();
        }
        drawnCard = null;
        events.add(new GameEvent.CardPlayed(player, card, hand.size()));
        if (hand.isEmpty()) {
            finish(player, false, events);
            return Result.ok(events);
        }
        if (hand.size() == 1 && !called.contains(player)) {
            exposed.add(player);
        }
        if (card.isWild()) {
            phase = Phase.CHOOSING_COLOR;
            events.add(new GameEvent.ColorRequested(player));
            return Result.ok(events);
        }
        color = card.color();
        applyEffect(card, events);
        return Result.ok(events);
    }

    public Result chooseColor(UUID player, CardColor chosen) {
        if (phase == Phase.FINISHED) {
            return Result.fail(Failure.GAME_OVER);
        }
        if (!hands.containsKey(player)) {
            return Result.fail(Failure.NOT_IN_GAME);
        }
        if (phase != Phase.CHOOSING_COLOR || !currentPlayer().equals(player)) {
            return Result.fail(Failure.NOT_CHOOSING);
        }
        List<GameEvent> events = new ArrayList<>();
        applyColor(player, chosen, false, events);
        return Result.ok(events);
    }

    public Result draw(UUID player) {
        Failure failure = turnCheck(player);
        if (failure != null) {
            return Result.fail(failure);
        }
        if (drawnCard != null) {
            return Result.fail(Failure.ALREADY_DREW);
        }
        List<GameEvent> events = new ArrayList<>();
        exposed.clear();
        if (pendingDraw > 0) {
            takePending(player, events);
            return Result.ok(events);
        }
        int limit = rules.drawUntilPlayable() ? DRAW_UNTIL_PLAYABLE_LIMIT : 1;
        List<Card> drawn = new ArrayList<>();
        while (drawn.size() < limit) {
            Card card = takeFromPile(events);
            drawn.add(card);
            if (isPlayable(card)) {
                break;
            }
        }
        hands.get(player).addAll(drawn);
        refreshCallState(player);
        events.add(new GameEvent.CardsDrawn(player, List.copyOf(drawn), DrawReason.VOLUNTARY));
        Card last = drawn.isEmpty() ? null : drawn.getLast();
        if (last != null && isPlayable(last)) {
            drawnCard = last;
            events.add(new GameEvent.DrawnPlayable(player, last));
        } else {
            events.add(new GameEvent.Passed(player));
            advance(1, events);
        }
        return Result.ok(events);
    }

    public Result pass(UUID player) {
        Failure failure = turnCheck(player);
        if (failure != null) {
            return Result.fail(failure);
        }
        if (drawnCard == null) {
            return Result.fail(Failure.CANNOT_PASS);
        }
        List<GameEvent> events = new ArrayList<>();
        exposed.clear();
        events.add(new GameEvent.Passed(player));
        advance(1, events);
        return Result.ok(events);
    }

    public Result call(UUID player) {
        if (phase == Phase.FINISHED) {
            return Result.fail(Failure.GAME_OVER);
        }
        if (!hands.containsKey(player)) {
            return Result.fail(Failure.NOT_IN_GAME);
        }
        List<GameEvent> events = new ArrayList<>();
        if (exposed.remove(player)) {
            called.add(player);
            events.add(new GameEvent.Called(player));
            return Result.ok(events);
        }
        if (!exposed.isEmpty()) {
            UUID target = exposed.iterator().next();
            exposed.remove(target);
            called.add(target);
            events.add(new GameEvent.Caught(player, target));
            if (rules.callPenalty() > 0) {
                drawCards(target, rules.callPenalty(), DrawReason.CALL_PENALTY, events);
            }
            return Result.ok(events);
        }
        if (hands.get(player).size() == 1) {
            if (!called.add(player)) {
                return Result.fail(Failure.ALREADY_CALLED);
            }
            events.add(new GameEvent.Called(player));
            return Result.ok(events);
        }
        return Result.fail(Failure.NOTHING_TO_CALL);
    }

    public Result penalty(UUID player, int amount, DrawReason reason) {
        if (phase == Phase.FINISHED) {
            return Result.fail(Failure.GAME_OVER);
        }
        if (!hands.containsKey(player)) {
            return Result.fail(Failure.NOT_IN_GAME);
        }
        List<GameEvent> events = new ArrayList<>();
        drawCards(player, amount, reason, events);
        return Result.ok(events);
    }

    public Result forceEndTurn(UUID player, boolean drawPenalty) {
        if (phase == Phase.FINISHED) {
            return Result.fail(Failure.GAME_OVER);
        }
        if (!hands.containsKey(player)) {
            return Result.fail(Failure.NOT_IN_GAME);
        }
        if (!currentPlayer().equals(player)) {
            return Result.fail(Failure.NOT_YOUR_TURN);
        }
        List<GameEvent> events = new ArrayList<>();
        exposed.clear();
        if (phase == Phase.CHOOSING_COLOR) {
            applyColor(player, bestColor(player), true, events);
            return Result.ok(events);
        }
        if (pendingDraw > 0) {
            takePending(player, events);
            return Result.ok(events);
        }
        if (drawnCard == null && drawPenalty) {
            drawCards(player, 1, DrawReason.TIMEOUT, events);
        }
        events.add(new GameEvent.Passed(player));
        advance(1, events);
        return Result.ok(events);
    }

    public Result removePlayer(UUID player) {
        if (phase == Phase.FINISHED) {
            return Result.fail(Failure.GAME_OVER);
        }
        int index = order.indexOf(player);
        if (index < 0) {
            return Result.fail(Failure.NOT_IN_GAME);
        }
        List<GameEvent> events = new ArrayList<>();
        boolean wasCurrent = index == current;
        Phase before = phase;
        drawPile.addAll(hands.remove(player));
        called.remove(player);
        exposed.remove(player);
        order.remove(index);
        events.add(new GameEvent.PlayerRemoved(player));
        if (order.size() < 2) {
            finish(order.isEmpty() ? null : order.getFirst(), true, events);
            return Result.ok(events);
        }
        if (index < current) {
            current--;
        }
        if (wasCurrent) {
            current = direction > 0 ? index % order.size() : Math.floorMod(index - 1, order.size());
            pendingDraw = 0;
            if (before == Phase.CHOOSING_COLOR) {
                phase = Phase.TURN;
                color = CardColor.values()[random.nextInt(CardColor.values().length)];
                events.add(new GameEvent.ColorChosen(player, color, true));
            }
            beginTurn(events);
        }
        return Result.ok(events);
    }

    public boolean isPlayable(Card card) {
        if (phase != Phase.TURN) {
            return false;
        }
        Card top = topCard();
        if (pendingDraw > 0) {
            return card.type().stacks() && top.type().stacks();
        }
        if (card.isWild() || card.color() == color) {
            return true;
        }
        if (card.type() == CardType.NUMBER) {
            return top.type() == CardType.NUMBER && top.number() == card.number();
        }
        return card.type() == top.type();
    }

    public boolean canPlay(UUID player, Card card) {
        return phase == Phase.TURN
                && currentPlayer().equals(player)
                && (drawnCard == null || drawnCard.equals(card))
                && hands.get(player).contains(card)
                && isPlayable(card);
    }

    public boolean canCall(UUID player) {
        if (phase == Phase.FINISHED || !hands.containsKey(player)) {
            return false;
        }
        if (!exposed.isEmpty()) {
            return true;
        }
        return hands.get(player).size() == 1 && !called.contains(player);
    }

    public CardColor bestColor(UUID player) {
        Map<CardColor, Integer> counts = new EnumMap<>(CardColor.class);
        for (Card card : hands.getOrDefault(player, List.of())) {
            if (card.color() != null) {
                counts.merge(card.color(), 1, Integer::sum);
            }
        }
        CardColor best = null;
        int bestCount = 0;
        for (CardColor candidate : CardColor.values()) {
            int count = counts.getOrDefault(candidate, 0);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best != null ? best : CardColor.values()[random.nextInt(CardColor.values().length)];
    }

    private Failure turnCheck(UUID player) {
        if (phase == Phase.FINISHED) {
            return Failure.GAME_OVER;
        }
        if (!hands.containsKey(player)) {
            return Failure.NOT_IN_GAME;
        }
        if (!currentPlayer().equals(player)) {
            return Failure.NOT_YOUR_TURN;
        }
        if (phase == Phase.CHOOSING_COLOR) {
            return Failure.CHOOSING_COLOR;
        }
        return null;
    }

    private void applyColor(UUID player, CardColor chosen, boolean automatic, List<GameEvent> events) {
        color = chosen;
        phase = Phase.TURN;
        events.add(new GameEvent.ColorChosen(player, chosen, automatic));
        applyEffect(topCard(), events);
    }

    private void applyEffect(Card card, List<GameEvent> events) {
        switch (card.type()) {
            case SKIP -> {
                events.add(new GameEvent.Skipped(peek(1)));
                advance(2, events);
            }
            case REVERSE -> {
                direction = -direction;
                events.add(new GameEvent.Reversed(direction > 0));
                if (order.size() == 2) {
                    events.add(new GameEvent.Skipped(peek(1)));
                    advance(2, events);
                } else {
                    advance(1, events);
                }
            }
            case DRAW_TWO, WILD_DRAW_FOUR, WILD_DRAW_TEN, WILD_DRAW_NINETY_NINE -> {
                UUID target = peek(1);
                if (rules.stacking()) {
                    pendingDraw += card.type().drawAmount();
                    events.add(new GameEvent.StackGrew(target, pendingDraw));
                    advance(1, events);
                } else {
                    drawCards(target, card.type().drawAmount(), DrawReason.PENALTY, events);
                    events.add(new GameEvent.Skipped(target));
                    advance(2, events);
                }
            }
            default -> advance(1, events);
        }
    }

    private void takePending(UUID player, List<GameEvent> events) {
        int amount = pendingDraw;
        pendingDraw = 0;
        drawCards(player, amount, DrawReason.STACK, events);
        advance(1, events);
    }

    private void advance(int steps, List<GameEvent> events) {
        current = Math.floorMod(current + direction * steps, order.size());
        beginTurn(events);
    }

    private void beginTurn(List<GameEvent> events) {
        drawnCard = null;
        turnNumber++;
        UUID player = currentPlayer();
        events.add(new GameEvent.TurnStarted(player));
        if (pendingDraw > 0 && !hasStackable(player)) {
            takePending(player, events);
        }
    }

    private boolean hasStackable(UUID player) {
        for (Card card : hands.get(player)) {
            if (isPlayable(card)) {
                return true;
            }
        }
        return false;
    }

    private List<Card> drawCards(UUID player, int amount, DrawReason reason, List<GameEvent> events) {
        List<Card> drawn = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            drawn.add(takeFromPile(events));
        }
        hands.get(player).addAll(drawn);
        refreshCallState(player);
        events.add(new GameEvent.CardsDrawn(player, List.copyOf(drawn), reason));
        return drawn;
    }

    private Card takeFromPile(List<GameEvent> events) {
        if (drawPile.isEmpty()) {
            List<Card> fresh = shuffled(Card.deck(rules.deck()), random);
            drawPile.addAll(fresh);
            events.add(new GameEvent.DeckRefilled(fresh.size()));
        }
        return drawPile.pollFirst();
    }

    private void refreshCallState(UUID player) {
        if (hands.get(player).size() >= 2) {
            exposed.remove(player);
            called.remove(player);
        }
    }

    private void finish(UUID winner, boolean byDefault, List<GameEvent> events) {
        phase = Phase.FINISHED;
        this.winner = winner;
        pendingDraw = 0;
        drawnCard = null;
        exposed.clear();
        int points = 0;
        for (Map.Entry<UUID, List<Card>> entry : hands.entrySet()) {
            if (!entry.getKey().equals(winner)) {
                for (Card card : entry.getValue()) {
                    points += card.points();
                }
            }
        }
        events.add(new GameEvent.Won(winner, points, byDefault));
    }

    private UUID peek(int steps) {
        return order.get(Math.floorMod(current + direction * steps, order.size()));
    }

    public Rules rules() {
        return rules;
    }

    public List<UUID> players() {
        return Collections.unmodifiableList(order);
    }

    public boolean isPlayer(UUID player) {
        return hands.containsKey(player);
    }

    public List<Card> hand(UUID player) {
        List<Card> hand = hands.get(player);
        return hand == null ? List.of() : Collections.unmodifiableList(hand);
    }

    public Card topCard() {
        return discard.getLast();
    }

    public CardColor color() {
        return color;
    }

    public UUID currentPlayer() {
        return order.get(current);
    }

    public boolean clockwise() {
        return direction > 0;
    }

    public int pendingDraw() {
        return pendingDraw;
    }

    public Phase phase() {
        return phase;
    }

    public Card drawnCard() {
        return drawnCard;
    }

    public int drawPileSize() {
        return drawPile.size();
    }

    public int turnNumber() {
        return turnNumber;
    }

    public boolean isExposed(UUID player) {
        return exposed.contains(player);
    }

    public boolean hasCalled(UUID player) {
        return called.contains(player);
    }

    public UUID winner() {
        return winner;
    }

    public int pointsInHand(UUID player) {
        int points = 0;
        for (Card card : hand(player)) {
            points += card.points();
        }
        return points;
    }
}
