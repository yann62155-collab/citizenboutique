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

/** Creation / modification d'un article (admins uniquement). Enregistre tout de suite sur le serveur. */
public class EditScreen extends BaseScreen implements ImageTarget {
    private final Screen parent;
    private final ShopData.Offer source;
    private final ShopData.Offer draft;
    private boolean useHand;
    private boolean clearItem;
    private String uploadStatus = "";
    private String previewKey;

    private EditBox name, category, real, stock, limit, image, commands;
    private MultiLineEditBox features, description;
    private Ui.Toggle enabled, popular;
    private int lx, lw, rx, rw, top, previewY, featY, descY, cmdY, objY;

    public EditScreen(Screen parent, ShopData.Offer offer) { this(parent, offer, ""); }

    public EditScreen(Screen parent, ShopData.Offer offer, String defaultCategory) {
        super("Article");
        this.parent = parent;
        this.source = offer;
        ShopData.Offer d = offer == null ? new ShopData.Offer() : ShopStore.GSON.fromJson(ShopStore.GSON.toJson(offer), ShopData.Offer.class);
        if (offer == null) d.category = defaultCategory != null && !defaultCategory.isEmpty() ? defaultCategory
                : shop().categories.isEmpty() ? "" : shop().categories.get(0);
        if (d.commands == null) d.commands = new ArrayList<>();
        if (d.features == null) d.features = new ArrayList<>();
        this.draft = d;
        this.previewKey = d.image == null ? "" : d.image;
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
        if (name != null) readFields();
        layout(540, 310);
        lx = px + 16; lw = 170;
        rx = px + 202; rw = pw - 218;
        top = py + 42;

        // ---------- colonne gauche
        int y = top;
        name = box(lx, y + 11, lw, "Pack Noble", draft.name, 48); y += 31;
        category = box(lx, y + 11, lw - 22, "Catégorie", draft.category, 32);
        addRenderableWidget(new Ui.MButton(lx + lw - 20, y + 11, 20, 15, ">", Ui.MButton.Style.WOOD, this::nextCategory));
        y += 31;
        real = box(lx, y + 11, 80, "9,99", draft.realPriceCents > 0 ? centsText(draft.realPriceCents) : "", 8);
        stock = box(lx + 90, y + 11, 80, "illimité", draft.stock < 0 ? "" : Integer.toString(draft.stock), 9);
        y += 31;
        limit = box(lx, y + 11, 80, "illimité", draft.limitPerPlayer <= 0 ? "" : Integer.toString(draft.limitPerPlayer), 9);
        enabled = addRenderableWidget(new Ui.Toggle(lx + 90, y + 11, 80, 15, "Visible", draft.enabled));
        y += 31;
        popular = addRenderableWidget(new Ui.Toggle(lx, y, lw, 16, "Le plus populaire (carte argent)", draft.popular));
        y += 26;
        objY = y;
        addRenderableWidget(new Ui.MButton(lx, y + 24, lw, 15, useHand ? "✔ Objet en main" : "Prendre l'objet en main", Ui.MButton.Style.GREEN, () -> {
            useHand = !useHand; if (useHand) clearItem = false; rebuildWidgets();
        }));
        addRenderableWidget(new Ui.MButton(lx, y + 42, lw, 15, clearItem ? "✔ Sans objet" : "Retirer l'objet", Ui.MButton.Style.RED, () -> {
            clearItem = !clearItem; if (clearItem) useHand = false; rebuildWidgets();
        }));

        // ---------- colonne droite
        int ry = top;
        image = box(rx, ry + 11, rw - 80, "Lien http(s) ou glissez une image", draft.image, 400);
        addRenderableWidget(new Ui.MButton(rx + rw - 76, ry + 11, 76, 15, "Parcourir…", Ui.MButton.Style.GOLD, this::browse));
        ry += 31;
        previewY = ry;
        ry += 46;
        featY = ry;
        features = new MultiLineEditBox(font, rx, ry + 11, rw, 50, Component.literal("Un avantage par ligne (6 max)"), Component.literal("Avantages"));
        features.setCharacterLimit(260);
        features.setValue(String.join("\n", draft.features));
        addRenderableWidget(features);
        ry += 66;
        descY = ry;
        description = new MultiLineEditBox(font, rx, ry + 11, rw, 36, Component.literal("Texte affiché sur la fiche détaillée"), Component.literal("Description"));
        description.setCharacterLimit(600);
        description.setValue(draft.description);
        addRenderableWidget(description);
        ry += 52;
        cmdY = ry;
        commands = box(rx, ry + 11, rw, "ex : lp user {player} parent add noble ; say Merci {player} !", String.join(" ; ", draft.commands), 1000);

        int by = py + ph - 26;
        addRenderableWidget(new Ui.MButton(px + pw - 190, by, 84, 17, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 100, by, 86, 17, "Enregistrer", Ui.MButton.Style.GOLD, this::save));
        setInitialFocus(name);
    }

    private void nextCategory() {
        List<String> cats = shop().categories;
        if (cats.isEmpty()) return;
        int i = cats.indexOf(category.getValue().trim());
        category.setValue(cats.get((i + 1) % cats.size()));
    }

    private static String centsText(int c) { return String.format(java.util.Locale.ROOT, "%d,%02d", c / 100, c % 100); }

    private static int cents(String v) {
        v = v.trim().replace(" ", "").replace("€", "").replace(',', '.');
        if (v.isEmpty()) return 0;
        try { return (int) Math.max(0, Math.min(100_000, Math.round(Double.parseDouble(v) * 100))); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int intOr(EditBox b, int def) {
        String v = b.getValue().trim();
        if (v.isEmpty()) return def;
        try { return Integer.parseInt(v.replace(" ", "")); } catch (NumberFormatException e) { return def; }
    }

    private void readFields() {
        draft.name = name.getValue().trim();
        draft.category = category.getValue().trim();
        draft.realPriceCents = cents(real.getValue());
        draft.stock = Math.max(-1, intOr(stock, -1));
        draft.limitPerPlayer = Math.max(0, intOr(limit, 0));
        draft.image = image.getValue().trim();
        draft.features = new ArrayList<>(Arrays.stream(features.getValue().split("\n")).map(String::trim).filter(s -> !s.isEmpty()).limit(6).toList());
        draft.description = description.getValue();
        draft.commands = new ArrayList<>(Arrays.stream(commands.getValue().split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        draft.enabled = enabled.value();
        draft.popular = popular.value();
    }

    private void save() {
        readFields();
        if (draft.name.isBlank()) { ClientState.toast("Donnez un nom à l'article.", false); return; }
        if (draft.realPriceCents < 50) { ClientState.toast("Indiquez un prix d'au moins 0,50 €.", false); return; }
        boolean hasItem = useHand || (!clearItem && source != null && source.item != null);
        if (!hasItem && draft.commands.isEmpty()) { ClientState.toast("Prenez un objet en main ou ajoutez une commande.", false); return; }
        ShopData.Offer send = ShopStore.GSON.fromJson(ShopStore.GSON.toJson(draft), ShopData.Offer.class);
        send.item = null;
        JsonObject o = new JsonObject();
        o.add("offer", ShopStore.GSON.toJsonTree(send));
        o.addProperty("useHand", useHand);
        o.addProperty("clearItem", clearItem);
        String json = ShopStore.GSON.toJson(o);
        if (json.length() > 8_500) { ClientState.toast("Article trop long.", false); return; }
        ClientSetup.admin("save_offer", json);
        Minecraft.getInstance().setScreen(parent);
    }

    private void browse() {
        Thread t = new Thread(() -> {
            String path = FileDialog.openImage();
            if (path != null) Minecraft.getInstance().execute(() -> upload(Path.of(path)));
        }, "CitizenBoutique-dialog");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onFilesDrop(List<Path> files) { if (!files.isEmpty()) upload(files.get(0)); }

    private void upload(Path file) {
        uploadStatus = "Envoi de l'image…";
        ClientSetup.uploadImage(file, this);
    }

    @Override
    public void onUploaded(String key) {
        uploadStatus = "✔ Image enregistrée sur le serveur.";
        draft.image = key;
        previewKey = key;
        if (image != null) image.setValue(key);
    }

    @Override
    public void onUploadFailed(String why) { uploadStatus = why; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        screenTitle(g, source == null ? "Nouvel article" : "Modifier l'article");
        g.fill(px + 193, top, px + 194, py + ph - 34, 0x886E4A1C);
        if (image != null && !image.isFocused()) previewKey = image.getValue().trim();
        g.fill(rx, previewY, rx + rw, previewY + 40, 0xFF120D09);
        Ui.goldBevel(g, rx, previewY, rw, 40);
        ImageCache.Tex t = ImageCache.get(previewKey);
        if (t != null) ImageCache.draw(g, t, rx + 3, previewY + 3, rw - 6, 34);
        else {
            String m = previewKey.isBlank() ? "Pas d'image : l'icône de l'objet sera affichée" : ImageCache.failed(previewKey) ? "Image illisible" : "Chargement…";
            Ui.textCentered(g, Ui.ellipsis(m, rw - 10), rx + rw / 2, previewY + 16, Ui.CREAM_DIM, true);
        }
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        int c = Ui.CREAM, y = top;
        Ui.text(g, "Nom", lx, y, c, true); y += 31;
        Ui.text(g, "Catégorie", lx, y, c, true); y += 31;
        Ui.text(g, "Prix (" + shop().settings.currency + ")", lx, y, Ui.GOLD_TEXT, true);
        Ui.text(g, "Stock", lx + 90, y, c, true); y += 31;
        Ui.text(g, "Limite / joueur", lx, y, c, true);

        Ui.text(g, "Objet donné", lx, objY + 4, c, true);
        Minecraft mc = Minecraft.getInstance();
        ItemStack shown = ItemStack.EMPTY;
        if (useHand && mc.player != null) shown = mc.player.getMainHandItem();
        else if (!clearItem && source != null && mc.level != null) shown = ItemJson.decode(source.item, mc.level.registryAccess());
        int ox = lx + 80;
        g.fill(ox, objY, ox + 20, objY + 20, 0xFF120D09);
        Ui.goldBevel(g, ox, objY, 20, 20);
        if (!shown.isEmpty()) {
            g.renderItem(shown, ox + 2, objY + 2);
            g.renderItemDecorations(font, shown, ox + 2, objY + 2);
            if (mx >= ox && mx < ox + 20 && my >= objY && my < objY + 20) g.renderTooltip(font, shown, mx, my);
        } else Ui.text(g, "aucun", ox + 26, objY + 5, Ui.CREAM_DIM, true);

        Ui.text(g, "Image (emblème de la carte)", rx, top, c, true);
        Ui.text(g, "Avantages — un par ligne, avec une coche", rx, featY, Ui.GOLD_TEXT, true);
        Ui.text(g, "Description (fiche détaillée)", rx, descY, c, true);
        Ui.text(g, "Commandes console à l'achat (séparées par ;)", rx, cmdY, c, true);
        String bottom = !uploadStatus.isEmpty() ? uploadStatus : "Astuce : glissez une image sur la fenêtre";
        Ui.text(g, Ui.ellipsis(bottom, pw - 230), px + 18, py + ph - 22, uploadStatus.isEmpty() ? Ui.CREAM_DIM : Ui.GOLD_TEXT, true);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
