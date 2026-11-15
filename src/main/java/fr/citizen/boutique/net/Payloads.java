package fr.citizen.boutique.net;

import fr.citizen.boutique.CitizenBoutique;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tous les paquets de la boutique. Rien de ce qu'envoie le client n'est cru : le serveur reverifie tout. */
public final class Payloads {
    private Payloads() { }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CitizenBoutique.MOD_ID, path));
    }

    /** Client -> serveur : ouvrir la boutique (touche F6). */
    public record Open(int unused) implements CustomPacketPayload {
        public static final Type<Open> TYPE = type("open");
        public static final StreamCodec<ByteBuf, Open> CODEC = ByteBufCodecs.VAR_INT.map(Open::new, Open::unused);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Serveur -> client : contenu de la boutique (JSON). open = ouvrir l'ecran, sinon simple mise a jour s'il est ouvert. */
    public record Sync(String json, boolean open) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = type("sync");
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(900_000), Sync::json,
                ByteBufCodecs.BOOL, Sync::open,
                Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> serveur : acheter un article avec un moyen de paiement (coins / bank / barter / xp / free). */
    public record Buy(String offerId, String method) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = type("buy");
        public static final StreamCodec<ByteBuf, Buy> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Buy::offerId,
                ByteBufCodecs.stringUtf8(16), Buy::method,
                Buy::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> serveur : action d'administration (save_offer, delete_offer, move_offer, save_settings). */
    public record Admin(String action, String json) implements CustomPacketPayload {
        public static final Type<Admin> TYPE = type("admin");
        public static final StreamCodec<ByteBuf, Admin> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Admin::action,
                ByteBufCodecs.stringUtf8(9_000), Admin::json,
                Admin::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> serveur : morceau d'une image envoyee par un admin (PNG deja reduit cote client). */
    public record Upload(String uploadId, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = type("upload");
        public static final StreamCodec<ByteBuf, Upload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(40), Upload::uploadId,
                ByteBufCodecs.VAR_INT, Upload::index,
                ByteBufCodecs.VAR_INT, Upload::total,
                ByteBufCodecs.BYTE_ARRAY, Upload::data,
                Upload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> serveur : demande d'une image stockee sur le serveur. */
    public record ImageRequest(String name) implements CustomPacketPayload {
        public static final Type<ImageRequest> TYPE = type("image_request");
        public static final StreamCodec<ByteBuf, ImageRequest> CODEC = ByteBufCodecs.stringUtf8(80).map(ImageRequest::new, ImageRequest::name);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Serveur -> client : image PNG (vide si introuvable). */
    public record ImageData(String name, byte[] data) implements CustomPacketPayload {
        public static final Type<ImageData> TYPE = type("image_data");
        public static final StreamCodec<ByteBuf, ImageData> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(80), ImageData::name,
                ByteBufCodecs.BYTE_ARRAY, ImageData::data,
                ImageData::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Serveur -> client : message a afficher dans la boutique. kind = ok / error / upload (text = nom de l'image). */
    public record Message(String kind, String text) implements CustomPacketPayload {
        public static final Type<Message> TYPE = type("message");
        public static final StreamCodec<ByteBuf, Message> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Message::kind,
                ByteBufCodecs.stringUtf8(512), Message::text,
                Message::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
