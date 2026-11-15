package fr.citizen.boutique.client;

import fr.citizen.boutique.net.Payloads;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fiche d'un article : grande image, description, objet et boutons de paiement. */
public class OfferScreen extends BaseScreen {
    private final Screen parent;
    private final String offerId;
    private ShopData.Offer offer;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean confirmDelete;
    private int ix, iy, iw, ih, tx, tw;

    public OfferScreen(Screen parent, String offerId) {
        super("Article");
        this.parent = parent;
        this.offerId = offerId;
    }

    @Override
    protected void init() {
        layout(440, 270);
        offer = ClientState.offer(offerId);
        if (offer == null) { Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(parent)); return; }
        Minecraft mc = Minecraft.getInstance();
        stack = mc.level == null ? ItemStack.EMPTY : ItemJson.decode(offer.item, mc.level.registryAccess());
        ShopData.Settings s = shop().settings;

        ix = px + 12; iy = py + 38; iw = Math.min(170, pw / 2 - 20); ih = ph - 38 - (ClientState.admin ? 40 : 14);
        tx = ix + iw + 12; tw = px + pw - 12 - tx;

        addRenderableWidget(new Ui.MButton(px + 10, py + 10, 60, 16, "< Retour", Ui.MButton.Style.WOOD, this::onClose));

        // --- moyens de paiement disponibles
        boolean soldOut = offer.stock == 0;
        boolean limit = offer.limitPerPlayer > 0 && ClientState.bought.getOrDefault(offer.id, 0) >= offer.limitPerPlayer;
        boolean canBuy = !soldOut && !limit && offer.enabled;
        int bw = tw, by = py + ph - 32;
        java.util.List<Ui.MButton> pay = new java.util.ArrayList<>();
        boolean free = offer.price <= 0 && offer.xpLevels <= 0 && (offer.barterItem.isBlank() || offer.barterCount <= 0);
        if (free) {
            pay.add(new Ui.MButton(0, 0, bw, 18, "Obtenir gratuitement", Ui.MButton.Style.GREEN, () -> buy("free")));
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
        for (int i = pay.size() - 1; i >= 0; i--) {
            Ui.MButton b = pay.get(i);
            b.setPosition(tx, by);
            if (!canBuy) b.active = false;
            addRenderableWidget(b);
            by -= 21;
        }
        payTop = by + 21;
        payCount = pay.size();

        // --- outils admin (sous l'image)
        if (ClientState.admin) {
            int ay = py + ph - 30, ax = ix;
            addRenderableWidget(new Ui.MButton(ax, ay, 18, 16, "▲", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":-1}")));
            ax += 20;
            addRenderableWidget(new Ui.MButton(ax, ay, 18, 16, "▼", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":1}")));
            ax += 20;
            int bw2 = (iw - 44) / 2;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 16, "Modifier", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, offer))));
            ax += bw2 + 4;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 16, confirmDelete ? "Confirmer ?" : "Supprimer", Ui.MButton.Style.RED, () -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                ClientSetup.admin("delete_offer", "{\"id\":\"" + offer.id + "\"}");
                Minecraft.getInstance().setScreen(parent);
            }));
        }
        status = soldOut ? "Rupture de stock" : limit ? "Limite d'achat atteinte" : !offer.enabled ? "Article masqué aux joueurs" : "";
    }

    private int payTop, payCount;
    private String status = "";

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
        int left = px + 76, right = px + pw - 22 - font.width(wallet());
        Ui.title(g, font, Ui.ellipsis(font, offer.name, Math.max(40, (int) ((right - left) / 1.4f))), (left + right) / 2, py + 12, 1.4f);
        Ui.divider(g, px + 10, py + 32, pw - 20);
        // cadre de l'image
        Ui.parchment(g, ix, iy, iw, ih);
        ImageCache.Tex t = ImageCache.get(offer.image);
        if (t != null) ImageCache.draw(g, t, ix + 4, iy + 4, iw - 8, ih - 8);
        else if (!stack.isEmpty()) {
            float sc = Math.min(6f, (Math.min(iw, ih) - 20) / 16f);
            g.pose().pushPose();
            g.pose().translate(ix + iw / 2f - 8 * sc, iy + ih / 2f - 8 * sc, 0);
            g.pose().scale(sc, sc, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        } else if (!offer.image.isBlank() && !ImageCache.failed(offer.image)) {
            g.drawCenteredString(font, "Chargement…", ix + iw / 2, iy + ih / 2, Ui.INK_LIGHT);
        }
        // parchemin de description
        int dh = payTop - 6 - iy;
        Ui.parchment(g, tx, iy, tw, Math.max(40, dh));
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        if (offer == null) return;
        int y = iy + 6;
        if (!offer.category.isBlank()) { g.drawString(font, offer.category.toUpperCase(java.util.Locale.ROOT), tx + 6, y, 0xFF8C6D1F, false); y += 12; }
        int maxLines = Math.max(1, (payTop - 6 - y - 46) / 10);
        y += Ui.wrapped(g, font, offer.description.isBlank() ? "Aucune description." : offer.description, tx + 6, y, tw - 12, Ui.INK, maxLines);
        y += 4;
        // objet recu
        if (!stack.isEmpty()) {
            g.renderItem(stack, tx + 6, y);
            g.renderItemDecorations(font, stack, tx + 6, y);
            g.drawString(font, Ui.ellipsis(font, "Vous recevez : " + stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), tw - 34),
                    tx + 26, y + 4, Ui.INK, false);
            if (mx >= tx + 6 && mx < tx + 22 && my >= y && my < y + 16) g.renderTooltip(font, stack, mx, my);
            y += 18;
        } else if (!offer.commands.isEmpty() || ClientState.admin) {
            g.drawString(font, offer.commands.isEmpty() ? "Aucun objet ni commande !" : "Récompense spéciale", tx + 6, y + 4, Ui.INK_LIGHT, false);
            y += 14;
        }
        StringBuilder info = new StringBuilder();
        if (offer.stock > 0) info.append("Stock : ").append(offer.stock).append("   ");
        if (offer.limitPerPlayer > 0) info.append("Achats : ").append(ClientState.bought.getOrDefault(offer.id, 0)).append(" / ").append(offer.limitPerPlayer);
        if (!info.isEmpty()) g.drawString(font, info.toString(), tx + 6, y + 2, Ui.INK_LIGHT, false);
        if (!status.isEmpty()) g.drawCenteredString(font, status, tx + tw / 2, payTop - 16, 0xFFE06A4A);
        if (payCount == 0) g.drawCenteredString(font, "Aucun moyen de paiement disponible", tx + tw / 2, py + ph - 28, 0xFFE06A4A);

        String w = wallet();
        g.drawString(font, w, px + pw - 12 - font.width(w), py + 14, Ui.GOLD, false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
