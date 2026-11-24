package io.github.beddeak.uno;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One-time folder migration; never merges over an existing UNO configuration. */
final class LegacyData {
    private LegacyData() {}

    static void migrate(Path target) throws IOException {
        Path legacy = target.resolveSibling("LastCard");
        if (!Files.isDirectory(legacy) || Files.exists(target.resolve("config.yml"))) return;
        if (Files.exists(target)) {
            try (var files = Files.list(target)) {
                if (files.findAny().isPresent()) {
                    throw new IOException("UNO 데이터 폴더가 비어 있지 않아 기존 설정을 옮기지 않았습니다: " + target);
                }
            }
            Files.delete(target); // Only an empty directory is removed.
        }
        Files.move(legacy, target);
        Path oldPack = target.resolve("LastCard-pack.zip");
        if (Files.exists(oldPack) && !Files.exists(target.resolve(PackService.ZIP_NAME))) {
            Files.move(oldPack, target.resolve(PackService.ZIP_NAME));
        }
    }
}
