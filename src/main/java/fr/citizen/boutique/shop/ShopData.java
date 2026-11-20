package fr.citizen.boutique.shop;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu de la boutique, enregistre dans config/citizenboutique/boutique.json.
 * Classes simples lues/ecrites par Gson : tout champ absent du fichier prend sa valeur par defaut.
 */
public final class ShopData {

    public Settings settings = new Settings();
    public List<String> categories = new ArrayList<>(List.of("Équipement", "Outils", "Nourriture", "Divers"));
    public List<Offer> offers = new ArrayList<>();

    /** Reglages generaux, modifiables dans le menu Reglages (admin). */
    public static final class Settings {
        public String title = "Échoppe du Royaume";
        public String subtitle = "Marchandises de qualité, prix honnêtes";
        /** Moyens de paiement actives globalement. */
        public boolean payCoins = true;
        public boolean payBank = true;
        public boolean payBarter = true;
        public boolean payXp = false;
        /** Les PO depensees (pieces ou banque) sont versees a la tresorerie royale de Citizen Economy. */
        public boolean revenueToTreasury = true;
        /** Pieces acceptees : identifiant d'objet -> valeur en PO. */
        public Map<String, Long> coins = defaultCoins();
        /** Admins de la boutique en plus des OP (pseudo ou UUID). */
        public List<String> admins = new ArrayList<>();
        /** Niveau d'OP qui donne l'acces admin (2 = OP standard). */
        public int opLevel = 2;

        // ---- Soutien en argent reel (PayPal)
        public boolean payPaypal = false;
        /** true = mode test PayPal (sandbox), false = vrais paiements. */
        public boolean paypalSandbox = true;
        public String paypalClientId = "";
        /** Jamais envoye aux clients (meme admins). */
        public String paypalSecret = "";
        public String currency = "EUR";
        /** Page ou PayPal renvoie le joueur apres paiement (la livraison ne depend pas de cette page). */
        public String returnUrl = "https://www.paypal.com/";
        /** Nom de l'onglet qui regroupe les articles payables en argent reel. */
        public String supportTab = "Soutenir le Royaume";
        /** Annonce dans le chat quand un joueur soutient le serveur. */
        public boolean announceSupport = true;

        static Map<String, Long> defaultCoins() {
            Map<String, Long> m = new LinkedHashMap<>();
            m.put("citizeneconomy:coin_platinum", 1000L);
            m.put("citizeneconomy:coin_gold", 100L);
            m.put("citizeneconomy:coin_silver", 10L);
            m.put("citizeneconomy:coin_bronze", 1L);
            return m;
        }
    }

    /** Un article en vente. */
    public static final class Offer {
        public String id = "";
        public String name = "Nouvel article";
        public String description = "";
        public String category = "Divers";
        /** "server:fichier.png" (image envoyee sur le serveur) ou lien http(s) vers une image. Vide = icone de l'objet. */
        public String image = "";
        /** Objet donne a l'achat (format ItemStack.CODEC) ; null = aucun objet. */
        public JsonElement item;
        /** Commandes lancees par la console a l'achat ({player} = pseudo, {uuid} = UUID). */
        public List<String> commands = new ArrayList<>();
        /** Prix en PO (pieces ou banque). 0 = pas de paiement en PO. */
        public long price = 10;
        public boolean allowCoins = true;
        public boolean allowBank = true;
        /** Troc : identifiant d'objet demande + quantite (vide/0 = pas de troc). */
        public String barterItem = "";
        public int barterCount = 0;
        /** Niveaux d'experience demandes (0 = pas de paiement en XP). */
        public int xpLevels = 0;
        /** Prix en argent reel, en centimes (499 = 4,99). 0 = pas de paiement PayPal. */
        public int realPriceCents = 0;
        /** -1 = stock illimite. */
        public int stock = -1;
        /** 0 = achats illimites par joueur. */
        public int limitPerPlayer = 0;
        public boolean enabled = true;
    }
}
