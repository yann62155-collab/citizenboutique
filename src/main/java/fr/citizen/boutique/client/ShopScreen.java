package fr.citizen.boutique.client;

import com.mojang.math.Axis;
import fr.citizen.boutique.net.Payloads;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique officielle (touche F6) : logo, categories a gauche, illustration et cartes de packs au centre,
 * « Mon compte » et paiement securise a droite.
 */
public class ShopScreen extends BaseScreen {
    private static String category = "";
    private static int page;

    private int lx, lw, cx0, cw, rx, rw, top, heroH, cardsY, cardsH;
    private boolean showRight;
    private final List<CardBox> cards = new ArrayList<>();

    private record CardBox(int x, int y, int w, int h, ShopData.Offer offer, ItemStack stack) { }

    public ShopScreen() { super("Boutique"); }

    @Override
    protected void init() {
        layout(700, 330, 30);
        ShopData d = shop();
        if (category.isEmpty() || !d.categories.contains(category)) category = d.categories.isEmpty() ? "" : d.categories.get(0);
        cards.clear();

        // colonnes
        int logoBottom = py + logoH() - 26;
        top = Math.max(py + 46, logoBottom + 22);
        showRight = pw >= 580;
        lx = px + 12; lw = 116;
        rw = 124; rx = px + pw - 12 - rw;
        cx0 = lx + lw + 10;
        cw = (showRight ? rx - 10 : px + pw - 12) - cx0;
        heroH = ph >= 280 ? 58 : 0;
        cardsY = top + heroH + (heroH > 0 ? 8 : 0);
        cardsH = py + ph - 12 - cardsY;

        addRenderableWidget(new Ui.MButton(px + pw - 26, py + 8, 17, 17, "X", Ui.MButton.Style.RED, this::onClose).titleFont());

        // categories
        int ny = top + 6;
        int bottomLimit = py + ph - 14 - (ClientState.admin ? 66 : 0);
        for (String c : d.categories) {
            if (ny + 22 > bottomLimit) break;
            ShopData.Category info = d.categoryInfo.get(c);
            ItemStack icon = info == null ? ItemStack.EMPTY : ItemJson.item(info.icon).map(ItemStack::new).orElse(ItemStack.EMPTY);
            addRenderableWidget(new Ui.MButton(lx + 4, ny, lw - 8, 22, c, Ui.MButton.Style.NAV,
                    () -> { category = c; page = 0; rebuildWidgets(); }).selected(c.equals(category)).icon(icon));
            ny += 26;
        }
        if (ClientState.admin) {
            int ay = py + ph - 14 - 62;
            addRenderableWidget(new Ui.MButton(lx + 4, ay, lw - 8, 18, "+ Article", Ui.MButton.Style.GREEN,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, null, category))));
            addRenderableWidget(new Ui.MButton(lx + 4, ay + 21, (lw - 12) / 2, 18, "+ Catég.", Ui.MButton.Style.WOOD,
                    () -> Minecraft.getInstance().setScreen(new CategoryScreen(this, null))));
            addRenderableWidget(new Ui.MButton(lx + 8 + (lw - 12) / 2, ay + 21, (lw - 12) / 2, 18, "Modifier", Ui.MButton.Style.WOOD,
                    () -> { if (!category.isEmpty()) Minecraft.getInstance().setScreen(new CategoryScreen(this, category)); })).active = !category.isEmpty();
            addRenderableWidget(new Ui.MButton(lx + 4, ay + 42, lw - 8, 18, "Réglages", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new SettingsScreen(this))));
        }

        // cartes
        List<ShopData.Offer> list = filtered();
        int per = Math.max(1, Math.min(3, (cw + 8) / 118));
        int pages = Math.max(1, (list.size() + per - 1) / per);
        if (page >= pages) page = pages - 1;
        int cardW = (cw - (per - 1) * 8) / per;
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= list.size()) break;
            ShopData.Offer o = list.get(idx);
            int x = cx0 + i * (cardW + 8);
            ItemStack st = mc.level == null ? ItemStack.EMPTY : ItemJson.decode(o.item, mc.level.registryAccess());
            cards.add(new CardBox(x, cardsY, cardW, cardsH, o, st));
            boolean soldOut = o.stock == 0;
            boolean limit = o.limitPerPlayer > 0 && ClientState.bought.getOrDefault(o.id, 0) >= o.limitPerPlayer;
            Ui.PayPalButton b = new Ui.PayPalButton(x + 8, cardsY + cardsH - 21, cardW - 16, 15,
                    () -> PacketDistributor.sendToServer(new Payloads.Buy(o.id, "paypal")));
            b.active = ClientState.paypalReady && !soldOut && !limit && o.enabled && o.realPriceCents > 0;
            addRenderableWidget(b);
        }
        if (pages > 1) {
            int ay = heroH > 0 ? top + heroH - 18 : cardsY - 16;
            addRenderableWidget(new Ui.MButton(cx0 + cw - 58, ay, 16, 14, "<", Ui.MButton.Style.WOOD, () -> { page--; rebuildWidgets(); })).active = page > 0;
            addRenderableWidget(new Ui.MButton(cx0 + cw - 20, ay, 16, 14, ">", Ui.MButton.Style.WOOD, () -> { page++; rebuildWidgets(); })).active = page < pages - 1;
            pageText = (page + 1) + "/" + pages;
        } else pageText = "";

        if (showRight) {
            addRenderableWidget(new Ui.MButton(rx + 6, top + 82, rw - 12, 18, "Mes Achats", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new PurchasesScreen(this))));
        }
    }

    private String pageText = "";

    private int logoW() { return Math.min(200, (int) (pw * 0.36f)); }
    private int logoH() { return logoW() * 243 / 760; }

    private List<ShopData.Offer> filtered() {
        List<ShopData.Offer> out = new ArrayList<>();
        for (ShopData.Offer o : shop().offers) if (category.isEmpty() || category.equals(o.category)) out.add(o);
        return out;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        ShopData d = shop();
        // bannieres et logo
        int bh = Math.max(40, Math.min(80, top - py + 14)), bw = bh * 140 / 312;
        Ui.blitSmooth(g, Ui.BANNER, px + 36, py - 22, bw, bh, 140, 312);
        Ui.blitSmooth(g, Ui.BANNER, px + pw - 36 - bw, py - 22, bw, bh, 140, 312);
        int lwid = logoW(), lh = logoH();
        Ui.blitSmooth(g, Ui.LOGO, px + (pw - lwid) / 2, py - 26, lwid, lh, 760, 243);
        // ruban
        String tag = d.settings.tagline;
        if (!tag.isBlank()) {
            int rwid = Math.min(pw - 120, Ui.width(tag) + 30), rxx = px + (pw - rwid) / 2, ry = top - 18;
            g.fillGradient(rxx, ry, rxx + rwid, ry + 14, 0xFF2A1E14, 0xFF16100B);
            Ui.goldBevel(g, rxx, ry, rwid, 14);
            Ui.ornament(g, rxx, ry + 7, 5);
            Ui.ornament(g, rxx + rwid - 1, ry + 7, 5);
            String t = Ui.ellipsis(tag, rwid - 20);
            Ui.textCentered(g, t, px + pw / 2, ry + 3, Ui.CREAM, true);
        }

        // colonnes
        Ui.column(g, lx, top, lw, py + ph - 12 - top);
        if (showRight) drawAccount(g);

        // illustration de la categorie
        if (heroH > 0) {
            ShopData.Category info = d.categoryInfo.get(category);
            ImageCache.Tex t = info == null ? null : ImageCache.get(info.banner);
            g.fill(cx0 - 1, top - 1, cx0 + cw + 1, top + heroH + 1, 0xFF000000);
            if (t != null) ImageCache.drawCover(g, t, cx0, top, cw, heroH);
            else Ui.blitSmooth(g, Ui.HERO, cx0, top, cw, heroH, 800, 180);
            g.fillGradient(cx0, top + heroH - 36, cx0 + cw, top + heroH, 0x00000000, 0xC8000000);
            Ui.goldBevel(g, cx0 - 1, top - 1, cw + 2, heroH + 2);
            String name = category.isEmpty() ? "Boutique" : category;
            Ui.titleText(g, Ui.ellipsisTitle(name, (int) ((cw - 80) / 1.5f)), cx0 + 10, top + heroH - 32, 0xFFFFE6A0, 1.5f, false, true);
            String desc = info == null ? "" : info.description;
            if (!desc.isBlank()) Ui.text(g, Ui.ellipsis(desc, cw - 20), cx0 + 10, top + heroH - 13, Ui.CREAM, true);
            if (!pageText.isEmpty()) Ui.textCentered(g, pageText, cx0 + cw - 30, top + heroH - 15, Ui.CREAM, true);
        }

        // cartes
        if (cards.isEmpty()) {
            g.fill(cx0, cardsY, cx0 + cw, cardsY + 40, 0x5A000000);
            Ui.goldBevel(g, cx0, cardsY, cw, 40);
            String t = ClientState.admin ? "Aucun article ici : ajoutez-en un avec « + Article »." : "Aucune offre ici pour le moment. Revenez bientôt !";
            Ui.textCentered(g, Ui.ellipsis(t, cw - 16), cx0 + cw / 2, cardsY + 16, Ui.CREAM, true);
        }
        for (CardBox c : cards) drawCard(g, c, mx, my);
    }

    private void drawCard(GuiGraphics g, CardBox c, int mx, int my) {
        int x = c.x(), y = c.y(), w = c.w(), h = c.h();
        ShopData.Offer o = c.offer();
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h - 24;
        boolean silver = o.popular;
        Ui.card(g, x, y, w, h, silver, hot);

        // embleme
        int eh = Math.max(24, Math.min(46, h / 4));
        ImageCache.Tex t = ImageCache.get(o.image);
        if (t != null) ImageCache.draw(g, t, x + 8, y + 6, w - 16, eh);
        else if (!c.stack().isEmpty()) {
            float sc = Math.min(2.6f, eh / 17f);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 8 * sc, y + 6 + eh / 2f - 8 * sc, 0);
            g.pose().scale(sc, sc, 1f);
            g.renderItem(c.stack(), 0, 0);
            g.pose().popPose();
        } else Ui.ornament(g, x + w / 2, y + 6 + eh / 2, 10);

        // nom
        int ny = y + eh + 10;
        Ui.titleText(g, Ui.ellipsisTitle(o.name, w - 10), x + w / 2f, ny, Ui.CREAM, 1.0f, true, true);
        Ui.divider(g, x + 12, ny + 13, w - 24, silver ? Ui.SILVER : Ui.GOLD);

        // avantages
        int fy = ny + 19, priceY = y + h - 42;
        List<String> feats = o.features.isEmpty() && !o.description.isBlank() ? List.of(o.description) : o.features;
        for (String f : feats) {
            if (fy + 10 > priceY - 2) break;
            Ui.check(g, x + 7, fy + 1, Ui.GOLD_TEXT);
            Ui.text(g, Ui.ellipsis(f, w - 26), x + 19, fy, Ui.CREAM, true);
            fy += 11;
        }

        // prix
        String price = o.realPriceCents > 0 ? Ui.money(o.realPriceCents, shop().settings.currency) : "—";
        int pwid = Math.min(w - 24, Math.max(60, Ui.widthTitle(price) + 24));
        Ui.pricePlate(g, x + (w - pwid) / 2, priceY, pwid, 16);
        Ui.titleText(g, price, x + w / 2f, priceY + 4, Ui.INK, 1.0f, true, false);

        // rubans
        if (silver) {
            g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
            g.pose().pushPose();
            g.pose().translate(x + w - 15, y + 15, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45f));
            g.fillGradient(-34, -6, 34, 6, 0xFFFFE6A0, 0xFFC8963E);
            g.fill(-34, -6, 34, -5, 0xFF6E4A1C);
            g.fill(-34, 5, 34, 6, 0xFF6E4A1C);
            String pop = "Populaire";
            g.drawString(font, Ui.tx(pop), -Ui.width(pop) / 2, -4, Ui.INK, false);
            g.pose().popPose();
            g.disableScissor();
        }
        String flag = o.stock == 0 ? "Épuisé" : !o.enabled ? "Masqué" : o.stock > 0 && o.stock <= 5 ? "Plus que " + o.stock : "";
        if (!flag.isEmpty()) {
            int fw = Ui.width(flag) + 10;
            g.fill(x + 3, y + 3, x + 3 + fw, y + 15, o.stock == 0 ? 0xFF8E1C1C : !o.enabled ? 0xFF444444 : 0xFFB0701A);
            Ui.frame(g, x + 3, y + 3, fw, 12, Ui.GOLD);
            Ui.text(g, flag, x + 8, y + 5, Ui.CREAM, true);
        }
    }

    private void drawAccount(GuiGraphics g) {
        int y = top, h = py + ph - 12 - top;
        Ui.column(g, rx, y, rw, h);
        Ui.titleText(g, "Mon Compte", rx + rw / 2f, y + 6, Ui.CREAM, 1.0f, true, true);
        Ui.divider(g, rx + 12, y + 19, rw - 24, Ui.GOLD);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.fill(rx + 7, y + 25, rx + 29, y + 47, 0xFF000000);
            PlayerFaceRenderer.draw(g, mc.player.getSkin(), rx + 8, y + 26, 20);
            Ui.text(g, Ui.ellipsis(mc.player.getGameProfile().getName(), rw - 40), rx + 34, y + 32, Ui.CREAM, true);
        }
        // mon soutien
        int sy = y + 52;
        g.fillGradient(rx + 4, sy, rx + rw - 4, sy + 26, 0xFF2A2016, 0xFF18120C);
        Ui.bevel(g, rx + 4, sy, rw - 8, 26, 0xFF8A6A3A, 0xFF5A4222, 0xFF2A1C0E);
        g.renderItem(new ItemStack(net.minecraft.world.item.Items.EMERALD), rx + 8, sy + 5);
        int total = ClientState.bought.values().stream().mapToInt(Integer::intValue).sum();
        Ui.text(g, "Mon Soutien", rx + 28, sy + 3, Ui.CREAM, true);
        Ui.text(g, Ui.ellipsis(total == 0 ? "Aucun pack actif" : total + " achat" + (total > 1 ? "s" : "") + ", merci !", rw - 36), rx + 28, sy + 14, Ui.CREAM_DIM, true);
        // paiement securise
        int py2 = y + 108, boxH = Math.min(84, y + h - 4 - py2);
        if (boxH < 40) return;
        g.fill(rx + 4, py2, rx + rw - 4, py2 + boxH, 0x60000000);
        Ui.frame(g, rx + 4, py2, rw - 8, boxH, Ui.GOLD_DARK);
        Ui.textCentered(g, "Paiement sécurisé", rx + rw / 2, py2 + 4, Ui.GOLD_TEXT, true);
        Component pay = Component.literal("Pay").withStyle(st -> st.withFont(Ui.TEXT_FONT).withBold(true).withItalic(true).withColor(0xFFFFFF));
        Component pal = Component.literal("Pal").withStyle(st -> st.withFont(Ui.TEXT_FONT).withBold(true).withItalic(true).withColor(0x29A8FF));
        int w1 = font.width(pay), w2 = font.width(pal);
        g.pose().pushPose();
        g.pose().translate(rx + rw / 2f - (w1 + w2) * 0.8f, py2 + 16, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, pay, 0, 0, 0xFFFFFFFF, true);
        g.drawString(font, pal, w1, 0, 0xFF29A8FF, true);
        g.pose().popPose();
        String[] lines = {"Paiement 100 % sécurisé", "Livraison automatique", "Support en cas de souci"};
        for (int i = 0; i < lines.length; i++) {
            int ly = py2 + 36 + i * 13;
            if (ly + 10 > py2 + boxH) break;
            Ui.check(g, rx + 9, ly + 1, Ui.GOLD_TEXT);
            Ui.text(g, Ui.ellipsis(lines[i], rw - 30), rx + 21, ly, Ui.CREAM, true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (CardBox c : cards) {
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h() - 24) {
                Minecraft.getInstance().setScreen(new OfferScreen(this, c.offer().id));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.OPEN_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        for (CardBox c : cards) {
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h() - 24 && !c.offer().description.isBlank() && !c.offer().features.isEmpty()) {
                List<FormattedCharSequence> tip = new ArrayList<>(Ui.wrap(c.offer().description, 180));
                tip.add(Ui.tx("Cliquez pour plus de détails").getVisualOrderText());
                g.renderTooltip(font, tip, mx, my);
            }
        }
    }
}
