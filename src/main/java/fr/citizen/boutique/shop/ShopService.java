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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Logique serveur : envoi de la boutique, achats, paiements, administration et images. Appele sur le thread serveur. */
public final class ShopService {
    private static final Pattern IMAGE_NAME = Pattern.compile("[a-z0-9_-]{1,40}\\.png");
    private static final int MAX_IMAGE_BYTES = 800_000;
    private static final int MAX_UPLOAD_PARTS = 40;

    /** Joueurs dont un paiement bancaire est en cours (anti double-clic). */
    private static final Set<UUID> PENDING_BANK = new HashSet<>();
    /** Achats payes par banque alors que le joueur s'est deconnecte : livres a la reconnexion. */
    private static final Map<UUID, List<String>> PENDING_DELIVERY = new HashMap<>();
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

    public static boolean isAdmin(ServerPlayer p) {
        ShopData.Settings s = ShopStore.data().settings;
        if (s.opLevel >= 0 && s.opLevel <= 4 && p.hasPermissions(s.opLevel)) return true;
        String name = p.getGameProfile().getName();
        String uuid = p.getUUID().toString();
        for (String a : s.admins) if (a.equalsIgnoreCase(name) || a.equalsIgnoreCase(uuid)) return true;
        return false;
    }

    // ------------------------------------------------------------------ envoi de la boutique

    /** Envoie la boutique au joueur (avec solde bancaire si Citizen Economy est present). */
    public static void sendShop(ServerPlayer player, boolean open) {
        if (!player.connection.hasChannel(Payloads.Sync.TYPE)) {
            if (open) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6La boutique nécessite le mod Citizen Boutique sur PC."));
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean bank = ShopStore.data().settings.payBank && BankBridge.available();
        if (!bank) {
            PacketDistributor.sendToPlayer(player, new Payloads.Sync(buildJson(player, -1, false), open));
            return;
        }
        UUID id = player.getUUID();
        BankBridge.balance(id, player.getGameProfile().getName()).whenComplete((bal, err) -> server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) PacketDistributor.sendToPlayer(p, new Payloads.Sync(buildJson(p, bal == null ? -1 : bal, true), open));
        }));
    }

    /** Met a jour la boutique chez tous les joueurs qui l'ont ouverte (sans l'ouvrir chez les autres). */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendShop(p, false);
    }

    private static String buildJson(ServerPlayer player, long bankBalance, boolean bankAvailable) {
        ShopData d = ShopStore.data();
        boolean admin = isAdmin(player);
        JsonObject root = new JsonObject();
        root.addProperty("admin", admin);
        root.addProperty("bankAvailable", bankAvailable);
        root.addProperty("bankInstalled", bankAvailable || BankBridge.available());
        root.addProperty("bank", bankBalance);
        root.addProperty("wallet", coinTotal(player, d.settings));
        root.addProperty("xp", player.experienceLevel);
        JsonElement shop = ShopStore.GSON.toJsonTree(d);
        JsonObject settingsJson = shop.getAsJsonObject().getAsJsonObject("settings");
        // Le secret PayPal ne quitte jamais le serveur, meme vers un admin.
        boolean secretSet = d.settings.paypalSecret != null && !d.settings.paypalSecret.isBlank();
        settingsJson.remove("paypalSecret");
        root.addProperty("paypalSecretSet", secretSet);
        root.addProperty("paypalReady", d.settings.payPaypal && PayPalService.configured(d.settings));
        if (!admin) {
            settingsJson.remove("paypalClientId");
            settingsJson.remove("returnUrl");
            // Les joueurs ne voient ni les commandes, ni la liste des admins, ni les articles desactives.
            JsonObject so = shop.getAsJsonObject();
            so.getAsJsonObject("settings").remove("admins");
            JsonArray offers = so.getAsJsonArray("offers");
            JsonArray visible = new JsonArray();
            for (JsonElement e : offers) {
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

    // ------------------------------------------------------------------ achats

    private static Optional<ShopData.Offer> find(String id) {
        return ShopStore.data().offers.stream().filter(o -> o.id.equals(id)).findFirst();
    }

    private static void error(ServerPlayer p, String text) {
        PacketDistributor.sendToPlayer(p, new Payloads.Message("error", text));
    }

    public static void buy(ServerPlayer player, String offerId, String method) {
        ShopData d = ShopStore.data();
        ShopData.Settings s = d.settings;
        ShopData.Offer o = find(offerId).orElse(null);
        if (o == null || !o.enabled) { error(player, "Cet article n'est plus en vente."); sendShop(player, false); return; }
        if (o.stock == 0) { error(player, "Rupture de stock !"); return; }
        if (o.limitPerPlayer > 0 && ShopStore.purchasesOf(player.getUUID(), o.id) >= o.limitPerPlayer) {
            error(player, "Vous avez atteint la limite d'achat pour cet article (" + o.limitPerPlayer + ").");
            return;
        }
        ItemStack reward = ItemJson.decode(o.item, player.registryAccess());
        if (reward.isEmpty() && o.commands.isEmpty()) { error(player, "Cet article est vide : un admin doit lui ajouter un objet ou une commande."); return; }

        switch (method) {
            case "coins" -> {
                if (!s.payCoins || !o.allowCoins || o.price <= 0) { error(player, "Paiement en pièces non accepté pour cet article."); return; }
                long wallet = coinTotal(player, s);
                if (wallet < o.price) { error(player, "Il vous manque " + (o.price - wallet) + " PO dans votre bourse."); return; }
                if (!canMakeChange(wallet - o.price, s)) { error(player, "Le marchand ne peut pas vous rendre la monnaie."); return; }
                takeCoins(player, o.price, s);
                if (s.revenueToTreasury) BankBridge.treasuryRevenue(o.price, reason(o, player));
                deliver(player, o, reward, "pièces", o.price + " PO", true);
            }
            case "bank" -> {
                if (!s.payBank || !o.allowBank || o.price <= 0) { error(player, "Paiement par la banque non accepté pour cet article."); return; }
                if (!BankBridge.available()) { error(player, "La banque est fermée pour le moment."); return; }
                UUID id = player.getUUID();
                if (!PENDING_BANK.add(id)) { error(player, "Un paiement est déjà en cours…"); return; }
                boolean reserved = o.stock > 0;
                if (reserved) { o.stock--; ShopStore.save(); }
                MinecraftServer server = player.getServer();
                String name = player.getGameProfile().getName();
                long price = o.price;
                BankBridge.withdraw(id, name, price).whenComplete((v, err) -> server.execute(() -> {
                    PENDING_BANK.remove(id);
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (err != null) {
                        if (reserved) { o.stock++; ShopStore.save(); }
                        if (p != null) error(p, BankBridge.message(err));
                        return;
                    }
                    if (s.revenueToTreasury) BankBridge.treasuryRevenue(price, reason(o, p != null ? p : player));
                    if (p == null) {
                        PENDING_DELIVERY.computeIfAbsent(id, k -> new ArrayList<>()).add(o.id);
                        CitizenBoutique.LOGGER.info("Citizen Boutique : {} déconnecté pendant l'achat de {}, livraison à la reconnexion", name, o.name);
                        return;
                    }
                    deliver(p, o, ItemJson.decode(o.item, p.registryAccess()), "banque", price + " PO", false);
                }));
            }
            case "barter" -> {
                Item want = ItemJson.item(o.barterItem).orElse(null);
                if (!s.payBarter || want == null || o.barterCount <= 0) { error(player, "Troc non accepté pour cet article."); return; }
                int have = count(player, want);
                if (have < o.barterCount) { error(player, "Il vous manque " + (o.barterCount - have) + " × " + want.getDescription().getString() + "."); return; }
                remove(player, want, o.barterCount);
                deliver(player, o, reward, "troc", o.barterCount + " × " + ItemJson.id(want), true);
            }
            case "xp" -> {
                if (!s.payXp || o.xpLevels <= 0) { error(player, "Paiement en expérience non accepté pour cet article."); return; }
                if (player.experienceLevel < o.xpLevels) { error(player, "Il vous faut " + o.xpLevels + " niveaux d'expérience."); return; }
                player.giveExperienceLevels(-o.xpLevels);
                deliver(player, o, reward, "expérience", o.xpLevels + " niveaux", true);
            }
            case "paypal" -> PayPalService.start(player, o);
            case "free" -> {
                boolean free = o.price <= 0 && o.xpLevels <= 0 && o.realPriceCents <= 0 && (o.barterItem.isBlank() || o.barterCount <= 0);
                if (!free) { error(player, "Cet article n'est pas gratuit."); return; }
                deliver(player, o, reward, "gratuit", "0", true);
            }
            default -> error(player, "Moyen de paiement inconnu.");
        }
    }

    private static String reason(ShopData.Offer o, ServerPlayer p) {
        String r = "Boutique : " + o.name + " (" + p.getGameProfile().getName() + ")";
        return r.length() > 190 ? r.substring(0, 190) : r;
    }

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
        player.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8f, 1.0f);
        PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Vous avez acheté « " + o.name + " » (" + paid + ")."));
        if (server != null) broadcast(server);
        else sendShop(player, false);
    }

    /** Livraison des achats bancaires payes pendant une deconnexion. */
    public static void onLogin(ServerPlayer player) {
        PayPalService.onLogin(player);
        List<String> todo = PENDING_DELIVERY.remove(player.getUUID());
        if (todo == null) return;
        for (String id : todo) find(id).ifPresent(o -> deliver(player, o, ItemJson.decode(o.item, player.registryAccess()), "banque (livraison différée)", o.price + " PO", false));
    }

    // ------------------------------------------------------------------ pieces et objets

    private static List<Map.Entry<Item, Long>> coinDefs(ShopData.Settings s) {
        List<Map.Entry<Item, Long>> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.coins.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            ItemJson.item(e.getKey()).ifPresent(i -> list.add(Map.entry(i, e.getValue())));
        }
        list.sort(Comparator.comparingLong((Map.Entry<Item, Long> e) -> e.getValue()).reversed());
        return list;
    }

    public static long coinTotal(ServerPlayer p, ShopData.Settings s) {
        Map<Item, Long> values = new HashMap<>();
        for (Map.Entry<Item, Long> e : coinDefs(s)) values.put(e.getKey(), e.getValue());
        Inventory inv = p.getInventory();
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            Long v = values.get(st.getItem());
            if (v != null) total += v * st.getCount();
        }
        return total;
    }

    private static boolean canMakeChange(long change, ShopData.Settings s) {
        for (Map.Entry<Item, Long> e : coinDefs(s)) change %= e.getValue();
        return change == 0;
    }

    /** Retire toutes les pieces puis rend la monnaie dans les plus grosses pieces possibles. */
    private static void takeCoins(ServerPlayer p, long price, ShopData.Settings s) {
        List<Map.Entry<Item, Long>> defs = coinDefs(s);
        Map<Item, Long> values = new HashMap<>();
        for (Map.Entry<Item, Long> e : defs) values.put(e.getKey(), e.getValue());
        Inventory inv = p.getInventory();
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            Long v = values.get(st.getItem());
            if (v != null) { total += v * st.getCount(); inv.setItem(i, ItemStack.EMPTY); }
        }
        long change = total - price;
        for (Map.Entry<Item, Long> e : defs) {
            long n = change / e.getValue();
            change %= e.getValue();
            while (n > 0) {
                ItemStack st = new ItemStack(e.getKey());
                int give = (int) Math.min(n, st.getMaxStackSize());
                st.setCount(give);
                ItemHandlerHelper.giveItemToPlayer(p, st);
                n -= give;
            }
        }
        inv.setChanged();
    }

    private static int count(ServerPlayer p, Item item) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    private static void remove(ServerPlayer p, Item item, int amount) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(item)) continue;
            int take = Math.min(amount, st.getCount());
            st.shrink(take);
            amount -= take;
        }
        inv.setChanged();
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
                    o.barterItem = clip(o.barterItem, 100, "");
                    o.price = Math.max(0, Math.min(o.price, 1_000_000_000L));
                    o.barterCount = Math.max(0, Math.min(o.barterCount, 10_000));
                    o.xpLevels = Math.max(0, Math.min(o.xpLevels, 10_000));
                    o.realPriceCents = Math.max(0, Math.min(o.realPriceCents, 100_000));
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
                    if (!o.category.isBlank() && !d.categories.contains(o.category)) d.categories.add(o.category);
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
                case "save_settings" -> {
                    ShopData.Settings ns = ShopStore.GSON.fromJson(in.get("settings"), ShopData.Settings.class);
                    ns.title = clip(ns.title, 40, "Boutique");
                    ns.subtitle = clip(ns.subtitle, 80, "");
                    ns.opLevel = Math.max(0, Math.min(4, ns.opLevel));
                    if (ns.coins == null || ns.coins.isEmpty()) ns.coins = ShopData.Settings.defaultCoins();
                    if (ns.admins == null) ns.admins = new ArrayList<>();
                    // Secret PayPal : vide = garder l'actuel, "-" = l'effacer.
                    String sec = ns.paypalSecret == null ? "" : ns.paypalSecret.trim();
                    ns.paypalSecret = sec.isEmpty() ? d.settings.paypalSecret : "-".equals(sec) ? "" : sec;
                    ns.paypalClientId = clip(ns.paypalClientId, 200, "");
                    ns.currency = clip(ns.currency, 3, "EUR").toUpperCase(java.util.Locale.ROOT);
                    if (!ns.currency.matches("[A-Z]{3}")) ns.currency = "EUR";
                    ns.returnUrl = clip(ns.returnUrl, 300, "https://www.paypal.com/");
                    ns.supportTab = clip(ns.supportTab, 32, "Soutenir le Royaume");
                    d.settings = ns;
                    if (in.has("categories")) {
                        List<String> cats = new ArrayList<>();
                        for (JsonElement e : in.getAsJsonArray("categories")) {
                            String c = clip(e.getAsString(), 32, "");
                            if (!c.isBlank() && !cats.contains(c)) cats.add(c);
                        }
                        d.categories = cats;
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
