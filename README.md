# Citizen Boutique (`citizenboutique`)

Boutique médiévale gothique du serveur **Citizen**, ouverte avec **F6** sur PC.
Minecraft 1.21.1 · NeoForge **21.1.215** · Java 21. Mod à installer **sur le serveur et chez les joueurs** (Citizen Updater s'en charge).

## Pour les joueurs
- **F6** (modifiable dans Options → Commandes → Citizen Boutique) ou `/boutique` : ouvre l'échoppe.
- Catégories à gauche, recherche en haut, étals avec image, nom et prix.
- Cliquer un étal : fiche complète (grande image, description, objet reçu) et boutons de paiement.

## Pour les admins (OP niveau 2, ou liste d'admins dans les Réglages)
Tout se fait **dans le jeu, sans redémarrer le serveur** : chaque modification est enregistrée tout de suite et la boutique se met à jour chez les joueurs qui l'ont ouverte.

### Ajouter un article : bouton « + Article »
1. Prends l'objet à vendre **en main** (avec ses enchantements, son nom…) et clique **« Prendre l'objet en main »**.
2. Remplis le nom, la catégorie, le prix en PO, la description.
3. **Image** (3 façons) :
   - **glisser-déposer** un fichier PNG/JPG sur la fenêtre de Minecraft ;
   - bouton **« Parcourir… »** ;
   - coller un **lien** http(s) (par ex. une image de ton site d'affiches, port 30089).
   Les fichiers sont réduits à 256 px et **stockés sur le serveur** (`config/citizenboutique/images/`) : tous les joueurs voient l'image, même si le lien d'origine disparaît.
4. Options : stock (-1 = illimité), limite d'achats par joueur, troc (bouton « Troc = objet main gauche »), niveaux d'XP, commandes console (`{player}` et `{uuid}` sont remplacés), « En vente » pour masquer l'article sans le supprimer.
5. **Enregistrer**.

Sur la fiche d'un article : **Modifier**, **Supprimer** (2 clics), **▲ ▼** pour changer l'ordre.

### Réglages
- Titre et sous-titre de la boutique.
- **Moyens de paiement** activés ou non pour toute la boutique : Pièces, Banque, Troc, Expérience.
- « → Trésorerie » : les PO dépensées (pièces ou banque) vont au **Trésor royal** de Citizen Economy.
- Catégories (séparées par des virgules, dans l'ordre d'affichage).
- Pièces acceptées : `citizeneconomy:coin_platinum=1000, citizeneconomy:coin_gold=100, citizeneconomy:coin_silver=10, citizeneconomy:coin_bronze=1` (bouton « + Pièce en main » pour en ajouter une).
- Admins de la boutique (pseudos) et niveau d'OP requis.

## Moyens de paiement
| Moyen | Fonctionnement |
|---|---|
| Pièces | Prend les pièces PO de l'inventaire et rend la monnaie dans les plus grosses pièces possibles. |
| Banque | Débite le compte personnel Citizen Economy (visible seulement si Citizen Economy est installé). |
| Troc | Prend X objets précis (ex. 5 diamants). |
| Expérience | Retire X niveaux d'expérience. |
| Gratuit | Automatique si l'article n'a aucun prix. |

Chaque article peut accepter plusieurs moyens : le joueur choisit.

## Soutenir le serveur avec PayPal (argent réel)
1. Sur https://developer.paypal.com → **Apps & Credentials** : crée une application et copie son **Client ID** et son **Secret**
   (onglet **Sandbox** pour tester avec de faux comptes, onglet **Live** pour les vrais paiements).
2. En jeu : **Réglages → onglet PayPal** : coche « Activer PayPal », colle le Client ID et le Secret, choisis « Mode test » ou non, enregistre.
   Le serveur teste la connexion et te répond tout de suite.
3. Dans l'éditeur d'un article, remplis le champ **Soutien €** (ex. `4,99`). L'article apparaît dans l'onglet **« Soutenir le Royaume »**.

Côté joueur : bouton « Soutenir — 4,99 € via PayPal » → la page sécurisée PayPal s'ouvre dans son navigateur → dès que le paiement est encaissé,
l'article est livré automatiquement en jeu (ou à sa prochaine connexion) et un message de remerciement peut être annoncé dans le chat.

Sécurité : le **Secret** reste sur le serveur (`boutique.json`) et n'est jamais envoyé aux joueurs, ni même aux admins. Le prix est fixé par le serveur,
le montant encaissé est vérifié avant livraison, une commande n'est jamais livrée deux fois. Tout est noté dans `paypal.json` et `achats.log`.

Commandes : `/boutique paypal` (10 derniers soutiens et leur état), `/boutique paypal verifier` (forcer la vérification).

⚠️ Règles de Mojang (Minecraft Usage Guidelines) : vends des avantages **cosmétiques** ou des dons de soutien, pas d'objets qui donnent un avantage de jeu (pay-to-win).

## Commandes
- `/boutique` : ouvrir la boutique.
- `/boutique ouvrir <joueur>` (admin) : ouvrir la boutique d'un joueur, pratique pour un **PNJ marchand** ou un bloc de commande.
- `/boutique recharger` (admin) : relire `boutique.json` après une modification à la main par FTP.

## Fichiers (serveur, dossier `config/citizenboutique/`)
- `boutique.json` : articles et réglages.
- `achats.json` : nombre d'achats par joueur (pour les limites).
- `achats.log` : journal de tous les achats (date, joueur, article, moyen, prix).
- `images/` : images des articles.

## Limites connues
- Joueurs **console** (Geyser) : l'écran ne peut pas s'afficher chez eux ; ils peuvent se connecter sans problème (le mod est optionnel côté réseau).
- Paiement par banque : la ligne apparaît dans l'historique bancaire du joueur comme un « Retrait en espèces » (API actuelle de Citizen Economy).
