package fr.citizen.boutique.client;

import com.google.gson.JsonObject;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;

/** Creation / modification d'une categorie (admins) : nom, icone, illustration, texte. */
public class CategoryScreen extends BaseScreen implements ImageTarget {
    private final Screen parent;
    private final String original;
    private String nameV, iconV, bannerV, descV, previewKey, status = "";
    private boolean confirmDelete;
    private EditBox name, icon, banner, desc;
    private int fx, fw, top, previewY;

    public CategoryScreen(Screen parent, String category) {
        super("Catégorie");
        this.parent = parent;
        this.original = category;
        ShopData.Category info = category == null ? null : shop().categoryInfo.get(category);
        nameV = category == null ? "" : category;
        iconV = info == null ? "minecraft:chest" : info.icon;
        bannerV = info == null ? "" : info.banner;
        descV = info == null ? "" : info.description;
        previewKey = bannerV;
    }

    @Override public boolean liveUpdate() { return false; }

    private EditBox box(int x, int y, int w, String hint, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 15, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        if (name != null) read();
        layout(460, 270);
        fx = px + 18; fw = pw - 36; top = py + 42;
        int y = top;
        name = box(fx, y + 11, fw / 2 - 6, "Packs & Grades", nameV, 32);
        icon = box(fx + fw / 2 + 6, y + 11, fw / 2 - 6 - 104, "minecraft:golden_helmet", iconV, 100);
        addRenderableWidget(new Ui.MButton(fx + fw - 100, y + 11, 100, 15, "Objet en main", Ui.MButton.Style.GREEN, () -> {
            ItemStack h = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
            if (h.isEmpty()) { ClientState.toast("Prenez l'objet-icône en main.", false); return; }
            icon.setValue(ItemJson.id(h.getItem()));
        }));
        y += 31;
        banner = box(fx, y + 11, fw - 80, "Lien http(s) ou glissez une image (format bandeau)", bannerV, 400);
        addRenderableWidget(new Ui.MButton(fx + fw - 76, y + 11, 76, 15, "Parcourir…", Ui.MButton.Style.GOLD, this::browse));
        y += 31;
        previewY = y;
        y += 62;
        desc = box(fx, y + 11, fw, "Débloquez des avantages et soutenez le serveur.", descV, 200);

        int by = py + ph - 26;
        if (original != null) {
            addRenderableWidget(new Ui.MButton(fx, by, 20, 17, "▲", Ui.MButton.Style.WOOD, () -> move(-1)));
            addRenderableWidget(new Ui.MButton(fx + 22, by, 20, 17, "▼", Ui.MButton.Style.WOOD, () -> move(1)));
            addRenderableWidget(new Ui.MButton(fx + 46, by, 76, 17, confirmDelete ? "Sûr ?" : "Supprimer", Ui.MButton.Style.RED, () -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                JsonObject o = new JsonObject();
                o.addProperty("name", original);
                ClientSetup.admin("delete_category", o.toString());
                Minecraft.getInstance().setScreen(parent);
            }));
        }
        addRenderableWidget(new Ui.MButton(px + pw - 190, by, 84, 17, "Annuler", Ui.MButton.Style.WOOD, this::onClose));
        addRenderableWidget(new Ui.MButton(px + pw - 100, by, 86, 17, "Enregistrer", Ui.MButton.Style.GOLD, this::save));
        setInitialFocus(name);
    }

    private void move(int dir) {
        JsonObject o = new JsonObject();
        o.addProperty("name", original);
        o.addProperty("dir", dir);
        ClientSetup.admin("move_category", o.toString());
    }

    private void read() {
        nameV = name.getValue().trim();
        iconV = icon.getValue().trim();
        bannerV = banner.getValue().trim();
        descV = desc.getValue().trim();
    }

    private void save() {
        read();
        if (nameV.isBlank()) { ClientState.toast("Donnez un nom à la catégorie.", false); return; }
        if (ItemJson.item(iconV).isEmpty()) { ClientState.toast("Icône inconnue : " + iconV, false); return; }
        JsonObject o = new JsonObject();
        if (original != null) o.addProperty("old", original);
        o.addProperty("name", nameV);
        o.addProperty("icon", iconV);
        o.addProperty("banner", bannerV);
        o.addProperty("description", descV);
        ClientSetup.admin("save_category", o.toString());
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

    private void upload(Path p) {
        status = "Envoi de l'image…";
        ClientSetup.uploadImage(p, this);
    }

    @Override
    public void onUploaded(String key) {
        status = "✔ Image enregistrée sur le serveur.";
        previewKey = key;
        if (banner != null) banner.setValue(key);
    }

    @Override
    public void onUploadFailed(String why) { status = why; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        screenTitle(g, original == null ? "Nouvelle catégorie" : "Modifier la catégorie");
        if (banner != null && !banner.isFocused()) previewKey = banner.getValue().trim();
        g.fill(fx - 1, previewY - 1, fx + fw + 1, previewY + 57, 0xFF000000);
        ImageCache.Tex t = ImageCache.get(previewKey);
        if (t != null) ImageCache.drawCover(g, t, fx, previewY, fw, 56);
        else Ui.blitSmooth(g, Ui.HERO, fx, previewY, fw, 56, 800, 180);
        g.fillGradient(fx, previewY + 22, fx + fw, previewY + 56, 0x00000000, 0xC8000000);
        Ui.goldBevel(g, fx - 1, previewY - 1, fw + 2, 58);
        String n = name == null ? nameV : name.getValue();
        Ui.titleText(g, Ui.ellipsisTitle(n.isBlank() ? "Aperçu" : n, (int) ((fw - 20) / 1.5f)), fx + 10, previewY + 24, 0xFFFFE6A0, 1.5f, false, true);
        String d = desc == null ? descV : desc.getValue();
        Ui.text(g, Ui.ellipsis(d, fw - 20), fx + 10, previewY + 43, Ui.CREAM, true);
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        Ui.text(g, "Nom de la catégorie", fx, top, Ui.CREAM, true);
        Ui.text(g, "Icône (objet)", fx + fw / 2 + 6, top, Ui.CREAM, true);
        ItemJson.item(icon == null ? iconV : icon.getValue()).ifPresent(i -> g.renderItem(new ItemStack(i), fx + fw / 2 + 80, top - 5));
        Ui.text(g, "Illustration du bandeau", fx, top + 31, Ui.CREAM, true);
        Ui.text(g, "Texte du bandeau", fx, previewY + 62, Ui.CREAM, true);
        String b = status.isEmpty() ? "Astuce : glissez une image sur la fenêtre" : status;
        Ui.text(g, Ui.ellipsis(b, pw - 340), fx + (original != null ? 128 : 0), py + ph - 22, status.isEmpty() ? Ui.CREAM_DIM : Ui.GOLD_TEXT, true);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
