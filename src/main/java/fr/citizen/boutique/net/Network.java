package fr.citizen.boutique.net;

import fr.citizen.boutique.shop.ShopService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/** Enregistrement des paquets. Aucun import client ici : ce code est charge aussi sur le serveur dedie. */
public final class Network {
    public static final String VERSION = "1";

    /** Renseignes cote client uniquement (ClientSetup). */
    public static volatile Consumer<Payloads.Sync> clientSync;
    public static volatile Consumer<Payloads.Message> clientMessage;
    public static volatile Consumer<Payloads.ImageData> clientImage;

    private Network() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional() : un client sans le mod (console via Geyser/ViaProxy) peut toujours se connecter.
        PayloadRegistrar r = event.registrar(VERSION).optional();
        r.playToServer(Payloads.Open.TYPE, Payloads.Open.CODEC, (p, ctx) -> withPlayer(ctx, pl -> ShopService.sendShop(pl, true)));
        r.playToServer(Payloads.Buy.TYPE, Payloads.Buy.CODEC, (p, ctx) -> withPlayer(ctx, pl -> ShopService.buy(pl, p.offerId(), p.method())));
        r.playToServer(Payloads.Admin.TYPE, Payloads.Admin.CODEC, (p, ctx) -> withPlayer(ctx, pl -> ShopService.admin(pl, p.action(), p.json())));
        r.playToServer(Payloads.Upload.TYPE, Payloads.Upload.CODEC, (p, ctx) -> withPlayer(ctx, pl -> ShopService.upload(pl, p)));
        r.playToServer(Payloads.ImageRequest.TYPE, Payloads.ImageRequest.CODEC, (p, ctx) -> withPlayer(ctx, pl -> ShopService.sendImage(pl, p.name())));

        r.playToClient(Payloads.Sync.TYPE, Payloads.Sync.CODEC, (p, ctx) -> { Consumer<Payloads.Sync> h = clientSync; if (h != null) h.accept(p); });
        r.playToClient(Payloads.Message.TYPE, Payloads.Message.CODEC, (p, ctx) -> { Consumer<Payloads.Message> h = clientMessage; if (h != null) h.accept(p); });
        r.playToClient(Payloads.ImageData.TYPE, Payloads.ImageData.CODEC, (p, ctx) -> { Consumer<Payloads.ImageData> h = clientImage; if (h != null) h.accept(p); });
    }

    private static void withPlayer(IPayloadContext ctx, Consumer<ServerPlayer> action) {
        if (ctx.player() instanceof ServerPlayer sp) action.accept(sp);
    }
}
