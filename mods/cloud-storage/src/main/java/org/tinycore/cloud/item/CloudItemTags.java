package org.tinycore.cloud.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.tinycore.cloud.TcCloud;

/**
 * Tags de item do mod. {@code #tccloud:never_transfer} (regra 3 do plano §6): itens que nunca podem ir para a
 * nuvem. O mod traz a lista vazia; cada item só entra depois de verificado no ATM10, e o dono do servidor pode
 * ampliar por datapack ({@code data/tccloud/tags/item/never_transfer.json}).
 */
public final class CloudItemTags {

    public static final TagKey<Item> NEVER_TRANSFER =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(TcCloud.MOD_ID, "never_transfer"));

    private CloudItemTags() {}
}
