package org.tinycore.cloud.integration.tcmine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Formato do JSON da API da nuvem do TCMine ({@code /api/cloud/v1}), com os mesmos nomes de campo que o TCMine usa
 * (camelCase). Pedidos são {@code record}s; respostas são classes simples, que o Gson preenche campo a campo
 * (campo ausente fica no valor padrão, então uma versão nova do TCMine com campo a mais não quebra o mod).
 * Mudança quebrada aqui = subir {@link HttpCloudBackend#PROTOCOL} (e o CloudProtocol do TCMine).
 */
public final class CloudApiDto {

    private CloudApiDto() {}

    public record SeqDto(long epoch, long seq) {}

    public record CheckpointDto(UUID worldId, Map<String, SeqDto> players) {}

    public record HelloRequest(int protocol, String modVersion, CheckpointDto checkpoint) {}

    public record AcquireRequest(String playerUuid, String playerName) {}

    public record LeaseDto(String playerUuid, long epoch) {}

    public record HeartbeatRequest(List<LeaseDto> leases) {}

    public record OpDto(UUID channelId, String fingerprint, long delta) {}

    public record ExpectedDto(UUID channelId, String fingerprint, long amount) {}

    public record ItemDto(String fingerprint, String itemId, String displayName, String encoded) {}

    public record BatchRequest(String playerUuid, long epoch, long seq, List<OpDto> ops, List<ExpectedDto> expected,
                        List<ItemDto> definitions) {}

    public record ReleaseRequest(String playerUuid, long epoch, long lastSeq) {}

    public record DoubtfulDto(String playerUuid, UUID channelId, String fingerprint, String kind, long amount) {}

    public record DoubtfulRequest(String reportId, List<DoubtfulDto> operations) {}

    public record SuspectDto(String itemId, String evidence, long attempts) {}

    public record SuspectsRequest(List<SuspectDto> items) {}

    // Respostas: classes simples (o Gson preenche campo a campo; campo ausente fica no valor padrão).

    public static final class RuleDto {
        public String scope;
        public String pattern;
        public String action;

        @Override
        public String toString() {
            return scope + " " + pattern + " " + action;
        }
    }

    public static final class QuotaDto {
        public int maxTypes;
        public long maxTotal;
    }

    public static final class HelloDto {
        public int protocol;
        public String policyMode;
        public long policyVersion;
        public List<RuleDto> rules;
        public QuotaDto quota = new QuotaDto();
        public int maxItemBytes;
        public int maxChannelsPerPlayer;
        public int leaseTtlSeconds;
        public boolean readOnly;
        public String readOnlyReason;
    }

    public static final class PolicyDto {
        public String policyMode;
        public long policyVersion;
        public List<RuleDto> rules;
    }

    public static final class ChannelItemDto {
        public String fingerprint;
        public long amount;
        public String itemId;
        public String displayName;
        public String encoded;
    }

    public static final class ChannelDto {
        public UUID id;
        public String name;
        public boolean frozen;
        public List<ChannelItemDto> items;
    }

    public static final class AcquireDto {
        public String status;
        public long epoch;
        public boolean readOnly;
        public List<ChannelDto> channels;
        public String holder;
    }

    public static final class HeartbeatDto {
        public List<String> lost;
        public long policyVersion;
    }

    public static final class BatchDto {
        public String result;
        public String reason;
    }

    public static final class ReleaseDto {
        public boolean released;
    }
}
