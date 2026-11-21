package fr.citizen.boutique.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import fr.citizen.boutique.CitizenBoutique;
import fr.citizen.boutique.net.Payloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Soutien en argent reel via PayPal (API Orders v2), sans webhook :
 * 1. le serveur cree la commande et envoie le lien PayPal au joueur ;
 * 2. toutes les 10 s, il interroge PayPal ; une commande approuvee est capturee (l'argent est encaisse) ;
 * 3. une fois la capture COMPLETED et le montant verifie, l'article est livre (ou a la reconnexion).
 * Le client ne fait que demander « je veux payer cet article » : prix, montant et livraison sont decides ici.
 * Toutes les commandes sont gardees dans config/citizenboutique/paypal.json.
 */
public final class PayPalService {
    private static final Duration EXPIRY = Duration.ofHours(3);
    private static final int MAX_PENDING_PER_PLAYER = 3;

    /** Une commande PayPal suivie par le serveur. */
    public static final class Order {
        public String orderId;
        public String player;
        public String playerName;
        public String offerId;
        public String offerName;
        public int cents;
        public String currency;
        public boolean sandbox;
        /** CREATED (attente du paiement), PAID (encaisse, a livrer), DELIVERED, FAILED, EXPIRED. */
        public String status = "CREATED";
        public long created;
        public long updated;
        public String link;
        public String captureId = "";
        public String note = "";
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CitizenBoutique-PayPal");
        t.setDaemon(true);
        return t;
    });

    private static List<Order> orders = new ArrayList<>();
    private static final Object TOKEN_LOCK = new Object();
    private static String token;
    private static long tokenUntil;
    private static String tokenKey = "";
    private static volatile boolean checking;
    private static int ticks;

    private PayPalService() { }

    private static Path file() { return ShopStore.dir().resolve("paypal.json"); }

    // ------------------------------------------------------------------ persistance

    public static synchronized void load() {
        try {
            if (Files.exists(file())) {
                List<Order> l = ShopStore.GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<List<Order>>() { }.getType());
                orders = l == null ? new ArrayList<>() : new ArrayList<>(l);
            }
        } catch (Exception e) {
            CitizenBoutique.LOGGER.error("Citizen Boutique : lecture de paypal.json impossible", e);
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("paypal.json.tmp");
            Files.writeString(tmp, ShopStore.GSON.toJson(orders), StandardCharsets.UTF_8);
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CitizenBoutique.LOGGER.error("Citizen Boutique : écriture de paypal.json impossible", e);
        }
    }

    public static synchronized List<Order> snapshot() { return new ArrayList<>(orders); }

    // ------------------------------------------------------------------ reglages

    public static boolean configured(ShopData.Settings s) {
        return s.paypalClientId != null && !s.paypalClientId.isBlank() && s.paypalSecret != null && !s.paypalSecret.isBlank();
    }

    private static String api(boolean sandbox) {
        return sandbox ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com";
    }

    public static String amount(int cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
    }

    // ------------------------------------------------------------------ achat (thread serveur)

    public static void start(ServerPlayer player, ShopData.Offer offer) {
        ShopData.Settings s = ShopStore.data().settings;
        if (!s.payPaypal || offer.realPriceCents <= 0) { error(player, "Cet article ne se paie pas par PayPal."); return; }
        if (!configured(s)) { error(player, "PayPal n'est pas encore configuré par les admins."); return; }
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        synchronized (PayPalService.class) {
            int pending = 0;
            for (Order o : orders) {
                if (!o.player.equals(id.toString()) || !"CREATED".equals(o.status)) continue;
                // Meme article demande recemment : on redonne le meme lien plutot que d'en creer un autre.
                if (o.offerId.equals(offer.id) && o.cents == offer.realPriceCents && o.sandbox == s.paypalSandbox
                        && now - o.created < Duration.ofMinutes(30).toMillis() && o.link != null) {
                    sendLink(player, o);
                    return;
                }
                pending++;
            }
            if (pending >= MAX_PENDING_PER_PLAYER) { error(player, "Vous avez déjà " + pending + " paiements en attente. Terminez-les ou attendez quelques minutes."); return; }
        }
        MinecraftServer server = player.getServer();
        String name = player.getGameProfile().getName();
        String offerId = offer.id, offerName = offer.name, currency = s.currency.trim().toUpperCase(Locale.ROOT);
        int cents = offer.realPriceCents;
        boolean sandbox = s.paypalSandbox;
        String clientId = s.paypalClientId.trim(), secret = s.paypalSecret.trim(), returnUrl = s.returnUrl.trim();
        PacketDistributor.sendToPlayer(player, new Payloads.Message("ok", "Préparation du paiement PayPal…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("intent", "CAPTURE");
                JsonObject unit = new JsonObject();
                unit.addProperty("reference_id", offerId);
                unit.addProperty("custom_id", id + "|" + offerId);
                unit.addProperty("description", clip("Soutien serveur Citizen : " + offerName, 120));
                JsonObject amount = new JsonObject();
                amount.addProperty("currency_code", currency);
                amount.addProperty("value", amount(cents));
                unit.add("amount", amount);
                JsonArray units = new JsonArray();
                units.add(unit);
                body.add("purchase_units", units);
                JsonObject ctx = new JsonObject();
                ctx.addProperty("brand_name", "Serveur Citizen");
                ctx.addProperty("locale", "fr-FR");
                ctx.addProperty("shipping_preference", "NO_SHIPPING");
                ctx.addProperty("user_action", "PAY_NOW");
                String ret = returnUrl.startsWith("http") ? returnUrl : "https://www.paypal.com/";
                ctx.addProperty("return_url", ret);
                ctx.addProperty("cancel_url", ret);
                JsonObject pp = new JsonObject();
                pp.add("experience_context", ctx);
                JsonObject src = new JsonObject();
                src.add("paypal", pp);
                body.add("payment_source", src);
                JsonObject res = call("POST", sandbox, clientId, secret, "/v2/checkout/orders", body.toString(), UUID.randomUUID().toString());
                Order o = new Order();
                o.orderId = res.get("id").getAsString();
                o.player = id.toString();
                o.playerName = name;
                o.offerId = offerId;
                o.offerName = offerName;
                o.cents = cents;
                o.currency = currency;
                o.sandbox = sandbox;
                o.created = o.updated = System.currentTimeMillis();
                for (JsonElement l : res.getAsJsonArray("links")) {
                    String rel = l.getAsJsonObject().get("rel").getAsString();
                    if ("payer-action".equals(rel) || "approve".equals(rel)) o.link = l.getAsJsonObject().get("href").getAsString();
                }
                if (o.link == null) throw new IllegalStateException("PayPal n'a pas renvoyé de lien de paiement");
                return o;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, WORKER).whenComplete((o, err) -> server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (err != null) {
                CitizenBoutique.LOGGER.warn("Citizen Boutique : création de commande PayPal impossible : {}", message(err));
                if (p != null) error(p, "PayPal indisponible pour le moment. Réessayez plus tard.");
                return;
            }
            synchronized (PayPalService.class) { orders.add(o); }
            save();
            CitizenBoutique.LOGGER.info("Citizen Boutique : commande PayPal {} créée pour {} ({} {} - {})", o.orderId, name, amount(cents), currency, offerName);
            if (p != null) sendLink(p, o);
        }));
    }

    private static void sendLink(ServerPlayer p, Order o) {
        PacketDistributor.sendToPlayer(p, new Payloads.Message("open_url", o.link));
    }

    private static void error(ServerPlayer p, String text) {
        PacketDistributor.sendToPlayer(p, new Payloads.Message("error", text));
    }

    // ------------------------------------------------------------------ verification periodique

    /** Appele a chaque tick serveur ; lance une verification toutes les 10 s s'il y a des commandes en cours. */
    public static void tick(MinecraftServer server) {
        if (++ticks < 200) return;
        ticks = 0;
        checkNow(server, null);
    }

    /** Verifie les commandes en attente (ou une seule si orderId est donne). */
    public static void checkNow(MinecraftServer server, String onlyOrder) {
        if (checking) return;
        ShopData.Settings s = ShopStore.data().settings;
        List<Order> todo = new ArrayList<>();
        synchronized (PayPalService.class) {
            for (Order o : orders)
                if (("CREATED".equals(o.status) || "PAID".equals(o.status) && o.captureId.isEmpty()) && (onlyOrder == null || onlyOrder.equals(o.orderId))) todo.add(o);
        }
        if (todo.isEmpty() || !configured(s)) return;
        checking = true;
        String clientId = s.paypalClientId.trim(), secret = s.paypalSecret.trim();
        CompletableFuture.runAsync(() -> {
            for (Order o : todo) {
                try { check(o, clientId, secret); }
                catch (Exception e) { CitizenBoutique.LOGGER.warn("Citizen Boutique : vérification PayPal {} : {}", o.orderId, e.getMessage()); }
            }
        }, WORKER).whenComplete((v, err) -> server.execute(() -> {
            checking = false;
            save();
            deliverPaid(server);
        }));
    }

    /** Thread PayPal : fait avancer une commande. */
    private static void check(Order o, String clientId, String secret) throws Exception {
        long now = System.currentTimeMillis();
        JsonObject res = call("GET", o.sandbox, clientId, secret, "/v2/checkout/orders/" + o.orderId, null, null);
        String status = res.get("status").getAsString();
        if ("APPROVED".equals(status)) {
            // Le joueur a valide sur PayPal : on encaisse. PayPal-Request-Id = identifiant stable -> jamais encaisse deux fois.
            res = call("POST", o.sandbox, clientId, secret, "/v2/checkout/orders/" + o.orderId + "/capture", "{}", "capture-" + o.orderId);
            status = res.get("status").getAsString();
        }
        if ("COMPLETED".equals(status)) {
            JsonObject capture = firstCapture(res);
            if (capture == null) return;
            String cs = capture.get("status").getAsString();
            JsonObject amt = capture.getAsJsonObject("amount");
            String value = amt.get("value").getAsString(), cur = amt.get("currency_code").getAsString();
            synchronized (PayPalService.class) {
                o.updated = now;
                if (!amount(o.cents).equals(value) || !o.currency.equalsIgnoreCase(cur)) {
                    o.status = "FAILED";
                    o.note = "Montant inattendu : " + value + " " + cur;
                    CitizenBoutique.LOGGER.error("Citizen Boutique : commande PayPal {} : montant {} {} au lieu de {} {} -> non livrée", o.orderId, value, cur, amount(o.cents), o.currency);
                } else if ("COMPLETED".equals(cs)) {
                    o.captureId = capture.get("id").getAsString();
                    if (!"DELIVERED".equals(o.status)) o.status = "PAID";
                } else if ("PENDING".equals(cs)) {
                    o.status = "PAID";      // encaissement en cours chez PayPal : on attend la confirmation
                    o.captureId = "";
                    o.note = "Paiement en attente chez PayPal";
                } else {
                    o.status = "FAILED";
                    o.note = "Capture " + cs;
                }
            }
        } else if ("VOIDED".equals(status) || now - o.created > EXPIRY.toMillis()) {
            synchronized (PayPalService.class) { o.status = "EXPIRED"; o.updated = now; }
        }
    }

    private static JsonObject firstCapture(JsonObject order) {
        try {
            JsonObject pu = order.getAsJsonArray("purchase_units").get(0).getAsJsonObject();
            return pu.getAsJsonObject("payments").getAsJsonArray("captures").get(0).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Thread serveur : livre les commandes encaissees dont le joueur est en ligne. */
    private static void deliverPaid(MinecraftServer server) {
        List<Order> toDeliver = new ArrayList<>();
        synchronized (PayPalService.class) {
            for (Order o : orders) if ("PAID".equals(o.status) && !o.captureId.isEmpty()) toDeliver.add(o);
        }
        for (Order o : toDeliver) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(o.player));
            if (p == null) continue;
            deliver(server, p, o);
        }
    }

    private static void deliver(MinecraftServer server, ServerPlayer p, Order o) {
        synchronized (PayPalService.class) {
            if (!"PAID".equals(o.status)) return;
            o.status = "DELIVERED";      // marque avant la livraison : jamais livre deux fois
            o.updated = System.currentTimeMillis();
        }
        save();
        ShopData.Offer offer = ShopStore.data().offers.stream().filter(x -> x.id.equals(o.offerId)).findFirst().orElse(null);
        String paid = amount(o.cents) + " " + o.currency + " (PayPal " + o.orderId + ")";
        if (offer == null) {
            CitizenBoutique.LOGGER.error("Citizen Boutique : commande PayPal {} payée mais article {} supprimé : à rembourser ou livrer à la main", o.orderId, o.offerName);
            p.sendSystemMessage(Component.literal("§6Merci pour votre soutien ! L'article « " + o.offerName + " » n'existe plus : un admin va vous contacter."));
            return;
        }
        ShopService.deliver(p, offer, ItemJson.decode(offer.item, p.registryAccess()), "paypal", paid, true);
        p.sendSystemMessage(Component.literal("§6✠ Paiement reçu, merci pour votre soutien au Royaume ! ✠"));
        if (ShopStore.data().settings.announceSupport)
            server.getPlayerList().broadcastSystemMessage(Component.literal("§6✠ §e" + p.getGameProfile().getName() + " §6soutient le Royaume ! Merci à lui ✠"), false);
    }

    /** A la connexion : livraison des soutiens payes pendant l'absence + verification immediate. */
    public static void onLogin(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        deliverPaid(server);
        checkNow(server, null);
    }

    /** Teste les identifiants (apres enregistrement des reglages) et previent l'admin. */
    public static void test(ServerPlayer admin) {
        ShopData.Settings s = ShopStore.data().settings;
        MinecraftServer server = admin.getServer();
        UUID id = admin.getUUID();
        boolean sandbox = s.paypalSandbox;
        String clientId = s.paypalClientId.trim(), secret = s.paypalSecret.trim();
        CompletableFuture.runAsync(() -> {
            synchronized (TOKEN_LOCK) { token = null; }
            try { token(sandbox, clientId, secret); }
            catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        }, WORKER).whenComplete((v, err) -> server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            if (err == null) PacketDistributor.sendToPlayer(p, new Payloads.Message("ok", "PayPal connecté (" + (sandbox ? "mode test" : "paiements réels") + ")."));
            else PacketDistributor.sendToPlayer(p, new Payloads.Message("error", "PayPal refuse la connexion : vérifiez Client ID, secret et mode test/réel."));
        }));
    }

    // ------------------------------------------------------------------ HTTP PayPal (thread PayPal uniquement)

    private static String token(boolean sandbox, String clientId, String secret) throws Exception {
      synchronized (TOKEN_LOCK) {
        String key = sandbox + "|" + clientId + "|" + secret.hashCode();
        if (token != null && key.equals(tokenKey) && System.currentTimeMillis() < tokenUntil) return token;
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(api(sandbox) + "/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("identifiants PayPal refusés (HTTP " + res.statusCode() + ")");
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        token = o.get("access_token").getAsString();
        tokenUntil = System.currentTimeMillis() + Math.max(60, o.get("expires_in").getAsLong() - 120) * 1000L;
        tokenKey = key;
        return token;
      }
    }

    private static JsonObject call(String method, boolean sandbox, String clientId, String secret, String path, String body, String requestId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api(sandbox) + path))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + token(sandbox, clientId, secret))
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation");
        if (requestId != null) b.header("PayPal-Request-Id", requestId);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) { synchronized (TOKEN_LOCK) { token = null; } }
        if (res.statusCode() / 100 != 2) {
            String detail = res.body() == null ? "" : res.body();
            if (detail.length() > 300) detail = detail.substring(0, 300);
            throw new IllegalStateException("PayPal HTTP " + res.statusCode() + " " + detail);
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** Message lisible d'une erreur (deballe CompletionException). */
    static String message(Throwable t) {
        while ((t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.toString() : m;
    }

    private static String clip(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
}
