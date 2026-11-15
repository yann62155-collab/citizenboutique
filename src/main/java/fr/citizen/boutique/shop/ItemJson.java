package fr.citizen.boutique.shop;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Conversion ItemStack <-> JSON (avec enchantements, noms, composants...), identique cote serveur et client. */
public final class ItemJson {
    private ItemJson() { }

    public static JsonElement encode(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return null;
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack).result().orElse(null);
    }

    public static ItemStack decode(JsonElement json, HolderLookup.Provider registries) {
        if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
        try {
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** Objet par identifiant ("minecraft:diamond"), vide si inconnu. */
    public static Optional<Item> item(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(rl).filter(i -> i != Items.AIR);
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
