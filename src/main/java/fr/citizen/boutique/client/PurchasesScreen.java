package fr.citizen.boutique.client;

import fr.citizen.boutique.shop.ShopData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;

/** « Mes Achats » : ce que le joueur a deja soutenu. */
public class PurchasesScreen extends BaseScreen {
    private final Screen parent;

    public PurchasesScreen(Screen parent) {
        super("Mes Achats");
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout(340, 240);
        addRenderableWidget(new Ui.MButton(px + (pw - 100) / 2, py + ph - 28, 100, 17, "Retour", Ui.MButton.Style.GOLD, this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        screenTitle(g, "Mes Achats");
        int x = px + 18, w = pw - 36, y = py + 44;
        Ui.column(g, x, y, w, py + ph - 36 - y);
        if (ClientState.bought.isEmpty()) {
            Ui.textCentered(g, "Vous n'avez encore rien acheté.", px + pw / 2, y + 20, Ui.CREAM_DIM, true);
            Ui.textCentered(g, "Merci de penser à soutenir le serveur !", px + pw / 2, y + 32, Ui.CREAM_DIM, true);
            return;
        }
        int ly = y + 6;
        for (Map.Entry<String, Integer> e : ClientState.bought.entrySet()) {
            if (ly + 12 > py + ph - 40) break;
            ShopData.Offer o = ClientState.offer(e.getKey());
            String n = o == null ? "Article retiré de la boutique" : o.name;
            Ui.check(g, x + 8, ly + 1, Ui.GOLD_TEXT);
            Ui.text(g, Ui.ellipsis(n, w - 70), x + 22, ly, Ui.CREAM, true);
            String c = "× " + e.getValue();
            Ui.text(g, c, x + w - 10 - Ui.width(c), ly, Ui.GOLD_TEXT, true);
            ly += 13;
        }
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
