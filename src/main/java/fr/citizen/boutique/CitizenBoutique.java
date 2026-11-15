package fr.citizen.boutique;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import fr.citizen.boutique.net.Network;
import fr.citizen.boutique.shop.ShopService;
import fr.citizen.boutique.shop.ShopStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(CitizenBoutique.MOD_ID)
public class CitizenBoutique {
    public static final String MOD_ID = "citizenboutique";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenBoutique(IEventBus modBus, ModContainer container) {
        modBus.addListener(Network::register);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onCommands);
        NeoForge.EVENT_BUS.addListener(this::onLogin);
        if (FMLEnvironment.dist == Dist.CLIENT) fr.citizen.boutique.client.ClientSetup.init(modBus);
    }

    private void onServerStarting(ServerStartingEvent e) { ShopStore.load(); }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ShopService.onLogin(p);
    }

    private static boolean admin(CommandSourceStack src) {
        return !(src.getEntity() instanceof ServerPlayer p) ? src.hasPermission(2) : ShopService.isAdmin(p);
    }

    private void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("boutique")
                .executes(c -> { ShopService.sendShop(c.getSource().getPlayerOrException(), true); return 1; })
                // Pour un PNJ marchand / bloc de commande : /boutique ouvrir @p
                .then(Commands.literal("ouvrir").requires(CitizenBoutique::admin)
                        .then(Commands.argument("joueur", EntityArgument.players()).executes(c -> {
                            int n = 0;
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "joueur")) { ShopService.sendShop(p, true); n++; }
                            return n;
                        })))
                // Recharger boutique.json apres une modification a la main (FTP)
                .then(Commands.literal("recharger").requires(CitizenBoutique::admin).executes(c -> {
                    ShopStore.load();
                    ShopService.broadcast(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("§6Boutique rechargée : " + ShopStore.data().offers.size() + " article(s)."), true);
                    return 1;
                })));
    }
}
