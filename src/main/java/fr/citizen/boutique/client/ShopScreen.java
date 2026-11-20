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

/** Ecran principal (touche F6) : nef des categories a gauche, vitraux des articles a droite. */
public class ShopScreen extends BaseScreen {
    /** Onglet special : articles payables en argent reel. */
    static final String SUPPORT = "\u0000support";
    private static String category = "";
    private static String search = "";
    private static int page;

    private EditBox searchBox;
    private int gx, gy, gw, gh;
    private String pageInfo = "";
    private boolean empty;

    public ShopScreen() { super("Boutique"); }

    static boolean supportEnabled() {
        if (!ClientState.paypalReady && !ClientState.admin) return false;
        for (ShopData.Offer o : shop().offers) if (o.realPriceCents > 0) return true;
        return false;
    }

    @Override
    protected void init() {
        layout(470, 290);
        ShopData d = shop();
        boolean support = supportEnabled();
        if (SUPPORT.equals(category) && !support) category = "";
        if (!category.isEmpty() && !SUPPORT.equals(category) && !d.categories.contains(category)) category = "";

        // --- nef des categories
        int cx = px + 12, cy = py + 48, cw = 98;
        if (support) {
            addRenderableWidget(new Ui.MButton(cx, cy, cw, 18, d.settings.supportTab, Ui.MButton.Style.ROYAL,
                    () -> { category = SUPPORT; page = 0; rebuildWidgets(); }));
            cy += 23;
        }
        addRenderableWidget(new Ui.MButton(cx, cy, cw, 15, "Tout l'étal", category.isEmpty() ? Ui.MButton.Style.GOLD : Ui.MButton.Style.WOOD,
                () -> { category = ""; page = 0; rebuildWidgets(); }));
        cy += 17;
        for (String c : d.categories) {
            if (cy > py + ph - 56) break;
            addRenderableWidget(new Ui.MButton(cx, cy, cw, 15, c, c.equals(category) ? Ui.MButton.Style.GOLD : Ui.MButton.Style.WOOD,
                    () -> { category = c; page = 0; rebuildWidgets(); }));
            cy += 17;
        }

        // --- recherche
        gx = px + 120;
        gy = py + 48;
        gw = pw - 132;
        gh = ph - 48 - 36;
        searchBox = new EditBox(font, px + pw - 128, py + 30, 114, 13, Component.literal("Rechercher"));
        searchBox.setMaxLength(40);
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("Rechercher…"));
        searchBox.setValue(search);
        searchBox.setResponder(v -> { if (!v.equals(search)) { search = v; page = 0; rebuildWidgetsKeepFocus(); } });
        addRenderableWidget(searchBox);

        // --- vitraux
        List<ShopData.Offer> list = filtered();
        int cols = Math.max(2, gw / 100);
        int rows = Math.max(1, gh / 104);
        int per = cols * rows;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        if (page >= pages) page = pages - 1;
        int cardW = (gw - (cols - 1) * 8) / cols;
        int cardH = (gh - (rows - 1) * 8) / rows;
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= list.size()) break;
            int x = gx + (i % cols) * (cardW + 8);
            int y = gy + (i / cols) * (cardH + 8);
            addRenderableWidget(new Card(x, y, cardW, cardH, list.get(idx)));
        }

        // --- bas
        int by = py + ph - 27;
        if (pages > 1) {
            addRenderableWidget(new Ui.MButton(gx + gw / 2 - 62, by, 20, 15, "<", Ui.MButton.Style.WOOD, () -> { page = Math.max(0, page - 1); rebuildWidgets(); })).active = page > 0;
            addRenderableWidget(new Ui.MButton(gx + gw / 2 + 42, by, 20, 15, ">", Ui.MButton.Style.WOOD, () -> { page++; rebuildWidgets(); })).active = page < pages - 1;
        }
        if (ClientState.admin) {
            addRenderableWidget(new Ui.MButton(px + pw - 172, by, 78, 15, "+ Article", Ui.MButton.Style.GREEN,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, null))));
            addRenderableWidget(new Ui.MButton(px + pw - 90, by, 78, 15, "Réglages", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new SettingsScreen(this))));
        }
        pageInfo = pages > 1 ? (page + 1) + " / " + pages : "";
        empty = list.isEmpty();
    }

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
            if (SUPPORT.equals(category)) { if (o.realPriceCents <= 0) continue; }
            else if (!category.isEmpty() && !category.equals(o.category)) continue;
            if (!q.isEmpty() && !o.name.toLowerCase(Locale.ROOT).contains(q) && !o.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(o);
        }
        return out;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        ShopData.Settings s = shop().settings;
        Ui.title(g, font, s.title, px + pw / 2, py + 10, 1.6f);
        if (!s.subtitle.isBlank() && !SUPPORT.equals(category)) Ui.subtitle(g, font, s.subtitle, px + pw / 2, py + 27);
        Ui.divider(g, px + 10, py + 41, pw - 20);
        // colonne de la nef
        g.fill(px + 115, py + 46, px + 116, py + ph - 34, Ui.GOLD_DIM);
        g.fill(px + 116, py + 46, px + 117, py + ph - 34, 0x66000000);
        Ui.divider(g, px + 10, py + ph - 32, pw - 20);
        if (SUPPORT.equals(category)) {
            Ui.inset(g, gx, py + 29, gw - 122, 14);
            g.drawString(font, Ui.ellipsis(font, "Soutenez le serveur : chaque don le fait vivre. Merci !", gw - 128), gx + 4, py + 32, Ui.GOLD, false);
        }
        if (empty) {
            Ui.vellum(g, gx, gy, gw, 40);
            g.drawCenteredString(font, ClientState.admin ? "L'étal est vide : ajoutez un article avec « + Article »." : "Le marchand n'a rien à proposer ici pour l'instant.",
                    gx + gw / 2, gy + 16, Ui.INK);
        }
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        StringBuilder w = new StringBuilder("Bourse : " + Ui.po(ClientState.wallet));
        if (ClientState.bankAvailable && ClientState.bank >= 0) w.append("   Banque : ").append(Ui.po(ClientState.bank));
        if (shop().settings.payXp) w.append("   XP : ").append(ClientState.xp);
        g.drawString(font, w.toString(), px + 14, py + ph - 23, Ui.GOLD, false);
        if (!pageInfo.isEmpty()) g.drawCenteredString(font, pageInfo, gx + gw / 2, py + ph - 23, Ui.TEXT_DIM);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.OPEN_KEY.matches(key, scan) && !searchBox.isFocused()) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Un etal en forme de vitrail : image sous ogive, nom et prix en dessous. */
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
            boolean real = offer.realPriceCents > 0;
            // socle de pierre
            g.fill(x, y, x + w, y + h, Ui.STONE_DARK);
            Ui.frame(g, x, y, w, h, hot ? Ui.GOLD : Ui.IRON);
            // vitrail
            int wx = x + 6, wy = y + 5, ww = w - 12, wh = h - 34;
            int arch = Ui.archHeight(ww);
            ImageCache.Tex t = ImageCache.get(offer.image);
            if (t != null) {
                g.fill(wx, wy, wx + ww, wy + wh, 0xFF050407);
                ImageCache.draw(g, t, wx + 1, wy + 1, ww - 2, wh - 2);
            } else {
                Ui.glass(g, wx, wy, ww, wh);
            }
            if (hot) g.fill(wx, wy, wx + ww, wy + wh, 0x18FFE9A0);
            Ui.archMask(g, wx, wy, ww, arch, Ui.STONE_DARK, hot ? Ui.GOLD_BRIGHT : Ui.GOLD);
            Ui.windowSides(g, wx, wy, ww, wh, arch, hot ? Ui.GOLD_BRIGHT : Ui.GOLD);
            if (t == null && !stack.isEmpty()) {
                float sc = Math.max(1f, Math.min(3f, (Math.min(ww, wh - arch) - 8) / 16f));
                g.pose().pushPose();
                g.pose().translate(wx + ww / 2f - 8 * sc, wy + arch / 2f + wh / 2f - 8 * sc, 0);
                g.pose().scale(sc, sc, 1f);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            } else if (t == null) {
                Ui.cross(g, wx + ww / 2, wy + wh / 2 + 3, Ui.GOLD_DIM);
            }
            // cartouche
            g.drawString(font, Ui.ellipsis(font, offer.name, w - 8), x + 4, y + h - 25, hot ? Ui.GOLD_BRIGHT : Ui.TEXT, false);
            g.drawString(font, Ui.ellipsis(font, priceLine(offer), w - 8), x + 4, y + h - 13, real ? 0xFFE0707A : Ui.GOLD, false);
            if (real) {
                // bandeau « Soutien »
                g.fill(x + w - 40, y + 3, x + w - 3, y + 13, Ui.BLOOD_LIGHT);
                Ui.frame(g, x + w - 40, y + 3, 37, 10, Ui.GOLD);
                g.drawCenteredString(font, "Soutien", x + w - 21, y + 4, Ui.GOLD_BRIGHT);
            }
            if (offer.stock == 0) {
                g.fill(x + 2, y + h / 2 - 12, x + w - 2, y + h / 2, 0xDD4E0B12);
                g.drawCenteredString(font, "ÉPUISÉ", x + w / 2, y + h / 2 - 10, Ui.GOLD_BRIGHT);
            } else if (!offer.enabled) {
                g.fill(x + 2, y + h / 2 - 12, x + w - 2, y + h / 2, 0xDD222222);
                g.drawCenteredString(font, "MASQUÉ", x + w / 2, y + h / 2 - 10, 0xFFDDDDDD);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    static String priceLine(ShopData.Offer o) {
        ShopData.Settings s = shop().settings;
        List<String> parts = new ArrayList<>();
        if (o.realPriceCents > 0 && (ClientState.paypalReady || ClientState.admin)) parts.add(Ui.money(o.realPriceCents, s.currency));
        if (o.price > 0 && ((s.payCoins && o.allowCoins) || (s.payBank && o.allowBank))) parts.add(Ui.po(o.price));
        if (s.payBarter && !o.barterItem.isBlank() && o.barterCount > 0) {
            String n = ItemJson.item(o.barterItem).map(i -> i.getDescription().getString()).orElse(o.barterItem);
            parts.add(o.barterCount + "× " + n);
        }
        if (s.payXp && o.xpLevels > 0) parts.add(o.xpLevels + " niv.");
        if (o.price <= 0 && o.xpLevels <= 0 && o.realPriceCents <= 0 && (o.barterItem.isBlank() || o.barterCount <= 0)) return "Gratuit";
        return parts.isEmpty() ? "Indisponible" : String.join(" ou ", parts);
    }
}
