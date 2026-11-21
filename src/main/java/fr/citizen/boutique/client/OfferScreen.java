package fr.citizen.boutique.client;

import fr.citizen.boutique.net.Payloads;
import fr.citizen.boutique.shop.ItemJson;
import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fiche detaillee d'un article : grande carte, avantages, description, prix et paiement PayPal. */
public class OfferScreen extends BaseScreen {
    private final Screen parent;
    private final String offerId;
    private ShopData.Offer offer;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean confirmDelete;
    private int ix, iy, iw, ih, tx, tw, priceY;
    private String status = "";

    public OfferScreen(Screen parent, String offerId) {
        super("Article");
        this.parent = parent;
        this.offerId = offerId;
    }

    @Override
    protected void init() {
        layout(520, 290);
        offer = ClientState.offer(offerId);
        if (offer == null) { Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(parent)); return; }
        Minecraft mc = Minecraft.getInstance();
        stack = mc.level == null ? ItemStack.EMPTY : ItemJson.decode(offer.item, mc.level.registryAccess());

        ix = px + 18; iy = py + 44; iw = Math.min(180, pw / 2 - 40); ih = ph - 44 - (ClientState.admin ? 44 : 18);
        tx = ix + iw + 16; tw = px + pw - 18 - tx;

        addRenderableWidget(new Ui.MButton(px + 14, py + 12, 64, 17, "< Retour", Ui.MButton.Style.WOOD, this::onClose));

        boolean soldOut = offer.stock == 0;
        boolean limit = offer.limitPerPlayer > 0 && ClientState.bought.getOrDefault(offer.id, 0) >= offer.limitPerPlayer;
        Ui.PayPalButton pay = new Ui.PayPalButton(tx, py + ph - 40, tw, 20, () -> PacketDistributor.sendToServer(new Payloads.Buy(offer.id, "paypal")));
        pay.active = ClientState.paypalReady && !soldOut && !limit && offer.enabled && offer.realPriceCents > 0;
        addRenderableWidget(pay);
        priceY = py + ph - 64;

        if (ClientState.admin) {
            int ay = py + ph - 34, ax = ix;
            addRenderableWidget(new Ui.MButton(ax, ay, 20, 17, "▲", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":-1}")));
            ax += 22;
            addRenderableWidget(new Ui.MButton(ax, ay, 20, 17, "▼", Ui.MButton.Style.WOOD,
                    () -> ClientSetup.admin("move_offer", "{\"id\":\"" + offer.id + "\",\"dir\":1}")));
            ax += 24;
            int bw2 = (iw - 50) / 2;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 17, "Modifier", Ui.MButton.Style.GOLD,
                    () -> Minecraft.getInstance().setScreen(new EditScreen(this, offer))));
            ax += bw2 + 4;
            addRenderableWidget(new Ui.MButton(ax, ay, bw2, 17, confirmDelete ? "Sûr ?" : "Supprimer", Ui.MButton.Style.RED, () -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                ClientSetup.admin("delete_offer", "{\"id\":\"" + offer.id + "\"}");
                Minecraft.getInstance().setScreen(parent);
            }));
        }
        status = !ClientState.paypalReady ? "Bientôt disponible" : soldOut ? "Rupture de stock" : limit ? "Limite d'achat atteinte"
                : !offer.enabled ? "Article masqué aux joueurs" : "";
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        if (offer == null) return;
        Ui.titleText(g, Ui.ellipsisTitle(offer.name, (int) ((pw - 200) / 1.5f)), px + pw / 2f, py + 12, Ui.GOLD_LIGHT, 1.5f, true, true);
        Ui.divider(g, px + 40, py + 32, pw - 80, Ui.GOLD);

        // grande carte
        Ui.card(g, ix, iy, iw, ih, offer.popular, false);
        ImageCache.Tex t = ImageCache.get(offer.image);
        if (t != null) ImageCache.draw(g, t, ix + 8, iy + 8, iw - 16, ih - 16);
        else if (!stack.isEmpty()) {
            float sc = Math.min(6f, (Math.min(iw, ih) - 30) / 16f);
            g.pose().pushPose();
            g.pose().translate(ix + iw / 2f - 8 * sc, iy + ih / 2f - 8 * sc, 0);
            g.pose().scale(sc, sc, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        } else if (!offer.image.isBlank() && !ImageCache.failed(offer.image)) {
            Ui.textCentered(g, "Chargement…", ix + iw / 2, iy + ih / 2, Ui.CREAM_DIM, true);
        } else Ui.ornament(g, ix + iw / 2, iy + ih / 2, 14);

        // colonne de texte
        Ui.column(g, tx, iy, tw, priceY - 6 - iy);
    }

    @Override
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) {
        if (offer == null) return;
        int y = iy + 7, maxY = priceY - 12;
        if (!offer.category.isBlank()) { Ui.titleText(g, offer.category.toUpperCase(java.util.Locale.ROOT), tx + 8, y, Ui.GOLD_TEXT, 0.8f, false, true); y += 12; }
        for (String f : offer.features) {
            if (y + 10 > maxY) break;
            Ui.check(g, tx + 8, y + 1, Ui.GOLD_TEXT);
            Ui.text(g, Ui.ellipsis(f, tw - 30), tx + 21, y, Ui.CREAM, true);
            y += 11;
        }
        if (!offer.description.isBlank() && y + 14 < maxY) {
            y += 4;
            Ui.divider(g, tx + 10, y, tw - 20, Ui.GOLD_DARK);
            y += 6;
            y += Ui.wrapped(g, offer.description, tx + 8, y, tw - 16, Ui.CREAM_DIM, Math.max(1, (maxY - y - 20) / 10), true);
        }
        if (!stack.isEmpty() && y + 20 < maxY + 6) {
            y += 4;
            g.fill(tx + 7, y, tx + 25, y + 18, 0xFF120D09);
            Ui.goldBevel(g, tx + 7, y, 18, 18);
            g.renderItem(stack, tx + 8, y + 1);
            g.renderItemDecorations(font, stack, tx + 8, y + 1);
            Ui.text(g, Ui.ellipsis("Vous recevez : " + stack.getHoverName().getString(), tw - 40), tx + 30, y + 5, Ui.CREAM, true);
            if (mx >= tx + 7 && mx < tx + 25 && my >= y && my < y + 18) g.renderTooltip(font, stack, mx, my);
        }
        // prix
        String price = offer.realPriceCents > 0 ? Ui.money(offer.realPriceCents, shop().settings.currency) : "—";
        int pwid = Math.max(90, Ui.widthTitle(price) * 2 + 30);
        Ui.pricePlate(g, tx + (tw - pwid) / 2, priceY, pwid, 20);
        Ui.titleText(g, price, tx + tw / 2f, priceY + 4, Ui.INK, 1.4f, true, false);
        String note = status.isEmpty() ? "Paiement sécurisé · livraison automatique" : status;
        Ui.textCentered(g, note, tx + tw / 2, py + ph - 16, status.isEmpty() ? Ui.CREAM_DIM : 0xFFFF8A70, true);
        StringBuilder info = new StringBuilder();
        if (offer.stock > 0) info.append("Stock : ").append(offer.stock).append("   ");
        if (offer.limitPerPlayer > 0) info.append("Achats : ").append(ClientState.bought.getOrDefault(offer.id, 0)).append(" / ").append(offer.limitPerPlayer);
        if (!info.isEmpty()) Ui.text(g, info.toString(), tx + 8, priceY - 16, Ui.CREAM_DIM, true);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
