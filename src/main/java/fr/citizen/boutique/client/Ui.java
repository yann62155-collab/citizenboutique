package fr.citizen.boutique.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.citizen.boutique.CitizenBoutique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Theme « Boutique officielle CITIZEN » : bois sombre, or bisaute, cartes de packs, polices a empattements
 * (Cinzel pour les titres, Crimson Pro pour les textes, livrees avec le mod).
 */
public final class Ui {
    // fonds
    public static final int BG_TOP = 0xFF1E1712;
    public static final int BG_BOTTOM = 0xFF0C0907;
    public static final int CARD_TOP = 0xFF2C2016;
    public static final int CARD_BOTTOM = 0xFF140E09;
    // or
    public static final int GOLD_LIGHT = 0xFFF5D58A;
    public static final int GOLD = 0xFFC8963E;
    public static final int GOLD_DARK = 0xFF6E4A1C;
    public static final int GOLD_TEXT = 0xFFF0C060;
    // argent (carte populaire)
    public static final int STEEL_TOP = 0xFF2A3446;
    public static final int STEEL_BOTTOM = 0xFF121822;
    public static final int SILVER_LIGHT = 0xFFE0E8F4;
    public static final int SILVER = 0xFFA8B4C6;
    public static final int SILVER_DARK = 0xFF4A5466;
    // textes
    public static final int CREAM = 0xFFF3E6C8;
    public static final int CREAM_DIM = 0xFFB8A684;
    public static final int INK = 0xFF2A1A08;
    public static final int RED = 0xFF8E1C1C;
    public static final int PAYPAL_TOP = 0xFF2C8CF0;
    public static final int PAYPAL_BOTTOM = 0xFF0B4BA8;

    public static final ResourceLocation TITLE_FONT = rl("title");
    public static final ResourceLocation TEXT_FONT = rl("text");
    public static final ResourceLocation LOGO = rl("textures/gui/logo.png");
    public static final ResourceLocation BANNER = rl("textures/gui/banner.png");
    public static final ResourceLocation HERO = rl("textures/gui/hero.png");
    private static final Set<ResourceLocation> SMOOTHED = new HashSet<>();

    private Ui() { }

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(CitizenBoutique.MOD_ID, p); }

    static Font font() { return Minecraft.getInstance().font; }

    // ------------------------------------------------------------------ textes (polices du mod)

    public static Component tx(String s) { return Component.literal(s).withStyle(st -> st.withFont(TEXT_FONT)); }
    public static Component ti(String s) { return Component.literal(s).withStyle(st -> st.withFont(TITLE_FONT)); }

    public static int width(String s) { return font().width(tx(s)); }
    public static int widthTitle(String s) { return font().width(ti(s)); }

    public static void text(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        g.drawString(font(), tx(s), x, y, color, shadow);
    }

    public static void textCentered(GuiGraphics g, String s, int cx, int y, int color, boolean shadow) {
        g.drawString(font(), tx(s), cx - width(s) / 2, y, color, shadow);
    }

    /** Titre en Cinzel, eventuellement agrandi. */
    public static void titleText(GuiGraphics g, String s, float cx, float y, int color, float scale, boolean center, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int w = widthTitle(s);
        g.drawString(font(), ti(s), center ? -w / 2 : 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static String ellipsis(String s, int w) {
        if (width(s) <= w) return s;
        while (!s.isEmpty() && width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static String ellipsisTitle(String s, int w) {
        if (widthTitle(s) <= w) return s;
        while (!s.isEmpty() && widthTitle(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static List<FormattedCharSequence> wrap(String s, int w) { return font().split(tx(s), w); }

    /** Texte sur plusieurs lignes ; renvoie la hauteur utilisee. */
    public static int wrapped(GuiGraphics g, String s, int x, int y, int w, int color, int maxLines, boolean shadow) {
        int n = 0;
        for (FormattedCharSequence l : wrap(s, w)) {
            if (n >= maxLines) break;
            g.drawString(font(), l, x, y + n * 10, color, shadow);
            n++;
        }
        return n * 10;
    }

    /** 499 -> "4,99 €". */
    public static String money(int cents, String currency) {
        String sym = switch (currency == null ? "" : currency) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            case "CHF" -> "CHF";
            case "CAD" -> "$ CA";
            default -> currency;
        };
        return String.format(java.util.Locale.FRANCE, "%d,%02d %s", cents / 100, cents % 100, sym);
    }

    // ------------------------------------------------------------------ formes

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Bordure bisautee (sombre / moyenne / reflet clair en haut a gauche). */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int light, int mid, int dark) {
        frame(g, x, y, w, h, dark);
        frame(g, x + 1, y + 1, w - 2, h - 2, mid);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
    }

    public static void goldBevel(GuiGraphics g, int x, int y, int w, int h) { bevel(g, x, y, w, h, GOLD_LIGHT, GOLD, GOLD_DARK); }

    public static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = r - Math.abs(i);
            g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
        }
    }

    /** Fleuron dore (losanges imbriques). */
    public static void ornament(GuiGraphics g, int cx, int cy, int r) {
        diamond(g, cx, cy, r, GOLD_DARK);
        diamond(g, cx, cy, r - 2, GOLD);
        diamond(g, cx, cy, Math.max(0, r - 4), GOLD_LIGHT);
    }

    /** Coche doree dessinee (sans dependre des polices). */
    public static void check(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 2, y + 5, color);
        g.fill(x + 1, y + 4, x + 3, y + 6, color);
        g.fill(x + 2, y + 5, x + 4, y + 7, color);
        g.fill(x + 3, y + 4, x + 5, y + 6, color);
        g.fill(x + 4, y + 3, x + 6, y + 5, color);
        g.fill(x + 5, y + 2, x + 7, y + 4, color);
        g.fill(x + 6, y + 1, x + 8, y + 3, color);
        g.fill(x + 7, y, x + 9, y + 2, color);
    }

    /** Grand panneau : bois sombre, double bordure doree bisautee, fleurons aux angles. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0x8C000000);
        g.fillGradient(x, y, x + w, y + h, BG_TOP, BG_BOTTOM);
        g.fillGradient(x, y, x + w, y + 40, 0x10FFE0A0, 0x00FFE0A0);
        goldBevel(g, x, y, w, h);
        frame(g, x + 3, y + 3, w - 6, h - 6, GOLD_DARK);
        frame(g, x + 5, y + 5, w - 10, h - 10, 0xA0C8963E);
        ornament(g, x + 2, y + 2, 6);
        ornament(g, x + w - 3, y + 2, 6);
        ornament(g, x + 2, y + h - 3, 6);
        ornament(g, x + w - 3, y + h - 3, 6);
        ornament(g, x + w / 2, y + h - 1, 5);
    }

    /** Zone sombre translucide encadree (colonnes). */
    public static void column(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x5A000000);
        frame(g, x, y, w, h, GOLD_DARK);
    }

    /** Carte de pack (or, ou argent si populaire). */
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean silver, boolean hot) {
        if (hot) g.fill(x - 2, y - 2, x + w + 2, y + h + 2, silver ? 0x55E0E8F4 : 0x55F5D58A);
        g.fillGradient(x, y, x + w, y + h, silver ? STEEL_TOP : CARD_TOP, silver ? STEEL_BOTTOM : CARD_BOTTOM);
        if (silver) bevel(g, x, y, w, h, SILVER_LIGHT, SILVER, SILVER_DARK);
        else goldBevel(g, x, y, w, h);
    }

    /** Plaque doree du prix. */
    public static void pricePlate(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xFFFFE6A0, GOLD);
        bevel(g, x, y, w, h, 0xFFFFF4D0, 0xFFE0B060, GOLD_DARK);
    }

    /** Filet dore avec fleuron central. */
    public static void divider(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
        diamond(g, x + w / 2, y, 2, color);
    }

    /** Texture du mod dessinee avec lissage (logo, banniere, illustration). */
    public static void blitSmooth(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        if (SMOOTHED.add(tex)) {
            try { Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(true, false); } catch (Exception ignored) { }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ boutons

    /**
     * Bouton : NAV (categorie, avec icone), GOLD (contour or lumineux), PAYPAL (bleu), RED, GREEN, WOOD (sombre).
     * ROYAL est un alias de GOLD (compatibilite).
     */
    public static class MButton extends AbstractButton {
        public enum Style { NAV, GOLD, PAYPAL, RED, GREEN, WOOD, ROYAL }
        private final Runnable action;
        private final Style style;
        private boolean selected;
        private ItemStack icon = ItemStack.EMPTY;
        private boolean titleFont;

        public MButton(int x, int y, int w, int h, String text, Style style, Runnable action) {
            super(x, y, w, h, Component.literal(text));
            this.action = action;
            this.style = style;
        }

        public void setText(String t) { setMessage(Component.literal(t)); }
        public MButton selected(boolean s) { this.selected = s; return this; }
        public MButton icon(ItemStack s) { this.icon = s == null ? ItemStack.EMPTY : s; return this; }
        public MButton titleFont() { this.titleFont = true; return this; }

        @Override public void onPress() { if (action != null) action.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = active && isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            String label = getMessage().getString();
            int textColor = CREAM;
            boolean shadow = true;
            switch (style) {
                case NAV -> {
                    if (selected) {
                        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x40F5D58A);
                        g.fillGradient(x, y, x + w, y + h, 0xFF4A3416, 0xFF2A1C0C);
                        bevel(g, x, y, w, h, GOLD_LIGHT, GOLD_LIGHT, GOLD);
                        diamond(g, x + w, y + h / 2, 3, GOLD_LIGHT);
                        textColor = 0xFFFFF0C0;
                    } else {
                        g.fillGradient(x, y, x + w, y + h, hot ? 0xFF33261A : 0xFF241B14, hot ? 0xFF201710 : 0xFF17110C);
                        bevel(g, x, y, w, h, hot ? GOLD : 0xFF8A6A3A, hot ? GOLD : 0xFF5A4222, 0xFF2A1C0E);
                    }
                }
                case GOLD, ROYAL -> {
                    if (hot) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x50F5D58A);
                    g.fillGradient(x, y, x + w, y + h, hot ? 0xFF6E5020 : 0xFF5A4018, 0xFF2E200C);
                    bevel(g, x, y, w, h, GOLD_LIGHT, GOLD_LIGHT, GOLD);
                    textColor = 0xFFFFF0C0;
                }
                case PAYPAL -> {
                    if (hot) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x6060B0FF);
                    g.fillGradient(x, y, x + w, y + h, hot ? 0xFF4AA0FF : PAYPAL_TOP, PAYPAL_BOTTOM);
                    frame(g, x, y, w, h, 0xFF06306E);
                    g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x55FFFFFF);
                    textColor = 0xFFFFFFFF;
                    shadow = false;
                }
                case RED -> {
                    g.fillGradient(x, y, x + w, y + h, hot ? 0xFFB02828 : 0xFF8E1C1C, 0xFF5A0E0E);
                    bevel(g, x, y, w, h, GOLD_LIGHT, GOLD, GOLD_DARK);
                }
                case GREEN -> {
                    g.fillGradient(x, y, x + w, y + h, hot ? 0xFF3E8A3A : 0xFF2E6E2A, 0xFF1A4418);
                    bevel(g, x, y, w, h, GOLD_LIGHT, GOLD, GOLD_DARK);
                }
                default -> {
                    g.fillGradient(x, y, x + w, y + h, hot ? 0xFF33261A : 0xFF241B14, 0xFF17110C);
                    bevel(g, x, y, w, h, hot ? GOLD : 0xFF8A6A3A, hot ? GOLD : 0xFF5A4222, 0xFF2A1C0E);
                }
            }
            if (!active) {
                g.fill(x, y, x + w, y + h, 0x88101010);
                textColor = 0xFF8A8070;
                shadow = false;
            }
            int left = x + 6;
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 5, y + (h - 16) / 2);
                left = x + 26;
            }
            Font f = font();
            Component c = titleFont ? ti(label) : tx(label);
            int avail = w - (left - x) - 6;
            String fit = titleFont ? ellipsisTitle(label, avail) : ellipsis(label, avail);
            c = titleFont ? ti(fit) : tx(fit);
            int tw = f.width(c);
            int tx0 = icon.isEmpty() ? x + (w - tw) / 2 : left;
            g.drawString(f, c, tx0, y + (h - 8) / 2, textColor, shadow);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    /** Bouton bleu « Payer avec PayPal » (PayPal en gras). */
    public static class PayPalButton extends MButton {
        public PayPalButton(int x, int y, int w, int h, Runnable action) { super(x, y, w, h, "Payer avec PayPal", Style.PAYPAL, action); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Component keep = getMessage();
            setMessage(Component.empty());
            super.renderWidget(g, mx, my, pt);
            setMessage(keep);
            Font f = font();
            Component a = tx("Payer avec ");
            Component b = Component.literal("PayPal").withStyle(st -> st.withFont(TEXT_FONT).withBold(true).withItalic(true));
            int w1 = f.width(a), w2 = f.width(b), x = getX() + (getWidth() - w1 - w2) / 2, y = getY() + (getHeight() - 8) / 2;
            int col = active ? 0xFFFFFFFF : 0xFF9AA8C0;
            g.drawString(f, a, x, y, col, false);
            g.drawString(f, b, x + w1, y, col, false);
        }
    }

    /** Case a cocher (doree quand cochee). */
    public static class Toggle extends MButton {
        private final String label;
        private boolean value;

        public Toggle(int x, int y, int w, int h, String label, boolean value) {
            super(x, y, w, h, "", Style.NAV, null);
            this.label = label;
            this.value = value;
            refresh();
        }

        public boolean value() { return value; }
        public void set(boolean v) { value = v; refresh(); }
        private void refresh() { setText((value ? "✔ " : "✘ ") + label); selected(value); }

        @Override public void onPress() { value = !value; refresh(); }
    }
}
