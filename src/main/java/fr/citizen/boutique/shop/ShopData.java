package fr.citizen.boutique.shop;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu de la boutique de soutien (argent reel via PayPal uniquement),
 * enregistre dans config/citizenboutique/boutique.json.
 * Les anciens champs (pieces, banque, troc...) d'un ancien fichier sont simplement ignores.
 */
public final class ShopData {

    public Settings settings = new Settings();
    public List<String> categories = new ArrayList<>(List.of("Packs & Grades", "Cosmétiques", "Dragonnerie", "Boutiques", "Pass Saison", "Offres spéciales"));
    /** Icone, illustration et texte de chaque categorie (cle = nom de la categorie). */
    public Map<String, Category> categoryInfo = defaultCategoryInfo();
    public List<Offer> offers = new ArrayList<>();

    /** Reglages generaux, modifiables par les admins dans le menu Reglages. */
    public static final class Settings {
        public String title = "Trésor du Royaume";
        public String subtitle = "Soutenez le serveur Citizen";
        /** Ruban sous le logo. */
        public String tagline = "Soutenez le serveur et obtenez des avantages exclusifs";
        /** Boutique ouverte aux joueurs (PayPal configure). */
        public boolean payPaypal = false;
        /** true = mode test PayPal (sandbox), false = vrais paiements. */
        public boolean paypalSandbox = true;
        public String paypalClientId = "";
        /** Jamais envoye aux clients (meme admins). */
        public String paypalSecret = "";
        public String currency = "EUR";
        /** Page ou PayPal renvoie le joueur apres paiement (la livraison ne depend pas de cette page). */
        public String returnUrl = "https://www.paypal.com/";
        /** Annonce dans le chat quand un joueur soutient le serveur. */
        public boolean announceSupport = true;
        /** Admins de la boutique en plus des OP de niveau 2+ (pseudo ou UUID). */
        public List<String> admins = new ArrayList<>();
    }

    /** Un article de soutien. */
    public static final class Offer {
        public String id = "";
        public String name = "Nouvel article";
        public String description = "";
        public String category = "";
        /** "server:fichier.png" (image envoyee sur le serveur) ou lien http(s). Vide = icone de l'objet. */
        public String image = "";
        /** Objet donne a l'achat (format ItemStack.CODEC) ; null = aucun objet. */
        public JsonElement item;
        /** Commandes lancees par la console a l'achat ({player} = pseudo, {uuid} = UUID). */
        public List<String> commands = new ArrayList<>();
        /** Avantages affiches avec une coche sur la carte (une ligne chacun). */
        public List<String> features = new ArrayList<>();
        /** Mise en avant « Le plus populaire » (carte argentee). */
        public boolean popular = false;
        /** Prix en centimes (499 = 4,99 €). */
        public int realPriceCents = 0;
        /** -1 = stock illimite. */
        public int stock = -1;
        /** 0 = achats illimites par joueur. */
        public int limitPerPlayer = 0;
        public boolean enabled = true;
    }

    /** Presentation d'une categorie. */
    public static final class Category {
        /** Objet affiche comme icone ("minecraft:golden_helmet"). */
        public String icon = "minecraft:chest";
        /** Grande illustration en haut ("server:..." ou lien) ; vide = paysage par defaut. */
        public String banner = "";
        public String description = "";

        public Category() { }
        Category(String icon, String description) { this.icon = icon; this.description = description; }
    }

    static Map<String, Category> defaultCategoryInfo() {
        Map<String, Category> m = new LinkedHashMap<>();
        m.put("Packs & Grades", new Category("minecraft:golden_helmet", "Débloquez des avantages, des cosmétiques et soutenez le développement de CITIZEN."));
        m.put("Cosmétiques", new Category("minecraft:chest", "Affichez votre style dans tout le royaume."));
        m.put("Dragonnerie", new Category("minecraft:dragon_head", "Tout pour les dompteurs de dragons."));
        m.put("Boutiques", new Category("minecraft:oak_door", "Ouvrez votre propre échoppe en ville."));
        m.put("Pass Saison", new Category("minecraft:name_tag", "Les récompenses de la saison en cours."));
        m.put("Offres spéciales", new Category("minecraft:cake", "Offres limitées dans le temps : ne les manquez pas !"));
        return m;
    }
}
