# Citizen Boutique (`citizenboutique`)

**Boutique officielle** du serveur **Citizen**, ouverte avec **F6** sur PC (ou `/boutique`).
Elle sert uniquement à **soutenir le serveur en argent réel via PayPal**. Minecraft 1.21.1 · NeoForge **21.1.215** · Java 21.
Mod à installer **sur le serveur et chez les joueurs** (Citizen Updater s'en charge).

## Pour les joueurs
- Catégories à gauche (avec icônes), grande illustration, cartes de packs avec avantages, prix et bouton **« Payer avec PayPal »**.
- Clic sur une carte : fiche détaillée. Colonne **Mon Compte** : tête du joueur, son soutien, **Mes Achats**.
- Après le paiement sur la page PayPal, la récompense arrive **automatiquement en jeu** (ou à la prochaine connexion).

## Pour les admins (OP niveau 2+, ou pseudos listés dans les Réglages)
Personne d'autre ne peut ajouter, modifier ou supprimer un article. Tout se fait en jeu, **sans redémarrer**.
- **+ Article** : nom, catégorie, **prix en €**, stock, limite par joueur, **avantages** (un par ligne, affichés avec une coche),
  description, image de la carte (glisser-déposer, « Parcourir… » ou lien), option **« Le plus populaire »** (carte argentée avec ruban),
  objet donné (« Prendre l'objet en main ») et/ou commandes console (`{player}`, `{uuid}`).
- **+ Catég.** / **Modifier** : nom, **icône** (objet en main), **illustration du bandeau** et son texte, ordre, suppression.
- **Réglages** : texte du ruban, admins, PayPal (Client ID, Secret, mode test, devise, annonce des dons).

## PayPal
1. https://developer.paypal.com → **Apps & Credentials** → crée une application, copie **Client ID** et **Secret**
   (Sandbox pour tester, Live pour les vrais paiements).
2. En jeu : **Réglages** → colle les clés, coche « Boutique ouverte » (et « Mode test » pour essayer) → Enregistrer. Le serveur teste la connexion.
3. `/boutique paypal` : 10 derniers soutiens et leur état. `/boutique paypal verifier` : forcer la vérification.

Sécurité : le Secret reste sur le serveur (jamais envoyé aux joueurs ni aux admins), le prix est fixé par le serveur,
le montant encaissé est vérifié avant livraison, une commande n'est jamais livrée deux fois (`paypal.json`, `achats.log`).

⚠️ Règles de Mojang : vends des avantages **cosmétiques** ou des dons, pas d'objets qui donnent un avantage de jeu.

## Fichiers serveur (`config/citizenboutique/`)
`boutique.json` (articles, catégories, réglages), `paypal.json`, `achats.json`, `achats.log`, `images/`.
`/boutique recharger` relit `boutique.json` après une modification par FTP. `/boutique ouvrir <joueur>` pour un PNJ.

## Crédits
Polices Cinzel et Crimson Pro (SIL Open Font License, fichiers de licence dans `assets/citizenboutique/font/`).
