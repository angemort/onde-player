# Onde Player 3.9.0 — Harmonisation automatique des thèmes

- À partir des trois zones personnalisables **Accent**, **Fond** et **Texte**, génération automatique des couleurs secondaires, fonds de sélection, surfaces, contours et séparateurs.
- États interactifs de **survol**, **appui**, **focus** et lignes sélectionnées : nuances basées sur l'accent du thème au lieu d'effets blancs/verts/bleus codés en dur.
- Couleur des libellés sur les boutons d'accent et du texte secondaire calculée selon le contraste.
- Pour un dégradé, les couleurs dérivées utilisent le mélange des deux extrémités ; pour une image, une couleur représentative est échantillonnée localement.
- Harmonisation des volets, des actions par glissement, des statistiques, du mini-lecteur et des commandes du lecteur.
- Petit aperçu des teintes calculées dans l'éditeur pour conserver **une interface simple**, sans nouveaux paramètres obligatoires.
- Tests JVM des contrastes, des accents clairs/sombres et de l'adaptation des états.
- Version `3.9.0` / Android `versionCode 19`, données et signature inchangées.

# Onde Player 3.8.3 — Lecture longue durée et diagnostic voiture

- Le service de lecture reste au premier plan **entre deux titres**, pendant leur chargement asynchrone, et durant une perte temporaire du focus audio (GPS, téléphone). Le statut Android n'est plus retiré durant ces transitions.
- La file de lecture et la position sont enregistrées : après une interruption du processus par Android, le service START_STICKY tente une restauration si la musique était en cours de lecture.
- Verrou CPU de préparation audio à durée limitée (90 s maximum), libéré à la préparation, la pause, l'erreur et l'arrêt ; le MediaPlayer conserve son wake lock durant la lecture.
- L'arrêt sur déconnexion Bluetooth reste volontaire afin d'éviter de diffuser la musique sur le haut-parleur du téléphone.
- Nouvelle option « Diagnostic de lecture » pour comprendre localement un arrêt : fin de file, minuteur, focus audio, Bluetooth, erreur de média ou arrêt du service. Les informations sont partageables manuellement, sans nom de musique ni donnée personnelle.
- Tests JVM supplémentaires de la politique de conservation du service et de reprise.
- Version Android 3.8.3 (`versionCode 18`), même identité et signature, données utilisateurs inchangées.

# Onde Player 3.8.2 — Color picker à deux curseurs et stabilité

- Nouveau **DualColorPickerView** pour les trois dégradés (Accent, Fond, Texte) : deux repères 1 et 2 dans un seul carré saturation/luminosité et dans la barre des teintes.
- Sélection d'un repère au toucher ; deux boutons de raccourci permettent aussi de choisir précisément la couleur à modifier.
- Aperçu du dégradé instantané pendant le déplacement du doigt, sans supprimer les composants ni reconstruire le lecteur dans le gestionnaire tactile.
- Sauvegarde des deux couleurs **après ACTION_UP**, grâce à un callback différé ; même précaution pour le picker de couleur unie.
- Déplacement des deux curseurs, changement de direction et d'intensité pris en charge sans fermeture de l'éditeur.
- Tests JVM ajoutés pour la sélection des curseurs proches, superposés ou éloignés.
- Version Android **3.8.2** (`versionCode 17`), signature et données utilisateur conservées.

# Onde Player 3.8.1 — Animations de navigation dans la bibliothèque

- Le header de la liste des titres coulisse et s'estompe sur une animation, au lieu de disparaître instantanément.
- Il revient après un geste vers le haut **depuis n'importe quel titre**, sans nécessiter le retour au tout début de la liste.
- La direction du doigt est mesurée dans les coordonnées physiques de l'écran ; les changements de hauteur sont réalisés après le relâchement, pour éviter le tremblement pendant un appui prolongé.
- Le décalage vertical de la liste est compensé et animé lorsque le header reprend ou libère son espace.
- Les animations en attente sont invalidées lors d'un changement d'écran ; les changements de thème conservent l'état du header.
- Tests de non-régression pour les gestes inversés, les petits mouvements involontaires et le seuil de déclenchement.
- Version 3.8.1 (code Android 16), même clé et même base de playlists.

# Onde Player 3.8.0 — Header compact et thèmes multi-zones

- La barre supérieure, la navigation et les outils de titres se replient réellement après le début du défilement, laissant davantage de place aux morceaux.
- Fin du scintillement lors du retour en haut : l'en-tête ne se réouvre qu'une fois la liste totalement revenue en haut et le geste terminé.
- Éditeur de thèmes simplifié : trois rubriques **Accent**, **Fond**, **Texte** ; dans chacune, **Uni**, **Dégradé**, **Image**.
- Six dégradés guidés par rubrique, deux couleurs sélectionnables dans le color picker, direction et intensité.
- Les dégradés et images d'accent s'appliquent aux surfaces accentuées, boutons et pictogrammes ; le texte accepte aussi les dégradés et les images locales.
- Import de photo via le sélecteur Android, URI persistante et cache bitmap échantillonné ; aucune image envoyée en ligne.
- Les anciens thèmes (y compris fond dégradé et photo) sont reconnus sans perdre les réglages existants.
- Version Android : `3.8.0` / `versionCode 15`. Identifiant et schéma de données inchangés.

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
