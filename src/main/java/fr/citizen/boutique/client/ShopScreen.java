package fr.citizen.boutique.client;

import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ecran principal de la boutique (touche F6) : categories a gauche, etals a droite. */
public class ShopScreen extends BaseScreen {
    private static String category = "";
    private static String search = "";
    private static int page;

    private EditBox searchBox;
    private int gx, gy, gw, gh;

    public ShopScreen() { super("Boutique"); }

    @Override
    protected void init() {
        layout(460, 290);
        ShopData d = shop();
        if (!category.isEmpty() && !d.categories.contains(category)) category = "";

        // --- categories
        int cx = px + 10, cy = py + 46, cw = 96;
        addRenderableWidget(new Ui.MButton(cx, cy, cw, 16, "Tout l'étal", category.isEmpty() ? Ui.MButton.Style.GOLD : Ui.MButton.Style.WOOD,
                () -> { category = ""; page = 0; rebuildWidgets(); }));
        cy += 18;
        for (String c : d.categories) {
            if (cy > py + ph - 60) break;
            addRenderableWidget(new Ui.MButton(cx, cy, cw, 16, c, c.equals(category) ? Ui.MButton.Style.GOLD : Ui.MButton.Style.WOOD,
                    () -> { category = c; page = 0; rebuildWidgets(); }));
            cy += 18;
        }

        // --- recherche
        gx = px + 114;
        gy = py + 46;
        gw = pw - 124;
        gh = ph - 46 - 34;
        searchBox = new EditBox(font, px + pw - 130, py + 26, 120, 14, Component.literal("Rechercher"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Rechercher…"));
        searchBox.setValue(search);
        searchBox.setResponder(v -> { if (!v.equals(search)) { search = v; page = 0; rebuildWidgetsKeepFocus(); } });
        addRenderableWidget(searchBox);

        // --- etals
        List<ShopData.Offer> list = filtered();
        int cols = Math.max(2, gw / 104);
        int rows = Math.max(1, gh / 92);
        int per = cols * rows;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        if (page >= pages) page = pages - 1;
        int cardW = (gw - (cols - 1) * 6) / cols;
        int cardH = (gh - (rows - 1) * 6) / rows;
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= list.size()) break;
            int x = gx + (i % cols) * (cardW + 6);
            int y = gy + (i / cols) * (cardH + 6);
            addRenderableWidget(new Card(x, y, cardW, cardH, list.get(idx)));
        }

        // --- bas : pages + admin
        int by = py + ph - 26;
        if (pages > 1) {
            addRenderableWidget(new Ui.MButton(gx + gw / 2 - 60, by, 20, 16, "<", Ui.MButton.Style.WOOD, () -> { page = Math.max(0, page - 1); rebuildWidgets(); })).active = page > 0;
            addRenderableWidget(new Ui.MButton(gx + gw / 2 + 40, by, 20, 16, ">", Ui.MButton.Style.WOOD, () -> { page++; rebuildWidgets(); })).active = page < pages - 1;
        }
        if (ClientState.admin) {
            addRenderableWidget(new Ui.MButton(px + pw - 170, by, 78, 16, "+ Article", Ui.MButton.Style.GREEN,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, null))));
            addRenderableWidget(new Ui.MButton(px + pw - 88, by, 78, 16, "Réglages", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new SettingsScreen(this))));
        }
        pageInfo = pages > 1 ? "Page " + (page + 1) + " / " + pages : "";
        empty = list.isEmpty();
    }

    private String pageInfo = "";
    private boolean empty;

    private void rebuildWidgetsKeepFocus() {
        rebuildWidgets();
        setFocused(searchBox);
        searchBox.setFocused(true);
        searchBox.moveCursorToEnd(false);
    }

    private List<ShopData.Offer> filtered() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        List<ShopData.Offer> out = new ArrayList<>();
        for (ShopData.Offer o : shop().offers) {
            if (!category.isEmpty() && !category.equals(o.category)) continue;
            if (!q.isEmpty() && !o.name.toLowerCase(Locale.ROOT).contains(q) && !o.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(o);
        }
        return out;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        ShopData.Settings s = shop().settings;
        Ui.title(g, font, s.title, px + pw / 2, py + 9, 1.6f);
        if (!s.subtitle.isBlank()) g.drawCenteredString(font, "~ " + s.subtitle + " ~", px + pw / 2, py + 27, 0xFFCDB580);
        Ui.divider(g, px + 10, py + 40, pw - 20);
        g.fill(px + 109, py + 44, px + 110, py + ph - 32, Ui.GOLD_DIM);
        Ui.divider(g, px + 10, py + ph - 31, pw - 20);
        if (empty) {
            Ui.parchment(g, gx, gy, gw, 40);
            g.drawCenteredString(font, ClientState.admin ? "L'étal est vide : ajoutez un article avec « + Article »." : "Le marchand n'a rien à vendre ici pour l'instant.",
                    gx + gw / 2, gy + 16, Ui.INK);
        }
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        // bourse du joueur
        StringBuilder w = new StringBuilder("Bourse : " + Ui.po(ClientState.wallet));
        if (ClientState.bankAvailable && ClientState.bank >= 0) w.append("   Banque : ").append(Ui.po(ClientState.bank));
        if (shop().settings.payXp) w.append("   XP : ").append(ClientState.xp);
        g.drawString(font, w.toString(), px + 12, py + ph - 22, Ui.GOLD, false);
        if (!pageInfo.isEmpty()) g.drawCenteredString(font, pageInfo, gx + gw / 2, py + ph - 22, 0xFFCDB580);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // F6 referme la boutique (sauf pendant la saisie dans la recherche)
        if (ClientSetup.OPEN_KEY.matches(key, scan) && !searchBox.isFocused()) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Un etal : image (ou icone de l'objet), nom et prix. */
    private class Card extends AbstractButton {
        private final ShopData.Offer offer;
        private final ItemStack stack;

        Card(int x, int y, int w, int h, ShopData.Offer offer) {
            super(x, y, w, h, Component.literal(offer.name));
            this.offer = offer;
            Minecraft mc = Minecraft.getInstance();
            this.stack = mc.level == null ? ItemStack.EMPTY : ItemJson.decode(offer.item, mc.level.registryAccess());
        }

        @Override public void onPress() { Minecraft.getInstance().setScreen(new OfferScreen(ShopScreen.this, offer.id)); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hot = isHoveredOrFocused();
            if (hot) g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x66D4AF37);
            Ui.parchment(g, x, y, w, h);
            int imgH = h - 30;
            g.fill(x + 4, y + 4, x + w - 4, y + 4 + imgH, 0x22000000);
            ImageCache.Tex t = ImageCache.get(offer.image);
            if (t != null) ImageCache.draw(g, t, x + 5, y + 5, w - 10, imgH - 2);
            else if (!stack.isEmpty()) {
                float sc = Math.max(1f, Math.min(3f, (imgH - 6) / 16f));
                g.pose().pushPose();
                g.pose().translate(x + w / 2f - 8 * sc, y + 4 + imgH / 2f - 8 * sc, 0);
                g.pose().scale(sc, sc, 1f);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            } else {
                g.drawCenteredString(font, offer.commands.isEmpty() ? "?" : "✦", x + w / 2, y + imgH / 2, Ui.INK_LIGHT);
            }
            g.drawString(font, Ui.ellipsis(font, offer.name, w - 8), x + 4, y + h - 23, Ui.INK, false);
            g.drawString(font, Ui.ellipsis(font, priceLine(offer), w - 8), x + 4, y + h - 12, 0xFF7A5A12, false);
            if (offer.stock == 0) {
                g.fill(x + 2, y + 6, x + w - 2, y + 18, 0xCC7A1C1C);
                g.drawCenteredString(font, "ÉPUISÉ", x + w / 2, y + 8, 0xFFFFE0A0);
            } else if (!offer.enabled) {
                g.fill(x + 2, y + 6, x + w - 2, y + 18, 0xCC333333);
                g.drawCenteredString(font, "MASQUÉ", x + w / 2, y + 8, 0xFFDDDDDD);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    static String priceLine(ShopData.Offer o) {
        ShopData.Settings s = shop().settings;
        List<String> parts = new ArrayList<>();
        if (o.price > 0 && ((s.payCoins && o.allowCoins) || (s.payBank && o.allowBank))) parts.add(Ui.po(o.price));
        if (s.payBarter && !o.barterItem.isBlank() && o.barterCount > 0) {
            String n = ItemJson.item(o.barterItem).map(i -> i.getDescription().getString()).orElse(o.barterItem);
            parts.add(o.barterCount + "× " + n);
        }
        if (s.payXp && o.xpLevels > 0) parts.add(o.xpLevels + " niv.");
        if (o.price <= 0 && o.xpLevels <= 0 && (o.barterItem.isBlank() || o.barterCount <= 0)) return "Gratuit";
        return parts.isEmpty() ? "Indisponible" : String.join(" ou ", parts);
    }
}
