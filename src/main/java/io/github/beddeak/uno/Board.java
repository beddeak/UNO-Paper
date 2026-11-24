package io.github.beddeak.uno;

import io.github.beddeak.uno.card.CardColor;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Board {

    private final Scoreboard scoreboard;
    private final Objective side;
    private final Objective below;
    private final Map<CardColor, Team> colorTeams = new EnumMap<>(CardColor.class);
    private final Team neutralTeam;
    private final Set<String> lineEntries = new HashSet<>();
    private final Set<String> belowEntries = new HashSet<>();

    Board() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        side = scoreboard.registerNewObjective("uno", Criteria.DUMMY,
                Component.text("UNO", NamedTextColor.GOLD, TextDecoration.BOLD));
        side.setDisplaySlot(DisplaySlot.SIDEBAR);
        side.numberFormat(NumberFormat.blank());
        below = scoreboard.registerNewObjective("uno_hand", Criteria.DUMMY, Component.text("장", NamedTextColor.GRAY));
        below.setDisplaySlot(DisplaySlot.BELOW_NAME);
        for (CardColor color : CardColor.values()) {
            Team team = scoreboard.registerNewTeam("lc_" + color.id());
            team.color(switch (color) {
                case RED -> NamedTextColor.RED;
                case YELLOW -> NamedTextColor.YELLOW;
                case GREEN -> NamedTextColor.GREEN;
                case BLUE -> NamedTextColor.BLUE;
            });
            colorTeams.put(color, team);
        }
        neutralTeam = scoreboard.registerNewTeam("lc_none");
        neutralTeam.color(NamedTextColor.WHITE);
    }

    void show(Player player) {
        player.setScoreboard(scoreboard);
    }

    void hide(Player player) {
        if (player.getScoreboard() == scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void lines(List<Component> lines) {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            // The client hides sidebar entries starting with '#', so use invisible color codes instead.
            String entry = "§" + Integer.toHexString(i) + "§r";
            used.add(entry);
            Score score = side.getScore(entry);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
        for (String old : lineEntries) {
            if (!used.contains(old)) {
                scoreboard.resetScores(old);
            }
        }
        lineEntries.clear();
        lineEntries.addAll(used);
    }

    void handSizes(Map<String, Integer> sizes) {
        for (String old : belowEntries) {
            if (!sizes.containsKey(old)) {
                below.getScore(old).resetScore();
            }
        }
        belowEntries.clear();
        for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
            below.getScore(entry.getKey()).setScore(entry.getValue());
            belowEntries.add(entry.getKey());
        }
    }

    void highlight(String playerName, CardColor color) {
        clearHighlights();
        if (playerName != null) {
            (color == null ? neutralTeam : colorTeams.get(color)).addEntry(playerName);
        }
    }

    void highlight(Map<String, CardColor> entries) {
        clearHighlights();
        entries.forEach((name, color) -> colorTeams.get(color).addEntry(name));
    }

    void clearHighlights() {
        for (Team team : scoreboard.getTeams()) {
            for (String entry : Set.copyOf(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
    }
}
