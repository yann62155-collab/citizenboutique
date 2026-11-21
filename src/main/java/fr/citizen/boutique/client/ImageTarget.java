package fr.citizen.boutique.client;

/** Ecran qui recoit le resultat d'un envoi d'image au serveur. */
interface ImageTarget {
    void onUploaded(String key);
    void onUploadFailed(String why);
}
