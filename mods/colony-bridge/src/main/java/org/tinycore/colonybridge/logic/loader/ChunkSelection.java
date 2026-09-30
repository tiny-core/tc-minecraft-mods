package org.tinycore.colonybridge.logic.loader;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.Collection;

/**
 * Regra pura: quais chunks o loader carrega quando a colônia tem mais do que o máximo — os mais perto do centro
 * da colônia (distância em chunks, empate pela ordem de x e z para o resultado não "pular" entre ciclos).
 * <p>
 * Chunks viajam como {@code long} no mesmo formato do {@code ChunkPos.asLong} do Minecraft (x nos 32 bits de
 * baixo, z nos de cima), repetido aqui para a regra ser testada sem o jogo. {@code LongList} é a lista de
 * {@code long} sem "caixas" da fastutil (biblioteca que vem com o Minecraft).
 */
public final class ChunkSelection {

    private ChunkSelection() {}

    public static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    public static int x(long chunk) {
        return (int) chunk;
    }

    public static int z(long chunk) {
        return (int) (chunk >>> 32);
    }

    /**
     * @param claimed chunks reivindicados pela colônia
     * @param centerX chunk do centro da colônia
     * @param max     máximo a carregar
     * @return até {@code max} chunks, do mais perto ao mais longe do centro
     */
    public static LongList nearest(Collection<Long> claimed, int centerX, int centerZ, int max) {
        LongList sorted = new LongArrayList(claimed.size());
        for (long chunk : claimed) {
            sorted.add(chunk);
        }
        sorted.sort((a, b) -> {
            int byDistance = Long.compare(distanceSq(a, centerX, centerZ), distanceSq(b, centerX, centerZ));
            if (byDistance != 0) {
                return byDistance;
            }
            int byX = Integer.compare(x(a), x(b));
            return byX != 0 ? byX : Integer.compare(z(a), z(b));
        });
        return sorted.size() <= max ? sorted : new LongArrayList(sorted.subList(0, Math.max(0, max)));
    }

    private static long distanceSq(long chunk, int centerX, int centerZ) {
        long dx = x(chunk) - (long) centerX;
        long dz = z(chunk) - (long) centerZ;
        return dx * dx + dz * dz;
    }
}
