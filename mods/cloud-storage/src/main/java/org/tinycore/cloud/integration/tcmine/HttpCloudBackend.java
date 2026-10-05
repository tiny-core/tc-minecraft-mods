package org.tinycore.cloud.integration.tcmine;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;
import org.tinycore.cloud.item.policy.ItemRule;
import org.tinycore.cloud.integration.tcmine.CloudApiDto.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A nuvem de verdade: a API {@code /api/cloud/v1} do TCMine (docs/CLOUD-STORAGE.md no repositório TCMine). Toda
 * chamada é assíncrona ({@code HttpClient.sendAsync}) e nunca roda na thread do servidor.
 *
 * <p>Como cada código HTTP vira resultado aqui:
 * <ul>
 *   <li>200: resposta normal;</li>
 *   <li>409 (outra requisição mexeu no mesmo lease) e 5xx/rede: exceção — quem chama tenta de novo depois;</li>
 *   <li>400 num lote: o TCMine nunca vai aceitar esse lote; vira {@code QUARANTINED} (o canal trava e o dono
 *       vê no log), em vez de reenviar para sempre;</li>
 *   <li>401/403 (chave revogada, servidor desligado da nuvem) e 426 (protocolo): exceção com a causa no log.</li>
 * </ul>
 * Os corpos são {@code record}s (≈ DTOs) serializados pelo Gson com os mesmos nomes que o TCMine espera.
 */
public final class HttpCloudBackend implements CloudBackend {

    public static final int PROTOCOL = 1;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();

    private final URI base;
    private final String key;
    private final String modVersion;
    private final HttpClient http;

    public HttpCloudBackend(@NotNull CloudCredentials credentials, @NotNull String modVersion) {
        this.base = URI.create(credentials.url() + "/api/cloud/v1");
        this.key = credentials.key();
        this.modVersion = modVersion;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    // ---------------------------------------------------------------- chamadas

    @Override
    public @NotNull CompletableFuture<HelloReply> hello(@NotNull Checkpoint checkpoint) {
        Map<String, SeqDto> players = new LinkedHashMap<>();
        checkpoint.lastSealed().forEach((player, pos) -> players.put(player.toString(), new SeqDto(pos.epoch(), pos.seq())));
        var body = new HelloRequest(PROTOCOL, modVersion, new CheckpointDto(checkpoint.worldId(), players));
        return post("/hello", body, HelloDto.class).thenApply(r -> new HelloReply(
                toPolicy(r.policyMode, r.policyVersion, r.rules),
                new CloudQuota(r.quota.maxTypes, r.quota.maxTotal),
                r.maxItemBytes, r.readOnly, r.readOnlyReason));
    }

    @Override
    public @NotNull CompletableFuture<LeaseResult> acquire(@NotNull UUID playerUuid, @NotNull String playerName) {
        return post("/leases/acquire", new AcquireRequest(playerUuid.toString(), playerName), AcquireDto.class)
                .thenApply(r -> "busy".equals(r.status)
                        ? new LeaseResult.Busy(r.holder == null ? "outro servidor" : r.holder)
                        : new LeaseResult.Granted(r.epoch, toChannels(r.channels), r.readOnly));
    }

    @Override
    public @NotNull CompletableFuture<HeartbeatReply> heartbeat(@NotNull Collection<HeldLease> leases) {
        List<LeaseDto> held = leases.stream().map(l -> new LeaseDto(l.playerUuid().toString(), l.epoch())).toList();
        return post("/leases/heartbeat", new HeartbeatRequest(held), HeartbeatDto.class).thenApply(r -> {
            List<UUID> lost = new ArrayList<>();
            for (String uuid : r.lost == null ? List.<String>of() : r.lost) lost.add(UUID.fromString(uuid));
            return new HeartbeatReply(lost, r.policyVersion);
        });
    }

    @Override
    public @NotNull CompletableFuture<ItemPolicy> policy() {
        return post("/policy", Map.of(), PolicyDto.class).thenApply(r -> toPolicy(r.policyMode, r.policyVersion, r.rules));
    }

    @Override
    public @NotNull CompletableFuture<BatchResult> submit(@NotNull Batch batch, @NotNull List<EncodedItem> definitions) {
        List<OpDto> ops = new ArrayList<>();
        for (CloudOp op : batch.ops()) ops.add(new OpDto(op.key().channelId(), op.key().fingerprint(), op.delta()));
        List<ExpectedDto> expected = new ArrayList<>();
        for (Map.Entry<BalanceKey, Long> e : batch.expected().entrySet()) {
            expected.add(new ExpectedDto(e.getKey().channelId(), e.getKey().fingerprint(), e.getValue()));
        }
        List<ItemDto> defs = definitions.stream()
                .map(d -> new ItemDto(d.fingerprint(), d.itemId(), d.displayName(), Base64.getEncoder().encodeToString(d.bytes())))
                .toList();
        var body = new BatchRequest(batch.playerUuid().toString(), batch.epoch(), batch.seq(), ops, expected, defs);
        return send("/batches", body).thenApply(response -> switch (response.statusCode()) {
            case 200 -> switch (GSON.fromJson(response.body(), BatchDto.class).result) {
                case "applied" -> BatchResult.APPLIED;
                case "duplicate" -> BatchResult.DUPLICATE;
                default -> BatchResult.QUARANTINED;
            };
            case 400 -> {
                // Pedido que o TCMine nunca vai aceitar: reenviar para sempre prenderia o jogador. Trava o canal.
                TcCloud.LOG.error("Nuvem: o TCMine recusou o lote {}/{} como inválido: {}", batch.epoch(), batch.seq(),
                        response.body());
                yield BatchResult.QUARANTINED;
            }
            default -> throw failure("/batches", response);
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> release(@NotNull UUID playerUuid, long epoch, long lastSeq) {
        return post("/leases/release", new ReleaseRequest(playerUuid.toString(), epoch, lastSeq), ReleaseDto.class)
                .thenApply(r -> null);
    }

    @Override
    public @NotNull CompletableFuture<Void> reportDoubtful(@NotNull String reportId,
                                                           @NotNull List<DoubtfulOperation> operations) {
        List<DoubtfulDto> ops = operations.stream().map(op -> new DoubtfulDto(op.playerUuid().toString(),
                op.key().channelId(), op.key().fingerprint(),
                op.kind() == DoubtfulOperation.Kind.PENDING_CREDIT ? "PendingCredit" : "RecentDebit",
                op.amount())).toList();
        return send("/reports/doubtful", new DoubtfulRequest(reportId, ops)).thenApply(r -> okOrThrow("/reports/doubtful", r));
    }

    @Override
    public @NotNull CompletableFuture<Void> reportSuspects(@NotNull List<SuspectReport> suspects) {
        List<SuspectDto> items = suspects.stream().map(s -> new SuspectDto(s.itemId(), s.evidence(), s.attempts())).toList();
        return send("/reports/suspects", new SuspectsRequest(items)).thenApply(r -> okOrThrow("/reports/suspects", r));
    }

    @Override
    public @NotNull String describe() {
        return "TCMine em " + base;
    }

    @Override
    public void close() {
        // O HttpClient do JDK 21 não precisa ser fechado; as requisições em voo terminam sozinhas.
    }

    // ---------------------------------------------------------------- transporte

    private <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
        return send(path, body).thenApply(response -> {
            if (response.statusCode() != 200) throw failure(path, response);
            return GSON.fromJson(response.body(), type);
        });
    }

    private CompletableFuture<HttpResponse<String>> send(String path, Object body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Void okOrThrow(String path, HttpResponse<String> response) {
        if (response.statusCode() != 200) throw failure(path, response);
        return null;
    }

    private static CloudCallException failure(String path, HttpResponse<String> response) {
        String hint = switch (response.statusCode()) {
            case 401, 403 -> " (chave revogada ou servidor desligado da nuvem no painel; reinicie pelo TCMine)";
            case 409 -> " (conflito com outra requisição; será tentado de novo)";
            case 426 -> " (versão do mod incompatível com este TCMine; atualize o mod)";
            default -> "";
        };
        return new CloudCallException(path + " respondeu " + response.statusCode() + hint, response.statusCode());
    }

    /** Falha de uma chamada à nuvem, com o código HTTP (0 = rede). */
    public static final class CloudCallException extends RuntimeException {
        private final int status;

        public CloudCallException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    // ---------------------------------------------------------------- conversões

    private static ItemPolicy toPolicy(@Nullable String mode, long version, @Nullable List<RuleDto> rules) {
        List<ItemRule> converted = new ArrayList<>();
        for (RuleDto r : rules == null ? List.<RuleDto>of() : rules) {
            try {
                converted.add(new ItemRule(ItemRule.Scope.valueOf(r.scope.toUpperCase(Locale.ROOT)), r.pattern,
                        ItemRule.Action.valueOf(r.action.toUpperCase(Locale.ROOT))));
            } catch (RuntimeException e) {
                TcCloud.LOG.warn("Nuvem: regra de item ignorada ({}): {}", e.getMessage(), r);
            }
        }
        ItemPolicy.Mode m = "Allowlist".equalsIgnoreCase(mode) ? ItemPolicy.Mode.ALLOWLIST : ItemPolicy.Mode.BLOCKLIST;
        return new ItemPolicy(m, converted, version);
    }

    private static List<ChannelSnapshot> toChannels(@Nullable List<ChannelDto> channels) {
        List<ChannelSnapshot> result = new ArrayList<>();
        for (ChannelDto c : channels == null ? List.<ChannelDto>of() : channels) {
            Map<String, Long> amounts = new LinkedHashMap<>();
            Map<String, EncodedItem> items = new LinkedHashMap<>();
            for (ChannelItemDto i : c.items == null ? List.<ChannelItemDto>of() : c.items) {
                amounts.put(i.fingerprint, i.amount);
                items.put(i.fingerprint, new EncodedItem(i.fingerprint, i.itemId, i.displayName,
                        Base64.getDecoder().decode(i.encoded)));
            }
            result.add(new ChannelSnapshot(c.id, c.name, amounts, items));
        }
        return result;
    }
}
