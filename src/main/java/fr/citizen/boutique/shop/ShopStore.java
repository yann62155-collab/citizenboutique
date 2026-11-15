package fr.citizen.boutique.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.citizen.boutique.CitizenBoutique;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture / ecriture des fichiers de la boutique (dossier config/citizenboutique, accessible en FTP).
 * Toutes les modifications faites en jeu sont ecrites immediatement : aucun redemarrage necessaire.
 */
public final class ShopStore {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static ShopData data = new ShopData();
    /** UUID joueur -> (id article -> nombre d'achats). */
    private static Map<String, Map<String, Integer>> purchases = new HashMap<>();

    private ShopStore() { }

    public static Path dir() { return FMLPaths.CONFIGDIR.get().resolve(CitizenBoutique.MOD_ID); }
    public static Path imagesDir() { return dir().resolve("images"); }
    private static Path shopFile() { return dir().resolve("boutique.json"); }
    private static Path purchasesFile() { return dir().resolve("achats.json"); }
    private static Path logFile() { return dir().resolve("achats.log"); }

    public static synchronized ShopData data() { return data; }

    public static synchronized void load() {
        try {
            Files.createDirectories(imagesDir());
            if (Files.exists(shopFile())) {
                ShopData d = GSON.fromJson(Files.readString(shopFile(), StandardCharsets.UTF_8), ShopData.class);
                data = sanitize(d == null ? new ShopData() : d);
            } else {
                data = new ShopData();
                save();
            }
            if (Files.exists(purchasesFile())) {
                Map<String, Map<String, Integer>> p = GSON.fromJson(Files.readString(purchasesFile(), StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Map<String, Integer>>>() { }.getType());
                purchases = p == null ? new HashMap<>() : p;
            }
            CitizenBoutique.LOGGER.info("Citizen Boutique : {} article(s) chargé(s)", data.offers.size());
        } catch (Exception e) {
            CitizenBoutique.LOGGER.error("Citizen Boutique : lecture de boutique.json impossible (le fichier est conservé tel quel)", e);
        }
    }

    /** Remet des valeurs sures si le fichier a ete modifie a la main. */
    private static ShopData sanitize(ShopData d) {
        if (d.settings == null) d.settings = new ShopData.Settings();
        if (d.settings.coins == null || d.settings.coins.isEmpty()) d.settings.coins = ShopData.Settings.defaultCoins();
        if (d.settings.admins == null) d.settings.admins = new java.util.ArrayList<>();
        if (d.categories == null) d.categories = new java.util.ArrayList<>();
        if (d.offers == null) d.offers = new java.util.ArrayList<>();
        d.offers.removeIf(o -> o == null);
        for (ShopData.Offer o : d.offers) {
            if (o.id == null || o.id.isBlank()) o.id = newId();
            if (o.commands == null) o.commands = new java.util.ArrayList<>();
            if (o.name == null) o.name = "Article";
            if (o.description == null) o.description = "";
            if (o.category == null) o.category = "";
            if (o.image == null) o.image = "";
            if (o.barterItem == null) o.barterItem = "";
            o.price = Math.max(0, o.price);
        }
        return d;
    }

    public static synchronized void save() {
        writeAtomic(shopFile(), GSON.toJson(data));
    }

    public static synchronized void replace(ShopData d) {
        data = sanitize(d);
        save();
    }

    public static synchronized int purchasesOf(UUID player, String offerId) {
        Map<String, Integer> m = purchases.get(player.toString());
        return m == null ? 0 : m.getOrDefault(offerId, 0);
    }

    public static synchronized void addPurchase(UUID player, String playerName, ShopData.Offer offer, String method, String paid) {
        purchases.computeIfAbsent(player.toString(), k -> new HashMap<>()).merge(offer.id, 1, Integer::sum);
        writeAtomic(purchasesFile(), GSON.toJson(purchases));
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + " | " + playerName + " (" + player + ") | " + offer.name + " [" + offer.id + "] | " + method + " | " + paid + System.lineSeparator();
        try {
            Files.writeString(logFile(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : journal des achats non écrit : {}", e.toString());
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static void writeAtomic(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CitizenBoutique.LOGGER.error("Citizen Boutique : écriture de {} impossible", file, e);
        }
    }
}
