package org.tinycore.colonybridge.block.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.logic.crafting.CraftableMods;
import org.tinycore.colonybridge.logic.crafting.ModFilterMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Preferências de craft de uma ponte para pedidos por tag/ferramenta/comida: qual ordem usar e quais
 * mods valem. Salvas no NBT do block entity e alteradas pelas abas "Geral" e "Mods" da tela.
 * Os itens preferidos (modo Lista) ficam em {@link PreferredItems}, porque são ghost slots.
 * <p>
 * Imutável (record), como o {@link BridgeSettings}: cada mudança cria um objeto novo.
 *
 * @param preference ordem dos candidatos; {@code null} = usar o padrão da config do servidor
 * @param modMode    como a lista {@code mods} é usada
 * @param mods       mods marcados (namespaces), sem repetição, no máximo {@link CraftableMods#MAX}
 */
public record CraftSettings(@Nullable CraftPreference preference, ModFilterMode modMode, List<String> mods) {

    public static final CraftSettings DEFAULT = new CraftSettings(null, ModFilterMode.ALL, List.of());

    /** Formato de um id de mod no Minecraft; qualquer outra coisa vinda do cliente é descartada. */
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final CraftPreference[] PREFERENCES = CraftPreference.values();

    /**
     * Pela rede, a preferência vai como número: 0 = padrão do servidor, 1.. = {@code ordinal + 1}.
     * Listas e textos têm teto no codec (pacote hostil não aloca memória sem limite).
     */
    public static final StreamCodec<ByteBuf, CraftSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftSettings::preferenceId,
            ByteBufCodecs.VAR_INT, s -> s.modMode().ordinal(),
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(CraftableMods.MAX)), CraftSettings::mods,
            (pref, mode, mods) -> sanitized(preferenceById(pref), ModFilterMode.byId(mode), mods));

    /**
     * Cria as configurações limpando a lista de mods: só ids válidos, sem repetição, até o teto.
     * Usado em tudo que vem de fora (pacote do cliente e NBT).
     */
    public static CraftSettings sanitized(@Nullable CraftPreference preference, ModFilterMode modMode,
                                          List<String> mods) {
        Set<String> clean = new LinkedHashSet<>();
        for (String mod : mods) {
            if (clean.size() >= CraftableMods.MAX) {
                break;
            }
            if (MOD_ID.matcher(mod).matches()) {
                clean.add(mod);
            }
        }
        return new CraftSettings(preference, modMode, List.copyOf(clean));
    }

    public CraftSettings withPreference(@Nullable CraftPreference value) {
        return new CraftSettings(value, modMode, mods);
    }

    public CraftSettings withModMode(ModFilterMode value) {
        return new CraftSettings(preference, value, mods);
    }

    /** Marca o mod se estiver desmarcado, e vice-versa. */
    public CraftSettings toggleMod(String mod) {
        Set<String> set = new LinkedHashSet<>(mods);
        if (!set.remove(mod)) {
            set.add(mod);
        }
        return sanitized(preference, modMode, List.copyOf(set));
    }

    /** Próxima opção de preferência no botão: padrão do servidor → mais barato → mais caro → lista → ... */
    public @Nullable CraftPreference nextPreference() {
        return preferenceById((preferenceId() + 1) % (PREFERENCES.length + 1));
    }

    private int preferenceId() {
        return preference == null ? 0 : preference.ordinal() + 1;
    }

    private static @Nullable CraftPreference preferenceById(int id) {
        return id >= 1 && id <= PREFERENCES.length ? PREFERENCES[id - 1] : null;
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("preference", preferenceId());
        tag.putInt("modMode", modMode.ordinal());
        ListTag list = new ListTag();
        mods.forEach(mod -> list.add(StringTag.valueOf(mod)));
        tag.put("mods", list);
        return tag;
    }

    /** Pontes antigas (sem estes campos) ficam em "padrão do servidor" e "todos os mods". */
    public static CraftSettings load(CompoundTag tag) {
        ListTag list = tag.getList("mods", Tag.TAG_STRING);
        List<String> mods = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            mods.add(list.getString(i));
        }
        return sanitized(preferenceById(tag.getInt("preference")), ModFilterMode.byId(tag.getInt("modMode")), mods);
    }
}
