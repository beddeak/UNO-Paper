package io.github.beddeak.uno.game;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.github.beddeak.uno.card.CardType;
import io.github.beddeak.uno.card.DeckMix;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static io.github.beddeak.uno.card.CardColor.BLUE;
import static io.github.beddeak.uno.card.CardColor.GREEN;
import static io.github.beddeak.uno.card.CardColor.RED;
import static io.github.beddeak.uno.card.CardColor.YELLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static Card n(CardColor color, int number) {
        return Card.number(color, number);
    }

    private static Card skip(CardColor color) {
        return Card.action(color, CardType.SKIP);
    }

    private static Card reverse(CardColor color) {
        return Card.action(color, CardType.REVERSE);
    }

    private static Card plusTwo(CardColor color) {
        return Card.action(color, CardType.DRAW_TWO);
    }

    private static Game game(Rules rules, List<UUID> players, Card... deck) {
        List<Card> cards = new ArrayList<>(Arrays.asList(deck));
        for (int i = 0; i < 30; i++) {
            cards.add(n(GREEN, 7));
        }
        Game game = new Game(players, rules, new Random(1), cards);
        game.start();
        return game;
    }

    private static Rules rules(int handSize, boolean stacking) {
        return new Rules(handSize, stacking, false, 2);
    }

    @Test
    void standardDeckHas108CardsAnd56DistinctIds() {
        List<Card> deck = Card.standardDeck();
        assertEquals(108, deck.size());
        assertEquals(4, deck.stream().filter(card -> card.type() == CardType.WILD).count());
        assertEquals(4, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_FOUR).count());
        assertEquals(0, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_TEN).count());
        assertEquals(0, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_NINETY_NINE).count());
        for (CardColor color : CardColor.values()) {
            assertEquals(25, deck.stream().filter(card -> card.color() == color).count());
            assertEquals(2, deck.stream().filter(card -> card.equals(n(color, 6))).count());
        }
        assertEquals(56, Card.distinct().size());
        assertEquals(56, new HashSet<>(Card.distinct().stream().map(Card::id).toList()).size());
        for (Card card : Card.distinct()) {
            assertEquals(card, Card.fromId(card.id()));
        }
    }

    @Test
    void deckMixSetsHowOftenSixesAndDrawCardsAppear() {
        List<Card> deck = Card.deck(new DeckMix(17, 12, 6, 3, 2));
        assertEquals(88 + 17 + 12 + 6 + 3 + 2, deck.size());
        assertEquals(17, deck.stream().filter(card -> card.type() == CardType.NUMBER && card.number() == 6).count());
        assertEquals(5, deck.stream().filter(card -> card.equals(n(RED, 6))).count());
        assertEquals(4, deck.stream().filter(card -> card.equals(n(BLUE, 6))).count());
        assertEquals(3, deck.stream().filter(card -> card.equals(plusTwo(GREEN))).count());
        assertEquals(6, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_FOUR).count());
        assertEquals(3, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_TEN).count());
        assertEquals(2, deck.stream().filter(card -> card.type() == CardType.WILD_DRAW_NINETY_NINE).count());
        Game game = new Game(List.of(A, B), new Rules(1, true, true, 2, new DeckMix(0, 0, 0, 40, 0)), new Random(2));
        game.start();
        assertEquals(88 + 40 - 3, game.drawPileSize());
    }

    @Test
    void plusTwoFourTenStackOnEachOtherButNinetyNineStaysOut() {
        Game game = game(rules(3, true), List.of(A, B, C),
                Card.wildDrawFour(), plusTwo(BLUE), Card.wildDrawTen(),
                Card.wildDrawNinetyNine(), Card.wildDrawNinetyNine(), plusTwo(RED),
                n(YELLOW, 1), n(YELLOW, 2), n(YELLOW, 3), n(RED, 8));
        assertTrue(game.play(A, Card.wildDrawFour()).success());
        assertTrue(game.chooseColor(A, GREEN).success());
        assertEquals(4, game.pendingDraw());
        assertTrue(game.canPlay(B, plusTwo(BLUE)));
        assertFalse(game.canPlay(B, Card.wildDrawNinetyNine()));
        assertEquals(Result.Failure.MUST_STACK_OR_DRAW, game.play(B, Card.wildDrawNinetyNine()).failure());
        assertTrue(game.play(B, plusTwo(BLUE)).success());
        assertTrue(game.canPlay(C, plusTwo(RED)));
        assertTrue(game.play(C, Card.wildDrawTen()).success());
        assertTrue(game.chooseColor(C, BLUE).success());
        // A only has +99 to answer with, which does not count: the 16 cards are taken at once.
        assertEquals(0, game.pendingDraw());
        assertEquals(2 + 16, game.hand(A).size());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.canPlay(B, Card.wildDrawNinetyNine()));
        assertTrue(game.play(B, Card.wildDrawNinetyNine()).success());
        assertTrue(game.chooseColor(B, RED).success());
        // C holds +2 but nothing answers +99.
        assertEquals(0, game.pendingDraw());
        assertEquals(2 + 99, game.hand(C).size());
        assertEquals(A, game.currentPlayer());
    }

    @Test
    void ninetyNineCanBePlayedOnAnything() {
        Game game = game(rules(1, true), List.of(A, B), Card.wildDrawNinetyNine(), n(BLUE, 2), n(RED, 8));
        assertTrue(game.canPlay(A, Card.wildDrawNinetyNine()));
        assertEquals(99, Card.wildDrawNinetyNine().points());
        assertEquals(Card.wildDrawNinetyNine(), Card.fromId("wild_draw_ninety_nine"));
    }

    @Test
    void starterSkipsActionCards() {
        Game game = game(rules(1, true), List.of(A, B), n(RED, 1), n(BLUE, 2), Card.wild(), skip(RED), n(YELLOW, 4));
        assertEquals(n(YELLOW, 4), game.topCard());
        assertEquals(YELLOW, game.color());
        assertEquals(A, game.currentPlayer());
        assertEquals(1, game.hand(A).size());
    }

    @Test
    void matchingPlayAdvancesAndMismatchIsRejected() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 5), n(BLUE, 1), n(GREEN, 2), n(BLUE, 9), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertEquals(Result.Failure.NOT_MATCHING, game.play(A, n(BLUE, 9)).failure());
        assertEquals(Result.Failure.NOT_YOUR_TURN, game.play(B, n(BLUE, 1)).failure());
        assertTrue(game.play(A, n(RED, 5)).success());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.play(B, n(BLUE, 1)).failure() == Result.Failure.NOT_MATCHING);
    }

    @Test
    void sameNumberDifferentColorIsPlayable() {
        Game game = game(rules(2, true), List.of(A, B), n(BLUE, 8), n(GREEN, 1), n(BLUE, 5), n(GREEN, 2), n(RED, 8));
        assertTrue(game.play(A, n(BLUE, 8)).success());
        assertEquals(BLUE, game.color());
    }

    @Test
    void skipSkipsNextPlayer() {
        Game game = game(rules(2, true), List.of(A, B, C),
                skip(RED), n(BLUE, 1), n(GREEN, 2), n(RED, 1), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertTrue(game.play(A, skip(RED)).success());
        assertEquals(C, game.currentPlayer());
    }

    @Test
    void reverseChangesDirectionAndActsAsSkipWithTwoPlayers() {
        Game three = game(rules(2, true), List.of(A, B, C),
                reverse(RED), n(BLUE, 1), n(GREEN, 2), n(RED, 1), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertTrue(three.play(A, reverse(RED)).success());
        assertFalse(three.clockwise());
        assertEquals(C, three.currentPlayer());

        Game two = game(rules(2, true), List.of(A, B), reverse(RED), n(BLUE, 1), n(RED, 2), n(BLUE, 3), n(RED, 8));
        assertTrue(two.play(A, reverse(RED)).success());
        assertEquals(A, two.currentPlayer());
    }

    @Test
    void drawTwoWithoutStackingMakesNextDrawAndSkips() {
        Game game = game(rules(2, false), List.of(A, B, C),
                plusTwo(RED), n(BLUE, 1), n(GREEN, 2), n(RED, 1), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertTrue(game.play(A, plusTwo(RED)).success());
        assertEquals(4, game.hand(B).size());
        assertEquals(C, game.currentPlayer());
        assertEquals(0, game.pendingDraw());
    }

    @Test
    void stackingAccumulatesAndAutoTakesWhenNoStackableCard() {
        Game game = game(rules(2, true), List.of(A, B, C),
                plusTwo(RED), plusTwo(BLUE), n(GREEN, 2), n(RED, 1), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertTrue(game.play(A, plusTwo(RED)).success());
        assertEquals(B, game.currentPlayer());
        assertEquals(2, game.pendingDraw());
        assertEquals(Result.Failure.MUST_STACK_OR_DRAW, game.play(B, n(BLUE, 3)).failure());
        Result stacked = game.play(B, plusTwo(BLUE));
        assertTrue(stacked.success());
        assertEquals(6, game.hand(C).size());
        assertEquals(A, game.currentPlayer());
        assertEquals(0, game.pendingDraw());
        assertTrue(stacked.events().stream().anyMatch(event -> event instanceof GameEvent.CardsDrawn drawn
                && drawn.player().equals(C) && drawn.cards().size() == 4 && drawn.reason() == GameEvent.DrawReason.STACK));
    }

    @Test
    void stackedPlayerMayTakeCardsInsteadOfStacking() {
        Game game = game(rules(2, true), List.of(A, B), plusTwo(RED), plusTwo(BLUE), n(RED, 1), n(BLUE, 3), n(RED, 8));
        assertTrue(game.play(A, plusTwo(RED)).success());
        assertTrue(game.draw(B).success());
        assertEquals(4, game.hand(B).size());
        assertEquals(A, game.currentPlayer());
    }

    @Test
    void wildDrawFourStacksOnDrawTwo() {
        Game game = game(rules(2, true), List.of(A, B, C),
                plusTwo(RED), Card.wildDrawFour(), n(GREEN, 2), n(RED, 1), n(BLUE, 3), n(GREEN, 4), n(RED, 8));
        assertTrue(game.play(A, plusTwo(RED)).success());
        assertTrue(game.play(B, Card.wildDrawFour()).success());
        assertEquals(Game.Phase.CHOOSING_COLOR, game.phase());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.chooseColor(B, GREEN).success());
        assertEquals(GREEN, game.color());
        assertEquals(8, game.hand(C).size());
        assertEquals(A, game.currentPlayer());
    }

    @Test
    void wildRequiresColorChoiceBeforeAnythingElse() {
        Game game = game(rules(2, true), List.of(A, B), Card.wild(), n(BLUE, 1), n(RED, 2), n(GREEN, 3), n(RED, 8));
        assertTrue(game.play(A, Card.wild()).success());
        assertEquals(Result.Failure.CHOOSING_COLOR, game.play(A, n(RED, 2)).failure());
        assertEquals(Result.Failure.NOT_CHOOSING, game.chooseColor(B, BLUE).failure());
        assertTrue(game.chooseColor(A, BLUE).success());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.canPlay(B, n(BLUE, 1)));
        assertFalse(game.canPlay(B, n(GREEN, 3)));
    }

    @Test
    void drawingPlayableCardAllowsOnlyThatCardOrPass() {
        Game game = game(rules(2, true), List.of(A, B), n(BLUE, 1), n(BLUE, 2), n(YELLOW, 3), n(BLUE, 4), n(RED, 8), n(RED, 2));
        assertTrue(game.draw(A).success());
        assertEquals(n(RED, 2), game.drawnCard());
        assertEquals(A, game.currentPlayer());
        assertEquals(Result.Failure.ALREADY_DREW, game.draw(A).failure());
        assertTrue(game.pass(A).success());
        assertEquals(B, game.currentPlayer());

        Game other = game(rules(2, true), List.of(A, B), n(RED, 1), n(BLUE, 2), n(YELLOW, 3), n(BLUE, 4), n(RED, 8), n(RED, 2));
        assertTrue(other.draw(A).success());
        assertEquals(Result.Failure.ONLY_DRAWN_CARD, other.play(A, n(RED, 1)).failure());
        assertTrue(other.play(A, n(RED, 2)).success());
    }

    @Test
    void drawingUnplayableCardEndsTurn() {
        Game game = game(rules(2, true), List.of(A, B), n(BLUE, 1), n(BLUE, 2), n(YELLOW, 3), n(BLUE, 4), n(RED, 8), n(GREEN, 2));
        assertTrue(game.draw(A).success());
        assertNull(game.drawnCard());
        assertEquals(B, game.currentPlayer());
        assertEquals(3, game.hand(A).size());
        assertEquals(Result.Failure.CANNOT_PASS, game.pass(B).failure());
    }

    @Test
    void drawUntilPlayableKeepsDrawing() {
        Game game = game(new Rules(1, true, true, 2), List.of(A, B), n(BLUE, 1), n(BLUE, 2), n(RED, 8),
                n(GREEN, 2), n(YELLOW, 3), n(RED, 4));
        assertTrue(game.draw(A).success());
        assertEquals(4, game.hand(A).size());
        assertEquals(n(RED, 4), game.drawnCard());
    }

    @Test
    void forgettingToCallLetsOthersCatch() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.isExposed(A));
        assertTrue(game.canCall(C));
        Result caught = game.call(C);
        assertTrue(caught.success());
        assertTrue(caught.events().contains(new GameEvent.Caught(C, A)));
        assertEquals(3, game.hand(A).size());
        assertFalse(game.isExposed(A));
    }

    @Test
    void selfCallBeforeCatchIsSafe() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.call(A).success());
        assertFalse(game.isExposed(A));
        Result late = game.call(B);
        assertFalse(late.events().stream().anyMatch(event -> event instanceof GameEvent.Caught));
        assertEquals(1, game.hand(A).size());
    }

    @Test
    void callingOnlyWorksAtOneCard() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertFalse(game.canCall(A));
        assertEquals(Result.Failure.NOTHING_TO_CALL, game.call(A).failure());
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.isExposed(A));
        assertTrue(game.call(A).success());
        assertEquals(Result.Failure.ALREADY_CALLED, game.call(A).failure());
        assertFalse(game.canCall(A));
    }

    @Test
    void drawingBackUpToTwoClearsCall() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8),
                n(YELLOW, 7), n(YELLOW, 9));
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.call(A).success());
        assertTrue(game.penalty(A, 1, GameEvent.DrawReason.CLAP).success());
        assertFalse(game.hasCalled(A));
        assertFalse(game.canCall(A));
    }

    @Test
    void catchWindowClosesOnNextAction() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(RED, 3), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.play(B, n(RED, 3)).success());
        assertFalse(game.isExposed(A));
        Result late = game.call(C);
        assertFalse(late.events().stream().anyMatch(event -> event instanceof GameEvent.Caught caught
                && caught.target().equals(A)));
        assertEquals(1, game.hand(A).size());
    }

    @Test
    void playingUNOWinsWithOpponentPoints() {
        Game game = game(rules(1, true), List.of(A, B), n(RED, 1), skip(BLUE), n(RED, 8));
        Result result = game.play(A, n(RED, 1));
        assertTrue(result.success());
        assertEquals(Game.Phase.FINISHED, game.phase());
        assertEquals(A, game.winner());
        assertTrue(result.events().contains(new GameEvent.Won(A, 20, false)));
        assertEquals(Result.Failure.GAME_OVER, game.draw(B).failure());
    }

    @Test
    void emptyDrawPileIsRefilledWithFreshDeck() {
        Game game = new Game(List.of(A, B), rules(2, true), new Random(3), List.of(
                n(RED, 1), n(RED, 2), n(RED, 3), n(RED, 4), n(RED, 5)));
        game.start();
        assertEquals(0, game.drawPileSize());
        assertTrue(game.play(A, n(RED, 1)).success());
        assertTrue(game.play(B, n(RED, 2)).success());
        Result drawn = game.draw(A);
        assertTrue(drawn.success());
        assertTrue(drawn.events().contains(new GameEvent.DeckRefilled(108)));
        assertEquals(n(RED, 2), game.topCard());
        assertEquals(2, game.hand(A).size());
        assertEquals(107, game.drawPileSize());
    }

    @Test
    void dealingMoreThanOneDeckNeverRunsOut() {
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            players.add(new UUID(1, i));
        }
        Game game = new Game(players, new Rules(20, true, true, 2), new Random(5));
        game.start();
        for (UUID player : players) {
            assertEquals(20, game.hand(player).size());
        }
        assertEquals(CardType.NUMBER, game.topCard().type());
    }

    @Test
    void drawUntilPlayableRefillsUntilMatch() {
        Game game = new Game(List.of(A, B), new Rules(1, true, true, 2), new Random(7), List.of(
                n(BLUE, 1), n(BLUE, 2), n(RED, 5)));
        game.start();
        Result drawn = game.draw(A);
        assertTrue(drawn.success());
        assertTrue(drawn.events().stream().anyMatch(event -> event instanceof GameEvent.DeckRefilled));
        assertTrue(game.drawnCard() != null && game.isPlayable(game.drawnCard()));
        assertEquals(A, game.currentPlayer());
    }

    @Test
    void penaltyDrawKeepsTurn() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        Result result = game.penalty(B, 1, GameEvent.DrawReason.CLAP);
        assertTrue(result.success());
        assertTrue(result.events().contains(new GameEvent.CardsDrawn(B, List.of(n(GREEN, 7)), GameEvent.DrawReason.CLAP)));
        assertEquals(3, game.hand(B).size());
        assertEquals(A, game.currentPlayer());
        assertTrue(game.play(A, n(RED, 1)).success());
        assertEquals(Result.Failure.NOT_IN_GAME, game.penalty(new UUID(9, 9), 1, GameEvent.DrawReason.CLAP).failure());
    }

    @Test
    void removingCurrentPlayerPassesTurnAndLastPlayerWinsByDefault() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertTrue(game.removePlayer(A).success());
        assertEquals(B, game.currentPlayer());
        assertEquals(List.of(B, C), game.players());
        Result last = game.removePlayer(C);
        assertTrue(last.success());
        assertEquals(Game.Phase.FINISHED, game.phase());
        assertEquals(B, game.winner());
        assertTrue(last.events().contains(new GameEvent.Won(B, 0, true)));
    }

    @Test
    void removingEarlierSeatKeepsCurrentPlayer() {
        Game game = game(rules(2, true), List.of(A, B, C),
                n(RED, 1), n(BLUE, 1), n(GREEN, 1), n(RED, 2), n(BLUE, 2), n(GREEN, 2), n(RED, 8));
        assertTrue(game.play(A, n(RED, 1)).success());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.removePlayer(A).success());
        assertEquals(B, game.currentPlayer());
    }

    @Test
    void timeoutDrawsAndPassesAndPicksColorWhenChoosing() {
        Game game = game(rules(2, true), List.of(A, B), Card.wild(), n(BLUE, 1), n(GREEN, 2), n(BLUE, 3), n(RED, 8));
        assertTrue(game.play(A, Card.wild()).success());
        assertTrue(game.forceEndTurn(A, true).success());
        assertEquals(GREEN, game.color());
        assertEquals(B, game.currentPlayer());
        assertTrue(game.forceEndTurn(B, true).success());
        assertEquals(3, game.hand(B).size());
        assertEquals(A, game.currentPlayer());
        assertTrue(game.forceEndTurn(A, false).success());
        assertEquals(1, game.hand(A).size());
    }
}
