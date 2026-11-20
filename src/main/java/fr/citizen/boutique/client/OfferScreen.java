package fr.citizen.boutique.client;

import fr.citizen.boutique.net.Payloads;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Fiche d'un article : grand vitrail, texte enlumine, objet recu et moyens de paiement. */
public class OfferScreen extends BaseScreen {
    private final Screen parent;
    private final String offerId;
    private ShopData.Offer offer;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean confirmDelete;
    private int ix, iy, iw, ih, tx, tw;
    private int payTop, payCount;
    private String status = "";

    public OfferScreen(Screen parent, String offerId) {
        super("Article");
        this.parent = parent;
        this.offerId = offerId;
    }

    @Override
    protected void init() {
        layout(450, 280);
        offer = ClientState.offer(offerId);
        if (offer == null) { Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(parent)); return; }
        Minecraft mc = Minecraft.getInstance();
        stack = mc.level == null ? ItemStack.EMPTY : ItemJson.decode(offer.item, mc.level.registryAccess());
        ShopData.Settings s = shop().settings;

        ix = px + 16; iy = py + 44; iw = Math.min(160, pw / 2 - 30); ih = ph - 44 - (ClientState.admin ? 42 : 16);
        tx = ix + iw + 16; tw = px + pw - 14 - tx;

        addRenderableWidget(new Ui.MButton(px + 12, py + 12, 58, 15, "< Retour", Ui.MButton.Style.WOOD, this::onClose));

        boolean soldOut = offer.stock == 0;
        boolean limit = offer.limitPerPlayer > 0 && ClientState.bought.getOrDefault(offer.id, 0) >= offer.limitPerPlayer;
        boolean canBuy = !soldOut && !limit && offer.enabled;
        int bw = tw;
        List<Ui.MButton> pay = new ArrayList<>();
        boolean free = offer.price <= 0 && offer.xpLevels <= 0 && offer.realPriceCents <= 0 && (offer.barterItem.isBlank() || offer.barterCount <= 0);
        if (free) pay.add(new Ui.MButton(0, 0, bw, 18, "Recevoir gratuitement", Ui.MButton.Style.GREEN, () -> buy("free")));
        if (offer.realPriceCents > 0 && (ClientState.paypalReady || ClientState.admin)) {
            Ui.MButton b = new Ui.MButton(0, 0, bw, 20, "Soutenir — " + Ui.money(offer.realPriceCents, s.currency) + " via PayPal", Ui.MButton.Style.ROYAL, () -> buy("paypal"));
            b.active = ClientState.paypalReady;
            pay.add(b);
        }
        if (offer.price > 0 && s.payCoins && offer.allowCoins) {
            Ui.MButton b = new Ui.MButton(0, 0, bw, 18, "Payer en pièces — " + Ui.po(offer.price), Ui.MButton.Style.GOLD, () -> buy("coins"));
            b.active = ClientState.wallet >= offer.price;
            pay.add(b);
        }
        if (offer.price > 0 && s.payBank && offer.allowBank && ClientState.bankAvailable) {
            Ui.MButton b = new Ui.MButton(0, 0, bw, 18, "Payer par la banque — " + Ui.po(offer.price), Ui.MButton.Style.GOLD, () -> buy("bank"));
            b.active = ClientState.bank < 0 || ClientState.bank >= offer.price;
            pay.add(b);
        }
        if (s.payBarter && !offer.barterItem.isBlank() && offer.barterCount > 0) {
            String n = ItemJson.item(offer.barterItem).map(i -> i.getDescription().getString()).orElse(offer.barterItem);
            pay.add(new Ui.MButton(0, 0, bw, 18, "Troquer — " + offer.barterCount + " × " + n, Ui.MButton.Style.WOOD, () -> buy("barter")));
        }
        if (s.payXp && offer.xpLevels > 0) {
            Ui.MButton b = new Ui.MButton(0, 0, bw, 18, "Payer en expérience — " + offer.xpLevels + " niveaux", Ui.MButton.Style.WOOD, () -> buy("xp"));
            b.active = ClientState.xp >= offer.xpLevels;
            pay.add(b);
        }
        int by = py + ph - 14;
        for (int i = pay.size() - 1; i >= 0; i--) {
            Ui.MButton b = pay.get(i);
            by -= b.getHeight() + 3;
            b.setPosition(tx, by);
            if (!canBuy) b.active = false;
            addRenderableWidget(b);
        }
        payTop = by;
        payCount = pay.size();

        if (ClientState.admin) {
            int ay = py + ph - 32, ax = ix;
            addRenderableWidget(new Ui.MButton(ax, ay, 18, 15, "▲", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":-1}")));
            ax += 20;
            addRenderableWidget(new Ui.MButton(ax, ay, 18, 15, "▼", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":1}")));
            ax += 20;
            int bw2 = (iw - 44) / 2;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 15, "Modifier", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, offer))));
            ax += bw2 + 4;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 15, confirmDelete ? "Confirmer ?" : "Supprimer", Ui.MButton.Style.RED, () -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                ClientSetup.admin("delete_offer", "{\"id\":\"" + offer.id + "\"}");
                Minecraft.getInstance().setScreen(parent);
            }));
        }
        status = soldOut ? "Rupture de stock" : limit ? "Limite d'achat atteinte" : !offer.enabled ? "Article masqué aux joueurs" : "";
    }

    private static String wallet() {
        return Ui.po(ClientState.wallet) + (ClientState.bankAvailable && ClientState.bank >= 0 ? "  ·  Banque " + Ui.po(ClientState.bank) : "");
    }

    private void buy(String method) {
        PacketDistributor.sendToServer(new Payloads.Buy(offer.id, method));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        if (offer == null) return;
        int left = px + 78, right = px + pw - 24 - font.width(wallet());
        Ui.title(g, font, Ui.ellipsis(font, offer.name, Math.max(40, (int) ((right - left - 40) / 1.4f))), (left + right) / 2, py + 13, 1.4f);
        Ui.divider(g, px + 10, py + 36, pw - 20);

        // grand vitrail
        int arch = Math.max(20, Math.min(44, iw / 3));
        g.fill(ix - 4, iy - 3, ix + iw + 4, iy + ih + 3, Ui.STONE_DARK);
        ImageCache.Tex t = ImageCache.get(offer.image);
        if (t != null) {
            g.fill(ix, iy, ix + iw, iy + ih, 0xFF050407);
            ImageCache.draw(g, t, ix + 2, iy + 2, iw - 4, ih - 4);
        } else {
            Ui.glass(g, ix, iy, iw, ih);
        }
        Ui.archMask(g, ix, iy, iw, arch, Ui.STONE_DARK, Ui.GOLD);
        Ui.windowSides(g, ix, iy, iw, ih, arch, Ui.GOLD);
        if (t == null && !stack.isEmpty()) {
            float sc = Math.min(5f, (Math.min(iw, ih - arch) - 24) / 16f);
            g.pose().pushPose();
            g.pose().translate(ix + iw / 2f - 8 * sc, iy + arch / 2f + ih / 2f - 8 * sc, 0);
            g.pose().scale(sc, sc, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        } else if (t == null && !offer.image.isBlank() && !ImageCache.failed(offer.image)) {
            g.drawCenteredString(font, "Chargement…", ix + iw / 2, iy + ih / 2, Ui.TEXT_DIM);
        }

        // velin
        Ui.vellum(g, tx, iy, tw, Math.max(40, payTop - 6 - iy));
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        if (offer == null) return;
        String w = wallet();
        g.drawString(font, w, px + pw - 14 - font.width(w), py + 16, Ui.GOLD, false);

        int y = iy + 7;
        if (!offer.category.isBlank()) {
            g.drawString(font, offer.category.toUpperCase(java.util.Locale.ROOT), tx + 7, y, Ui.BLOOD_LIGHT, false);
            y += 12;
        }
        int maxLines = Math.max(3, (payTop - 6 - y - 40) / 10);
        String desc = offer.description.isBlank() ? "Aucune description." : offer.description;
        y += Ui.illuminated(g, font, desc, tx + 7, y, tw - 14, Ui.INK, maxLines) + 4;
        if (!stack.isEmpty()) {
            g.fill(tx + 6, y - 1, tx + 24, y + 17, 0x33000000);
            Ui.frame(g, tx + 6, y - 1, 18, 18, Ui.GOLD_DIM);
            g.renderItem(stack, tx + 7, y);
            g.renderItemDecorations(font, stack, tx + 7, y);
            g.drawString(font, Ui.ellipsis(font, "Vous recevez : " + stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), tw - 36),
                    tx + 28, y + 4, Ui.INK, false);
            if (mx >= tx + 6 && mx < tx + 24 && my >= y && my < y + 16) g.renderTooltip(font, stack, mx, my);
            y += 20;
        } else if (!offer.commands.isEmpty() || ClientState.admin) {
            g.drawString(font, offer.commands.isEmpty() ? "Aucun objet ni commande !" : "Récompense spéciale", tx + 7, y + 2, Ui.INK_LIGHT, false);
            y += 14;
        }
        StringBuilder info = new StringBuilder();
        if (offer.stock > 0) info.append("Stock : ").append(offer.stock).append("   ");
        if (offer.limitPerPlayer > 0) info.append("Achats : ").append(ClientState.bought.getOrDefault(offer.id, 0)).append(" / ").append(offer.limitPerPlayer);
        if (!info.isEmpty() && y < payTop - 18) g.drawString(font, info.toString(), tx + 7, y, Ui.INK_LIGHT, false);
        if (!status.isEmpty()) g.drawString(font, status, tx + tw / 2 - font.width(status) / 2, payTop - 16, 0xFFB0303A, false);
        else if (offer.realPriceCents > 0 && ClientState.paypalReady) {
            String n = Ui.ellipsis(font, "Paiement sécurisé PayPal · livré automatiquement", tw - 10);
            g.drawString(font, n, tx + tw / 2 - font.width(n) / 2, payTop - 16, Ui.INK_LIGHT, false);
        }
        if (payCount == 0) g.drawCenteredString(font, "Aucun moyen de paiement disponible", tx + tw / 2, py + ph - 30, 0xFFE06A4A);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
