# Onde Player 3.7.0 — En-tête stable et dégradés

- L’en-tête ne change plus de hauteur pendant un geste de défilement : il se replie seulement lorsque la liste s’immobilise.
- Six dégradés disponibles : Nuit électrique, Océan, Aurore, Braise, Forêt et Prune.
- Édition guidée de deux couleurs, direction, intensité, aperçu immédiat et retour au fond uni.
- Compatibilité avec le texte personnalisé et les fonds photo. Choix mémorisés localement.
- Tests de régression sur l’hystérésis du défilement.

# Changements V3.6.0

- Drag & drop en maintenant un morceau dans une playlist personnelle, avec ordre persistant en SQLite et défilement proche des bords.
- Bouton de sélection multiple conservé dans la barre de la playlist, tout en réservant l’appui long au déplacement.
- Rail d’accès rapide # A–Z à droite de la bibliothèque, compatible avec les caractères accentués, uniquement en tri alphabétique.
- Menu **Mises à jour** : manifeste JSON HTTPS configurable, vérification au maximum quotidienne, notes de version et ouverture explicite du téléchargement dans le navigateur.
- Numéro de version Android : `versionCode 12` / `versionName 3.6.0`.
- Même identifiant `fr.ondes.player`, schéma SQLite conservé. Aucune collecte de données musicales ajoutée.

> Publication de l’APK et du manifeste à faire par l'éditeur. Sans URL, l’application ne détecte pas de mises à jour. La signature identique est obligatoire pour installer un APK par-dessus l'ancien.

---

# Onde Player 3.5.3 — 05/11/2026

- **Thème** : couleur du texte principal paramétrable dans un troisième onglet du color picker, huit teintes rapides, aperçu temps réel et avertissement lorsque le contraste texte/fond est insuffisant. Texte secondaire teinté automatiquement lorsque le texte est personnalisé.
- **Recherche** : liste de résultats immédiatement visible dans la fenêtre, mise à jour au fil de la saisie, pochette et durée, lecture au toucher, bouton ✕ de réinitialisation sans fermeture, aucun filtre résiduel dans la bibliothèque.
- **Pertinence** : recherche insensible aux accents et permettant plusieurs mots répartis entre titre/artiste/album ; 7 nouveaux tests Java.
- **Espacement** : marges augmentées entre commandes d'en-tête, boutons du mini-lecteur, contrôles principaux, onglets du thème et actions de volets.
- **Compatibilité** : application `fr.ondes.player`, base SQLite et préférences antérieures préservées ; `versionCode 11`, `versionName 3.5.3`, Java 17.

# Onde Player 3.5.2 — 05/11/2026

- **Color picker visuel** dans l’éditeur de thème : carré saturation/luminosité, réglette de teintes arc-en-ciel, poignées et aperçu coloré en direct.
- **Enregistrement au relâchement** : les déplacements n’entraînent pas de reconstruction répétée de l’écran ; le volet reste ouvert et la lecture continue.
- **Palette conservée** : accents et fonds prédéfinis toujours disponibles ; le champ hexadécimal devient une option avancée masquée par défaut.
- **Aperçu fidèle** : les fonds trop lumineux sont atténués pour conserver des textes lisibles et un message l’explique dans le volet.
- **Compatibilité** : identifiant `fr.ondes.player` et base SQLite inchangés ; `versionCode 10`, Java 17.

# Onde Player 3.5.1 — 05/11/2026

- **Correctif du thème** : sélection des palettes et codes hexadécimaux dans un éditeur persistent ; changement immédiat sans fermeture du volet ni redémarrage de l’activité.
- **Aperçu et retour visuel** : couleur active cochée, aperçu du fond et de l’accent, validation des codes personnalisés.
- **Fond image et réinitialisation** : mise à jour de l’interface sans interrompre la lecture en cours.
- **Compatibilité** : même package `fr.ondes.player`, base SQLite inchangée ; `versionCode 9`, Java 17.

# Onde Player 3.5.0 — 05/11/2026

- **Playlists :** badge « ✓ N » par titre, fondé sur une lecture SQL groupée ; accès direct à une fenêtre de playlists précochées, ajout et retrait par cases, mise à jour après modifications individuelles et groupées.
- **Suppression définitive :** dialogue explicite + confirmation Android MediaStore (API 30+) ou permission contrôlée (API 29 et antérieurs) ; retrait des références SQLite et de la file d'attente seulement après suppression acceptée.
- **Recherche :** loupe en haut à la place du champ permanent ; recherche dans une fenêtre avec filtrage immédiat et action Effacer.
- **Espace de lecture :** en-têtes raccourcis, repli du logo, des filtres et des raccourcis pendant le défilement de la liste des titres.
- **Personnalisation :** accents et fonds colorés, palettes et codes hexadécimaux, image de fond locale via le sélecteur Android, contraste ajusté, préférences durables et réinitialisation.
- **Compatibilité :** `versionCode` 8, signature/projet `fr.ondes.player` conservés, Java 17 et fichiers `.trashed` exclus.

# Onde Player 3.4.0 — 05/11/2026

- **Vidéo** : passage de `SurfaceView` à `TextureView` avec cycle de vie complet, réattachement de la surface au service après transition et reprise, détection de la première image et message après délai ; options « Réinitialiser l'image » et « Diagnostic vidéo » ; erreurs de décodage visibles.
- **Égaliseur simplifié** : commandes natives Basses / Aigus (réglages actifs en direct, enregistrables) avec test unitaire du mappage des bandes. Toutes les options avancées et les profils précédents restent accessibles.
- **Minuteur** : bulle de compte à rebours déplaçable au doigt et consultable au toucher, position persistante, affichée au-dessus du lecteur et du plein écran vidéo. Visible dans Onde Player, pas en dehors de l'application.
- **Bibliothèque** : suppression de la carte « Reprendre l'écoute » au-dessus de la liste des titres ; le mini-lecteur reste en place.
- **Compatibilité** : même application ID et stockage SQLite ; versionCode 7 ; configuration de JDK 17 pour PHONE AS maintenue.
- **Vérifications** : tests unitaires des contrôles Basses/Aigus et des gestes ; analyses XML, ZIP et des signatures de classes Java. Compilation Android et rendu vidéo réels restant à vérifier sur un téléphone.

---

# Onde Player 3.3.0 — 05/11/2026

## Bibliothèque et playlists

- Indexation locale des vidéos `MediaStore.Video` en complément des fichiers musicaux.
- Identifiants distincts pour les deux types de médias ; `Track.isVideo` et URL de contenu d'origine conservées.
- Miniatures vidéo via `ContentResolver.loadThumbnail()` ou récupération de trame pour Android plus ancien.
- Permission `READ_MEDIA_VIDEO` (API 33+) et accès partiel (API 34+) ; demande de réautorisation via menu.
- Filtres persistants **Tout / Musique / Vidéos**, badges dans les lignes, recherche, même file de lecture audio-vidéo.
- Playlists mixtes, import/export avec indicateur `isVideo`, favoris, statistiques de lecture vidéo.
- Suppression de l'étiquette décorative en bas de la fenêtre principale.

## Lecteur multimédia

- Attachement/détachement dynamique de la Surface de la vue vidéo au `MediaPlayer` du service en arrière-plan.
- Vidéo visible dans le lecteur, mode paysage plein écran immersif avec contrôles, sortie via Retour/✕.
- Passage à l'audio seul puis réactivation de l'image.
- Choix de la vitesse de lecture ; mise à jour du ratio vidéo selon la largeur et la hauteur rapportées par le décodeur.
- Lecteur vidéo ouvert au toucher et quand une vidéo commence dans la file d'attente mixte au premier plan.

## Égaliseur

- Curseurs par bande avec fréquence réelle et gain en dB, appliqués immédiatement sur la session audio active.
- Mode manuel mémorisé entre morceaux, courbes nommées sauvegardables, import d'un profil déjà sauvegardé, suppression et remise à zéro.
- Modes d'usine et renforcement des basses conservés.

## Compatibilité et stabilité

- Maintien de l'application ID `fr.ondes.player`, du schéma SQLite 3, des playlists V3.2 et de la configuration JDK 17 de PHONE AS.
- Prise en compte du temps d'écoute des vidéos (identifiants négatifs des médias vidéo).
- Exclusion des sources cachées `.trashed-*.java` générées par l'éditeur.

## Limites connues

- La disponibilité des bandes d'égalisation dépend de l'effet audio Android du constructeur.
- Tous les conteneurs/codecs vidéo ne sont pas nécessairement décodables par `MediaPlayer`.
- Compilation Android complète et tests tactiles à confirmer dans PHONE AS sur appareil.
