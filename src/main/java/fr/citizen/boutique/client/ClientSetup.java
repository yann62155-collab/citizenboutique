package fr.citizen.boutique.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.citizen.boutique.CitizenBoutique;
import fr.citizen.boutique.net.Network;
import fr.citizen.boutique.net.Payloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Initialisation cote client uniquement (classe chargee seulement quand Dist == CLIENT). */
public final class ClientSetup {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.citizenboutique.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.citizenboutique");
    private static final int CHUNK = 24_000;

    private static EditScreen uploadTarget;
    private static byte[] uploadPng;

    private ClientSetup() { }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null && mc.getConnection() != null) {
                    if (mc.getConnection().hasChannel(Payloads.Open.TYPE)) PacketDistributor.sendToServer(new Payloads.Open(0));
                    else ClientState.toast("Ce serveur n'a pas la boutique Citizen.", false);
                }
            }
        });

        Network.clientSync = p -> Minecraft.getInstance().execute(() -> {
            try { ClientState.apply(p.json()); }
            catch (RuntimeException ex) { CitizenBoutique.LOGGER.warn("Boutique reçue invalide : {}", ex.toString()); return; }
            Minecraft mc = Minecraft.getInstance();
            Screen s = mc.screen;
            if (s instanceof BaseScreen bs) { if (bs.liveUpdate()) bs.refresh(); }
            else if (p.open() && s == null) mc.setScreen(new ShopScreen());
        });

        Network.clientMessage = p -> Minecraft.getInstance().execute(() -> {
            if ("open_url".equals(p.kind())) {
                Minecraft mc = Minecraft.getInstance();
                if (!LinkScreen.isPayPal(p.text())) { CitizenBoutique.LOGGER.warn("Lien de paiement refusé : {}", p.text()); return; }
                mc.setScreen(new LinkScreen(mc.screen, p.text()));
                return;
            }
            if ("upload".equals(p.kind())) {
                if (uploadPng != null) ImageCache.put(p.text(), uploadPng);
                uploadPng = null;
                if (uploadTarget != null) uploadTarget.onUploaded(p.text());
                uploadTarget = null;
                return;
            }
            boolean ok = "ok".equals(p.kind());
            ClientState.toast(p.text(), ok);
            if (!ok && uploadTarget != null) { uploadTarget.onUploadFailed(p.text()); uploadTarget = null; uploadPng = null; }
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal((ok ? "§6" : "§c") + p.text()), true);
        });

        Network.clientImage = p -> Minecraft.getInstance().execute(() -> ImageCache.onServerImage(p));
    }

    /** Action d'administration (verifiee par le serveur). */
    static void admin(String action, String json) {
        PacketDistributor.sendToServer(new Payloads.Admin(action, json));
    }

    /** Reduit l'image en PNG 256 px puis l'envoie au serveur par morceaux. */
    static void uploadImage(Path file, EditScreen target) {
        Thread t = new Thread(() -> {
            try {
                if (Files.size(file) > 20L * 1024 * 1024) throw new IllegalArgumentException("fichier trop lourd (max 20 Mo)");
                byte[] png = ImageCache.toPng(Files.readAllBytes(file));
                String id = UUID.randomUUID().toString();
                int total = (png.length + CHUNK - 1) / CHUNK;
                Minecraft.getInstance().execute(() -> {
                    uploadTarget = target;
                    uploadPng = png;
                    for (int i = 0; i < total; i++) {
                        byte[] part = Arrays.copyOfRange(png, i * CHUNK, Math.min(png.length, (i + 1) * CHUNK));
                        PacketDistributor.sendToServer(new Payloads.Upload(id, i, total, part));
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> target.onUploadFailed("Image refusée : " + e.getMessage()));
            }
        }, "CitizenBoutique-upload");
        t.setDaemon(true);
        t.start();
    }
}
