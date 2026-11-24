package io.github.beddeak.uno;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.beddeak.uno.art.ResourcePack;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

final class PackService {

    static final String ZIP_NAME = "UNO-pack.zip";

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("uno:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final UnoPlugin plugin;
    private ResourcePack pack;
    private Settings.Pack config;
    private HttpServer server;
    private ExecutorService executor;

    PackService(UnoPlugin plugin) {
        this.plugin = plugin;
    }

    void start(Settings.Pack config) {
        this.config = config;
        if (pack == null) {
            pack = ResourcePack.build();
            File zip = new File(plugin.getDataFolder(), ZIP_NAME);
            try {
                Files.createDirectories(plugin.getDataFolder().toPath());
                Files.write(zip.toPath(), pack.bytes());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "리소스팩 파일을 저장하지 못했습니다: " + zip, e);
            }
            plugin.getLogger().info("카드 리소스팩 생성: " + pack.bytes().length + " bytes, sha1 " + pack.sha1());
        }
        if (!config.enabled() || !config.externalUrl().isEmpty()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(config.port()), 16);
            executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "UNO-pack-http");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/", this::serve);
            server.start();
            plugin.getLogger().info("리소스팩 HTTP 서버 시작: 포트 " + config.port());
        } catch (IOException e) {
            server = null;
            plugin.getLogger().log(Level.SEVERE, "리소스팩 HTTP 서버를 열지 못했습니다(포트 " + config.port()
                    + "). resource-pack.port를 바꾸거나 external-url을 쓰세요.", e);
        }
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    boolean serving() {
        return server != null || config != null && !config.externalUrl().isEmpty();
    }

    String fileName() {
        return "uno-" + pack.sha1().substring(0, 12) + ".zip";
    }

    String sha1() {
        return pack.sha1();
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            boolean head = "HEAD".equals(method);
            if (!head && !"GET".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!path.equals("/" + fileName()) && !path.equals("/" + ZIP_NAME)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = pack.bytes();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
            if (!head) {
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(bytes);
                }
            }
        }
    }

    void send(Player player) {
        if (config == null || !config.enabled() || !serving()) {
            return;
        }
        String url = urlFor(player);
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), pack.sha1());
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(config.required())
                .replace(false)
                .prompt(Component.text("UNO의 카드 그림 리소스팩입니다.", NamedTextColor.GOLD))
                .build());
    }

    String urlFor(Player player) {
        if (!config.externalUrl().isEmpty()) {
            return config.externalUrl();
        }
        String host = config.publicHost();
        if (host.isEmpty()) {
            InetSocketAddress virtualHost = player.getConnection().getVirtualHost();
            host = virtualHost != null ? virtualHost.getHostString() : "";
        }
        int nul = host.indexOf('\0');
        if (nul >= 0) {
            host = host.substring(0, nul);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            host = Bukkit.getIp().isEmpty() ? "127.0.0.1" : Bukkit.getIp();
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + config.port() + "/" + fileName();
    }
}
