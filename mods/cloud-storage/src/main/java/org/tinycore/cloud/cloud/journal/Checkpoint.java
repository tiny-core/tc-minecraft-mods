package org.tinycore.cloud.cloud.journal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code <mundo>/tccloud/checkpoint.json}: identidade do mundo e o último lote gravado por jogador.
 *
 * <p>Por que existe: no boot o mod manda isto no {@code hello}. Se o TCMine já aplicou lotes além do
 * checkpoint, o mundo voltou no tempo (backup restaurado, cópia manual) e o TCMine abre um incidente de
 * rollback. É JSON (e não binário) para o TCMine conseguir lê-lo de dentro do zip de backup.
 *
 * @param worldId id aleatório criado uma vez por mundo; um mundo novo no mesmo servidor tem outro id
 */
public record Checkpoint(@NotNull UUID worldId, @NotNull Map<UUID, JournalReplay.SeqPosition> lastSealed) {

    public Checkpoint {
        lastSealed = Map.copyOf(lastSealed);
    }

    /** Checkpoint de um mundo que nunca usou a nuvem. */
    public static @NotNull Checkpoint fresh() {
        return new Checkpoint(UUID.randomUUID(), Map.of());
    }

    public @NotNull Checkpoint withLastSealed(@NotNull Map<UUID, JournalReplay.SeqPosition> positions) {
        Map<UUID, JournalReplay.SeqPosition> merged = new LinkedHashMap<>(lastSealed);
        positions.forEach((player, pos) -> merged.merge(player, pos, JournalReplay.SeqPosition::max));
        return new Checkpoint(worldId, merged);
    }

    /** Grava de forma atômica (arquivo temporário + troca). */
    public void write(@NotNull Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("worldId", worldId.toString());
        JsonObject players = new JsonObject();
        lastSealed.forEach((player, pos) -> {
            JsonObject p = new JsonObject();
            p.addProperty("epoch", pos.epoch());
            p.addProperty("seq", pos.seq());
            players.add(player.toString(), p);
        });
        root.add("players", players);

        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
        JournalFile.moveAtomically(tmp, file);
    }

    /** Lê o checkpoint; {@code null} se não existe ou está ilegível (o chamador decide criar um novo). */
    public static @Nullable Checkpoint read(@NotNull Path file) {
        if (!Files.exists(file)) return null;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            UUID worldId = UUID.fromString(root.get("worldId").getAsString());
            Map<UUID, JournalReplay.SeqPosition> positions = new LinkedHashMap<>();
            root.getAsJsonObject("players").entrySet().forEach(e -> {
                JsonObject p = e.getValue().getAsJsonObject();
                positions.put(UUID.fromString(e.getKey()),
                        new JournalReplay.SeqPosition(p.get("epoch").getAsLong(), p.get("seq").getAsLong()));
            });
            return new Checkpoint(worldId, positions);
        } catch (IOException | RuntimeException e) {
            TcCloud.LOG.warn("Checkpoint da nuvem ilegível em {}: {}", file, e.toString());
            return null;
        }
    }
}
