package fr.citizen.boutique.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Fenetre « Ouvrir un fichier » du systeme (LWJGL tinyfd, fourni avec Minecraft). */
final class FileDialog {
    private FileDialog() { }

    /** Chemin choisi, ou null si annule / indisponible. Bloquant : a appeler hors du thread de rendu. */
    static String openImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog("Choisir l'image de l'article", "", filters, "Images (png, jpg, gif)", false);
        } catch (Throwable t) {
            ClientState.toast("Fenêtre de fichiers indisponible : glissez l'image sur la fenêtre.", false);
            return null;
        }
    }
}
