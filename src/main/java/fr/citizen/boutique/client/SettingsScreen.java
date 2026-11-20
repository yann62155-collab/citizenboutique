package fr.citizen.boutique.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import fr.citizen.boutique.shop.ShopStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reglages (admins), en deux onglets : General et PayPal. Tout est applique a chaud. */
public class SettingsScreen extends BaseScreen {
    private final Screen parent;
    private final ShopData.Settings draft;
    private String categoriesText;
    private String secretTyped = "";
    private int tab;

    // onglet General
    private EditBox title, subtitle, categories, coins, admins, opLevel;
    private Ui.Toggle payCoins, payBank, payBarter, payXp, treasury;
    // onglet PayPal
    private EditBox clientId, secret, currency, returnUrl, supportTab;
    private Ui.Toggle payPaypal, sandbox, announce;

    public SettingsScreen(Screen parent) {
        super("Réglages");
        this.parent = parent;
        this.draft = ShopStore.GSON.fromJson(ShopStore.GSON.toJson(shop().settings), ShopData.Settings.class);
        if (draft.paypalClientId == null) draft.paypalClientId = "";
        this.categoriesText = String.join(", ", shop().categories);
    }

    @Override public boolean liveUpdate() { return false; }

    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        return b;
    }

    private static String coinsText(Map<String, Long> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(", ", parts);
    }

    @Override
    protected void init() {
        read();
        clearFieldRefs();
        layout(450, 290);
        int lx = px + 16, w = pw - 32;
        // onglets
        addRenderableWidget(new Ui.MButton(px + pw / 2 - 104, py + 38, 100, 15, "Général", tab == 0 ? Ui.MButton.Style.GOLD : Ui.MButton.Style.WOOD,
                () -> { if (tab != 0) { read(); tab = 0; rebuildWidgets(); } }));
        addRenderableWidget(new Ui.MButton(px + pw / 2 + 4, py + 38, 100, 15, "PayPal (€)", tab == 1 ? Ui.MButton.Style.ROYAL : Ui.MButton.Style.WOOD,
                () -> { if (tab != 1) { read(); tab = 1; rebuildWidgets(); } }));
        int y = py + 60;
        if (tab == 0) {
            title = box(lx, y + 10, w / 2 - 4, "Titre", draft.title, 40);
            subtitle = box(lx + w / 2 + 4, y + 10, w / 2 - 4, "Sous-titre", draft.subtitle, 80);
            y += 30;
            int tw = (w - 16) / 5;
            payCoins = addRenderableWidget(new Ui.Toggle(lx, y + 10, tw, 15, "Pièces", draft.payCoins));
            payBank = addRenderableWidget(new Ui.Toggle(lx + (tw + 4), y + 10, tw, 15, "Banque", draft.payBank));
            payBarter = addRenderableWidget(new Ui.Toggle(lx + 2 * (tw + 4), y + 10, tw, 15, "Troc", draft.payBarter));
            payXp = addRenderableWidget(new Ui.Toggle(lx + 3 * (tw + 4), y + 10, tw, 15, "Expérience", draft.payXp));
            treasury = addRenderableWidget(new Ui.Toggle(lx + 4 * (tw + 4), y + 10, tw, 15, "→ Trésor", draft.revenueToTreasury));
            y += 30;
            categories = box(lx, y + 10, w, "Armes, Outils, Nourriture…", categoriesText, 600);
            y += 30;
            coins = box(lx, y + 10, w - 112, "citizeneconomy:coin_gold=100, …", coinsText(draft.coins), 600);
            addRenderableWidget(new Ui.MButton(lx + w - 108, y + 9, 108, 15, "+ Pièce en main", Ui.MButton.Style.GOLD, this::coinFromHand));
            y += 30;
            admins = box(lx, y + 10, w - 60, "Pseudo1, Pseudo2…", String.join(", ", draft.admins), 600);
            opLevel = box(lx + w - 50, y + 10, 50, "2", Integer.toString(draft.opLevel), 1);
        } else {
            payPaypal = addRenderableWidget(new Ui.Toggle(lx, y + 2, w / 3 - 4, 15, "Activer PayPal", draft.payPaypal));
            sandbox = addRenderableWidget(new Ui.Toggle(lx + w / 3, y + 2, w / 3 - 4, 15, "Mode test (sandbox)", draft.paypalSandbox));
            announce = addRenderableWidget(new Ui.Toggle(lx + 2 * w / 3, y + 2, w / 3, 15, "Annoncer les dons", draft.announceSupport));
            y += 24;
            clientId = box(lx, y + 10, w, "Client ID de votre application PayPal", draft.paypalClientId, 200);
            y += 30;
            secret = box(lx, y + 10, w, ClientState.paypalSecretSet ? "•••••• enregistré (laisser vide pour le garder, « - » pour l'effacer)" : "Secret de votre application PayPal", secretTyped, 200);
            secret.setFormatter((text, pos) -> FormattedCharSequence.forward("-".equals(text) ? text : "•".repeat(text.length()), Style.EMPTY));
            y += 30;
            currency = box(lx, y + 10, 50, "EUR", draft.currency, 3);
            supportTab = box(lx + 58, y + 10, w - 58, "Soutenir le Royaume", draft.supportTab, 32);
            y += 30;
            returnUrl = box(lx, y + 10, w, "https://… (page affichée après le paiement)", draft.returnUrl, 300);
        }

        int by = py + ph - 26;
        addRenderableWidget(new Ui.MButton(px + pw - 182, by, 80, 15, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 96, by, 80, 15, "Enregistrer", Ui.MButton.Style.GREEN, this::save));
    }

    private void clearFieldRefs() {
        title = subtitle = categories = coins = admins = opLevel = clientId = secret = currency = returnUrl = supportTab = null;
        payCoins = payBank = payBarter = payXp = treasury = payPaypal = sandbox = announce = null;
    }

    private void coinFromHand() {
        ItemStack hand = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        if (hand.isEmpty()) { ClientState.toast("Prenez la pièce en main.", false); return; }
        String id = ItemJson.id(hand.getItem());
        String cur = coins.getValue().trim();
        if (cur.contains(id + "=")) { ClientState.toast("Cette pièce est déjà dans la liste.", false); return; }
        coins.setValue(cur.isEmpty() ? id + "=1" : cur + ", " + id + "=1");
    }

    /** Recopie les champs de l'onglet affiche dans le brouillon. */
    private void read() {
        if (title != null) {
            draft.title = title.getValue().trim();
            draft.subtitle = subtitle.getValue().trim();
            draft.payCoins = payCoins.value();
            draft.payBank = payBank.value();
            draft.payBarter = payBarter.value();
            draft.payXp = payXp.value();
            draft.revenueToTreasury = treasury.value();
            categoriesText = categories.getValue();
            Map<String, Long> m = new LinkedHashMap<>();
            for (String part : coins.getValue().split(",")) {
                String[] kv = part.trim().split("=");
                if (kv.length != 2) continue;
                try { long v = Long.parseLong(kv[1].trim()); if (v > 0) m.put(kv[0].trim(), v); } catch (NumberFormatException ignored) { }
            }
            draft.coins = m;
            List<String> a = new ArrayList<>();
            for (String s : admins.getValue().split(",")) if (!s.isBlank()) a.add(s.trim());
            draft.admins = a;
            try { draft.opLevel = Math.max(0, Math.min(4, Integer.parseInt(opLevel.getValue().trim()))); } catch (NumberFormatException ignored) { }
        }
        if (clientId != null) {
            draft.payPaypal = payPaypal.value();
            draft.paypalSandbox = sandbox.value();
            draft.announceSupport = announce.value();
            draft.paypalClientId = clientId.getValue().trim();
            secretTyped = secret.getValue().trim();
            draft.currency = currency.getValue().trim().toUpperCase(java.util.Locale.ROOT);
            draft.supportTab = supportTab.getValue().trim();
            draft.returnUrl = returnUrl.getValue().trim();
        }
    }

    private void save() {
        read();
        if (draft.coins.isEmpty() && draft.payCoins) { ClientState.toast("Indiquez au moins une pièce (id=valeur).", false); return; }
        for (String id : draft.coins.keySet())
            if (ItemJson.item(id).isEmpty()) { ClientState.toast("Pièce inconnue : " + id, false); return; }
        if (draft.payPaypal && draft.paypalClientId.isBlank()) { ClientState.toast("Renseignez le Client ID PayPal (onglet PayPal).", false); tab = 1; rebuildWidgets(); return; }
        if (draft.payPaypal && !ClientState.paypalSecretSet && secretTyped.isEmpty()) { ClientState.toast("Renseignez le secret PayPal (onglet PayPal).", false); tab = 1; rebuildWidgets(); return; }
        if (!draft.currency.matches("[A-Z]{3}")) { ClientState.toast("Devise invalide (ex : EUR).", false); return; }
        draft.paypalSecret = secretTyped;   // vide = le serveur garde l'actuel
        JsonObject o = new JsonObject();
        o.add("settings", ShopStore.GSON.toJsonTree(draft));
        JsonArray cats = new JsonArray();
        for (String c : categoriesText.split(",")) if (!c.isBlank()) cats.add(c.trim());
        o.add("categories", cats);
        String json = ShopStore.GSON.toJson(o);
        if (json.length() > 8_500) { ClientState.toast("Réglages trop longs.", false); return; }
        ClientSetup.admin("save_settings", json);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.title(g, font, "Réglages de la boutique", px + pw / 2, py + 12, 1.4f);
        Ui.divider(g, px + 10, py + 32, pw - 20);
        if (tab == 1) Ui.inset(g, px + 14, py + ph - 76, pw - 28, 44);
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        int lx = px + 16, w = pw - 32, y = py + 60, c = Ui.TEXT_DIM;
        if (tab == 0) {
            g.drawString(font, "Titre de la boutique", lx, y, c, false);
            g.drawString(font, "Sous-titre", lx + w / 2 + 4, y, c, false); y += 30;
            g.drawString(font, "Moyens de paiement en jeu (→ Trésor : recettes PO versées au Trésor royal)", lx, y, c, false); y += 30;
            g.drawString(font, "Catégories (séparées par des virgules, dans l'ordre)", lx, y, c, false); y += 30;
            g.drawString(font, "Pièces acceptées : objet=valeur en PO", lx, y, c, false); y += 30;
            g.drawString(font, "Admins de la boutique (en plus des OP)", lx, y, c, false);
            g.drawString(font, "Niv. OP", lx + w - 50, y, c, false);
            y += 32;
            String bank = ClientState.bankInstalled ? "Banque Citizen Economy : connectée" : "Banque Citizen Economy : non détectée";
            g.drawString(font, bank, lx, y, ClientState.bankInstalled ? 0xFF8FCB6A : 0xFFE0A060, false);
        } else {
            y += 24;
            g.drawString(font, "Client ID", lx, y, c, false); y += 30;
            g.drawString(font, "Secret" + (ClientState.paypalSecretSet ? "  (déjà enregistré sur le serveur, jamais affiché)" : ""), lx, y, c, false); y += 30;
            g.drawString(font, "Devise", lx, y, c, false);
            g.drawString(font, "Nom de l'onglet de soutien", lx + 58, y, c, false); y += 30;
            g.drawString(font, "Page de retour après paiement (facultatif)", lx, y, c, false);
            int iy = py + ph - 72;
            String state = ClientState.paypalReady ? "✠ PayPal actif : les joueurs peuvent soutenir le serveur." : "PayPal inactif.";
            g.drawString(font, state, px + 20, iy, ClientState.paypalReady ? 0xFF8FCB6A : 0xFFE0A060, false);
            Ui.wrapped(g, font, "Clés : developer.paypal.com → Apps & Credentials → votre application (onglet Sandbox pour tester, Live pour les vrais paiements). "
                    + "Pour chaque article, indiquez son prix « Soutien € » dans l'éditeur.", px + 20, iy + 11, pw - 40, c, 3);
        }
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
