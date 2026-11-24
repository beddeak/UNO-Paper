package io.github.beddeak.uno.card;

import java.util.Locale;

public enum CardColor {
    RED("빨강", 0xE53935),
    YELLOW("노랑", 0xFBC02D),
    GREEN("초록", 0x2EAD4B),
    BLUE("파랑", 0x1E7BE0);

    private final String koreanName;
    private final int rgb;

    CardColor(String koreanName, int rgb) {
        this.koreanName = koreanName;
        this.rgb = rgb;
    }

    public String koreanName() {
        return koreanName;
    }

    public int rgb() {
        return rgb;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CardColor fromId(String id) {
        for (CardColor color : values()) {
            if (color.id().equals(id) || color.koreanName.equals(id)) {
                return color;
            }
        }
        return null;
    }
}
