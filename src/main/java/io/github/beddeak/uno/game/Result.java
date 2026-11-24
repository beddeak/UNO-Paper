package io.github.beddeak.uno.game;

import java.util.List;

public record Result(Failure failure, List<GameEvent> events) {

    public enum Failure {
        GAME_OVER("게임이 끝났습니다."),
        NOT_IN_GAME("게임 참가자가 아닙니다."),
        NOT_YOUR_TURN("아직 차례가 아닙니다."),
        CHOOSING_COLOR("먼저 색을 골라 주세요."),
        NOT_CHOOSING("지금은 색을 고를 수 없습니다."),
        NOT_IN_HAND("손에 없는 카드입니다."),
        NOT_MATCHING("색이나 숫자, 기호가 같은 카드만 낼 수 있습니다."),
        MUST_STACK_OR_DRAW("+2나 +4로 받아치거나 카드를 받아야 합니다."),
        ONLY_DRAWN_CARD("뽑은 카드만 낼 수 있습니다. 아니면 턴을 넘기세요."),
        ALREADY_DREW("이미 카드를 뽑았습니다. 뽑은 카드를 내거나 턴을 넘기세요."),
        CANNOT_PASS("카드를 뽑은 뒤에만 넘길 수 있습니다."),
        ALREADY_CALLED("이미 외쳤습니다."),
        NOTHING_TO_CALL("카드가 1장일 때만 외칠 수 있습니다.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public static Result ok(List<GameEvent> events) {
        return new Result(null, List.copyOf(events));
    }

    public static Result fail(Failure failure) {
        return new Result(failure, List.of());
    }

    public boolean success() {
        return failure == null;
    }
}
