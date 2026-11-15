package fr.citizen.boutique.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Theme medieval : bois sombre, or ancien, parchemin, rouge sombre (dessine sans texture). */
public final class Ui {
    public static final int WOOD_DARK = 0xFF140D08;
    public static final int WOOD = 0xFF2E1F14;
    public static final int WOOD_LIGHT = 0xFF4A3220;
    public static final int GOLD = 0xFFD4AF37;
    public static final int GOLD_DIM = 0xFF8C6D1F;
    public static final int PARCH = 0xFFEBDDB4;
    public static final int PARCH_DARK = 0xFFCDB580;
    public static final int INK = 0xFF3A2413;
    public static final int INK_LIGHT = 0xFF6B4A2B;
    public static final int RED = 0xFF7A1C1C;
    public static final int RED_LIGHT = 0xFF9E2B2B;
    public static final int GREEN = 0xFF2F5E1E;

    private Ui() { }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Grand panneau en bois cercle d'or, avec rivets aux coins. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xAA000000);
        g.fill(x, y, x + w, y + h, WOOD_DARK);
        frame(g, x, y, w, h, GOLD_DIM);
        frame(g, x + 2, y + 2, w - 4, h - 4, GOLD);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, WOOD);
        // veinage du bois
        for (int yy = y + 9; yy < y + h - 4; yy += 11) g.fill(x + 4, yy, x + w - 4, yy + 1, 0x22000000);
        for (int[] c : new int[][]{{x, y}, {x + w - 5, y}, {x, y + h - 5}, {x + w - 5, y + h - 5}}) {
            g.fill(c[0], c[1], c[0] + 5, c[1] + 5, GOLD);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 4, c[1] + 4, GOLD_DIM);
        }
    }

    /** Zone parchemin (bords assombris). */
    public static void parchment(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PARCH_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PARCH);
        frame(g, x, y, w, h, INK_LIGHT);
    }

    /** Separateur dore avec losange central. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GOLD_DIM);
        int cx = x + w / 2;
        g.fill(cx - 2, y - 1, cx + 3, y + 2, GOLD);
    }

    public static void title(GuiGraphics g, Font f, String text, int cx, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int w = f.width(text);
        g.drawString(f, text, -w / 2 + 1, 1, 0xFF000000, false);
        g.drawString(f, text, -w / 2, 0, GOLD, false);
        g.pose().popPose();
    }

    /** Texte sur plusieurs lignes ; renvoie la hauteur utilisee. */
    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> lines = f.split(Component.literal(text), w);
        int n = 0;
        for (FormattedCharSequence l : lines) {
            if (n >= maxLines) break;
            g.drawString(f, l, x, y + n * 10, color, false);
            n++;
        }
        return n * 10;
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

    /** Bouton style medieval. */
    public static class MButton extends AbstractButton {
        public enum Style { GOLD, WOOD, RED, GREEN }
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
                case GOLD -> hot ? 0xFF6E5214 : 0xFF4F3A0E;
                case WOOD -> hot ? WOOD_LIGHT : 0xFF3A2718;
                case RED -> hot ? RED_LIGHT : RED;
                case GREEN -> hot ? 0xFF3F7A28 : GREEN;
            };
            if (!active) base = 0xFF2A2420;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0xFF0D0805);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFFFFF);
            frame(g, x, y, w, h, active ? (hot ? GOLD : GOLD_DIM) : 0xFF4A4038);
            Font f = Minecraft.getInstance().font;
            String txt = ellipsis(f, getMessage().getString(), w - 6);
            int color = active ? (hot ? 0xFFFFF1C1 : 0xFFF0DFA8) : 0xFF8A8070;
            g.drawCenteredString(f, txt, x + w / 2, y + (h - 8) / 2, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    /** Case a cocher medievale : [X] / [ ]. */
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
        private void refresh() { setText((value ? "[X] " : "[ ] ") + label); }

        @Override public void onPress() { value = !value; refresh(); }
    }
}
