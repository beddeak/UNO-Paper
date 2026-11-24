package io.github.beddeak.uno.game;

public final class MatchSize {
    public static final int MAX_PLAYERS = 10;

    private MatchSize() {}

    public static void require(int humans, int bots) {
        if (humans < 0 || bots < 0) {
            throw new IllegalArgumentException("참가 인원과 봇 수는 0 이상이어야 합니다.");
        }
        long total = (long) humans + bots;
        if (total < 2) throw new IllegalArgumentException("2명 이상 필요합니다. (봇 포함)");
        if (total > MAX_PLAYERS) throw new IllegalArgumentException("최대 10명까지 할 수 있습니다. (봇 포함)");
    }
}
