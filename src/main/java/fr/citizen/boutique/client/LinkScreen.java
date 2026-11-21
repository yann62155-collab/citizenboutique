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
        layout(330, 180);
        int bw = (pw - 44) / 2;
        Ui.PayPalButton pay = new Ui.PayPalButton(px + 18, py + ph - 54, pw - 36, 20, () -> { Util.getPlatform().openUri(url); opened = true; });
        addRenderableWidget(pay);
        addRenderableWidget(new Ui.MButton(px + 18, py + ph - 28, bw, 17, "Copier le lien", Ui.MButton.Style.WOOD, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
            ClientState.toast("Lien copié dans le presse-papiers.", true);
        }));
        addRenderableWidget(new Ui.MButton(px + pw - 18 - bw, py + ph - 28, bw, 17, "Retour à la boutique", Ui.MButton.Style.GOLD, this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        screenTitle(g, "Merci pour votre soutien");
        String t = opened
                ? "Terminez le paiement dans votre navigateur. Votre récompense arrivera toute seule en jeu dans la minute qui suit."
                : "La page sécurisée de PayPal va s'ouvrir dans votre navigateur. Payez par compte PayPal ou par carte bancaire : la récompense est livrée automatiquement en jeu.";
        Ui.wrapped(g, t, px + 22, py + 46, pw - 44, Ui.CREAM, 6, true);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
