package io.github.beddeak.uno;

import io.github.beddeak.uno.CardItems.Button;
import io.github.beddeak.uno.art.CardArt;
import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.CardColor;
import io.github.beddeak.uno.card.CardType;
import io.github.beddeak.uno.game.Game;
import io.github.beddeak.uno.game.MatchSize;
import io.github.beddeak.uno.game.GameEvent;
import io.github.beddeak.uno.game.Result;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GameSession {

    private static final int[] CARD_SLOTS = buildCardSlots();
    private static final int HAND_SLOT = 6;
    private static final int CALL_SLOT = 7;
    private static final int DRAW_SLOT = 8;
    private static final int CLAP_PENALTY = 1;
    private static final int RECEIVE_FLY_TICKS = 8;
    /** Big penalties (+99) speed up after this many cards so the receiver is not stuck for over a minute. */
    private static final int RUSH_AFTER = 10;
    private static final int RUSH_TICKS = 2;
    private static final int CLAP_READY_TICKS = 15;
    private static final int CLAP_READY_SPREAD = 20;
    private static final int SIDEBAR_LINES = 15;
    private static final Component PREFIX = Component.text("[UNO] ", NamedTextColor.GOLD);

    private record BotCall(UUID actor, long due) {}

    private record Incoming(Card card, Boolean match) {}

    private static final class Receiving {
        private final ArrayDeque<Incoming> cards = new ArrayDeque<>();
        private final List<Card> received = new ArrayList<>();
        private long nextAt;
        private boolean flying;
    }

    private static final class Clap {
        private final Set<UUID> waiting = new LinkedHashSet<>();
        private final List<UUID> pressed = new ArrayList<>();
        private final Map<UUID, Long> botDue = new HashMap<>();
        private final long started;
        private final long opensAt;
        private boolean open;
        private long deadline;

        private Clap(long started, long opensAt) {
            this.started = started;
            this.opensAt = opensAt;
        }
    }

    private final UnoPlugin plugin;
    private final Settings settings;
    private final CardItems items;
    private final Game game;
    private final TableView table;
    private final Board board = new Board();
    private final Map<UUID, Board> personalBoards = new HashMap<>();
    private final BossBar bossBar = BossBar.bossBar(Component.text("UNO"), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
    private final Map<UUID, String> names;
    private final Set<UUID> humans;
    private final Map<UUID, Location> seats = new HashMap<>();
    private final Map<UUID, Mannequin> bots = new HashMap<>();
    private final Map<UUID, Integer> dealt = new HashMap<>();
    private final Map<UUID, Receiving> incoming = new LinkedHashMap<>();
    private final Map<UUID, Card[]> layouts = new HashMap<>();
    private final Map<UUID, Long> lastInput = new HashMap<>();
    private final Map<UUID, BotCall> botCalls = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Random random = new Random();
    private final List<GameEvent> openingEvents;
    private Clap clap;
    private boolean dealing = true;
    private boolean finished;
    private boolean closed;
    private int lastTurn = -1;
    private int turnTicksLeft;
    private int waitTicks;
    private long ticks;

    private GameSession(UnoPlugin plugin, Game game, Location center, Map<UUID, String> names,
                        Set<UUID> humans, List<GameEvent> openingEvents) {
        this.plugin = plugin;
        this.settings = plugin.settings();
        this.items = plugin.items();
        this.game = game;
        this.names = names;
        this.humans = humans;
        this.openingEvents = openingEvents;
        this.table = new TableView(plugin, items, center, settings.table().cardScale());
    }

    static GameSession start(UnoPlugin plugin, List<Player> players, int botCount, Location center) throws IOException {
        Settings settings = plugin.settings();
        MatchSize.require(players.size(), botCount);
        Map<UUID, String> names = new LinkedHashMap<>();
        Set<UUID> humans = new HashSet<>();
        List<UUID> order = new ArrayList<>();
        for (Player player : players) {
            names.put(player.getUniqueId(), player.getName());
            humans.add(player.getUniqueId());
            order.add(player.getUniqueId());
        }
        for (int i = 1; i <= botCount; i++) {
            UUID bot = UUID.randomUUID();
            names.put(bot, "봇 " + i);
            order.add(bot);
        }
        Collections.shuffle(order);
        for (Player player : players) {
            plugin.stash().restore(player);
        }
        for (Player player : players) {
            plugin.stash().save(player);
        }
        Game game = new Game(order, settings.rules(), new Random());
        List<GameEvent> opening = game.start();
        GameSession session = new GameSession(plugin, game, center, names, humans, opening);
        session.begin();
        return session;
    }

    private void begin() {
        table.spawn(settings.table().feltSize());
        List<UUID> order = game.players();
        for (int i = 0; i < order.size(); i++) {
            seats.put(order.get(i), seatLocation(i, order.size()));
        }
        for (UUID id : order) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeInventory();
                player.getInventory().clear();
                player.getInventory().setHeldItemSlot(0);
                player.setGlowing(false);
                if (settings.play().adventureMode()) {
                    player.setGameMode(GameMode.ADVENTURE);
                }
                if (settings.table().teleportPlayers()) {
                    player.teleport(seats.get(id));
                }
            } else if (!humans.contains(id)) {
                bots.put(id, table.spawnBot(seats.get(id), Component.text(names.get(id), NamedTextColor.AQUA)));
            }
        }
        for (UUID id : humans) {
            personalBoards.put(id, new Board());
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            boardFor(online.getUniqueId()).show(online);
            online.showBossBar(bossBar);
        }
        Bukkit.getServer().showTitle(Title.title(
                Component.text("UNO", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("카드를 나눠 줍니다", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(1800), Duration.ofMillis(400))));
        broadcast(Component.text("게임 시작! 순서: ", NamedTextColor.YELLOW)
                .append(Component.text(String.join(" → ", order.stream().map(names::get).toList()), NamedTextColor.WHITE)));
        sound(Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        refreshUi();
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L));
        int total = settings.rules().handSize() * order.size();
        int[] step = {0};
        BukkitTask[] deal = new BukkitTask[1];
        deal[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (closed) {
                deal[0].cancel();
                return;
            }
            if (step[0] >= total) {
                deal[0].cancel();
                finishDealing();
                return;
            }
            UUID id = order.get(step[0] % order.size());
            dealt.put(id, step[0] / order.size() + 1);
            table.flyFromDeck(handLocation(id), 1);
            sound(Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.5f + random.nextFloat() * 0.3f);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                renderHand(player);
            }
            step[0]++;
            if (step[0] % order.size() == 0) {
                refreshUi();
            }
        }, 30L, settings.play().dealIntervalTicks());
        tasks.add(deal[0]);
    }

    private void finishDealing() {
        dealing = false;
        dealt.clear();
        handle(openingEvents);
    }

    private void tick() {
        ticks++;
        if (closed || finished || dealing) {
            return;
        }
        if (!incoming.isEmpty()) {
            deliver();
        }
        if (clap != null) {
            tickClap();
            if (ticks % 5 == 0) {
                updateBossBar();
                actionBars();
            }
            return;
        }
        UUID current = game.currentPlayer();
        if (game.phase() == Game.Phase.CHOOSING_COLOR && humans.contains(current)) {
            Player chooser = Bukkit.getPlayer(current);
            if (chooser != null && !hasColorMenu(chooser)) {
                chooser.openInventory(new ColorMenu(items).getInventory());
            }
        }
        if (bots.containsKey(current)) {
            if (!incoming.containsKey(current) && ++waitTicks >= settings.play().botDelayTicks()) {
                waitTicks = 0;
                botMove(current);
                return;
            }
        } else if (Bukkit.getPlayer(current) == null) {
            if (++waitTicks >= 40) {
                waitTicks = 0;
                broadcast(Component.text(name(current) + " 님이 자리에 없어 차례를 넘깁니다.", NamedTextColor.GRAY));
                apply(game.forceEndTurn(current, false), null);
                return;
            }
        } else {
            waitTicks = 0;
            if (settings.turnSeconds() > 0 && !incoming.containsKey(current)) {
                turnTicksLeft--;
                if (turnTicksLeft <= 0) {
                    broadcast(Component.text(name(current) + " 시간 초과!", NamedTextColor.RED));
                    apply(game.forceEndTurn(current, true), null);
                    return;
                }
                if (turnTicksLeft <= 100 && turnTicksLeft % 20 == 0) {
                    Player player = Bukkit.getPlayer(current);
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.6f);
                }
            }
        }
        runBotCalls();
        if (ticks % 10 == 0) {
            updateBossBar();
            actionBars();
        }
    }

    public void playCard(Player player, Card card) {
        if (clap != null) {
            clapBy(player.getUniqueId());
            return;
        }
        if (!accept(player)) {
            return;
        }
        if (game.phase() == Game.Phase.CHOOSING_COLOR && player.getUniqueId().equals(game.currentPlayer())) {
            openColorMenu(player);
            return;
        }
        apply(game.play(player.getUniqueId(), card), player);
    }

    public void pressButton(Player player, Button button) {
        if (button == Button.CALL) {
            call(player);
            return;
        }
        if (clap != null) {
            clapBy(player.getUniqueId());
            return;
        }
        if (button == Button.HAND) {
            openHand(player);
            return;
        }
        if (button == Button.CLAP || !accept(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (game.phase() == Game.Phase.CHOOSING_COLOR && id.equals(game.currentPlayer())) {
            openColorMenu(player);
            return;
        }
        boolean drew = id.equals(game.currentPlayer()) && game.drawnCard() != null;
        apply(drew ? game.pass(id) : game.draw(id), player);
    }

    public void call(Player player) {
        if (!accept(player)) {
            return;
        }
        apply(game.call(player.getUniqueId()), player);
    }

    public void swing(Player player) {
        if (clap != null) {
            clapBy(player.getUniqueId());
        }
    }

    private void openHand(Player player) {
        if (closed || !isParticipant(player.getUniqueId())) {
            return;
        }
        HandMenu menu = new HandMenu();
        player.openInventory(menu.getInventory());
        renderHand(player);
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
    }

    public void chooseColor(Player player, CardColor color) {
        if (closed || finished) {
            return;
        }
        if (apply(game.chooseColor(player.getUniqueId(), color), player)) {
            player.closeInventory();
        }
    }

    public boolean kick(UUID player) {
        if (closed || finished || dealing) {
            return false;
        }
        return apply(game.removePlayer(player), null);
    }

    private boolean accept(Player player) {
        if (closed || finished) {
            return false;
        }
        if (dealing) {
            player.sendActionBar(Component.text("카드를 나눠 주는 중입니다.", NamedTextColor.GRAY));
            return false;
        }
        if (!isParticipant(player.getUniqueId())) {
            player.sendActionBar(Component.text("이번 게임 참가자가 아닙니다.", NamedTextColor.GRAY));
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastInput.put(player.getUniqueId(), now);
        return last == null || now - last >= 150;
    }

    private boolean apply(Result result, Player actor) {
        if (!result.success()) {
            if (actor != null) {
                actor.sendActionBar(Component.text(result.failure().message(), NamedTextColor.RED));
                actor.playSound(actor, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            }
            return false;
        }
        handle(result.events());
        return true;
    }

    private void handle(List<GameEvent> events) {
        GameEvent.Won won = null;
        boolean six = false;
        for (GameEvent event : events) {
            present(event);
            if (event instanceof GameEvent.Won w) {
                won = w;
            }
            if (event instanceof GameEvent.CardPlayed played && isSix(played.card())
                    || event instanceof GameEvent.StarterFlipped flipped && isSix(flipped.card())) {
                six = true;
            }
        }
        if (won != null) {
            finish(won);
            return;
        }
        if (six && clap == null && settings.clap().enabled()) {
            startClap();
        }
        refreshUi();
    }

    private void present(GameEvent event) {
        switch (event) {
            case GameEvent.StarterFlipped(Card card) -> {
                table.playCard(card.id(), null);
                sound(Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                broadcast(Component.text("첫 카드: ", NamedTextColor.YELLOW).append(CardItems.name(card)));
            }
            case GameEvent.TurnStarted _ -> {
            }
            case GameEvent.CardPlayed(UUID player, Card card, int handSize) -> {
                table.playCard(card.id(), handLocation(player));
                sound(Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f + random.nextFloat() * 0.3f);
                switch (card.type()) {
                    case SKIP -> sound(Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
                    case REVERSE -> sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
                    case DRAW_TWO, WILD_DRAW_FOUR -> sound(Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.2f);
                    case WILD_DRAW_TEN -> {
                        sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.3f);
                        Bukkit.getServer().showTitle(Title.title(
                                Component.text("+10", NamedTextColor.RED, TextDecoration.BOLD),
                                Component.text(name(player) + "의 와일드 +10!", NamedTextColor.WHITE),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
                    }
                    case WILD_DRAW_NINETY_NINE -> {
                        Location center = table.center();
                        center.getWorld().strikeLightningEffect(center);
                        sound(Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
                        Bukkit.getServer().showTitle(Title.title(
                                Component.text("+99", NamedTextColor.GOLD, TextDecoration.BOLD),
                                Component.text(name(player) + "의 무적 카드! 아무도 못 막습니다", NamedTextColor.YELLOW),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(400))));
                    }
                    case WILD -> sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    default -> {
                    }
                }
                broadcast(Component.text(name(player), NamedTextColor.WHITE)
                        .append(Component.text(" ▶ ", NamedTextColor.GRAY))
                        .append(CardItems.name(card))
                        .append(Component.text(handSize == 1 ? "  (1장 남음)" : "", NamedTextColor.RED)));
            }
            case GameEvent.ColorRequested(UUID player) -> {
                Player chooser = Bukkit.getPlayer(player);
                if (chooser != null) {
                    later(1, () -> {
                        if (!closed && game.phase() == Game.Phase.CHOOSING_COLOR && chooser.isOnline() && !hasColorMenu(chooser)) {
                            openColorMenu(chooser);
                        }
                    });
                }
            }
            case GameEvent.ColorChosen(UUID player, CardColor color, boolean automatic) -> {
                Card top = game.topCard();
                if (top.isWild()) {
                    table.recolorTop(CardArt.chosenWildId(top, color));
                }
                sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
                broadcast(Component.text(name(player) + (automatic ? "의 색이 자동으로 " : " → 다음 색 "), NamedTextColor.WHITE)
                        .append(Component.text("■ " + color.koreanName(), CardItems.color(color), TextDecoration.BOLD)));
                Player chooser = Bukkit.getPlayer(player);
                if (chooser != null) {
                    later(1, () -> {
                        if (hasColorMenu(chooser)) {
                            chooser.closeInventory();
                        }
                    });
                }
            }
            case GameEvent.Skipped(UUID player) -> {
                broadcast(Component.text(name(player) + " 차례 건너뜀", NamedTextColor.GRAY));
                subtitle(player, Component.text("차례를 건너뜁니다!", NamedTextColor.RED));
            }
            case GameEvent.Reversed(boolean clockwise) ->
                    broadcast(Component.text("방향 전환! " + (clockwise ? "↻ 시계 방향" : "↺ 반시계 방향"), NamedTextColor.AQUA));
            case GameEvent.StackGrew(UUID target, int total) -> {
                boolean unstoppable = !game.topCard().type().stacks();
                broadcast(Component.text("+" + total + (unstoppable ? "! " : " 누적! "), NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text(name(target) + (unstoppable ? " 님은 무조건 받습니다" : " 님, 받아치거나 받으세요"), NamedTextColor.WHITE)));
            }
            case GameEvent.CardsDrawn(UUID player, List<Card> cards, GameEvent.DrawReason reason) -> presentDraw(player, cards, reason);
            case GameEvent.DrawnPlayable _ -> {
            }
            case GameEvent.Passed(UUID player) -> broadcast(Component.text(name(player) + " 패스", NamedTextColor.GRAY));
            case GameEvent.Called(UUID player) -> {
                Bukkit.getServer().showTitle(Title.title(
                        Component.text("UNO!", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(name(player), NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1300), Duration.ofMillis(300))));
                sound(Sound.BLOCK_BELL_USE, 1f, 1.2f);
                broadcast(Component.text(name(player) + ": UNO!", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
            case GameEvent.Caught(UUID catcher, UUID target) -> {
                Bukkit.getServer().showTitle(Title.title(
                        Component.text(name(target) + " 걸렸다!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text(name(catcher) + " 님이 잡았습니다 · +" + game.rules().callPenalty() + "장", NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300))));
                sound(Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                broadcast(Component.text(name(catcher) + " 님이 안 외친 " + name(target) + " 님을 잡았습니다!", NamedTextColor.RED));
            }
            case GameEvent.DeckRefilled _ -> {
            }
            case GameEvent.PlayerRemoved(UUID player) -> {
                broadcast(Component.text(name(player) + " 님이 게임에서 빠졌습니다.", NamedTextColor.GRAY));
                Mannequin bot = bots.remove(player);
                if (bot != null) {
                    bot.remove();
                }
                incoming.remove(player);
                Player removed = Bukkit.getPlayer(player);
                if (removed != null) {
                    removed.setGlowing(false);
                    if (removed.getOpenInventory().getTopInventory().getHolder(false) instanceof HandMenu) {
                        removed.closeInventory();
                    }
                    plugin.stash().restore(removed);
                }
            }
            case GameEvent.Won _ -> {
            }
        }
    }

    private void presentDraw(UUID player, List<Card> cards, GameEvent.DrawReason reason) {
        int count = cards.size();
        if (!cards.isEmpty()) {
            Receiving receiving = incoming.computeIfAbsent(player, _ -> {
                Receiving fresh = new Receiving();
                fresh.nextAt = ticks + 1;
                return fresh;
            });
            boolean check = reason == GameEvent.DrawReason.VOLUNTARY;
            boolean playable = player.equals(game.currentPlayer()) && game.drawnCard() != null;
            for (int i = 0; i < count; i++) {
                receiving.cards.add(new Incoming(cards.get(i), check ? i == count - 1 && playable : null));
            }
        }
        Player drawer = Bukkit.getPlayer(player);
        switch (reason) {
            case VOLUNTARY -> broadcast(Component.text(name(player) + " 카드 " + count + "장 뽑음", count >= 5 ? NamedTextColor.RED : NamedTextColor.GRAY));
            case TIMEOUT -> broadcast(Component.text(name(player) + " 시간 초과로 " + count + "장 받음", NamedTextColor.GRAY));
            case CLAP -> sound(Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
            case PENALTY, STACK, CALL_PENALTY -> {
                broadcast(Component.text(name(player) + " +" + count + "장!", NamedTextColor.RED, TextDecoration.BOLD));
                if (drawer != null) {
                    drawer.showTitle(Title.title(Component.text("+" + count, NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text(reason == GameEvent.DrawReason.CALL_PENALTY ? "안 외쳐서 걸렸습니다" : "카드를 받았습니다", NamedTextColor.WHITE),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1300), Duration.ofMillis(300))));
                }
                sound(Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
            }
        }
    }

    private void deliver() {
        boolean landed = false;
        for (UUID id : List.copyOf(incoming.keySet())) {
            Receiving receiving = incoming.get(id);
            Incoming next = receiving.cards.peekFirst();
            if (next == null || !game.isPlayer(id)) {
                incoming.remove(id);
                continue;
            }
            if (ticks < receiving.nextAt) {
                continue;
            }
            boolean rush = next.match() == null && receiving.received.size() >= RUSH_AFTER;
            if (!receiving.flying) {
                table.flyFromDeck(handLocation(id), 1);
                if (!rush) {
                    receiving.flying = true;
                    receiving.nextAt = ticks + RECEIVE_FLY_TICKS;
                    sound(Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.5f + random.nextFloat() * 0.3f);
                    continue;
                }
            }
            receiving.flying = false;
            receiving.cards.pollFirst();
            receiving.received.add(next.card());
            receiving.nextAt = ticks + (rush ? RUSH_TICKS : Math.max(1, settings.play().drawIntervalTicks() - RECEIVE_FLY_TICKS));
            landed = true;
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                renderHand(player);
                if (next.match() == null) {
                    player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f + random.nextFloat() * 0.4f);
                } else {
                    showCheck(player, next, receiving.received.size());
                }
            }
            if (receiving.cards.isEmpty()) {
                incoming.remove(id);
                finishReceiving(player, receiving.received);
            }
        }
        if (landed) {
            updateBoard();
        }
    }

    private void showCheck(Player player, Incoming drawn, int nth) {
        boolean match = drawn.match();
        player.showTitle(Title.title(Component.text(nth + "장째", NamedTextColor.GRAY),
                CardItems.name(drawn.card()).append(match
                        ? Component.text("  ✔ 낼 수 있음!", NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text("  ✗ 안 맞음", NamedTextColor.RED, TextDecoration.BOLD)),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(match ? 1500 : settings.play().drawIntervalTicks() * 50L + 200), Duration.ofMillis(150))));
        if (match) {
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        }
    }

    private void finishReceiving(Player player, List<Card> cards) {
        if (player == null) {
            return;
        }
        cards.sort(Card.HAND_ORDER);
        Map<Card, Integer> grouped = new LinkedHashMap<>();
        for (Card card : cards) {
            grouped.merge(card, 1, Integer::sum);
        }
        Component list = Component.text("받은 카드 " + cards.size() + "장: ", NamedTextColor.GRAY);
        boolean first = true;
        for (Map.Entry<Card, Integer> entry : grouped.entrySet()) {
            list = list.append(first ? Component.empty() : Component.text(", ", NamedTextColor.GRAY)).append(CardItems.name(entry.getKey()));
            if (entry.getValue() > 1) {
                list = list.append(Component.text(" ×" + entry.getValue(), NamedTextColor.WHITE));
            }
            first = false;
        }
        player.sendMessage(PREFIX.append(list));
    }

    private void finish(GameEvent.Won won) {
        finished = true;
        clap = null;
        incoming.clear();
        UUID winner = won.winner();
        String winnerName = winner == null ? null : name(winner);
        Bukkit.getServer().showTitle(Title.title(
                Component.text(winnerName == null ? "게임 종료" : winnerName + " 승리!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(winnerName == null ? "" : won.byDefault() ? "남은 상대가 없습니다" : "+" + won.points() + "점", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3500), Duration.ofMillis(800))));
        sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (winner != null) {
            for (int i = 0; i < 3; i++) {
                later(i * 12L, () -> firework(handLocation(winner)));
            }
        }
        broadcast(Component.text("===== 결과 =====", NamedTextColor.GOLD));
        for (UUID id : game.players()) {
            boolean isWinner = id.equals(winner);
            broadcast(Component.text((isWinner ? "★ " : "  ") + name(id), isWinner ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                    .append(Component.text(isWinner ? "  승리" : "  " + game.hand(id).size() + "장 (" + game.pointsInHand(id) + "점)", NamedTextColor.GRAY)));
        }
        if (winner != null && humans.contains(winner) && !won.byDefault()) {
            plugin.scores().recordWin(winner, winnerName, won.points());
        }
        List<Scores.Entry> ranking = plugin.scores().ranking();
        if (!ranking.isEmpty()) {
            Component line = Component.text("누적 점수: ", NamedTextColor.YELLOW);
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                Scores.Entry entry = ranking.get(i);
                line = line.append(Component.text((i == 0 ? "" : " · ") + entry.name() + " " + entry.points() + "점(" + entry.wins() + "승)", NamedTextColor.WHITE));
            }
            broadcast(line);
        }
        bossBar.name(Component.text(winnerName == null ? "게임 종료" : winnerName + " 승리!", NamedTextColor.GOLD, TextDecoration.BOLD));
        bossBar.progress(1f);
        bossBar.color(BossBar.Color.YELLOW);
        table.info(Component.text(winnerName == null ? "게임 종료" : winnerName + " 승리!", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (UUID id : game.players()) {
            setGlowing(id, id.equals(winner));
        }
        for (Board each : boards()) {
            each.highlight(winner == null ? null : entryName(winner), CardColor.YELLOW);
        }
        for (UUID id : game.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                renderHand(player);
            }
        }
        updateBoard();
        later(settings.play().finishSeconds() * 20L, () -> plugin.endSession(this));
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        table.remove();
        bots.clear();
        for (Board each : boards()) {
            each.clearHighlights();
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (Board each : boards()) {
                each.hide(online);
            }
            online.hideBossBar(bossBar);
            if (online.getOpenInventory().getTopInventory().getHolder(false) instanceof ColorMenu
                    || online.getOpenInventory().getTopInventory().getHolder(false) instanceof HandMenu) {
                online.closeInventory();
            }
        }
        for (UUID id : humans) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setGlowing(false);
                plugin.stash().restore(player);
            }
        }
    }

    public void onJoin(Player player) {
        if (closed) {
            return;
        }
        boardFor(player.getUniqueId()).show(player);
        player.showBossBar(bossBar);
        if (isParticipant(player.getUniqueId())) {
            names.put(player.getUniqueId(), player.getName());
            later(1, () -> {
                if (!closed && player.isOnline()) {
                    renderHand(player);
                    refreshUi();
                }
            });
        }
    }

    public void onRespawn(Player player) {
        if (isParticipant(player.getUniqueId())) {
            later(1, () -> {
                if (!closed && player.isOnline()) {
                    renderHand(player);
                }
            });
        }
    }

    public boolean isParticipant(UUID player) {
        return !closed && humans.contains(player) && game.isPlayer(player);
    }

    public boolean wasParticipant(UUID player) {
        return !closed && humans.contains(player);
    }

    public boolean isDeck(org.bukkit.entity.Entity entity) {
        return table.isDeck(entity);
    }

    public boolean isChoosing(Player player) {
        return game.phase() == Game.Phase.CHOOSING_COLOR && player.getUniqueId().equals(game.currentPlayer());
    }

    public Location center() {
        return table.center();
    }

    public List<Component> status() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("상태: " + (finished ? "끝남" : dealing ? "카드 나눠 주는 중" : "진행 중")
                + " · 현재 색 " + game.color().koreanName() + (clap != null ? " · 박수 중" : ""), NamedTextColor.YELLOW));
        for (UUID id : game.players()) {
            boolean current = id.equals(game.currentPlayer());
            lines.add(Component.text((current ? "▶ " : "  ") + name(id) + " " + game.hand(id).size() + "장"
                    + (bots.containsKey(id) ? " (봇)" : Bukkit.getPlayer(id) == null ? " (나감)" : ""), current ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        }
        return lines;
    }

    public UUID playerByName(String name) {
        for (UUID id : game.players()) {
            if (name(id).equalsIgnoreCase(name)) {
                return id;
            }
        }
        return null;
    }

    public List<String> playerNames() {
        return game.players().stream().map(this::name).toList();
    }

    private void botMove(UUID bot) {
        if (game.phase() == Game.Phase.CHOOSING_COLOR) {
            apply(game.chooseColor(bot, game.bestColor(bot)), null);
            return;
        }
        Card drawn = game.drawnCard();
        if (drawn != null) {
            apply(game.canPlay(bot, drawn) ? game.play(bot, drawn) : game.pass(bot), null);
            return;
        }
        Card choice = null;
        for (Card card : game.hand(bot)) {
            if (game.canPlay(bot, card) && (choice == null || botPrefers(card, choice))) {
                choice = card;
            }
        }
        if (choice == null) {
            apply(game.draw(bot), null);
            return;
        }
        apply(game.play(bot, choice), null);
    }

    private static boolean botPrefers(Card candidate, Card current) {
        if (candidate.isWild() != current.isWild()) {
            return !candidate.isWild();
        }
        return candidate.points() > current.points();
    }

    private void scheduleBotCalls() {
        if (bots.isEmpty()) {
            botCalls.clear();
            return;
        }
        botCalls.keySet().removeIf(target -> !game.isExposed(target));
        for (UUID target : game.players()) {
            if (!game.isExposed(target) || botCalls.containsKey(target)) {
                continue;
            }
            if (bots.containsKey(target) && random.nextDouble() < 0.8) {
                botCalls.put(target, new BotCall(target, ticks + 10 + random.nextInt(16)));
                continue;
            }
            List<UUID> catchers = new ArrayList<>(bots.keySet());
            catchers.remove(target);
            if (!catchers.isEmpty()) {
                UUID catcher = catchers.get(random.nextInt(catchers.size()));
                botCalls.put(target, new BotCall(catcher, ticks + 40 + random.nextInt(31)));
            }
        }
    }

    private void runBotCalls() {
        if (botCalls.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, BotCall> entry : List.copyOf(botCalls.entrySet())) {
            BotCall call = entry.getValue();
            if (ticks < call.due()) {
                continue;
            }
            botCalls.remove(entry.getKey());
            if (game.isExposed(entry.getKey()) && game.isPlayer(call.actor())) {
                apply(game.call(call.actor()), null);
                return;
            }
        }
    }

    private static boolean isSix(Card card) {
        return card.type() == CardType.NUMBER && card.number() == 6;
    }

    private boolean canClap(UUID id) {
        return game.isPlayer(id) && (bots.containsKey(id) || humans.contains(id) && Bukkit.getPlayer(id) != null);
    }

    private void startClap() {
        int ready = CLAP_READY_TICKS + random.nextInt(CLAP_READY_SPREAD + 1);
        Clap round = new Clap(ticks, ticks + ready);
        for (UUID id : game.players()) {
            if (canClap(id)) {
                round.waiting.add(id);
            }
        }
        if (round.waiting.size() < 2) {
            return;
        }
        clap = round;
        Bukkit.getServer().showTitle(Title.title(
                Component.text("6!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("준비…", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(ready * 50L + 500), Duration.ZERO)));
        sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.8f);
    }

    private void openClap(Clap round) {
        round.open = true;
        round.deadline = ticks + settings.clap().seconds() * 20L;
        for (UUID id : round.waiting) {
            if (bots.containsKey(id)) {
                round.botDue.put(id, ticks + 8 + random.nextInt(25));
            }
        }
        sound(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.8f);
        sound(Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
        showClapTitle(round);
        refreshUi();
    }

    private void showClapTitle(Clap round) {
        Component names = Component.empty();
        boolean first = true;
        for (UUID id : game.players()) {
            boolean pressed = round.pressed.contains(id);
            if (!pressed && !round.waiting.contains(id)) {
                continue;
            }
            names = names.append(Component.text(first ? "" : "  "))
                    .append(Component.text((pressed ? "✔" : "") + name(id), pressed ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));
            first = false;
        }
        long left = Math.max(0, round.deadline - ticks);
        Bukkit.getServer().showTitle(Title.title(
                Component.text("치세요!", NamedTextColor.GOLD, TextDecoration.BOLD), names,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(left * 50 + 300), Duration.ofMillis(200))));
    }

    private void clapBy(UUID id) {
        Clap round = clap;
        if (round == null) {
            return;
        }
        if (!round.open) {
            Player early = Bukkit.getPlayer(id);
            if (early != null && ticks - round.started > 5) {
                early.sendActionBar(Component.text("아직! '치세요!'가 뜨면 클릭", NamedTextColor.RED));
            }
            return;
        }
        if (!round.waiting.remove(id)) {
            return;
        }
        round.pressed.add(id);
        sound(Sound.BLOCK_NOTE_BLOCK_SNARE, 1f, 0.9f + round.pressed.size() * 0.1f);
        Mannequin bot = bots.get(id);
        if (bot != null && bot.isValid()) {
            bot.swingMainHand();
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.sendActionBar(Component.text("짝! " + round.pressed.size() + "번째", NamedTextColor.GREEN, TextDecoration.BOLD));
            renderHand(player);
        }
        if (round.waiting.isEmpty()) {
            resolveClap();
            return;
        }
        showClapTitle(round);
        updateGlow();
        updateBossBar();
    }

    private void tickClap() {
        Clap round = clap;
        round.waiting.removeIf(id -> !canClap(id));
        if (!round.open) {
            if (round.waiting.size() < 2) {
                clap = null;
                refreshUi();
                onTurnStart(game.currentPlayer());
            } else if (ticks >= round.opensAt) {
                openClap(round);
            }
            return;
        }
        for (Map.Entry<UUID, Long> entry : List.copyOf(round.botDue.entrySet())) {
            if (ticks >= entry.getValue()) {
                round.botDue.remove(entry.getKey());
                clapBy(entry.getKey());
                if (clap != round) {
                    return;
                }
            }
        }
        if (round.waiting.isEmpty() || ticks >= round.deadline) {
            resolveClap();
        }
    }

    private void resolveClap() {
        Clap round = clap;
        clap = null;
        List<UUID> losers = new ArrayList<>();
        for (UUID id : round.waiting) {
            if (canClap(id)) {
                losers.add(id);
            }
        }
        if (losers.isEmpty() && !round.pressed.isEmpty() && game.isPlayer(round.pressed.getLast())) {
            losers.add(round.pressed.getLast());
        }
        Component line = Component.text("박수 순서: ", NamedTextColor.YELLOW);
        for (int i = 0; i < round.pressed.size(); i++) {
            UUID id = round.pressed.get(i);
            line = line.append(Component.text((i == 0 ? "" : " → ") + name(id), losers.contains(id) ? NamedTextColor.RED : NamedTextColor.WHITE));
        }
        for (UUID id : round.waiting) {
            if (losers.contains(id)) {
                line = line.append(Component.text(" · 안 누름: " + name(id), NamedTextColor.RED));
            }
        }
        broadcast(line);
        if (!losers.isEmpty()) {
            String names = String.join(", ", losers.stream().map(this::name).toList());
            Bukkit.getServer().showTitle(Title.title(
                    Component.text(names + " 꼴찌!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("박수 +" + CLAP_PENALTY + "장", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            broadcast(Component.text(names + " 박수 꼴찌 +" + CLAP_PENALTY + "장!", NamedTextColor.RED, TextDecoration.BOLD));
        }
        List<GameEvent> events = new ArrayList<>();
        for (UUID loser : losers) {
            Result result = game.penalty(loser, CLAP_PENALTY, GameEvent.DrawReason.CLAP);
            if (result.success()) {
                events.addAll(result.events());
            }
        }
        handle(events);
        if (!closed && !finished) {
            int turn = game.turnNumber();
            later(25, () -> {
                if (clap == null && !finished && game.turnNumber() == turn) {
                    onTurnStart(game.currentPlayer());
                }
            });
        }
    }

    private void refreshUi() {
        if (closed) {
            return;
        }
        for (UUID id : game.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && humans.contains(id)) {
                renderHand(player);
            }
        }
        if (!dealing && !finished && game.turnNumber() != lastTurn) {
            lastTurn = game.turnNumber();
            turnTicksLeft = settings.turnSeconds() * 20;
            waitTicks = 0;
            if (clap == null) {
                onTurnStart(game.currentPlayer());
            }
        }
        if (game.drawnCard() != null) {
            turnTicksLeft = Math.max(turnTicksLeft, 200);
        }
        scheduleBotCalls();
        updateBoard();
        updateBossBar();
        updateTable();
        updateGlow();
        actionBars();
    }

    private void onTurnStart(UUID current) {
        Player player = Bukkit.getPlayer(current);
        if (player == null || !humans.contains(current)) {
            return;
        }
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        Component sub = game.pendingDraw() > 0
                ? Component.text("+" + game.pendingDraw() + "! 받아치거나 받으세요", NamedTextColor.RED, TextDecoration.BOLD)
                : Component.text("당신의 차례입니다", NamedTextColor.GOLD);
        player.showTitle(Title.title(Component.empty(), sub,
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(250))));
    }

    private void renderHand(Player player) {
        UUID id = player.getUniqueId();
        Map<Card, Integer> grouped = new LinkedHashMap<>();
        for (Card card : visibleHand(id)) {
            grouped.merge(card, 1, Integer::sum);
        }
        boolean myTurn = clap == null && !dealing && !finished && game.phase() == Game.Phase.TURN && id.equals(game.currentPlayer());
        ItemStack[] wanted = new ItemStack[36];
        int held = dealing ? -1 : cardSlotIndex(player.getInventory().getHeldItemSlot());
        Card[] layout = HandLayout.arrange(dealing ? null : layouts.get(id), grouped.keySet(), CARD_SLOTS.length, held);
        layouts.put(id, layout);
        for (int i = 0; i < layout.length; i++) {
            Card card = layout[i];
            if (card != null) {
                wanted[CARD_SLOTS[i]] = items.card(card, grouped.get(card), myTurn && game.canPlay(id, card));
            }
        }
        if (!finished) {
            wanted[HAND_SLOT] = items.button(Button.HAND, 0, false);
            wanted[CALL_SLOT] = items.button(Button.CALL, 0, !dealing && game.canCall(id));
            if (clap != null && clap.open && clap.waiting.contains(id)) {
                wanted[DRAW_SLOT] = items.button(Button.CLAP, 0, true);
            } else if (myTurn && game.pendingDraw() > 0) {
                wanted[DRAW_SLOT] = items.button(Button.TAKE, game.pendingDraw(), true);
            } else if (myTurn && game.drawnCard() != null) {
                wanted[DRAW_SLOT] = items.button(Button.PASS, 0, true);
            } else {
                wanted[DRAW_SLOT] = items.button(Button.DRAW, 0, false);
            }
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < wanted.length; slot++) {
            ItemStack current = inventory.getItem(slot);
            boolean currentEmpty = current == null || current.isEmpty();
            if (wanted[slot] == null ? !currentEmpty : !Objects.equals(current, wanted[slot])) {
                inventory.setItem(slot, wanted[slot]);
            }
        }
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof HandMenu menu) {
            Inventory view = menu.getInventory();
            Card[] cards = HandLayout.menu(grouped.keySet());
            for (int slot = 0; slot < HandLayout.MENU_SIZE; slot++) {
                Card card = cards[slot];
                Integer count = card == null ? null : grouped.get(card);
                ItemStack want = count == null ? null : items.card(card, count, myTurn && game.canPlay(id, card));
                ItemStack current = view.getItem(slot);
                if (want == null ? current != null && !current.isEmpty() : !Objects.equals(current, want)) {
                    view.setItem(slot, want);
                }
            }
        }
    }

    private Board boardFor(UUID id) {
        return personalBoards.getOrDefault(id, board);
    }

    private List<Board> boards() {
        List<Board> all = new ArrayList<>(personalBoards.values());
        all.add(board);
        return all;
    }

    private void updateBoard() {
        List<Component> header = new ArrayList<>();
        boolean choosing = game.phase() == Game.Phase.CHOOSING_COLOR;
        header.add(Component.text("현재 색 ", NamedTextColor.GRAY).append(choosing
                        ? Component.text("고르는 중…", NamedTextColor.LIGHT_PURPLE)
                        : Component.text("■ " + game.color().koreanName(), CardItems.color(game.color()), TextDecoration.BOLD))
                .append(Component.text(game.clockwise() ? "  ↻" : "  ↺", NamedTextColor.GRAY)));
        if (game.pendingDraw() > 0) {
            header.add(Component.text("누적 +" + game.pendingDraw(), NamedTextColor.RED, TextDecoration.BOLD));
        }
        if (clap != null) {
            header.add(Component.text("6! 박수!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        header.add(Component.empty());
        List<Component> entries = new ArrayList<>();
        Map<String, Integer> sizes = new HashMap<>();
        for (UUID id : game.players()) {
            int count = visibleCount(id);
            boolean current = !dealing && id.equals(game.currentPlayer());
            Component entry = Component.text(current ? "▶" : " ", NamedTextColor.GOLD)
                    .append(Component.text(name(id), current ? NamedTextColor.YELLOW : NamedTextColor.WHITE))
                    .append(Component.text(" " + count + "장", count <= 2 ? NamedTextColor.RED : NamedTextColor.GRAY));
            if (game.isExposed(id)) {
                entry = entry.append(Component.text("!", NamedTextColor.RED, TextDecoration.BOLD));
            } else if (game.hasCalled(id)) {
                entry = entry.append(Component.text("★", NamedTextColor.GOLD));
            }
            if (humans.contains(id) && Bukkit.getPlayer(id) == null) {
                entry = entry.append(Component.text("(나감)", NamedTextColor.DARK_GRAY));
            }
            entries.add(entry);
            if (humans.contains(id)) {
                sizes.put(name(id), count);
            }
            Mannequin bot = bots.get(id);
            if (bot != null && bot.isValid()) {
                bot.setDescription(Component.text(count + "장", count <= 2 ? NamedTextColor.RED : NamedTextColor.GRAY));
            }
        }
        List<Component> shared = new ArrayList<>(header);
        shared.addAll(packEntries(entries, SIDEBAR_LINES - header.size()));
        board.lines(shared);
        board.handSizes(sizes);
        for (Map.Entry<UUID, Board> personal : personalBoards.entrySet()) {
            Board view = personal.getValue();
            view.handSizes(sizes);
            if (!game.isPlayer(personal.getKey())) {
                view.lines(shared);
                continue;
            }
            List<Component> hand = handLines(personal.getKey());
            List<Component> lines = new ArrayList<>(header);
            lines.addAll(packEntries(entries, SIDEBAR_LINES - header.size() - hand.size() - 1));
            lines.add(Component.empty());
            lines.addAll(hand);
            view.lines(lines);
        }
    }

    private static List<Component> packEntries(List<Component> entries, int maxLines) {
        int perLine = 1;
        while (perLine < entries.size() && (entries.size() + perLine - 1) / perLine > Math.max(1, maxLines)) {
            perLine++;
        }
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i += perLine) {
            Component line = Component.empty();
            for (int j = i; j < Math.min(entries.size(), i + perLine); j++) {
                line = line.append(j == i ? Component.empty() : Component.text("  ")).append(entries.get(j));
            }
            lines.add(line);
        }
        return lines;
    }

    private List<Component> handLines(UUID id) {
        List<Card> hand = visibleHand(id);
        boolean myTurn = clap == null && !dealing && !finished && game.phase() == Game.Phase.TURN && id.equals(game.currentPlayer());
        Map<Card, Integer> grouped = new LinkedHashMap<>();
        for (Card card : hand) {
            grouped.merge(card, 1, Integer::sum);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("내 카드 " + hand.size() + "장", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(myTurn ? "  굵은 카드 = 낼 수 있음" : "", NamedTextColor.GRAY)));
        for (CardColor color : CardColor.values()) {
            Component line = Component.text(color.koreanName(), CardItems.color(color));
            boolean any = false;
            for (Map.Entry<Card, Integer> entry : grouped.entrySet()) {
                if (entry.getKey().color() == color) {
                    line = line.append(Component.text(" ")).append(handToken(id, entry.getKey(), entry.getValue(), myTurn));
                    any = true;
                }
            }
            if (any) {
                lines.add(line);
            }
        }
        Component wilds = Component.empty();
        boolean anyWild = false;
        for (Map.Entry<Card, Integer> entry : grouped.entrySet()) {
            if (entry.getKey().isWild()) {
                wilds = wilds.append(Component.text(anyWild ? " " : "")).append(handToken(id, entry.getKey(), entry.getValue(), myTurn));
                anyWild = true;
            }
        }
        if (anyWild) {
            lines.add(wilds);
        }
        return lines;
    }

    private Component handToken(UUID id, Card card, int count, boolean myTurn) {
        String symbol = switch (card.type()) {
            case NUMBER -> Integer.toString(card.number());
            case SKIP -> "⊘";
            case REVERSE -> "⇄";
            case DRAW_TWO -> "+2";
            case WILD -> "와일드";
            case WILD_DRAW_FOUR -> "와일드+4";
            case WILD_DRAW_TEN -> "와일드+10";
            case WILD_DRAW_NINETY_NINE -> "와일드+99";
        };
        String text = symbol + (count > 1 ? "×" + count : "");
        if (!myTurn) {
            return Component.text(text, NamedTextColor.WHITE);
        }
        return game.canPlay(id, card)
                ? Component.text(text, CardItems.color(card), TextDecoration.BOLD)
                : Component.text(text, NamedTextColor.DARK_GRAY);
    }

    private List<Card> visibleHand(UUID id) {
        List<Card> hand = new ArrayList<>(game.hand(id));
        if (dealing) {
            hand = new ArrayList<>(hand.subList(0, Math.min(hand.size(), dealt.getOrDefault(id, 0))));
        }
        Receiving receiving = incoming.get(id);
        for (Incoming pending : receiving == null ? List.<Incoming>of() : receiving.cards) {
            int at = hand.lastIndexOf(pending.card());
            if (at >= 0) {
                hand.remove(at);
            }
        }
        hand.sort(Card.HAND_ORDER);
        return hand;
    }

    private void updateBossBar() {
        if (finished || closed) {
            return;
        }
        if (dealing) {
            bossBar.name(Component.text("카드를 나눠 주는 중…", NamedTextColor.YELLOW));
            bossBar.color(BossBar.Color.WHITE);
            bossBar.progress(1f);
            return;
        }
        if (clap != null && !clap.open) {
            bossBar.name(Component.text("6! 준비…", NamedTextColor.GOLD, TextDecoration.BOLD));
            bossBar.color(BossBar.Color.YELLOW);
            bossBar.progress(1f);
            return;
        }
        if (clap != null) {
            int total = clap.pressed.size() + clap.waiting.size();
            bossBar.name(Component.text("치세요! " + clap.pressed.size() + "/" + total, NamedTextColor.GOLD, TextDecoration.BOLD));
            bossBar.color(BossBar.Color.YELLOW);
            bossBar.progress(Math.clamp((clap.deadline - ticks) / (settings.clap().seconds() * 20f), 0f, 1f));
            return;
        }
        UUID current = game.currentPlayer();
        boolean choosing = game.phase() == Game.Phase.CHOOSING_COLOR;
        boolean timed = settings.turnSeconds() > 0 && humans.contains(current) && Bukkit.getPlayer(current) != null;
        Component name = Component.text(name(current) + "의 차례", NamedTextColor.WHITE, TextDecoration.BOLD);
        if (choosing) {
            name = name.append(Component.text(" · 색 고르는 중", NamedTextColor.LIGHT_PURPLE));
        }
        if (game.pendingDraw() > 0) {
            name = name.append(Component.text(" · +" + game.pendingDraw() + " 누적", NamedTextColor.RED));
        }
        if (timed) {
            name = name.append(Component.text(" · " + Math.max(0, (turnTicksLeft + 19) / 20) + "초", NamedTextColor.GRAY));
        }
        bossBar.name(name);
        bossBar.color(choosing ? BossBar.Color.PURPLE : switch (game.color()) {
            case RED -> BossBar.Color.RED;
            case YELLOW -> BossBar.Color.YELLOW;
            case GREEN -> BossBar.Color.GREEN;
            case BLUE -> BossBar.Color.BLUE;
        });
        bossBar.progress(timed ? Math.clamp(turnTicksLeft / (settings.turnSeconds() * 20f), 0f, 1f) : 1f);
    }

    private void updateTable() {
        boolean choosing = game.phase() == Game.Phase.CHOOSING_COLOR;
        table.color(choosing || dealing ? null : game.color());
        Component text = Component.text("UNO", NamedTextColor.GOLD, TextDecoration.BOLD);
        if (dealing) {
            text = text.appendNewline().append(Component.text("카드를 나눠 주는 중", NamedTextColor.YELLOW));
        } else {
            text = text.appendNewline().append(choosing
                    ? Component.text("색 고르는 중…", NamedTextColor.LIGHT_PURPLE)
                    : Component.text("■ " + game.color().koreanName(), CardItems.color(game.color()), TextDecoration.BOLD));
            text = text.appendNewline().append(Component.text(name(game.currentPlayer()) + "의 차례", NamedTextColor.WHITE));
            text = text.appendNewline().append(Component.text(game.clockwise() ? "↻" : "↺", NamedTextColor.GRAY));
            if (game.pendingDraw() > 0) {
                text = text.appendNewline().append(Component.text("+" + game.pendingDraw() + " 누적!", NamedTextColor.RED, TextDecoration.BOLD));
            }
            if (clap != null) {
                text = text.appendNewline().append(Component.text("6! 박수!", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        }
        table.info(text);
    }

    private void updateGlow() {
        if (clap != null && clap.open) {
            Map<String, CardColor> colors = new HashMap<>();
            for (UUID id : game.players()) {
                boolean pressed = clap.pressed.contains(id);
                boolean clapping = pressed || clap.waiting.contains(id);
                setGlowing(id, clapping);
                if (clapping) {
                    colors.put(entryName(id), pressed ? CardColor.GREEN : CardColor.RED);
                }
            }
            for (Board each : boards()) {
                each.highlight(colors);
            }
            return;
        }
        UUID current = dealing || finished ? null : game.currentPlayer();
        for (UUID id : game.players()) {
            setGlowing(id, id.equals(current));
        }
        for (Board each : boards()) {
            each.highlight(current == null ? null : entryName(current),
                    current == null || game.phase() == Game.Phase.CHOOSING_COLOR ? null : game.color());
        }
    }

    private void setGlowing(UUID id, boolean glowing) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && humans.contains(id)) {
            if (player.isGlowing() != glowing) {
                player.setGlowing(glowing);
            }
            return;
        }
        Mannequin bot = bots.get(id);
        if (bot != null && bot.isValid() && bot.isGlowing() != glowing) {
            bot.setGlowing(glowing);
        }
    }

    private String entryName(UUID id) {
        Mannequin bot = bots.get(id);
        return bot != null ? bot.getUniqueId().toString() : name(id);
    }

    private void actionBars() {
        if (dealing || finished || closed) {
            return;
        }
        UUID current = game.currentPlayer();
        boolean someoneExposed = game.players().stream().anyMatch(game::isExposed);
        int seconds = Math.max(0, (turnTicksLeft + 19) / 20);
        String timer = settings.turnSeconds() > 0 ? " · " + seconds + "초" : "";
        for (UUID id : game.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !humans.contains(id)) {
                continue;
            }
            Component message;
            if (clap != null && !clap.open) {
                message = Component.text("6! '띵' 소리와 함께 '치세요!'가 뜨면 아무 데나 클릭", NamedTextColor.YELLOW);
            } else if (clap != null) {
                message = clap.waiting.contains(id)
                        ? Component.text("치세요! 아무 데나 빨리 클릭! 제일 늦으면 +1장", NamedTextColor.GOLD, TextDecoration.BOLD)
                        : Component.text("짝! 다른 사람을 기다리는 중 (" + clap.waiting.size() + "명 남음)", NamedTextColor.GREEN);
            } else if (incoming.containsKey(id)) {
                message = Component.text("카드 받는 중… " + incoming.get(id).cards.size() + "장 남음", NamedTextColor.AQUA);
            } else if (game.isExposed(id)) {
                message = Component.text("1장 남았습니다! F를 눌러 'UNO!'를 외치세요", NamedTextColor.GOLD, TextDecoration.BOLD);
            } else if (id.equals(current)) {
                if (game.phase() == Game.Phase.CHOOSING_COLOR) {
                    message = Component.text("다음 색을 고르세요" + timer, NamedTextColor.LIGHT_PURPLE);
                } else if (game.pendingDraw() > 0) {
                    message = Component.text("+" + game.pendingDraw() + " 누적! +2/+4로 받아치거나 9번 칸으로 받기" + timer, NamedTextColor.RED);
                } else if (game.drawnCard() != null) {
                    message = Component.text("뽑은 카드를 우클릭으로 내거나 9번 칸으로 넘기세요" + timer, NamedTextColor.GREEN);
                } else if (game.hand(id).size() == 2) {
                    message = Component.text("2장 남았습니다! 내서 1장이 되면 바로 F로 'UNO!'를 외치세요" + timer, NamedTextColor.GOLD);
                } else {
                    message = Component.text("당신의 차례 · 반짝이는 카드를 우클릭 · 없으면 9번 칸으로 뽑기" + timer, NamedTextColor.YELLOW);
                }
            } else if (someoneExposed) {
                message = Component.text("1장인데 안 외친 사람이 있습니다! F를 눌러 잡으세요", NamedTextColor.RED, TextDecoration.BOLD);
            } else if (incoming.containsKey(current)) {
                message = Component.text(name(current) + " 카드 뽑는 중… " + (incoming.get(current).received.size() + 1) + "장째", NamedTextColor.GRAY);
            } else {
                message = Component.text(name(current) + "의 차례", NamedTextColor.GRAY);
            }
            player.sendActionBar(message);
        }
    }

    private void openColorMenu(Player player) {
        player.openInventory(new ColorMenu(items).getInventory());
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    private static boolean hasColorMenu(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder(false) instanceof ColorMenu;
    }

    private int visibleCount(UUID id) {
        int size = game.hand(id).size();
        Receiving receiving = incoming.get(id);
        return dealing ? Math.min(size, dealt.getOrDefault(id, 0)) : Math.max(0, size - (receiving == null ? 0 : receiving.cards.size()));
    }

    private Location handLocation(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null && humans.contains(id)) {
            Location eye = player.getEyeLocation();
            return eye.add(eye.getDirection().multiply(0.4)).subtract(0, 0.35, 0);
        }
        Location seat = seats.get(id);
        return seat != null ? seat.clone().add(0, 1.2, 0) : table.center().add(0, 1, 0);
    }

    private Location seatLocation(int index, int count) {
        Location center = table.center();
        ArcadeLayout.Offset offset = ArcadeLayout.seatOffset(index, count, settings.table().seatRadius(),
                plugin.arcade().isHallTable(center));
        Location seat = ground(center.getWorld(), center.getX() + offset.x(), center.getZ() + offset.z(), center.getY());
        seat.setYaw((float) Math.toDegrees(Math.atan2(offset.x(), -offset.z())));
        seat.setPitch(35f);
        return seat;
    }

    private static Location ground(World world, double x, double z, double surfaceY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(surfaceY) + 1;
        for (int y = top; y >= top - 6; y--) {
            Block below = world.getBlockAt(bx, y - 1, bz);
            if (below.getType().isSolid() && world.getBlockAt(bx, y, bz).isPassable() && world.getBlockAt(bx, y + 1, bz).isPassable()) {
                return new Location(world, x, y, z);
            }
        }
        return new Location(world, x, Math.floor(surfaceY) - 1, z);
    }

    private void firework(Location location) {
        if (closed || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawn(location.clone().add(0, 0.8, 0), Firework.class, firework -> {
            firework.setPersistent(false);
            FireworkMeta meta = firework.getFireworkMeta();
            CardColor color = CardColor.values()[random.nextInt(CardColor.values().length)];
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(org.bukkit.Color.fromRGB(color.rgb()), org.bukkit.Color.WHITE)
                    .withFade(org.bukkit.Color.YELLOW)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            later(1, firework::detonate);
        });
    }

    private void subtitle(UUID id, Component text) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.showTitle(Title.title(Component.empty(), text,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(250))));
        }
    }

    private void sound(Sound sound, float volume, float pitch) {
        Location center = table.center();
        center.getWorld().playSound(center, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private void broadcast(Component message) {
        Bukkit.getServer().sendMessage(PREFIX.append(message));
    }

    private void later(long delay, Runnable task) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!closed) {
                task.run();
            }
        }, Math.max(1L, delay)));
    }

    private String name(UUID id) {
        return names.getOrDefault(id, "?");
    }

    private static int[] buildCardSlots() {
        int[] slots = new int[HAND_SLOT + 27];
        for (int i = 0; i < HAND_SLOT; i++) {
            slots[i] = i;
        }
        for (int i = 0; i < 27; i++) {
            slots[HAND_SLOT + i] = 9 + i;
        }
        return slots;
    }

    private static int cardSlotIndex(int inventorySlot) {
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (CARD_SLOTS[i] == inventorySlot) {
                return i;
            }
        }
        return -1;
    }
}
