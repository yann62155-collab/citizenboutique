package fr.citizen.boutique.client;

import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Socle commun : panneau en bois, bandeau de message, mise a jour quand le serveur renvoie la boutique. */
public abstract class BaseScreen extends Screen {
    protected int px, py, pw, ph;

    protected BaseScreen(String title) { super(Component.literal(title)); }

    protected void layout(int maxW, int maxH) {
        pw = Math.min(width - 16, maxW);
        ph = Math.min(height - 16, maxH);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    /** Appele quand une nouvelle version de la boutique arrive. */
    public void refresh() { rebuildWidgets(); }

    /** Les ecrans de saisie ne doivent pas etre reconstruits pendant que l'admin tape. */
    public boolean liveUpdate() { return true; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.panel(g, px, py, pw, ph);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderOver(g, mx, my, pt);
        if (System.currentTimeMillis() < ClientState.messageUntil && !ClientState.message.isEmpty()) {
            String m = ClientState.message;
            int w = Math.min(pw - 20, font.width(m) + 16);
            int x = px + (pw - w) / 2, y = py >= 20 ? py - 19 : py + ph - 20;
            g.fill(x, y, x + w, y + 16, ClientState.messageOk ? 0xF0244A16 : 0xF0601414);
            Ui.frame(g, x, y, w, 16, Ui.GOLD);
            g.drawCenteredString(font, Ui.ellipsis(font, m, w - 8), x + w / 2, y + 4, 0xFFFFF1C1);
        }
    }

    /** Dessins par-dessus les boutons (infobulles, textes). */
    protected void renderOver(GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public boolean isPauseScreen() { return false; }

    protected static ShopData shop() { return ClientState.shop; }
}
