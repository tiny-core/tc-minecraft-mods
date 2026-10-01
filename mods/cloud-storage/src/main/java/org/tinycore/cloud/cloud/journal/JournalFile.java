package org.tinycore.cloud.cloud.journal;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Arquivo do diário dentro da pasta do mundo ({@code <mundo>/tccloud/journal.bin}). Vive no mundo de
 * propósito: um backup do mundo leva o diário junto, e um mundo restaurado "volta no tempo" de forma
 * coerente (o TCMine detecta pelo checkpoint e abre um incidente de rollback).
 *
 * <p>Não usa {@code SavedData}: o Minecraft grava o {@code SavedData} no INÍCIO do save, antes do evento
 * {@code LevelEvent.Save}; um diário ali ficaria sempre um ciclo atrasado (resultado da fase 0).
 */
public final class JournalFile {

    private final Path path;

    public JournalFile(@NotNull Path path) {
        this.path = path;
    }

    /**
     * Acrescenta registros ao fim do arquivo.
     *
     * @param force {@code true} = espera o sistema operacional confirmar a gravação física (fsync). Usar
     *              para lotes (débitos precisam estar no disco antes do chunk); dispensável para os
     *              registros informativos (créditos pendentes), que só alimentam as operações em dúvida.
     */
    public void append(@NotNull List<JournalRecord> records, boolean force) throws IOException {
        if (records.isEmpty()) return;
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            writeAll(channel, encode(records));
            if (force) channel.force(false);
        }
    }

    /** Lê todos os registros válidos. Um fim corrompido (crash no meio da gravação) é ignorado com aviso. */
    public @NotNull List<JournalRecord> readAll() throws IOException {
        List<JournalRecord> records = new ArrayList<>();
        if (!Files.exists(path)) return records;
        long size = Files.size(path);
        long read = 0;
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(path));
             DataInputStream in = new DataInputStream(raw)) {
            JournalRecord record;
            while ((record = JournalCodec.readFrame(in)) != null) {
                records.add(record);
                read += JournalCodec.frame(record).length;
            }
        }
        if (read < size) {
            TcCloud.LOG.warn("Diário {}: {} bytes finais ilegíveis descartados (gravação interrompida).",
                    path, size - read);
        }
        return records;
    }

    /**
     * Substitui o arquivo inteiro pelos registros dados (compactação: só o que ainda importa). Grava num
     * arquivo temporário e troca de uma vez, para um crash no meio nunca deixar um diário pela metade.
     */
    public void rewrite(@NotNull List<JournalRecord> records) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            writeAll(channel, encode(records));
            channel.force(false);
        }
        moveAtomically(tmp, path);
    }

    public @NotNull Path path() {
        return path;
    }

    /** Troca {@code source} por {@code target} de uma vez (cai para troca comum se o sistema não suportar). */
    static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            TcCloud.LOG.debug("Troca atômica não suportada em {}; usando troca comum.", target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ByteBuffer encode(List<JournalRecord> records) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (JournalRecord record : records) bytes.writeBytes(JournalCodec.frame(record));
        return ByteBuffer.wrap(bytes.toByteArray());
    }

    private static void writeAll(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
