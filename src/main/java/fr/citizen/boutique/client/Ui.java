package fr.citizen.boutique.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Theme medieval gothique dessine sans texture : pierre noire maconnee, or terni, pourpre sang,
 * ogives et vitraux, flechettes et pinacles, velin et lettrines.
 */
public final class Ui {
    // pierre
    public static final int STONE_DARK = 0xFF08070A;
    public static final int STONE = 0xFF141218;
    public static final int STONE_LIGHT = 0xFF221F28;
    public static final int MORTAR = 0xFF0B0A0E;
    public static final int IRON = 0xFF2B2830;
    // metal et sang
    public static final int GOLD = 0xFFC9A646;
    public static final int GOLD_BRIGHT = 0xFFEAD18A;
    public static final int GOLD_DIM = 0xFF6E5824;
    public static final int BLOOD = 0xFF4E0B12;
    public static final int BLOOD_LIGHT = 0xFF7E1420;
    public static final int MOSS = 0xFF1F3A1E;
    // velin
    public static final int VELLUM = 0xFFE3D2A8;
    public static final int VELLUM_DARK = 0xFFB89F6C;
    public static final int INK = 0xFF231410;
    public static final int INK_LIGHT = 0xFF5B4030;
    // vitrail
    public static final int GLASS_TOP = 0xFF2B1840;
    public static final int GLASS_BOTTOM = 0xFF0E0A1A;
    public static final int LEAD = 0x55000000;

    /** Couleurs de texte. */
    public static final int TEXT = 0xFFE3D2A8;
    public static final int TEXT_DIM = 0xFF9C8A66;

    private Ui() { }

    // ------------------------------------------------------------------ formes de base

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Triangle plein (pinacle, fleche, pignon). Pointe vers le haut, base sur baseY. */
    public static void spire(GuiGraphics g, int cx, int baseY, int halfW, int h, int color, int edge) {
        for (int i = 0; i < h; i++) {
            int w = Math.round(halfW * (h - i) / (float) h);
            g.fill(cx - w, baseY - i - 1, cx + w + 1, baseY - i, color);
            if (edge != 0) {
                g.fill(cx - w, baseY - i - 1, cx - w + 1, baseY - i, edge);
                g.fill(cx + w, baseY - i - 1, cx + w + 1, baseY - i, edge);
            }
        }
    }

    /** Petit losange (fleuron). */
    public static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = r - Math.abs(i);
            g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
        }
    }

    /** Croix patee (✠ dessinee en pixels). */
    public static void cross(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 4, cx + 2, cy + 5, color);
        g.fill(cx - 4, cy - 1, cx + 5, cy + 2, color);
        g.fill(cx - 2, cy - 4, cx + 3, cy - 3, color);
        g.fill(cx - 2, cy + 3, cx + 3, cy + 4, color);
        g.fill(cx - 4, cy - 2, cx - 3, cy + 3, color);
        g.fill(cx + 3, cy - 2, cx + 4, cy + 3, color);
    }

    /** Mur de pierre maconne. */
    private static void masonry(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, STONE);
        int row = 0;
        for (int yy = y; yy < y + h; yy += 9, row++) {
            g.fill(x, yy, x + w, yy + 1, MORTAR);
            int off = (row % 2) * 9;
            for (int xx = x + off; xx < x + w; xx += 18) g.fill(xx, yy, xx + 1, Math.min(y + h, yy + 9), MORTAR);
            g.fill(x, yy + 1, x + w, yy + 2, 0x0FFFFFFF);
        }
        // vignettage
        g.fillGradient(x, y, x + w, y + 24, 0x66000000, 0x00000000);
        g.fillGradient(x, y + h - 24, x + w, y + h, 0x00000000, 0x77000000);
    }

    /**
     * Grand panneau gothique : pierre noire, bordure or / pourpre, pinacles aux coins
     * et pignon central avec croix au-dessus du bord superieur (prevoir ~16 px au-dessus).
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xB0000000);
        // pignon central
        int gw = Math.min(70, w / 4);
        spire(g, x + w / 2, y + 1, gw, 16, STONE_LIGHT, GOLD);
        cross(g, x + w / 2, y - 6, GOLD);
        // pinacles
        for (int px : new int[]{x + 4, x + w - 5}) {
            g.fill(px - 3, y - 6, px + 4, y + 1, STONE_LIGHT);
            frame(g, px - 3, y - 6, 7, 7, GOLD_DIM);
            spire(g, px, y - 6, 3, 10, STONE_LIGHT, GOLD);
            g.fill(px, y - 18, px + 1, y - 15, GOLD);
        }
        masonry(g, x, y, w, h);
        frame(g, x, y, w, h, STONE_DARK);
        frame(g, x + 1, y + 1, w - 2, h - 2, GOLD);
        g.fill(x + 2, y + 2, x + w - 2, y + 4, BLOOD);
        g.fill(x + 2, y + h - 4, x + w - 2, y + h - 2, BLOOD);
        g.fill(x + 2, y + 2, x + 4, y + h - 2, BLOOD);
        g.fill(x + w - 4, y + 2, x + w - 2, y + h - 2, BLOOD);
        frame(g, x + 4, y + 4, w - 8, h - 8, GOLD_DIM);
        // fleurons d'angle
        for (int[] c : new int[][]{{x + 9, y + 9}, {x + w - 10, y + 9}, {x + 9, y + h - 10}, {x + w - 10, y + h - 10}}) {
            diamond(g, c[0], c[1], 3, GOLD_DIM);
            diamond(g, c[0], c[1], 1, GOLD_BRIGHT);
        }
    }

    /** Encadre de velin (pour les textes longs) avec filet pourpre. */
    public static void vellum(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, VELLUM_DARK);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE9DAB4, 0xFFD6C291);
        frame(g, x, y, w, h, GOLD_DIM);
        frame(g, x + 2, y + 2, w - 4, h - 4, 0x667E1420);
    }

    /** Ancien nom conserve : encadre clair. */
    public static void parchment(GuiGraphics g, int x, int y, int w, int h) { vellum(g, x, y, w, h); }

    /** Encadre sombre (fond de liste, cartouche). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC07060A);
        frame(g, x, y, w, h, GOLD_DIM);
    }

    /** Filet dore avec fleurons. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        int cx = x + w / 2;
        g.fill(x + 6, y, cx - 8, y + 1, GOLD_DIM);
        g.fill(cx + 9, y, x + w - 6, y + 1, GOLD_DIM);
        diamond(g, cx, y, 3, GOLD);
        diamond(g, cx - 12, y, 1, GOLD_DIM);
        diamond(g, cx + 12, y, 1, GOLD_DIM);
        diamond(g, x + 3, y, 1, GOLD_DIM);
        diamond(g, x + w - 4, y, 1, GOLD_DIM);
    }

    // ------------------------------------------------------------------ ogives et vitraux

    /**
     * Masque en ogive : recouvre les coins superieurs d'un rectangle avec la couleur du fond
     * et trace l'arc dore. A dessiner APRES l'image du vitrail.
     */
    public static void archMask(GuiGraphics g, int x, int y, int w, int archH, int mask, int line) {
        double a = w / 2.0, r = a * 1.6, apex = Math.sqrt(r * r - (r - a) * (r - a));
        double cx = x + a;
        for (int i = 0; i < archH; i++) {
            double yy = (archH - i) / (double) archH * apex;
            double hw = Math.sqrt(Math.max(0, r * r - yy * yy)) - (r - a);
            hw = Math.max(0, Math.min(a, hw));
            int l = (int) Math.round(cx - hw), rr = (int) Math.round(cx + hw);
            if (l > x) g.fill(x, y + i, l, y + i + 1, mask);
            if (rr < x + w) g.fill(rr, y + i, x + w, y + i + 1, mask);
            g.fill(l - 1, y + i, l + 1, y + i + 1, line);
            g.fill(rr - 1, y + i, rr + 1, y + i + 1, line);
        }
    }

    /** Cotes et appui d'un vitrail sous l'ogive. */
    public static void windowSides(GuiGraphics g, int x, int y, int w, int h, int archH, int line) {
        g.fill(x, y + archH, x + 1, y + h, line);
        g.fill(x + w - 1, y + archH, x + w, y + h, line);
        g.fill(x, y + h - 1, x + w, y + h, line);
        g.fill(x - 1, y + h, x + w + 1, y + h + 2, GOLD_DIM);
    }

    /** Fond de vitrail sans image : verre sombre et plombs. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, GLASS_TOP, GLASS_BOTTOM);
        for (int xx = x + 7; xx < x + w; xx += 8) g.fill(xx, y, xx + 1, y + h, LEAD);
        for (int yy = y + 7; yy < y + h; yy += 8) g.fill(x, yy, x + w, yy + 1, LEAD);
        diamond(g, x + w / 2, y + h / 2, Math.min(w, h) / 6, 0x334E0B12);
    }

    /** Hauteur d'ogive adaptee a la largeur. */
    public static int archHeight(int w) { return Math.max(8, Math.min(26, w / 3)); }

    // ------------------------------------------------------------------ textes

    public static void title(GuiGraphics g, Font f, String text, int cx, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int w = f.width(text);
        g.drawString(f, text, -w / 2 + 1, 1, 0xFF000000, false);
        g.drawString(f, text, -w / 2, 0, GOLD_BRIGHT, false);
        g.pose().popPose();
        int half = Math.round(w * scale / 2f);
        cross(g, cx - half - 10, y + Math.round(4 * scale), BLOOD_LIGHT);
        cross(g, cx + half + 10, y + Math.round(4 * scale), BLOOD_LIGHT);
    }

    public static void subtitle(GuiGraphics g, Font f, String text, int cx, int y) {
        g.drawCenteredString(f, Component.literal(text).withStyle(ChatFormatting.ITALIC), cx, y, TEXT_DIM);
    }

    /** Coupe un texte en lignes (largeur variable par ligne). */
    public static List<String> wrapLines(Font f, String text, int width, int firstLinesWidth, int firstLines) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                int max = out.size() < firstLines ? firstLinesWidth : width;
                String cand = line.isEmpty() ? word : line + " " + word;
                if (f.width(cand) <= max || line.isEmpty()) {
                    line.setLength(0);
                    line.append(cand);
                    while (f.width(line.toString()) > max && line.length() > 1) { // mot trop long
                        int cut = line.length() - 1;
                        while (cut > 1 && f.width(line.substring(0, cut)) > max) cut--;
                        out.add(line.substring(0, cut));
                        line.delete(0, cut);
                        max = out.size() < firstLines ? firstLinesWidth : width;
                    }
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Texte sur plusieurs lignes ; renvoie la hauteur utilisee. */
    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y, int w, int color, int maxLines) {
        List<String> lines = wrapLines(f, text, w, w, 0);
        int n = 0;
        for (String l : lines) {
            if (n >= maxLines) break;
            g.drawString(f, l, x, y + n * 10, color, false);
            n++;
        }
        return n * 10;
    }

    /** Texte avec lettrine enluminee (premiere lettre en grand, pourpre et or). Renvoie la hauteur. */
    public static int illuminated(GuiGraphics g, Font f, String text, int x, int y, int w, int color, int maxLines) {
        text = text.strip();
        if (text.isEmpty()) return 0;
        String first = text.substring(0, text.offsetByCodePoints(0, 1));
        String rest = text.substring(first.length());
        int box = 26;
        g.fill(x, y, x + box, y + box, BLOOD);
        frame(g, x, y, box, box, GOLD);
        frame(g, x + 2, y + 2, box - 4, box - 4, GOLD_DIM);
        g.pose().pushPose();
        g.pose().translate(x + box / 2f - f.width(first) * 1.25f, y + 4, 0);
        g.pose().scale(2.5f, 2.5f, 1f);
        g.drawString(f, first, 0, 0, GOLD_BRIGHT, false);
        g.pose().popPose();
        List<String> lines = wrapLines(f, rest, w, w - box - 4, 3);
        int n = 0;
        for (String l : lines) {
            if (n >= maxLines) break;
            int lx = n < 3 ? x + box + 4 : x;
            g.drawString(f, l, lx, y + n * 10 + 1, color, false);
            n++;
        }
        return Math.max(box + 2, n * 10 + 2);
    }

    public static String ellipsis(Font f, String s, int w) {
        if (f.width(s) <= w) return s;
        while (!s.isEmpty() && f.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static String po(long v) {
        String s = String.format(java.util.Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ');
        return s + " PO";
    }

    /** 499 -> "4,99 €" (ou code devise). */
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

    // ------------------------------------------------------------------ widgets

    /** Bouton gothique (coins abattus). */
    public static class MButton extends AbstractButton {
        public enum Style { GOLD, WOOD, RED, GREEN, ROYAL }
        private final Runnable action;
        private final Style style;

        public MButton(int x, int y, int w, int h, String text, Style style, Runnable action) {
            super(x, y, w, h, Component.literal(text));
            this.action = action;
            this.style = style;
        }

        public void setText(String t) { setMessage(Component.literal(t)); }

        @Override public void onPress() { if (action != null) action.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = active && isHoveredOrFocused();
            int base = switch (style) {
                case GOLD -> hot ? 0xFF5E4818 : 0xFF3A2C0E;
                case WOOD -> hot ? 0xFF2E2A36 : 0xFF1A1820;
                case RED -> hot ? BLOOD_LIGHT : BLOOD;
                case GREEN -> hot ? 0xFF2C5228 : MOSS;
                case ROYAL -> hot ? 0xFF9A1A28 : 0xFF6A0F1A;
            };
            if (!active) base = 0xFF141317;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int border = active ? (hot ? GOLD_BRIGHT : GOLD_DIM) : 0xFF35313A;
            g.fill(x + 1, y, x + w - 1, y + h, border);
            g.fill(x, y + 1, x + w, y + h - 1, border);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, brighten(base), base);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x55000000);
            if (style == Style.ROYAL && active) {
                diamond(g, x + 5, y + h / 2, 2, GOLD);
                diamond(g, x + w - 6, y + h / 2, 2, GOLD);
            }
            Font f = Minecraft.getInstance().font;
            String txt = ellipsis(f, getMessage().getString(), w - (style == Style.ROYAL ? 18 : 6));
            int color = active ? (hot ? 0xFFFFF4D0 : GOLD_BRIGHT) : 0xFF6E6658;
            g.drawCenteredString(f, txt, x + w / 2, y + (h - 8) / 2, color);
        }

        private static int brighten(int c) {
            int r = Math.min(255, ((c >> 16) & 0xFF) + 22), gg = Math.min(255, ((c >> 8) & 0xFF) + 18), b = Math.min(255, (c & 0xFF) + 18);
            return 0xFF000000 | (r << 16) | (gg << 8) | b;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    /** Case a cocher : ✠ coche / ○ vide. */
    public static class Toggle extends MButton {
        private final String label;
        private boolean value;

        public Toggle(int x, int y, int w, int h, String label, boolean value) {
            super(x, y, w, h, "", Style.WOOD, null);
            this.label = label;
            this.value = value;
            refresh();
        }

        public boolean value() { return value; }
        public void set(boolean v) { value = v; refresh(); }
        private void refresh() { setText((value ? "[✠] " : "[  ] ") + label); }

        @Override public void onPress() { value = !value; refresh(); }
    }
}
