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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reglages generaux (admins) : titre, moyens de paiement, categories, pieces, admins. Applique a chaud. */
public class SettingsScreen extends BaseScreen {
    private final Screen parent;
    private final ShopData.Settings draft;
    private String categoriesText;
    private boolean loaded;

    private EditBox title, subtitle, categories, coins, admins, opLevel;
    private Ui.Toggle payCoins, payBank, payBarter, payXp, treasury;

    public SettingsScreen(Screen parent) {
        super("Réglages");
        this.parent = parent;
        this.draft = ShopStore.GSON.fromJson(ShopStore.GSON.toJson(shop().settings), ShopData.Settings.class);
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
        if (loaded) read();
        loaded = true;
        layout(440, 290);
        int lx = px + 14, w = pw - 28;
        int y = py + 40;
        title = box(lx, y + 10, w / 2 - 4, "Titre", draft.title, 40);
        subtitle = box(lx + w / 2 + 4, y + 10, w / 2 - 4, "Sous-titre", draft.subtitle, 80);
        y += 30;
        int tw = (w - 16) / 5;
        payCoins = addRenderableWidget(new Ui.Toggle(lx, y + 10, tw, 16, "Pièces", draft.payCoins));
        payBank = addRenderableWidget(new Ui.Toggle(lx + (tw + 4), y + 10, tw, 16, "Banque", draft.payBank));
        payBarter = addRenderableWidget(new Ui.Toggle(lx + 2 * (tw + 4), y + 10, tw, 16, "Troc", draft.payBarter));
        payXp = addRenderableWidget(new Ui.Toggle(lx + 3 * (tw + 4), y + 10, tw, 16, "Expérience", draft.payXp));
        treasury = addRenderableWidget(new Ui.Toggle(lx + 4 * (tw + 4), y + 10, tw, 16, "→ Trésorerie", draft.revenueToTreasury));
        y += 32;
        categories = box(lx, y + 10, w, "Armes, Outils, Nourriture…", categoriesText, 600);
        y += 30;
        coins = box(lx, y + 10, w - 112, "citizeneconomy:coin_gold=100, …", coinsText(draft.coins), 600);
        addRenderableWidget(new Ui.MButton(lx + w - 108, y + 9, 108, 16, "+ Pièce en main", Ui.MButton.Style.GOLD, this::coinFromHand));
        y += 30;
        admins = box(lx, y + 10, w - 60, "Pseudo1, Pseudo2…", String.join(", ", draft.admins), 600);
        opLevel = box(lx + w - 50, y + 10, 50, "2", Integer.toString(draft.opLevel), 1);

        int by = py + ph - 24;
        addRenderableWidget(new Ui.MButton(px + pw - 180, by, 80, 16, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 94, by, 80, 16, "Enregistrer", Ui.MButton.Style.GREEN, this::save));
    }

    /** Ajoute l'objet tenu en main comme piece (valeur = 1 par defaut, a modifier dans le champ). */
    private void coinFromHand() {
        ItemStack hand = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        if (hand.isEmpty()) { ClientState.toast("Prenez la pièce en main.", false); return; }
        String id = ItemJson.id(hand.getItem());
        String cur = coins.getValue().trim();
        if (cur.contains(id + "=")) { ClientState.toast("Cette pièce est déjà dans la liste.", false); return; }
        coins.setValue(cur.isEmpty() ? id + "=1" : cur + ", " + id + "=1");
    }

    private void read() {
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

    private void save() {
        read();
        if (draft.coins.isEmpty() && draft.payCoins) { ClientState.toast("Indiquez au moins une pièce (id=valeur).", false); return; }
        for (String id : draft.coins.keySet())
            if (ItemJson.item(id).isEmpty()) { ClientState.toast("Pièce inconnue : " + id, false); return; }
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
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        int lx = px + 14, w = pw - 28, y = py + 40, c = 0xFFCDB580;
        g.drawString(font, "Titre de la boutique", lx, y, c, false);
        g.drawString(font, "Sous-titre", lx + w / 2 + 4, y, c, false); y += 30;
        g.drawString(font, "Moyens de paiement acceptés (et recettes PO versées au Trésor royal)", lx, y, c, false); y += 32;
        g.drawString(font, "Catégories (séparées par des virgules, dans l'ordre d'affichage)", lx, y, c, false); y += 30;
        g.drawString(font, "Pièces acceptées : objet=valeur en PO", lx, y, c, false); y += 30;
        g.drawString(font, "Admins de la boutique (en plus des OP)", lx, y, c, false);
        g.drawString(font, "Niv. OP", lx + w - 50, y, c, false);
        y += 34;
        String bank = ClientState.bankInstalled ? "Banque Citizen Economy : connectée" : "Banque Citizen Economy : non détectée (paiement banque masqué)";
        g.drawString(font, bank, lx, y, ClientState.bankInstalled ? 0xFF8FCB6A : 0xFFE0A060, false);
        g.drawString(font, "Tout est appliqué tout de suite, sans redémarrer le serveur.", lx, y + 12, 0xFF8C7A55, false);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
