package fr.citizen.boutique.client;

import com.google.gson.JsonObject;
import fr.citizen.boutique.shop.ShopData;
import fr.citizen.boutique.shop.ShopStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Reglages (admins) : textes et admins a gauche, PayPal a droite. Applique a chaud. */
public class SettingsScreen extends BaseScreen {
    private final Screen parent;
    private final ShopData.Settings draft;
    private String secretTyped = "";
    private EditBox tagline, admins, clientId, secret, currency, returnUrl;
    private Ui.Toggle open, sandbox, announce;
    private int lx, lw, rx, rw, top;

    public SettingsScreen(Screen parent) {
        super("Réglages");
        this.parent = parent;
        this.draft = ShopStore.GSON.fromJson(ShopStore.GSON.toJson(shop().settings), ShopData.Settings.class);
        if (draft.paypalClientId == null) draft.paypalClientId = "";
        if (draft.admins == null) draft.admins = new ArrayList<>();
    }

    @Override public boolean liveUpdate() { return false; }

    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 15, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        if (tagline != null) read();
        layout(540, 290);
        top = py + 44;
        lx = px + 18; lw = 200;
        rx = px + 238; rw = pw - 256;
        int y = top;
        tagline = box(lx, y + 11, lw, "Soutenez le serveur…", draft.tagline, 80); y += 31;
        admins = box(lx, y + 11, lw, "Pseudo1, Pseudo2…", String.join(", ", draft.admins), 600);

        int ry = top;
        open = addRenderableWidget(new Ui.Toggle(rx, ry, rw / 3 - 3, 17, "Boutique ouverte", draft.payPaypal));
        sandbox = addRenderableWidget(new Ui.Toggle(rx + rw / 3, ry, rw / 3 - 3, 17, "Mode test", draft.paypalSandbox));
        announce = addRenderableWidget(new Ui.Toggle(rx + 2 * rw / 3, ry, rw - 2 * rw / 3, 17, "Annoncer", draft.announceSupport));
        ry += 24;
        clientId = box(rx, ry + 11, rw, "Client ID de l'application PayPal", draft.paypalClientId, 200); ry += 31;
        secret = box(rx, ry + 11, rw, ClientState.paypalSecretSet ? "•••••• enregistré (vide = garder, « - » = effacer)" : "Secret de l'application PayPal", secretTyped, 200);
        secret.setFormatter((text, pos) -> FormattedCharSequence.forward("-".equals(text) ? text : "•".repeat(text.length()), Style.EMPTY));
        ry += 31;
        currency = box(rx, ry + 11, 46, "EUR", draft.currency, 3);
        returnUrl = box(rx + 54, ry + 11, rw - 54, "https://… (facultatif)", draft.returnUrl, 300);

        int by = py + ph - 26;
        addRenderableWidget(new Ui.MButton(px + pw - 190, by, 84, 17, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 100, by, 86, 17, "Enregistrer", Ui.MButton.Style.GOLD, this::save));
    }

    private void read() {
        draft.tagline = tagline.getValue().trim();
        List<String> a = new ArrayList<>();
        for (String s : admins.getValue().split(",")) if (!s.isBlank()) a.add(s.trim());
        draft.admins = a;
        draft.payPaypal = open.value();
        draft.paypalSandbox = sandbox.value();
        draft.announceSupport = announce.value();
        draft.paypalClientId = clientId.getValue().trim();
        secretTyped = secret.getValue().trim();
        draft.currency = currency.getValue().trim().toUpperCase(java.util.Locale.ROOT);
        draft.returnUrl = returnUrl.getValue().trim();
    }

    private void save() {
        read();
        if (draft.payPaypal && draft.paypalClientId.isBlank()) { ClientState.toast("Renseignez le Client ID PayPal.", false); return; }
        if (draft.payPaypal && !ClientState.paypalSecretSet && secretTyped.isEmpty()) { ClientState.toast("Renseignez le secret PayPal.", false); return; }
        if (!draft.currency.matches("[A-Z]{3}")) { ClientState.toast("Devise invalide (ex : EUR).", false); return; }
        draft.paypalSecret = secretTyped;
        JsonObject o = new JsonObject();
        o.add("settings", ShopStore.GSON.toJsonTree(draft));
        ClientSetup.admin("save_settings", ShopStore.GSON.toJson(o));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        screenTitle(g, "Réglages de la boutique");
        g.fill(px + 228, top, px + 229, py + ph - 34, 0x886E4A1C);
        Ui.column(g, lx, top + 70, lw, py + ph - 36 - (top + 70));
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        int c = Ui.CREAM;
        Ui.text(g, "Texte du ruban sous le logo", lx, top, c, true);
        Ui.text(g, "Admins de la boutique (en plus des OP)", lx, top + 31, c, true);
        Ui.wrapped(g, "Les catégories se gèrent depuis la boutique : « + Catég. » et « Modifier ». "
                + "Seuls les OP et les admins listés ici peuvent ajouter ou modifier des articles.", lx + 6, top + 76, lw - 12, Ui.CREAM_DIM, 6, true);
        int ry = top + 24;
        Ui.text(g, "Client ID PayPal", rx, ry, c, true); ry += 31;
        Ui.text(g, "Secret PayPal" + (ClientState.paypalSecretSet ? " (enregistré, jamais affiché)" : ""), rx, ry, c, true); ry += 31;
        Ui.text(g, "Devise", rx, ry, c, true);
        Ui.text(g, "Page après paiement", rx + 54, ry, c, true);
        ry += 34;
        String state = ClientState.paypalReady ? "✔ PayPal actif : les joueurs peuvent soutenir le serveur." : "PayPal inactif.";
        Ui.text(g, Ui.ellipsis(state, rw), rx, ry, ClientState.paypalReady ? 0xFF8FDB6A : 0xFFFFB070, true);
        Ui.wrapped(g, "Clés : developer.paypal.com → Apps & Credentials (Sandbox pour tester, Live pour les vrais paiements).",
                rx, ry + 13, rw, Ui.CREAM_DIM, 3, true);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
