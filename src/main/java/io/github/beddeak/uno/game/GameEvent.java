package io.github.beddeak.uno.game;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;

import java.util.List;
import java.util.UUID;

public sealed interface GameEvent {

    enum DrawReason { VOLUNTARY, PENALTY, STACK, CALL_PENALTY, TIMEOUT, CLAP }

    record StarterFlipped(Card card) implements GameEvent {}

    record TurnStarted(UUID player) implements GameEvent {}

    record CardPlayed(UUID player, Card card, int handSize) implements GameEvent {}

    record ColorRequested(UUID player) implements GameEvent {}

    record ColorChosen(UUID player, CardColor color, boolean automatic) implements GameEvent {}

    record Skipped(UUID player) implements GameEvent {}

    record Reversed(boolean clockwise) implements GameEvent {}

    record StackGrew(UUID target, int total) implements GameEvent {}

    record CardsDrawn(UUID player, List<Card> cards, DrawReason reason) implements GameEvent {}

    record DrawnPlayable(UUID player, Card card) implements GameEvent {}

    record Passed(UUID player) implements GameEvent {}

    record Called(UUID player) implements GameEvent {}

    record Caught(UUID catcher, UUID target) implements GameEvent {}

    record DeckRefilled(int size) implements GameEvent {}

    record PlayerRemoved(UUID player) implements GameEvent {}

    record Won(UUID winner, int points, boolean byDefault) implements GameEvent {}
}
