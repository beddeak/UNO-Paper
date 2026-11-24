package io.github.beddeak.uno;

import io.github.beddeak.uno.card.Card;
import io.github.beddeak.uno.card.DeckMix;
import io.github.beddeak.uno.game.MatchSize;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class UnoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("start", "test", "stop", "join", "leave", "table", "kick",
            "arcade", "rule", "pack", "score", "status", "reload", "help");
    private static final List<String> RULES = List.of("hand-size", "stacking", "draw-until-playable", "call-penalty", "turn-seconds",
            "six-clap", "clap-seconds", "six-cards", "plus2-cards", "plus4-cards", "plus10-cards", "plus99-cards");

    private final UnoPlugin plugin;

    UnoCommand(UnoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start", "시작" -> start(sender, args);
            case "test", "테스트" -> test(sender, args);
            case "stop", "종료" -> {
                if (admin(sender)) {
                    reply(sender, plugin.stopGame() ? "게임을 끝내고 인벤토리를 돌려줬습니다." : "진행 중인 게임이 없습니다.");
                }
            }
            case "join", "참가" -> {
                if (sender instanceof Player player) {
                    plugin.lobby().removeIf(id -> Bukkit.getPlayer(id) == null);
                    if (plugin.lobby().contains(player.getUniqueId())) {
                        reply(sender, "이미 다음 판 대기열에 참가했습니다.");
                        return true;
                    }
                    if (plugin.lobby().size() >= MatchSize.MAX_PLAYERS) {
                        reply(sender, "대기열이 가득 찼습니다. 다음 판에 참가해 주세요.");
                        return true;
                    }
                    plugin.lobby().add(player.getUniqueId());
                    Bukkit.getServer().sendMessage(Component.text("[UNO] " + player.getName() + " 님 참가 (대기 " + plugin.lobby().size() + "명)", NamedTextColor.GOLD));
                }
            }
            case "leave", "나가기" -> {
                if (sender instanceof Player player) {
                    reply(sender, plugin.lobby().remove(player.getUniqueId()) ? "대기열에서 빠졌습니다." : "대기열에 없습니다.");
                }
            }
            case "table", "테이블" -> table(sender);
            case "arcade", "아케이드" -> arcade(sender, args);
            case "kick" -> kick(sender, args);
            case "rule", "규칙" -> rule(sender, args);
            case "pack", "리소스팩" -> pack(sender, args);
            case "score", "점수" -> score(sender, args);
            case "status", "상태" -> status(sender);
            case "reload" -> {
                if (admin(sender)) {
                    plugin.reloadSettings();
                    reply(sender, "config.yml을 다시 읽었습니다. 규칙은 다음 판부터 적용됩니다.");
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        List<Player> players = new ArrayList<>();
        int bots = 0;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.matches("\\d+")) {
                try {
                    bots = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    reply(sender, "봇 수는 0~10 사이로 적어 주세요.");
                    return;
                }
                continue;
            }
            Player player = Bukkit.getPlayerExact(arg);
            if (player == null) {
                reply(sender, arg + " 님은 접속해 있지 않습니다.");
                return;
            }
            if (!players.contains(player)) {
                players.add(player);
            }
        }
        String error = plugin.startGame(sender instanceof Player player ? player : null, players, bots);
        if (error != null) {
            reply(sender, error);
        }
    }

    private void test(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "게임 안에서만 쓸 수 있습니다.");
            return;
        }
        int bots = 3;
        if (args.length > 1) {
            try {
                bots = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                reply(sender, "봇 수는 숫자로 적어 주세요.");
                return;
            }
        }
        String error = plugin.startGame(player, List.of(player), Math.max(1, bots));
        if (error != null) {
            reply(sender, error);
        }
    }

    private void table(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "게임 안에서만 쓸 수 있습니다.");
            return;
        }
        Location center = plugin.pickTable(player);
        plugin.saveTable(center);
        reply(sender, String.format(Locale.ROOT, "테이블 중심을 %s %.1f %.2f %.1f 로 정했습니다. 다음 판부터 적용됩니다.",
                center.getWorld().getName(), center.getX(), center.getY(), center.getZ()));
    }

    private void arcade(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (!args[1].equalsIgnoreCase("exit")) {
                reply(sender, "/uno arcade [exit]");
                return;
            }
            if (sender instanceof Player player) {
                String error = plugin.arcade().exit(player);
                reply(sender, error == null ? "원래 위치로 돌아왔습니다." : error);
            } else {
                reply(sender, "게임 안에서만 쓸 수 있습니다.");
            }
            return;
        }
        if (plugin.session() != null) {
            // Allow spectators to visit, but never move the current table or participants.
            if (plugin.arcade().ready() && sender instanceof Player player) {
                String error = plugin.arcade().enter(player);
                if (error != null) reply(sender, error);
            } else {
                reply(sender, "게임이 끝난 뒤 경기장을 설정해 주세요.");
            }
            return;
        }
        if (!plugin.arcade().ready() || sender.hasPermission("uno.admin")) {
            if (!admin(sender)) return;
            try {
                reply(sender, "아케이드 경기장을 준비합니다. 처음에는 잠시 걸립니다.");
                plugin.saveTable(plugin.arcade().ensureBuilt());
                reply(sender, "아케이드 테이블 설정 완료. /uno test 또는 /uno start 로 시작하세요.");
            } catch (IllegalStateException e) {
                reply(sender, e.getMessage());
                return;
            }
        }
        if (sender instanceof Player player) {
            String error = plugin.arcade().enter(player);
            if (error != null) reply(sender, error);
        }
    }

    private void kick(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        GameSession session = plugin.session();
        if (session == null || args.length < 2) {
            reply(sender, session == null ? "진행 중인 게임이 없습니다." : "/uno kick <이름>");
            return;
        }
        UUID target = session.playerByName(args[1]);
        if (target == null) {
            reply(sender, args[1] + " 님은 이번 게임에 없습니다.");
            return;
        }
        if (!session.kick(target)) {
            reply(sender, "지금은 뺄 수 없습니다.");
        }
    }

    private void rule(CommandSender sender, String[] args) {
        Settings settings = plugin.settings();
        if (args.length < 3) {
            reply(sender, "규칙: hand-size " + settings.rules().handSize()
                    + " · stacking " + settings.rules().stacking()
                    + " · draw-until-playable " + settings.rules().drawUntilPlayable()
                    + " · call-penalty " + settings.rules().callPenalty()
                    + " · turn-seconds " + settings.turnSeconds()
                    + " · six-clap " + settings.clap().enabled()
                    + " · clap-seconds " + settings.clap().seconds());
            DeckMix mix = settings.rules().deck();
            int pack = Card.deck(mix).size();
            reply(sender, "덱 한 묶음 " + pack + "장: six-cards " + share(mix.sixes(), pack)
                    + " · plus2-cards " + share(mix.drawTwos(), pack)
                    + " · plus4-cards " + share(mix.drawFours(), pack)
                    + " · plus10-cards " + share(mix.drawTens(), pack)
                    + " · plus99-cards " + share(mix.drawNinetyNines(), pack));
            if (sender.hasPermission("uno.admin")) {
                reply(sender, "바꾸기: /uno rule <" + String.join("|", RULES) + "> <값>");
            }
            return;
        }
        if (!admin(sender)) {
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        String value = args[2].toLowerCase(Locale.ROOT);
        switch (key) {
            case "stacking", "draw-until-playable", "six-clap" -> {
                if (!value.equals("true") && !value.equals("false")) {
                    reply(sender, key + " 값은 true 또는 false 입니다.");
                    return;
                }
                plugin.saveRule(key, Boolean.parseBoolean(value));
            }
            case "hand-size", "call-penalty", "turn-seconds", "clap-seconds",
                 "six-cards", "plus2-cards", "plus4-cards", "plus10-cards", "plus99-cards" -> {
                int number;
                try {
                    number = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    reply(sender, key + " 값은 숫자입니다.");
                    return;
                }
                int min = key.equals("hand-size") || key.equals("clap-seconds") ? 1 : 0;
                int max = switch (key) {
                    case "hand-size" -> 20;
                    case "call-penalty" -> 10;
                    case "clap-seconds" -> 30;
                    case "six-cards", "plus2-cards", "plus4-cards", "plus10-cards", "plus99-cards" -> DeckMix.MAX;
                    default -> 600;
                };
                if (number < min || number > max) {
                    reply(sender, key + " 값은 " + min + "~" + max + " 사이입니다.");
                    return;
                }
                plugin.saveRule(key, number);
            }
            default -> {
                reply(sender, "모르는 규칙입니다: " + key);
                return;
            }
        }
        reply(sender, key + " = " + value + " 저장했습니다. 다음 판부터 적용됩니다.");
        if (key.endsWith("-cards")) {
            DeckMix mix = plugin.settings().rules().deck();
            int pack = Card.deck(mix).size();
            reply(sender, "덱 한 묶음 " + pack + "장: 6 " + share(mix.sixes(), pack) + " · +2 " + share(mix.drawTwos(), pack)
                    + " · +4 " + share(mix.drawFours(), pack) + " · +10 " + share(mix.drawTens(), pack)
                    + " · +99 " + share(mix.drawNinetyNines(), pack));
        }
    }

    private static String share(int count, int pack) {
        double percent = count * 100.0 / pack;
        return count + "장(" + String.format(Locale.ROOT, percent > 0 && percent < 1 ? "%.2f" : "%.1f", percent) + "%)";
    }

    private void pack(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (!admin(sender)) {
                return;
            }
            List<Player> targets = new ArrayList<>();
            if (args[1].equalsIgnoreCase("all")) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else {
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) {
                    reply(sender, args[1] + " 님은 접속해 있지 않습니다.");
                    return;
                }
                targets.add(player);
            }
            targets.forEach(plugin.packs()::send);
            reply(sender, targets.size() + "명에게 리소스팩을 보냈습니다.");
            return;
        }
        if (sender instanceof Player player) {
            if (!plugin.packs().serving()) {
                reply(sender, "리소스팩 전송이 꺼져 있거나 HTTP 서버를 열지 못했습니다. 콘솔을 확인해 주세요.");
                return;
            }
            plugin.packs().send(player);
            reply(sender, "리소스팩을 보냈습니다: " + plugin.packs().urlFor(player));
        } else {
            reply(sender, "리소스팩 sha1 " + plugin.packs().sha1() + " · plugins/UNO/" + PackService.ZIP_NAME);
        }
    }

    private void score(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
            if (admin(sender)) {
                plugin.scores().reset();
                reply(sender, "누적 점수를 지웠습니다.");
            }
            return;
        }
        List<Scores.Entry> ranking = plugin.scores().ranking();
        if (ranking.isEmpty()) {
            reply(sender, "아직 기록이 없습니다.");
            return;
        }
        reply(sender, "누적 점수 (이긴 사람이 남은 카드 점수를 가져갑니다)");
        for (int i = 0; i < ranking.size(); i++) {
            Scores.Entry entry = ranking.get(i);
            sender.sendMessage(Component.text((i + 1) + ". " + entry.name() + " - " + entry.points() + "점, " + entry.wins() + "승", NamedTextColor.WHITE));
        }
    }

    private void status(CommandSender sender) {
        GameSession session = plugin.session();
        if (session == null) {
            Location center = plugin.tableCenter();
            reply(sender, "진행 중인 게임이 없습니다. 대기 " + plugin.lobby().size() + "명 · 테이블 "
                    + (center == null ? "미설정" : String.format(Locale.ROOT, "%s %.1f %.1f %.1f", center.getWorld().getName(), center.getX(), center.getY(), center.getZ())));
            return;
        }
        session.status().forEach(sender::sendMessage);
    }

    private void help(CommandSender sender) {
        reply(sender, "명령어");
        String[][] lines = {
                {"/uno test [봇 수]", "본인 + 봇(기본 3명)으로 테스트 시작"},
                {"/uno start [이름...] [봇 수]", "사람과 봇을 합쳐 2~10명으로 시작. 이름 생략 시 대기열, 없으면 접속자 전원"},
                {"/uno stop", "게임 강제 종료(인벤토리 복구)"},
                {"/uno join · /uno leave", "다음 판 대기열 참가/취소"},
                {"/uno table", "바라보는 블록 위를 테이블 중심으로 정하기"},
                {"/uno arcade [exit]", "모던 석영 경기장 생성·입장 / 원래 위치 복귀"},
                {"/uno rule [규칙 값]", "규칙 보기/바꾸기"},
                {"/uno kick <이름>", "게임에서 빼기"},
                {"/uno pack [all|이름]", "카드 리소스팩 다시 받기/보내기"},
                {"/uno score [reset]", "누적 점수"},
                {"/uno status", "현재 상태"},
                {"/uno reload", "config.yml 다시 읽기"},
        };
        for (String[] line : lines) {
            sender.sendMessage(Component.text(line[0], NamedTextColor.YELLOW).append(Component.text("  " + line[1], NamedTextColor.GRAY)));
        }
        sender.sendMessage(Component.text("조작: 카드 우클릭 = 내기 · 9번 칸/테이블 덱 = 뽑기 · 7번 칸 = 손패 전체 보기 · F = UNO! 외치기/잡기", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("6이 나오면 박수: 아무 데나 클릭, 제일 늦은 사람 +1장", NamedTextColor.AQUA));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("uno.admin")) {
            return true;
        }
        reply(sender, "권한이 없습니다.");
        return false;
    }

    private static void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[UNO] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("arcade") && args.length == 2) {
            return filter(List.of("exit"), args[1]);
        }
        if (sub.equals("rule") && args.length == 2) {
            return filter(RULES, args[1]);
        }
        if (sub.equals("rule") && args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "stacking", "draw-until-playable", "six-clap" -> filter(List.of("true", "false"), args[2]);
                default -> List.of();
            };
        }
        if (sub.equals("kick") && args.length == 2 && plugin.session() != null) {
            return filter(plugin.session().playerNames(), args[1]);
        }
        if (sub.equals("score") && args.length == 2) {
            return filter(List.of("reset"), args[1]);
        }
        if (sub.equals("pack") && args.length == 2) {
            List<String> options = new ArrayList<>(List.of("all"));
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return filter(options, args[1]);
        }
        if (sub.equals("start")) {
            List<String> options = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return filter(options, args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
