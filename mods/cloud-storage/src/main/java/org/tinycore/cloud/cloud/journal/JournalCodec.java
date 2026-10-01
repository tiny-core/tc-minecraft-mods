package org.tinycore.cloud.cloud.journal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.item.EncodedItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Formato binário dos registros do diário. Cada registro vira um <b>quadro</b>:
 * {@code [int tamanho][byte tipo + dados][long CRC32]}.
 *
 * <p>O CRC existe por causa do crash no meio de uma gravação: o último quadro pode ficar pela metade. Na
 * leitura, um quadro incompleto ou com CRC errado marca o fim do diário válido, e o resto é descartado
 * (era justamente o que não chegou a ficar durável).
 */
public final class JournalCodec {

    /** Teto de um quadro; acima disso é lixo (proteção contra ler um tamanho corrompido gigante). */
    static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;

    private static final byte BATCH = 1, ACK = 2, SAVE_MARK = 3, PENDING = 4, CLEAN_SHUTDOWN = 5, ITEM_DEFINED = 6;

    private JournalCodec() {}

    /** Quadro completo (tamanho + dados + CRC) de um registro. */
    public static byte @NotNull [] frame(@NotNull JournalRecord record) {
        byte[] payload = payload(record);
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 12);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(payload.length);
            out.write(payload);
            out.writeLong(crc.getValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream não falha
        }
        return bytes.toByteArray();
    }

    /**
     * Lê o próximo quadro. Devolve {@code null} quando o diário válido acabou (fim do arquivo, quadro
     * incompleto ou CRC errado).
     */
    static @Nullable JournalRecord readFrame(@NotNull DataInputStream in) {
        try {
            int length = in.readInt();
            if (length <= 0 || length > MAX_FRAME_BYTES) return null;
            byte[] payload = in.readNBytes(length);
            if (payload.length < length) return null;
            long expectedCrc = in.readLong();
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) return null;
            return parse(payload);
        } catch (IOException e) {
            return null; // fim do arquivo no meio do quadro: crash durante a gravação
        }
    }

    private static byte[] payload(JournalRecord record) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            switch (record) {
                case JournalRecord.BatchWritten(Batch b) -> {
                    out.writeByte(BATCH);
                    writeUuid(out, b.playerUuid());
                    out.writeLong(b.epoch());
                    out.writeLong(b.seq());
                    out.writeInt(b.ops().size());
                    for (CloudOp op : b.ops()) {
                        writeKey(out, op.key());
                        out.writeLong(op.delta());
                    }
                    writeAmounts(out, b.expected());
                }
                case JournalRecord.Ack(UUID player, long epoch, long seq) -> {
                    out.writeByte(ACK);
                    writeUuid(out, player);
                    out.writeLong(epoch);
                    out.writeLong(seq);
                }
                case JournalRecord.SaveMark(long time) -> {
                    out.writeByte(SAVE_MARK);
                    out.writeLong(time);
                }
                case JournalRecord.PendingCredits(UUID player, Map<BalanceKey, Long> credits) -> {
                    out.writeByte(PENDING);
                    writeUuid(out, player);
                    writeAmounts(out, credits);
                }
                case JournalRecord.CleanShutdown(long time) -> {
                    out.writeByte(CLEAN_SHUTDOWN);
                    out.writeLong(time);
                }
                case JournalRecord.ItemDefined(EncodedItem item) -> {
                    out.writeByte(ITEM_DEFINED);
                    out.writeUTF(item.fingerprint());
                    out.writeUTF(item.itemId());
                    out.writeUTF(item.displayName());
                    out.writeInt(item.bytes().length);
                    out.write(item.bytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static JournalRecord parse(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        byte type = in.readByte();
        return switch (type) {
            case BATCH -> {
                UUID player = readUuid(in);
                long epoch = in.readLong();
                long seq = in.readLong();
                int count = in.readInt();
                List<CloudOp> ops = new ArrayList<>(count);
                for (int i = 0; i < count; i++) ops.add(new CloudOp(readKey(in), in.readLong()));
                yield new JournalRecord.BatchWritten(new Batch(player, epoch, seq, ops, readAmounts(in)));
            }
            case ACK -> new JournalRecord.Ack(readUuid(in), in.readLong(), in.readLong());
            case SAVE_MARK -> new JournalRecord.SaveMark(in.readLong());
            case PENDING -> new JournalRecord.PendingCredits(readUuid(in), readAmounts(in));
            case CLEAN_SHUTDOWN -> new JournalRecord.CleanShutdown(in.readLong());
            case ITEM_DEFINED -> {
                String fingerprint = in.readUTF();
                String itemId = in.readUTF();
                String name = in.readUTF();
                int length = in.readInt();
                if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("tamanho de item inválido: " + length);
                byte[] bytes = in.readNBytes(length);
                if (bytes.length < length) throw new IOException("item cortado");
                yield new JournalRecord.ItemDefined(new EncodedItem(fingerprint, itemId, name, bytes));
            }
            default -> throw new IOException("tipo de registro desconhecido: " + type);
        };
    }

    private static void writeAmounts(DataOutputStream out, Map<BalanceKey, Long> amounts) throws IOException {
        out.writeInt(amounts.size());
        for (Map.Entry<BalanceKey, Long> e : amounts.entrySet()) {
            writeKey(out, e.getKey());
            out.writeLong(e.getValue());
        }
    }

    private static Map<BalanceKey, Long> readAmounts(DataInputStream in) throws IOException {
        int count = in.readInt();
        Map<BalanceKey, Long> amounts = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) amounts.put(readKey(in), in.readLong());
        return amounts;
    }

    private static void writeKey(DataOutputStream out, BalanceKey key) throws IOException {
        writeUuid(out, key.channelId());
        out.writeUTF(key.fingerprint());
    }

    private static BalanceKey readKey(DataInputStream in) throws IOException {
        return new BalanceKey(readUuid(in), in.readUTF());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
