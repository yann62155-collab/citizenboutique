package fr.citizen.boutique.client;

import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Socle commun : panneau dore, bandeau de message, mise a jour quand le serveur renvoie la boutique. */
public abstract class BaseScreen extends Screen {
    protected int px, py, pw, ph;

    protected BaseScreen(String title) { super(Component.literal(title)); }

    /** Panneau centre ; topSpace = place reservee au-dessus (logo, bannieres). */
    protected void layout(int maxW, int maxH, int topSpace) {
        pw = Math.min(width - 16, maxW);
        ph = Math.min(height - 24 - topSpace, maxH);
        px = (width - pw) / 2;
        py = (height - ph + topSpace) / 2;
    }

    protected void layout(int maxW, int maxH) { layout(maxW, maxH, 8); }

    /** Appele quand une nouvelle version de la boutique arrive. */
    public void refresh() { rebuildWidgets(); }

    /** Les ecrans de saisie ne doivent pas etre reconstruits pendant que l'admin tape. */
    public boolean liveUpdate() { return true; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.panel(g, px, py, pw, ph);
    }

    /** Titre d'ecran secondaire (Cinzel dore) avec filet. */
    protected void screenTitle(GuiGraphics g, String title) {
        Ui.titleText(g, title, px + pw / 2f, py + 12, Ui.GOLD_LIGHT, 1.5f, true, true);
        Ui.divider(g, px + 40, py + 32, pw - 80, Ui.GOLD);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderOver(g, mx, my, pt);
        if (System.currentTimeMillis() < ClientState.messageUntil && !ClientState.message.isEmpty()) {
            String m = ClientState.message;
            int w = Math.min(pw - 20, Ui.width(m) + 20);
            int x = px + (pw - w) / 2, y = py + ph + 5;
            if (y + 16 > height) y = py + ph - 22;
            g.fillGradient(x, y, x + w, y + 16, ClientState.messageOk ? 0xF02E6E2A : 0xF08E1C1C, ClientState.messageOk ? 0xF01A4418 : 0xF05A0E0E);
            Ui.goldBevel(g, x, y, w, 16);
            String mm = Ui.ellipsis(m, w - 10);
            Ui.text(g, mm, x + (w - Ui.width(mm)) / 2, y + 4, Ui.CREAM, true);
        }
    }

    /** Dessins par-dessus les boutons (infobulles, textes). */
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public boolean isPauseScreen() { return false; }

    protected static ShopData shop() { return ClientState.shop; }
}
