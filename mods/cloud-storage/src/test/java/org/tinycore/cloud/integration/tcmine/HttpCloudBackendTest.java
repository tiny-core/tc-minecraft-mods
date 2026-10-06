package org.tinycore.cloud.integration.tcmine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.cloud.journal.JournalReplay;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;
import org.tinycore.cloud.item.policy.ItemRule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpCloudBackend} contra um servidor HTTP falso que responde como o TCMine: o JSON que sai tem os nomes
 * que a API espera, a chave vai no cabeçalho e cada código HTTP vira o resultado certo.
 */
class HttpCloudBackendTest {

    private static final String KEY = "tcs_abcdefghijkm_segredo";
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID CHANNEL = UUID.randomUUID();
    private static final String FP = "d".repeat(64);

    private HttpServer server;
    private final Map<String, String> bodies = new HashMap<>();
    private final Map<String, String> auth = new HashMap<>();
    private final Map<String, Integer> status = new HashMap<>();
    private final Map<String, String> replies = new HashMap<>();
    private HttpCloudBackend backend;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/cloud/v1/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/api/cloud/v1".length());
            bodies.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = replies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.getOrDefault(path, 200), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        backend = new HttpCloudBackend(new CloudCredentials(url, KEY, "teste"), "0.2.0");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private JsonObject sent(String path) {
        return JsonParser.parseString(bodies.get(path)).getAsJsonObject();
    }

    @Test
    void helloLevaChaveProtocoloECheckpointETrazAPolitica() {
        replies.put("/hello", """
                {"protocol":1,"policyMode":"Allowlist","policyVersion":7,
                 "rules":[{"scope":"Mod","pattern":"minecraft","action":"Allow"},{"scope":"Nada","pattern":"x","action":"Block"}],
                 "quota":{"maxTypes":50,"maxTotal":1000},"maxItemBytes":4096,"readOnly":true,"readOnlyReason":"rollback"}""");
        UUID world = UUID.randomUUID();
        Checkpoint cp = new Checkpoint(world, Map.of(PLAYER, new JournalReplay.SeqPosition(3, 9)));

        CloudBackend.HelloReply reply = backend.hello(cp).join();

        assertEquals("Bearer " + KEY, auth.get("/hello"));
        JsonObject body = sent("/hello");
        assertEquals(HttpCloudBackend.PROTOCOL, body.get("protocol").getAsInt());
        assertEquals(world.toString(), body.getAsJsonObject("checkpoint").get("worldId").getAsString());
        assertEquals(9, body.getAsJsonObject("checkpoint").getAsJsonObject("players")
                .getAsJsonObject(PLAYER.toString()).get("seq").getAsLong());

        assertEquals(ItemPolicy.Mode.ALLOWLIST, reply.policy().mode());
        assertEquals(7, reply.policy().version());
        assertEquals(List.of(new ItemRule(ItemRule.Scope.MOD, "minecraft", ItemRule.Action.ALLOW)), reply.policy().rules(),
                "regra com escopo desconhecido é ignorada, não derruba o hello");
        assertEquals(50, reply.quota().maxTypes());
        assertEquals(4096, reply.maxItemBytes());
        assertTrue(reply.readOnly());
    }

    @Test
    void acquireTrazCanaisComOsBytesDosItens() {
        byte[] bytes = {1, 2, 3, 4};
        replies.put("/leases/acquire", """
                {"status":"granted","epoch":4,"readOnly":false,"channels":[{"id":"%s","name":"Principal","frozen":false,
                 "items":[{"fingerprint":"%s","amount":64,"itemId":"minecraft:diamond","displayName":"Diamante","encoded":"%s"}]}]}"""
                .formatted(CHANNEL, FP, Base64.getEncoder().encodeToString(bytes)));

        var granted = assertInstanceOf(CloudBackend.LeaseResult.Granted.class, backend.acquire(PLAYER, "ana").join());

        assertEquals(PLAYER.toString(), sent("/leases/acquire").get("playerUuid").getAsString());
        assertEquals(4, granted.epoch());
        CloudBackend.ChannelSnapshot channel = granted.channels().getFirst();
        assertEquals(CHANNEL, channel.id());
        assertEquals(64L, channel.amounts().get(FP));
        assertArrayEquals(bytes, channel.items().get(FP).bytes());
    }

    @Test
    void acquireOcupadoDizQuemEsta() {
        replies.put("/leases/acquire", """
                {"status":"busy","epoch":0,"readOnly":true,"channels":[],"holder":"Servidor B"}""");

        var busy = assertInstanceOf(CloudBackend.LeaseResult.Busy.class, backend.acquire(PLAYER, "ana").join());

        assertEquals("Servidor B", busy.holder());
    }

    private Batch batch() {
        BalanceKey key = new BalanceKey(CHANNEL, FP);
        return new Batch(PLAYER, 2, 5, List.of(new CloudOp(key, 64)), Map.of(key, 64L));
    }

    @Test
    void loteLevaOperacoesSaldosEsperadosEDefinicoes() {
        replies.put("/batches", "{\"result\":\"applied\"}");
        EncodedItem item = new EncodedItem(FP, "minecraft:diamond", "Diamante", new byte[]{9});

        assertEquals(CloudBackend.BatchResult.APPLIED, backend.submit(batch(), List.of(item)).join());

        JsonObject body = sent("/batches");
        assertEquals(5, body.get("seq").getAsLong());
        assertEquals(CHANNEL.toString(), body.getAsJsonArray("ops").get(0).getAsJsonObject().get("channelId").getAsString());
        assertEquals(64, body.getAsJsonArray("expected").get(0).getAsJsonObject().get("amount").getAsLong());
        assertEquals("CQ==", body.getAsJsonArray("definitions").get(0).getAsJsonObject().get("encoded").getAsString());
    }

    @Test
    void respostasDoLoteViramOResultadoCerto() {
        replies.put("/batches", "{\"result\":\"duplicate\"}");
        assertEquals(CloudBackend.BatchResult.DUPLICATE, backend.submit(batch(), List.of()).join());

        replies.put("/batches", "{\"result\":\"quarantined\",\"reason\":\"StaleEpoch\"}");
        assertEquals(CloudBackend.BatchResult.QUARANTINED, backend.submit(batch(), List.of()).join());

        // 400: nunca vai passar; reenviar para sempre prenderia o jogador.
        status.put("/batches", 400);
        assertEquals(CloudBackend.BatchResult.QUARANTINED, backend.submit(batch(), List.of()).join());

        // 409: corrida com outra requisição; nada gravado, o mod tenta de novo.
        status.put("/batches", 409);
        var error = assertThrows(CompletionException.class, () -> backend.submit(batch(), List.of()).join());
        assertEquals(409, assertInstanceOf(HttpCloudBackend.CloudCallException.class, error.getCause()).status());
    }

    @Test
    void chaveRecusadaEhFalhaNaoResposta() {
        status.put("/hello", 401);
        var error = assertThrows(CompletionException.class,
                () -> backend.hello(new Checkpoint(UUID.randomUUID(), Map.of())).join());
        assertTrue(error.getCause().getMessage().contains("401"));
    }

    @Test
    void heartbeatDevolveOsLeasesPerdidosEAVersaoDaPolitica() {
        replies.put("/leases/heartbeat", "{\"lost\":[\"%s\"],\"policyVersion\":12}".formatted(PLAYER));

        var reply = backend.heartbeat(List.of(new CloudBackend.HeldLease(PLAYER, 3))).join();

        assertEquals(List.of(PLAYER), reply.lost());
        assertEquals(12, reply.policyVersion());
        assertEquals(3, sent("/leases/heartbeat").getAsJsonArray("leases").get(0).getAsJsonObject().get("epoch").getAsLong());
    }

    @Test
    void duvidasVaoComOIdDoRelatorioEONomeDoTipoDoTcmine() {
        backend.reportDoubtful("boot-1", List.of(
                new DoubtfulOperation(PLAYER, new BalanceKey(CHANNEL, FP), DoubtfulOperation.Kind.PENDING_CREDIT, 4))).join();

        JsonObject body = sent("/reports/doubtful");
        assertEquals("boot-1", body.get("reportId").getAsString());
        assertEquals("PendingCredit", body.getAsJsonArray("operations").get(0).getAsJsonObject().get("kind").getAsString());
    }

    @Test
    void chaveNuncaApareceNoToString() {
        var credentials = new CloudCredentials(URI.create("https://x"), KEY, "teste");
        assertTrue(credentials.toString().contains("tcs_abcdefghijkm_…"));
        assertTrue(!credentials.toString().contains("segredo"));
    }

    @Test
    void criarCanalMandaONomeELeOCanal() {
        UUID id = UUID.randomUUID();
        replies.put("/channels", "{\"id\":\"" + id + "\",\"name\":\"Minérios\"}");
        CloudBackend.ChannelResult r = backend.createChannel(PLAYER, "Minérios").join();
        assertEquals(id, r.id());
        assertEquals("Minérios", r.name());
        assertEquals(PLAYER.toString(), sent("/channels").get("playerUuid").getAsString());
        assertEquals("Minérios", sent("/channels").get("name").getAsString());
    }

    @Test
    void tcmineSemEndpointDeCanalEhSemSuporte() {
        status.put("/channels/rename", 404);
        assertEquals(CloudBackend.ChannelRefusal.UNSUPPORTED,
                backend.renameChannel(PLAYER, CHANNEL, "X").join().refusal());
        assertEquals(CHANNEL.toString(), sent("/channels/rename").get("channelId").getAsString());
    }

    @Test
    void recusaDeCanalVemNoCorpo() {
        status.put("/channels", 409);
        replies.put("/channels", "{\"refusal\":\"limit\"}");
        assertEquals(CloudBackend.ChannelRefusal.LIMIT, backend.createChannel(PLAYER, "X").join().refusal());
    }
}
