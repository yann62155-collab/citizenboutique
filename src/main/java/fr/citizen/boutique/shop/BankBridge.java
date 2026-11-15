package fr.citizen.boutique.shop;

import fr.citizen.boutique.CitizenBoutique;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Lien facultatif avec Citizen Economy, par reflexion (aucune dependance de compilation) :
 * - solde bancaire personnel,
 * - paiement par la banque (debit du compte personnel),
 * - recettes versees a la tresorerie royale.
 * Si Citizen Economy est absent ou change d'API, ces fonctions renvoient simplement "indisponible".
 */
public final class BankBridge {
    private BankBridge() { }

    public static boolean available() {
        if (!ModList.get().isLoaded("citizeneconomy")) return false;
        try { return economy() != null; } catch (Throwable t) { return false; }
    }

    private static Object economy() throws Exception {
        Class<?> rt = Class.forName("fr.citizen.economy.platform.EconomyRuntime");
        Object current = rt.getMethod("current").invoke(null);
        if (current == null) return null;
        return rt.getMethod("economy").invoke(current);
    }

    private static Class<?> actorClass() throws ClassNotFoundException {
        return Class.forName("fr.citizen.economy.domain.Actor");
    }

    private static Object actor(UUID id, String name) throws Exception {
        Constructor<?> c = actorClass().getConstructor(UUID.class, String.class, boolean.class);
        return c.newInstance(id, name, false);
    }

    private static Object field(Object o, String name) throws Exception {
        return o.getClass().getField(name).get(o);
    }

    /** Solde du compte personnel, ou -1 si indisponible. */
    public static CompletableFuture<Long> balance(UUID player, String name) {
        try {
            Object eco = economy();
            if (eco == null) return CompletableFuture.completedFuture(-1L);
            Object bank = field(eco, "bank");
            Method m = bank.getClass().getMethod("personalBalance", actorClass());
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> f = (CompletableFuture<Object>) m.invoke(bank, actor(player, name));
            return f.thenApply(v -> v instanceof Number n ? n.longValue() : 0L).exceptionally(t -> -1L);
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(-1L);
        }
    }

    /** Debite le compte personnel. Le futur echoue avec un message lisible (fonds insuffisants...). */
    public static CompletableFuture<Void> withdraw(UUID player, String name, long amount) {
        try {
            Object eco = economy();
            if (eco == null) return CompletableFuture.failedFuture(new IllegalStateException("La banque est fermée pour le moment."));
            Object bank = field(eco, "bank");
            Method m = bank.getClass().getMethod("withdraw", actorClass(), UUID.class, long.class);
            CompletableFuture<?> f = (CompletableFuture<?>) m.invoke(bank, actor(player, name), UUID.randomUUID(), amount);
            return f.thenApply(v -> (Void) null);
        } catch (Throwable t) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : paiement bancaire impossible : {}", t.toString());
            return CompletableFuture.failedFuture(new IllegalStateException("La banque est indisponible."));
        }
    }

    /** Versement des recettes a la tresorerie royale (sans effet si indisponible). */
    public static void treasuryRevenue(long amount, String reason) {
        if (amount <= 0) return;
        try {
            Object eco = economy();
            if (eco == null) return;
            Object treasury = field(eco, "treasury");
            Object council = actorClass().getMethod("council").invoke(null);
            Method m = treasury.getClass().getMethod("addRevenue", actorClass(), UUID.class, long.class, String.class);
            CompletableFuture<?> f = (CompletableFuture<?>) m.invoke(treasury, council, UUID.randomUUID(), amount, reason);
            f.exceptionally(t -> {
                CitizenBoutique.LOGGER.warn("Citizen Boutique : recette non versée à la trésorerie : {}", message(t));
                return null;
            });
        } catch (Throwable t) {
            CitizenBoutique.LOGGER.warn("Citizen Boutique : trésorerie indisponible : {}", t.toString());
        }
    }

    /** Message lisible d'une erreur (deballe CompletionException / InvocationTargetException). */
    public static String message(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException
                || t instanceof java.lang.reflect.InvocationTargetException) && t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return m == null || m.isBlank() ? "Paiement refusé." : m;
    }
}
