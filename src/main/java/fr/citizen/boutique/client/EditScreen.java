package fr.citizen.boutique.client;

import com.google.gson.JsonObject;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import fr.citizen.boutique.shop.ShopStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Creation / modification d'un article (admins). Tout est applique immediatement sur le serveur.
 * Image : glisser-deposer un fichier sur la fenetre, bouton « Parcourir… », ou coller un lien.
 */
public class EditScreen extends BaseScreen {
    private final Screen parent;
    private final ShopData.Offer source;
    /** Valeurs saisies, conservees si l'ecran est reconstruit (redimensionnement). */
    private final ShopData.Offer draft;
    private boolean useHand;
    private boolean clearItem;
    private String uploadStatus = "";

    private EditBox name, category, price, real, stock, limit, xp, barterItem, barterCount, image, commands;
    private MultiLineEditBox description;
    private Ui.Toggle coins, bank, enabled;

    public EditScreen(Screen parent, ShopData.Offer offer) {
        super("Article");
        this.parent = parent;
        this.source = offer;
        ShopData.Offer d = offer == null ? new ShopData.Offer() : ShopStore.GSON.fromJson(ShopStore.GSON.toJson(offer), ShopData.Offer.class);
        if (offer == null && !shop().categories.isEmpty()) d.category = shop().categories.get(0);
        if (d.commands == null) d.commands = new ArrayList<>();
        this.draft = d;
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

    @Override
    protected void init() {
        if (name != null) readFields();
        layout(460, 300);
        int lx = px + 14, lw = 150;
        int rx = px + 180, rw = pw - 194;
        int y = py + 40;

        // ---------- colonne gauche
        name = box(lx, y + 10, lw, "Nom de l'article", draft.name, 48); y += 28;
        category = box(lx, y + 10, lw - 22, "Catégorie", draft.category, 32);
        addRenderableWidget(new Ui.MButton(lx + lw - 20, y + 9, 20, 16, ">", Ui.MButton.Style.WOOD, this::nextCategory));
        y += 28;
        price = box(lx, y + 10, 48, "0", Long.toString(draft.price), 12);
        real = box(lx + 51, y + 10, 48, "0,00", draft.realPriceCents > 0 ? centsText(draft.realPriceCents) : "", 8);
        xp = box(lx + 102, y + 10, 48, "0", Integer.toString(draft.xpLevels), 6); y += 28;
        stock = box(lx, y + 10, 70, "-1", Integer.toString(draft.stock), 9);
        limit = box(lx + 80, y + 10, 70, "0", Integer.toString(draft.limitPerPlayer), 9); y += 28;
        barterItem = box(lx, y + 10, 104, "minecraft:diamond", draft.barterItem, 100);
        barterCount = box(lx + 108, y + 10, 42, "0", Integer.toString(draft.barterCount), 6); y += 18;
        addRenderableWidget(new Ui.MButton(lx, y + 9, lw, 14, "Troc = objet main gauche", Ui.MButton.Style.WOOD, () -> {
            ItemStack off = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getOffhandItem();
            if (off.isEmpty()) { ClientState.toast("Mettez l'objet de troc dans votre main gauche.", false); return; }
            barterItem.setValue(ItemJson.id(off.getItem()));
            barterCount.setValue(Integer.toString(off.getCount()));
        }));
        y += 28;
        coins = addRenderableWidget(new Ui.Toggle(lx, y, 73, 16, "Pièces", draft.allowCoins));
        bank = addRenderableWidget(new Ui.Toggle(lx + 77, y, 73, 16, "Banque", draft.allowBank));
        y += 19;
        enabled = addRenderableWidget(new Ui.Toggle(lx, y, lw, 16, "En vente (visible)", draft.enabled));

        // ---------- colonne droite
        int ry = py + 40;
        image = box(rx, ry + 10, rw - 76, "Lien http(s) ou glissez une image ici", draft.image, 400);
        addRenderableWidget(new Ui.MButton(rx + rw - 72, ry + 9, 72, 16, "Parcourir…", Ui.MButton.Style.GOLD, this::browse));
        ry += 28;
        int previewH = 54;
        previewY = ry + 2;
        ry += previewH + 4;
        description = new MultiLineEditBox(font, rx, ry + 10, rw, 54, Component.literal("Description visible par les joueurs"), Component.literal("Description"));
        description.setCharacterLimit(600);
        description.setValue(draft.description);
        addRenderableWidget(description);
        ry += 68;
        commands = box(rx, ry + 10, rw, "ex : give {player} minecraft:apple 3 ; say Merci {player}", String.join(" ; ", draft.commands), 1000);
        ry += 30;
        objY = ry;
        addRenderableWidget(new Ui.MButton(rx + 24, ry, (rw - 24) / 2 - 2, 16, useHand ? "[X] Objet en main" : "Prendre l'objet en main", Ui.MButton.Style.GREEN, () -> {
            useHand = !useHand; if (useHand) clearItem = false; rebuildWidgets();
        }));
        addRenderableWidget(new Ui.MButton(rx + 24 + (rw - 24) / 2 + 2, ry, (rw - 24) / 2 - 2, 16, clearItem ? "[X] Sans objet" : "Retirer l'objet", Ui.MButton.Style.RED, () -> {
            clearItem = !clearItem; if (clearItem) useHand = false; rebuildWidgets();
        }));

        // ---------- bas
        int by = py + ph - 24;
        addRenderableWidget(new Ui.MButton(px + pw - 180, by, 80, 16, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 94, by, 80, 16, "Enregistrer", Ui.MButton.Style.GREEN, this::save));
        setInitialFocus(name);
    }

    private int previewY, objY;
    private String previewKey = "";

    private void nextCategory() {
        List<String> cats = shop().categories;
        if (cats.isEmpty()) return;
        int i = cats.indexOf(category.getValue().trim());
        category.setValue(cats.get((i + 1) % cats.size()));
    }

    private static long num(EditBox b, long def) {
        try { return Long.parseLong(b.getValue().trim().replace(" ", "")); } catch (NumberFormatException e) { return def; }
    }

    private static String centsText(int c) { return String.format(java.util.Locale.ROOT, "%d,%02d", c / 100, c % 100); }

    /** "4,99" / "4.99" / "5" -> 499 centimes ; vide ou invalide -> 0. */
    private static int cents(String v) {
        v = v.trim().replace(" ", "").replace("€", "").replace(',', '.');
        if (v.isEmpty()) return 0;
        try { return (int) Math.max(0, Math.min(100_000, Math.round(Double.parseDouble(v) * 100))); }
        catch (NumberFormatException e) { return 0; }
    }

    private void readFields() {
        draft.name = name.getValue().trim();
        draft.category = category.getValue().trim();
        draft.price = Math.max(0, num(price, 0));
        draft.xpLevels = (int) Math.max(0, num(xp, 0));
        draft.realPriceCents = cents(real.getValue());
        draft.stock = (int) Math.max(-1, num(stock, -1));
        draft.limitPerPlayer = (int) Math.max(0, num(limit, 0));
        draft.barterItem = barterItem.getValue().trim();
        draft.barterCount = (int) Math.max(0, num(barterCount, 0));
        draft.image = image.getValue().trim();
        draft.description = description.getValue();
        draft.commands = new ArrayList<>(Arrays.stream(commands.getValue().split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        draft.allowCoins = coins.value();
        draft.allowBank = bank.value();
        draft.enabled = enabled.value();
    }

    private void save() {
        readFields();
        if (draft.name.isBlank()) { ClientState.toast("Donnez un nom à l'article.", false); return; }
        if (!draft.barterItem.isBlank() && ItemJson.item(draft.barterItem).isEmpty()) { ClientState.toast("Objet de troc inconnu : " + draft.barterItem, false); return; }
        boolean hasItem = useHand || (!clearItem && source != null && source.item != null);
        if (!hasItem && draft.commands.isEmpty()) { ClientState.toast("Prenez un objet en main (bouton vert) ou ajoutez une commande.", false); return; }
        ShopData.Offer send = ShopStore.GSON.fromJson(ShopStore.GSON.toJson(draft), ShopData.Offer.class);
        send.item = null; // l'objet est garde ou pris en main cote serveur
        JsonObject o = new JsonObject();
        o.add("offer", ShopStore.GSON.toJsonTree(send));
        o.addProperty("useHand", useHand);
        o.addProperty("clearItem", clearItem);
        String json = ShopStore.GSON.toJson(o);
        if (json.length() > 8_500) { ClientState.toast("Article trop long (description ou commandes).", false); return; }
        ClientSetup.admin("save_offer", json);
        Minecraft.getInstance().setScreen(parent);
    }

    // ------------------------------------------------------------------ images

    private void browse() {
        Thread t = new Thread(() -> {
            String path = FileDialog.openImage();
            if (path != null) Minecraft.getInstance().execute(() -> upload(Path.of(path)));
        }, "CitizenBoutique-dialog");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty()) upload(files.get(0));
    }

    private void upload(Path file) {
        uploadStatus = "Envoi de l'image…";
        ClientSetup.uploadImage(file, this);
    }

    /** Appele quand le serveur a enregistre l'image. */
    void onUploaded(String key) {
        uploadStatus = "Image envoyée sur le serveur.";
        draft.image = key;
        previewKey = key;
        if (image != null) image.setValue(key);
    }

    void onUploadFailed(String why) {
        uploadStatus = why;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.title(g, font, source == null ? "Nouvel article" : "Modifier l'article", px + pw / 2, py + 12, 1.4f);
        Ui.divider(g, px + 10, py + 32, pw - 20);
        g.fill(px + 172, py + 38, px + 173, py + ph - 30, Ui.GOLD_DIM);
        int rx = px + 180, rw = pw - 194;
        Ui.parchment(g, rx, previewY, rw, 54);
        // pas de telechargement a chaque lettre : l'apercu suit le champ quand on n'y tape plus
        if (image == null) previewKey = draft.image;
        else if (!image.isFocused()) previewKey = image.getValue().trim();
        String key = previewKey;
        ImageCache.Tex t = ImageCache.get(key);
        if (t != null) ImageCache.draw(g, t, rx + 3, previewY + 3, rw - 6, 48);
        else g.drawCenteredString(font, key.isBlank() ? "Aucune image (l'icône de l'objet sera affichée)" : ImageCache.failed(key) ? "Image illisible" : "Chargement…",
                rx + rw / 2, previewY + 23, Ui.INK_LIGHT);
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        int lx = px + 14, y = py + 40;
        int c = Ui.TEXT_DIM;
        g.drawString(font, "Nom", lx, y, c, false); y += 28;
        g.drawString(font, "Catégorie", lx, y, c, false); y += 28;
        g.drawString(font, "Prix PO", lx, y, c, false);
        g.drawString(font, "Soutien " + Ui.money(0, shop().settings.currency).replaceAll("[0-9, ]", ""), lx + 51, y, 0xFFE0707A, false);
        g.drawString(font, "XP niv.", lx + 102, y, c, false); y += 28;
        g.drawString(font, "Stock (-1 = ∞)", lx, y, c, false);
        g.drawString(font, "Limite/joueur", lx + 80, y, c, false); y += 28;
        g.drawString(font, "Troc : objet   × qté", lx, y, c, false);
        int rx = px + 180, rw = pw - 194;
        g.drawString(font, "Image", rx, py + 40, c, false);
        int ry = py + 40 + 28 + 58;
        g.drawString(font, "Description", rx, ry, c, false);
        g.drawString(font, "Commandes console (séparées par ;)", rx, ry + 68, c, false);
        // objet vendu
        Minecraft mc = Minecraft.getInstance();
        ItemStack shown = ItemStack.EMPTY;
        if (useHand && mc.player != null) shown = mc.player.getMainHandItem();
        else if (!clearItem && source != null && mc.level != null) shown = ItemJson.decode(source.item, mc.level.registryAccess());
        g.fill(rx, objY, rx + 20, objY + 18, 0x55000000);
        Ui.frame(g, rx, objY, 20, 18, Ui.GOLD_DIM);
        if (!shown.isEmpty()) {
            g.renderItem(shown, rx + 2, objY + 1);
            g.renderItemDecorations(font, shown, rx + 2, objY + 1);
            if (mx >= rx && mx < rx + 20 && my >= objY && my < objY + 18) g.renderTooltip(font, shown, mx, my);
        }
        g.drawString(font, "Objet vendu : " + (shown.isEmpty() ? "aucun" : shown.getHoverName().getString()), rx, objY + 21, c, false);
        if (!uploadStatus.isEmpty()) g.drawString(font, uploadStatus, px + 14, py + ph - 20, Ui.GOLD, false);
        else g.drawString(font, "Astuce : glissez une image sur la fenêtre", px + 14, py + ph - 20, 0xFF8C7A55, false);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
