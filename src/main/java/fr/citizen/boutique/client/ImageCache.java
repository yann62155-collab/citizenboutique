package fr.citizen.boutique.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.citizen.boutique.CitizenBoutique;
import fr.citizen.boutique.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Images des articles : "server:xxx.png" (stockee sur le serveur, envoyee par le reseau Minecraft)
 * ou lien http(s). Converties en PNG 256 px max, puis gardees en texture pour la session.
 */
public final class ImageCache {
    public record Tex(ResourceLocation id, int w, int h) { }

    public static final int MAX_SIDE = 256;
    private static final Map<String, Tex> READY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int counter;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private ImageCache() { }

    /** Texture prete, ou null (le chargement demarre en arriere-plan). */
    public static Tex get(String key) {
        if (key == null || key.isBlank()) return null;
        Tex t = READY.get(key);
        if (t != null || LOADING.contains(key) || FAILED.contains(key)) return t;
        LOADING.add(key);
        if (key.startsWith("server:")) {
            PacketDistributor.sendToServer(new Payloads.ImageRequest(key.substring(7)));
        } else if (key.startsWith("http://") || key.startsWith("https://")) {
            Thread th = new Thread(() -> download(key), "CitizenBoutique-image");
            th.setDaemon(true);
            th.start();
        } else {
            LOADING.remove(key);
            FAILED.add(key);
        }
        return null;
    }

    public static boolean failed(String key) { return key != null && FAILED.contains(key); }

    private static void download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "CitizenBoutique/1.0").GET().build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode());
            byte[] raw;
            try (InputStream in = res.body()) { raw = in.readNBytes(8 * 1024 * 1024); }
            byte[] png = toPng(raw);
            Minecraft.getInstance().execute(() -> register(url, png));
        } catch (Exception e) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : image {} illisible : {}", url, e.toString());
            Minecraft.getInstance().execute(() -> { LOADING.remove(url); FAILED.add(url); });
        }
    }

    /** Reponse du serveur pour une image "server:". Appele sur le thread client. */
    static void onServerImage(Payloads.ImageData p) {
        String key = "server:" + p.name();
        if (p.data().length == 0) { LOADING.remove(key); FAILED.add(key); return; }
        register(key, p.data());
    }

    /** Enregistre directement une image (utilise apres un envoi par l'admin). */
    static void put(String key, byte[] png) {
        FAILED.remove(key);
        register(key, png);
    }

    private static void register(String key, byte[] png) {
        LOADING.remove(key);
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture tex = new DynamicTexture(img);
            tex.setFilter(true, false);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CitizenBoutique.MOD_ID, "shopimg/" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            Tex old = READY.put(key, new Tex(id, img.getWidth(), img.getHeight()));
            if (old != null) Minecraft.getInstance().getTextureManager().release(old.id());
        } catch (Exception e) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : image {} non affichable : {}", key, e.toString());
            FAILED.add(key);
        }
    }

    /** N'importe quel format lisible (png, jpg, gif, bmp) -> PNG reduit a MAX_SIDE px. */
    public static byte[] toPng(byte[] raw) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
        if (src == null) throw new IOException("format d'image non reconnu (utilisez PNG ou JPG)");
        int w = src.getWidth(), h = src.getHeight();
        double s = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * s)), nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", out);
        return out.toByteArray();
    }

    /** Remplit tout le cadre (recadrage au centre), comme une illustration. */
    public static void drawCover(GuiGraphics g, Tex t, int x, int y, int w, int h) {
        float s = Math.max(w / (float) t.w(), h / (float) t.h());
        int uw = Math.max(1, Math.round(w / s)), vh = Math.max(1, Math.round(h / s));
        int u = (t.w() - uw) / 2, v = (t.h() - vh) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(t.id(), x, y, w, h, (float) u, (float) v, uw, vh, t.w(), t.h());
        RenderSystem.disableBlend();
    }

    /** Dessine l'image centree dans le cadre en gardant ses proportions. */
    public static void draw(GuiGraphics g, Tex t, int x, int y, int w, int h) {
        float s = Math.min(w / (float) t.w(), h / (float) t.h());
        int dw = Math.max(1, (int) (t.w() * s)), dh = Math.max(1, (int) (t.h() * s));
        int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(t.id(), dx, dy, dw, dh, 0f, 0f, t.w(), t.h(), t.w(), t.h());
        RenderSystem.disableBlend();
    }
}
