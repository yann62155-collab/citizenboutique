package fr.citizen.boutique.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.citizen.boutique.CitizenBoutique;
import fr.citizen.boutique.net.Payloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Logique serveur de la boutique de soutien : envoi aux joueurs, achat (PayPal uniquement),
 * administration (admins seulement) et images. Appele sur le thread serveur.
 */
public final class ShopService {
    private static final Pattern IMAGE_NAME = Pattern.compile("[a-z0-9_-]{1,40}\\.png");
    private static final int MAX_IMAGE_BYTES = 800_000;
    private static final int MAX_UPLOAD_PARTS = 40;
    /** Niveau d'OP minimum pour gerer la boutique (non modifiable). */
    private static final int ADMIN_OP_LEVEL = 2;

    private static final Map<String, UploadBuffer> UPLOADS = new HashMap<>();

    private static final class UploadBuffer {
        final UUID owner;
        final byte[][] parts;
        int received;
        final long started = System.currentTimeMillis();
        UploadBuffer(UUID owner, int total) { this.owner = owner; this.parts = new byte[total][]; }
    }

    private ShopService() { }

    // ------------------------------------------------------------------ droits

    /** Seuls les OP (niveau 2+) et les admins listes par un OP peuvent gerer la boutique. */
    public static boolean isAdmin(ServerPlayer p) {
        if (p.hasPermissions(ADMIN_OP_LEVEL)) return true;
        String name = p.getGameProfile().getName();
        String uuid = p.getUUID().toString();
        for (String a : ShopStore.data().settings.admins) if (a.equalsIgnoreCase(name) || a.equalsIgnoreCase(uuid)) return true;
        return false;
    }

    // ------------------------------------------------------------------ envoi de la boutique

    public static void sendShop(ServerPlayer player, boolean open) {
        if (!player.connection.hasChannel(Payloads.Sync.TYPE)) {
            if (open) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6La boutique nécessite le mod Citizen Boutique sur PC."));
            return;
        }
        PacketDistributor.sendToPlayer(player, new Payloads.Sync(buildJson(player), open));
    }

    /** Met a jour la boutique chez tous les joueurs qui l'ont ouverte (sans l'ouvrir chez les autres). */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendShop(p, false);
    }

    private static String buildJson(ServerPlayer player) {
        ShopData d = ShopStore.data();
        boolean admin = isAdmin(player);
        JsonObject root = new JsonObject();
        root.addProperty("admin", admin);
        JsonElement shop = ShopStore.GSON.toJsonTree(d);
        JsonObject so = shop.getAsJsonObject();
        JsonObject settingsJson = so.getAsJsonObject("settings");
        // Le secret PayPal ne quitte jamais le serveur, meme vers un admin.
        boolean secretSet = d.settings.paypalSecret != null && !d.settings.paypalSecret.isBlank();
        settingsJson.remove("paypalSecret");
        root.addProperty("paypalSecretSet", secretSet);
        root.addProperty("paypalReady", d.settings.payPaypal && PayPalService.configured(d.settings));
        if (!admin) {
            settingsJson.remove("paypalClientId");
            settingsJson.remove("returnUrl");
            settingsJson.remove("admins");
            JsonArray visible = new JsonArray();
            for (JsonElement e : so.getAsJsonArray("offers")) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                o.remove("commands");
                visible.add(o);
            }
            so.add("offers", visible);
        }
        root.add("shop", shop);
        JsonObject bought = new JsonObject();
        for (ShopData.Offer o : d.offers) {
            int n = ShopStore.purchasesOf(player.getUUID(), o.id);
            if (n > 0) bought.addProperty(o.id, n);
        }
        root.add("bought", bought);
        return ShopStore.GSON.toJson(root);
    }

    // ------------------------------------------------------------------ achat

    private static Optional<ShopData.Offer> find(String id) {
        return ShopStore.data().offers.stream().filter(o -> o.id.equals(id)).findFirst();
    }

    private static void error(ServerPlayer p, String text) {
        PacketDistributor.sendToPlayer(p, new Payloads.Message("error", text));
    }

    /** Seul moyen de paiement : PayPal (argent reel). */
    public static void buy(ServerPlayer player, String offerId, String method) {
        ShopData.Offer o = find(offerId).orElse(null);
        if (o == null || !o.enabled) { error(player, "Cet article n'est plus disponible."); sendShop(player, false); return; }
        if (!"paypal".equals(method)) { error(player, "Cette boutique accepte uniquement PayPal."); return; }
        if (o.stock == 0) { error(player, "Rupture de stock !"); return; }
        if (o.limitPerPlayer > 0 && ShopStore.purchasesOf(player.getUUID(), o.id) >= o.limitPerPlayer) {
            error(player, "Vous avez atteint la limite d'achat pour cet article (" + o.limitPerPlayer + ").");
            return;
        }
        ItemStack reward = ItemJson.decode(o.item, player.registryAccess());
        if (reward.isEmpty() && o.commands.isEmpty()) { error(player, "Cet article est vide : un admin doit lui ajouter un objet ou une commande."); return; }
        PayPalService.start(player, o);
    }

    /** Livraison apres paiement confirme par PayPal. */
    static void deliver(ServerPlayer player, ShopData.Offer o, ItemStack reward, String method, String paid, boolean consumeStock) {
        if (!reward.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, reward.copy());
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (String cmd : o.commands) {
                if (cmd == null || cmd.isBlank()) continue;
                String c = cmd.trim().replace("{player}", player.getGameProfile().getName()).replace("{uuid}", player.getUUID().toString());
                if (c.startsWith("/")) c = c.substring(1);
                try {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
                } catch (Exception e) {
                    CitizenBoutique.LOGGER.warn("Citizen Boutique : commande en échec « {} » : {}", c, e.toString());
                }
            }
        }
        if (consumeStock && o.stock > 0) o.stock--;
        ShopStore.save();
        ShopStore.addPurchase(player.getUUID(), player.getGameProfile().getName(), o, method, paid);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
        PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Merci ! « " + o.name + " » vous a été livré."));
        if (server != null) broadcast(server);
        else sendShop(player, false);
    }

    public static void onLogin(ServerPlayer player) {
        PayPalService.onLogin(player);
    }

    // ------------------------------------------------------------------ administration

    public static void admin(ServerPlayer player, String action, String json) {
        if (!isAdmin(player)) { error(player, "Réservé aux administrateurs."); return; }
        ShopData d = ShopStore.data();
        JsonObject in;
        try { in = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException e) { error(player, "Requête invalide."); return; }
        MinecraftServer server = player.getServer();
        try {
            switch (action) {
                case "save_offer" -> {
                    ShopData.Offer o = ShopStore.GSON.fromJson(in.get("offer"), ShopData.Offer.class);
                    ShopData.Offer old = o.id == null || o.id.isBlank() ? null : find(o.id).orElse(null);
                    if (old == null) o.id = ShopStore.newId();
                    o.name = clip(o.name, 48, "Article");
                    o.description = clip(o.description, 600, "");
                    o.category = clip(o.category, 32, "");
                    o.image = clip(o.image, 400, "");
                    if (o.features == null) o.features = new ArrayList<>();
                    o.features.replaceAll(f -> clip(f, 40, ""));
                    o.features.removeIf(String::isBlank);
                    while (o.features.size() > 6) o.features.remove(o.features.size() - 1);
                    o.realPriceCents = Math.max(0, Math.min(o.realPriceCents, 100_000));
                    if (o.realPriceCents < 50) { error(player, "Indiquez un prix d'au moins 0,50 €."); return; }
                    o.stock = Math.max(-1, o.stock);
                    o.limitPerPlayer = Math.max(0, o.limitPerPlayer);
                    if (o.commands == null) o.commands = new ArrayList<>();
                    o.commands.removeIf(c -> c == null || c.isBlank());
                    if (in.has("useHand") && in.get("useHand").getAsBoolean()) {
                        ItemStack hand = player.getMainHandItem();
                        if (hand.isEmpty()) { error(player, "Votre main est vide : prenez l'objet à vendre en main."); return; }
                        o.item = ItemJson.encode(hand, player.registryAccess());
                    } else if (in.has("clearItem") && in.get("clearItem").getAsBoolean()) {
                        o.item = null;
                    } else {
                        o.item = old == null ? null : old.item;
                    }
                    if (old == null) d.offers.add(o);
                    else d.offers.set(d.offers.indexOf(old), o);
                    if (!o.category.isBlank() && !d.categories.contains(o.category)) {
                        d.categories.add(o.category);
                        d.categoryInfo.computeIfAbsent(o.category, k -> new ShopData.Category());
                    }
                    ShopStore.save();
                    PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Article « " + o.name + " » enregistré."));
                }
                case "delete_offer" -> {
                    String id = in.get("id").getAsString();
                    if (d.offers.removeIf(o -> o.id.equals(id))) {
                        ShopStore.save();
                        PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Article supprimé."));
                    }
                }
                case "move_offer" -> {
                    String id = in.get("id").getAsString();
                    int dir = in.get("dir").getAsInt() < 0 ? -1 : 1;
                    for (int i = 0; i < d.offers.size(); i++) {
                        if (!d.offers.get(i).id.equals(id)) continue;
                        int j = i + dir;
                        if (j >= 0 && j < d.offers.size()) { java.util.Collections.swap(d.offers, i, j); ShopStore.save(); }
                        break;
                    }
                }
                case "save_category" -> {
                    String oldName = in.has("old") ? clip(in.get("old").getAsString(), 32, "") : "";
                    String name = clip(in.get("name").getAsString(), 32, "");
                    if (name.isBlank()) { error(player, "Donnez un nom à la catégorie."); return; }
                    if (!name.equals(oldName) && d.categories.contains(name)) { error(player, "Cette catégorie existe déjà."); return; }
                    ShopData.Category cat = new ShopData.Category();
                    cat.icon = clip(in.get("icon").getAsString(), 100, "minecraft:chest");
                    cat.banner = clip(in.get("banner").getAsString(), 400, "");
                    cat.description = clip(in.get("description").getAsString(), 200, "");
                    int idx = oldName.isBlank() ? -1 : d.categories.indexOf(oldName);
                    if (idx >= 0) {
                        d.categories.set(idx, name);
                        d.categoryInfo.remove(oldName);
                        for (ShopData.Offer o : d.offers) if (o.category.equals(oldName)) o.category = name;
                    } else d.categories.add(name);
                    d.categoryInfo.put(name, cat);
                    ShopStore.save();
                    PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Catégorie « " + name + " » enregistrée."));
                }
                case "delete_category" -> {
                    String name = in.get("name").getAsString();
                    if (d.categories.remove(name)) {
                        d.categoryInfo.remove(name);
                        for (ShopData.Offer o : d.offers) if (o.category.equals(name)) o.category = "";
                        ShopStore.save();
                        PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Catégorie supprimée (ses articles restent dans « Tout »)."));
                    }
                }
                case "move_category" -> {
                    String name = in.get("name").getAsString();
                    int i = d.categories.indexOf(name), j = i + (in.get("dir").getAsInt() < 0 ? -1 : 1);
                    if (i >= 0 && j >= 0 && j < d.categories.size()) { java.util.Collections.swap(d.categories, i, j); ShopStore.save(); }
                }
                case "save_settings" -> {
                    ShopData.Settings ns = ShopStore.GSON.fromJson(in.get("settings"), ShopData.Settings.class);
                    ns.title = clip(ns.title, 40, "Boutique");
                    ns.subtitle = clip(ns.subtitle, 80, "");
                    if (ns.admins == null) ns.admins = new ArrayList<>();
                    // Secret PayPal : vide = garder l'actuel, "-" = l'effacer.
                    String sec = ns.paypalSecret == null ? "" : ns.paypalSecret.trim();
                    ns.paypalSecret = sec.isEmpty() ? d.settings.paypalSecret : "-".equals(sec) ? "" : sec;
                    ns.paypalClientId = clip(ns.paypalClientId, 200, "");
                    ns.currency = clip(ns.currency, 3, "EUR").toUpperCase(java.util.Locale.ROOT);
                    if (!ns.currency.matches("[A-Z]{3}")) ns.currency = "EUR";
                    ns.returnUrl = clip(ns.returnUrl, 300, "https://www.paypal.com/");
                    ns.tagline = clip(ns.tagline, 80, "");
                    d.settings = ns;
                    if (in.has("categories")) {
                        List<String> cats = new ArrayList<>();
                        for (JsonElement e : in.getAsJsonArray("categories")) {
                            String c = clip(e.getAsString(), 32, "");
                            if (!c.isBlank() && !cats.contains(c)) cats.add(c);
                        }
                        d.categories = cats;
                        for (String c : cats) d.categoryInfo.computeIfAbsent(c, k -> new ShopData.Category());
                    }
                    ShopStore.save();
                    PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Réglages enregistrés."));
                    if (ns.payPaypal && PayPalService.configured(ns)) PayPalService.test(player);
                }
                default -> { error(player, "Action inconnue."); return; }
            }
        } catch (RuntimeException e) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : action admin {} invalide : {}", action, e.toString());
            error(player, "Données invalides : " + e.getMessage());
            return;
        }
        if (server != null) broadcast(server);
    }

    private static String clip(String s, int max, String def) {
        if (s == null) return def;
        s = s.replace('§', ' ').strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ------------------------------------------------------------------ images

    public static void upload(ServerPlayer player, Payloads.Upload u) {
        if (!isAdmin(player)) return;
        long now = System.currentTimeMillis();
        for (Iterator<UploadBuffer> it = UPLOADS.values().iterator(); it.hasNext(); ) if (now - it.next().started > 120_000) it.remove();
        if (u.total() <= 0 || u.total() > MAX_UPLOAD_PARTS || u.index() < 0 || u.index() >= u.total()) { error(player, "Image trop grande."); return; }
        UploadBuffer b = UPLOADS.computeIfAbsent(u.uploadId(), k -> new UploadBuffer(player.getUUID(), u.total()));
        if (!b.owner.equals(player.getUUID()) || b.parts.length != u.total()) return;
        if (b.parts[u.index()] == null) { b.parts[u.index()] = u.data(); b.received++; }
        if (b.received < b.parts.length) return;
        UPLOADS.remove(u.uploadId());
        int size = 0;
        for (byte[] p : b.parts) size += p.length;
        if (size > MAX_IMAGE_BYTES) { error(player, "Image trop lourde (max 800 Ko)."); return; }
        byte[] all = new byte[size];
        int pos = 0;
        for (byte[] p : b.parts) { System.arraycopy(p, 0, all, pos, p.length); pos += p.length; }
        if (size < 8 || (all[0] & 0xFF) != 0x89 || all[1] != 'P' || all[2] != 'N' || all[3] != 'G') { error(player, "Le fichier reçu n'est pas une image PNG."); return; }
        String name = ShopStore.newId() + ".png";
        try {
            Files.createDirectories(ShopStore.imagesDir());
            Files.write(ShopStore.imagesDir().resolve(name), all);
        } catch (Exception e) {
            error(player, "Enregistrement de l'image impossible : " + e.getMessage());
            return;
        }
        PacketDistributor.sendToPlayer(player, new Payloads.Message("upload", "server:" + name));
    }

    public static void sendImage(ServerPlayer player, String name) {
        byte[] data = new byte[0];
        if (IMAGE_NAME.matcher(name).matches()) {
            Path f = ShopStore.imagesDir().resolve(name);
            try {
                if (Files.isRegularFile(f) && Files.size(f) <= MAX_IMAGE_BYTES) data = Files.readAllBytes(f);
            } catch (Exception ignored) { }
        }
        PacketDistributor.sendToPlayer(player, new Payloads.ImageData(name, data));
    }
}
