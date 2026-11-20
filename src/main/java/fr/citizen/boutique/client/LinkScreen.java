package fr.citizen.boutique.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Confirmation avant d'ouvrir la page de paiement PayPal dans le navigateur. */
public class LinkScreen extends BaseScreen {
    private final Screen parent;
    private final String url;
    private boolean opened;

    public LinkScreen(Screen parent, String url) {
        super("PayPal");
        this.parent = parent;
        this.url = url;
    }

    /** Seuls les liens de paiement PayPal sont acceptes. */
    static boolean isPayPal(String url) {
        return url.startsWith("https://www.paypal.com/") || url.startsWith("https://www.sandbox.paypal.com/");
    }

    @Override public boolean liveUpdate() { return false; }

    @Override
    protected void init() {
        layout(320, 170);
        int bw = (pw - 40) / 2;
        addRenderableWidget(new Ui.MButton(px + 16, py + ph - 52, pw - 32, 20, opened ? "Rouvrir la page PayPal" : "Ouvrir la page de paiement PayPal",
                Ui.MButton.Style.ROYAL, () -> { Util.getPlatform().openUri(url); opened = true; rebuildWidgets(); }));
        addRenderableWidget(new Ui.MButton(px + 16, py + ph - 28, bw, 15, "Copier le lien", Ui.MButton.Style.WOOD, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
            ClientState.toast("Lien copié dans le presse-papiers.", true);
        }));
        addRenderableWidget(new Ui.MButton(px + pw - 16 - bw, py + ph - 28, bw, 15, opened ? "Retour à la boutique" : "Annuler", Ui.MButton.Style.WOOD, this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.title(g, font, "Soutenir le Royaume", px + pw / 2, py + 12, 1.4f);
        Ui.divider(g, px + 10, py + 32, pw - 20);
        Ui.vellum(g, px + 16, py + 40, pw - 32, ph - 98);
        String t = opened
                ? "Terminez le paiement dans votre navigateur. Votre récompense arrivera toute seule en jeu dans la minute qui suit. Merci !"
                : "La page sécurisée de PayPal va s'ouvrir dans votre navigateur. Payez par compte PayPal ou par carte : la récompense est livrée automatiquement en jeu.";
        Ui.wrapped(g, font, t, px + 22, py + 46, pw - 44, Ui.INK, 5);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
