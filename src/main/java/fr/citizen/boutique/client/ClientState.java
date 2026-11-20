package fr.citizen.boutique.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.citizen.boutique.shop.ShopData;
import fr.citizen.boutique.shop.ShopStore;

import java.util.HashMap;
import java.util.Map;

/** Derniere boutique recue du serveur (cote client). */
public final class ClientState {
    public static ShopData shop = new ShopData();
    public static boolean admin;
    public static boolean bankAvailable;
    public static boolean bankInstalled;
    public static boolean paypalReady;
    public static boolean paypalSecretSet;
    public static long bank = -1;
    public static long wallet;
    public static int xp;
    public static Map<String, Integer> bought = new HashMap<>();

    public static String message = "";
    public static boolean messageOk = true;
    public static long messageUntil;

    private ClientState() { }

    static void apply(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        admin = root.get("admin").getAsBoolean();
        bankAvailable = root.get("bankAvailable").getAsBoolean();
        bankInstalled = root.has("bankInstalled") && root.get("bankInstalled").getAsBoolean();
        bank = root.get("bank").getAsLong();
        paypalReady = root.has("paypalReady") && root.get("paypalReady").getAsBoolean();
        paypalSecretSet = root.has("paypalSecretSet") && root.get("paypalSecretSet").getAsBoolean();
        wallet = root.get("wallet").getAsLong();
        xp = root.get("xp").getAsInt();
        ShopData d = ShopStore.GSON.fromJson(root.get("shop"), ShopData.class);
        shop = d == null ? new ShopData() : d;
        if (shop.offers == null) shop.offers = new java.util.ArrayList<>();
        if (shop.categories == null) shop.categories = new java.util.ArrayList<>();
        if (shop.settings == null) shop.settings = new ShopData.Settings();
        Map<String, Integer> b = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("bought").entrySet()) b.put(e.getKey(), e.getValue().getAsInt());
        bought = b;
    }

    public static void toast(String text, boolean ok) {
        message = text;
        messageOk = ok;
        messageUntil = System.currentTimeMillis() + 4500;
    }

    public static ShopData.Offer offer(String id) {
        for (ShopData.Offer o : shop.offers) if (o.id.equals(id)) return o;
        return null;
    }
}
