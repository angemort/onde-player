# Onde Player V3.6.0 — Playlists réorganisables, index A–Z et mises à jour

## Les nouveautés de la V3.6.0

**Réorganiser une playlist** : dans **Playlists → une playlist personnelle**, maintenir le doigt sur une ligne jusqu’au retour haptique, faire glisser jusqu’à la nouvelle position puis relâcher. Le morceau se déplace immédiatement et **l’ordre est sauvegardé en SQLite**. La liste défile automatiquement à proximité de son bord pendant un glissement. Un pictogramme ☷ identifie les titres déplaçables. Le bouton **✓ Sélectionner** dans la barre supérieure active la sélection multiple, puisque l’appui long est réservé au déplacement. Les favoris ne disposent pas d’un ordre manuel, car il s’agit d’une collection calculée automatiquement.

**Index alphabétique** : dans **Titres**, une réglette **# A–Z** apparaît sur la droite lorsque le tri **Titre (A→Z)** est actif. Faire glisser le doigt sur une lettre saute à cette section. Les accents sont regroupés (« É » dans E, « À » dans A). Avec un autre mode de tri, l’index est masqué pour ne pas pointer vers des positions trompeuses. Un petit espace est réservé à droite des titres pour préserver le fonctionnement des gestes de lecture.

**Mises à jour facultatives** : dans **⋮ → Mises à jour**, enregistrer l’adresse HTTPS d’un manifeste JSON public (par exemple sur GitHub). Onde vérifie le lien au lancement, une fois par jour au maximum ; une nouvelle version propose **Télécharger**, qui ouvre le navigateur. Aucun téléchargement ou installation silencieux. Le lien n’est pas préconfiguré et le mécanisme n’est activé qu’une fois l’adresse enregistrée. Guide complet : [`updates/GUIDE_GITHUB_RELEASES.md`](updates/GUIDE_GITHUB_RELEASES.md).

**Compatibilité** : `applicationId fr.ondes.player`, `minSdk 26`, JDK 17, AndroidIDE PHONE AS, playlists et préférences conservées sous réserve de garder la même signature APK. `versionCode 12`, `versionName 3.6.0`. Permission Internet ajoutée **uniquement** pour la vérification facultative des mises à jour ; lecture multimédia toujours hors ligne.

## Installer sur Android IDE — PHONE AS

1. Extraire `OndePlayer_V3_6_AndroidIDE.zip` dans un **nouveau dossier**, sans fusionner avec d'anciennes versions ; l’IDE peut conserver des fichiers cachés `.trashed-*.java`.
2. Ouvrir le dossier `OndePlayer_V3_6_AndroidIDE` (celui qui contient `settings.gradle`).
3. Lancer **Play ▶**. Si l’IDE impose encore Java 8, lancer `sh compiler_sur_androidide.sh` dans le terminal, depuis le dossier du projet. Le JDK Java 17 installé dans `/home/test/.local/jdk-17` est prévu par la configuration.
4. Vérifier le drag & drop sur une petite playlist, puis l’index A–Z sur **Titres**.

**Vérifications locales :** tests Java du tri, du déplacement d’IDs et tests de régression existants ; validité XML et structure de l’archive. Une compilation Android complète avec le SDK ainsi que les gestes sur téléphone doivent encore être validés dans PHONE AS.

---

# Onde Player 3.5.3 — Android IDE / PHONE AS

## Nouveau dans la V3.5.3 — Couleur du texte, confort et recherche directe

- **Thème → Texte** : troisième onglet du color picker, avec huit couleurs prêtes à utiliser ou sélection libre au doigt. Le texte principal de la bibliothèque, des options et du lecteur adopte la couleur choisie. Les textes secondaires reçoivent une nuance dérivée pour rester cohérents. Le thème original peut être restauré. Un avertissement apparaît dans l'aperçu lorsque le contraste texte/fond est trop faible.
- **Boutons mieux espacés** : écart augmenté entre les boutons de la barre supérieure, les onglets du thème, les raccourcis du lecteur et les actions des volets ; le mini-lecteur et les filtres multimédias ont des zones de toucher mieux séparées.
- **Recherche autonome** : toucher la loupe ouvre une fenêtre avec champ, bouton « ✕ », compteur et liste de résultats **mise à jour à chaque caractère**. Chaque résultat affiche la pochette, le titre, l'artiste, le type de média et la durée. Toucher une ligne lance directement sa lecture. Effacer ramène tous les résultats **sans quitter la fenêtre**, et fermer n'applique plus de filtre invisible à la bibliothèque.
- **Recherche tolérante aux accents** : `Beyonce` retrouve `Beyoncé`, et les mots peuvent être cherchés à travers titre, artiste et album.
- **Compatibilité** : `fr.ondes.player`, données SQLite, JDK 17 et fichier de fond personnalisable inchangés. `versionCode 11` et `versionName 3.5.3`.

### À vérifier sur ton téléphone

1. Ouvrir ⋮ → **Personnaliser l'apparence** → **Texte**, puis choisir une teinte avec le color picker ; vérifier que le lecteur change sans fermer l'éditeur.
2. Changer aussi la couleur du fond, vérifier l'avertissement de contraste si le texte est trop proche du fond, puis utiliser **Rétablir le thème d'origine**.
3. Toucher la loupe, taper quelques lettres : la liste doit se modifier immédiatement. Effacer avec ✕, puis toucher un titre et vérifier qu'il démarre.
4. Vérifier que le menu d'options, les commandes du lecteur et le mini-lecteur conservent suffisamment d'espace pour les doigts.

**Vérifications dans cet environnement** : tests unitaires Java du moteur de recherche et des règles de gestes, vérification XML, structure du projet et ZIP. Le SDK Android n'est pas disponible ici : la compilation complète et les interactions visuelles doivent être validées dans PHONE AS.

## V3.5.2 — Color picker visuel

Dans **⋮ → Personnaliser l’apparence**, choisis l’onglet **Couleur principale** ou **Couleur du fond** :

1. Déplace le doigt dans le **grand carré** pour choisir une nuance (plus ou moins colorée et lumineuse).
2. Déplace le doigt sur la **barre arc-en-ciel** pour choisir la teinte (rouge, bleu, vert, etc.).
3. Regarde l’aperçu au-dessus du sélecteur : il suit le doigt **en direct**.
4. Au relâchement, la couleur s’enregistre immédiatement et l’application actualise son apparence, **sans fermer le volet** ni interrompre la lecture.

Les couleurs prédéfinies restent accessibles juste sous le color picker. Le champ hexadécimal est **masqué par défaut** sous « Saisir un code couleur (avancé) » ; il n’est plus nécessaire de connaître un code. Le fond reste automatiquement assombri lorsqu’une couleur trop claire compromettrait la lisibilité du texte clair ; l’aperçu en tient compte. Le comportement est identique pour l’accent et le fond. L’image d’arrière-plan et la remise à zéro sont toujours disponibles.

### Contrôles de qualité

- 20 sources Java vérifiées par analyse syntaxique.
- 12 assertions sur le calcul des gestes du color picker.
- Tests existants des gestes des morceaux, des volets, des tons et des types de média relancés.
- XML, structure Gradle et intégrité du ZIP vérifiés.
- **Compilation complète sur Android IDE et interactions réelles à vérifier sur téléphone.**


Lecteur multimédia local Android **audio + vidéo**, construit en Java avec les composants Android natifs. Les playlists, favoris, file d'attente, statistiques, widgets et commandes Bluetooth/notification de Onde V3 sont conservés. Aucun service distant n'est nécessaire pour lire tes fichiers.

## Installer et compiler depuis Android IDE sur téléphone

1. Extraire entièrement `OndePlayer_V3_5_2_AndroidIDE.zip` dans un **nouveau dossier** : ne pas le fusionner avec V3.2, car PHONE AS peut conserver des fichiers cachés `.trashed-*.java`.
2. Ouvrir le dossier `OndePlayer_V3_5_2_AndroidIDE` (celui qui contient `settings.gradle`) dans Android IDE.
3. Appuyer sur **Play ▶** pour synchroniser / compiler et installer l'APK debug.
4. Si le bouton Play affiche un problème de JDK, utiliser **dans le dossier du projet** :

```sh
sh compiler_sur_androidide.sh
```

Le script utilise `/home/test/.local/jdk-17`, le JDK 17 déjà installé pour cet IDE. En cas de compilation réussie, le fichier se situe dans `app/build/outputs/apk/debug/app-debug.apk`. L'application a `applicationId fr.ondes.player`, `versionCode 11`, `versionName 3.5.3`, `compileSdk 34`, Java 17, Gradle 8.2.1, Android Gradle Plugin 8.2.2, `minSdk 26` (Android 8+).

Le premier téléchargement Gradle et des dépendances peut nécessiter Internet. Les fichiers audio/vidéo sont lus **hors ligne** et restent sur le téléphone.

## Correctif 3.5.1 — personnalisation qui reste ouverte

- Le volet **Personnaliser l'apparence** est désormais un éditeur dédié qui **ne se ferme pas** lorsqu'on touche une couleur.
- Les deux onglets **Couleur principale** et **Couleur du fond** permettent de choisir des palettes avec un indicateur ✓ sur la couleur active.
- Un aperçu illustre immédiatement la nouvelle teinte ; la bibliothèque derrière le volet est rafraîchie **sans redémarrer l'activité ni le service audio**.
- Le bouton **Appliquer** accepte un code `#RRGGBB` et affiche une erreur en cas de saisie incorrecte, sans quitter l'éditeur.
- Les commandes **Choisir une photo**, **Retirer l'image** et **Rétablir le thème d'origine** préservent également le lecteur. Le volet peut se fermer avec **Terminer**, le bouton Retour ou en tirant sa poignée vers le bas.
- La configuration du JDK 17 (`/home/test/.local/jdk-17`) est conservée. L'ID `fr.ondes.player` reste identique, avec `versionCode 10`.

**Test recommandé dans Android IDE :** Ouvrir ⋮ → Personnaliser l'apparence → choisir Violet, Bleu, puis passer dans Couleur du fond → choisir Forêt. Vérifier que l'éditeur reste ouvert, que le fond et les icônes changent immédiatement et que le morceau en cours continue. Tester ensuite un code personnalisé `#FF8800`, le bouton Réinitialiser et une image de fond.

## Nouveautés V3.5 — Playlists, suppression, recherche, thèmes

### 1. Savoir où est déjà un morceau

- Un badge **✓ 2** est visible à droite d'un titre quand il est présent dans deux playlists (il n'occupe pas une ligne supplémentaire).
- Toucher ce badge ou choisir **⋮ → Ajouter à une playlist** ouvre un panneau de gestion. Toutes les playlists contenant déjà le titre sont **cochées**.
- Cocher une playlist ajoute le fichier ; **décocher retire le fichier de cette playlist uniquement**, sans le supprimer du téléphone. **Terminé** ferme le volet. Glisser sur sa poignée vers le bas permet de le réduire ou de le fermer.
- Le badge est actualisé après les actions individuelles et les ajouts groupés. Les données sont lues directement dans SQLite ; aucune connexion n'est requise.

### 2. Suppression définitive avec double confirmation

Le menu **⋮ d'un titre → Supprimer définitivement du téléphone** lance une confirmation claire, puis la procédure Android adaptée à la version du système. Cette action supprime **le fichier audio ou vidéo lui-même**, contrairement à « Retirer de cette playlist » qui ne modifie pas le fichier.

Sur Android 11+, Onde utilise `MediaStore.createDeleteRequest` : Android affiche sa propre fenêtre de confirmation. Sur Android 10, Onde gère la demande de permission de modification récupérable. Sur Android 8/9, une autorisation de stockage supplémentaire peut être requise. Après suppression confirmée, le titre disparaît des playlists, favoris et de la file d'attente. Les statistiques historiques restent conservées.

**Attention : la suppression définitive ne place pas le fichier dans la corbeille.** Vérifier le titre avant de confirmer. Si Android refuse, aucune référence n'est supprimée volontairement.

### 3. Bibliothèque plus compacte et recherche à la demande

- La page **Titres** utilise un petit titre de section sur une seule ligne et l'en-tête d'Onde est moins haut.
- La barre de recherche permanente a été retirée. Depuis la V3.5.3, la loupe ouvre une fenêtre affichant aussi les résultats en direct, avec une commande ✕ pour effacer la saisie. Le filtre ne reste pas appliqué à la bibliothèque après fermeture.
- Lorsqu'on fait défiler les morceaux vers le bas, le logo, le titre de section, les filtres et les raccourcis de la liste se replient pour gagner de la hauteur. Ils réapparaissent au retour en haut de liste.
- En sélection multiple, les actions restent prioritaires et continuent à masquer l'en-tête redondant.

### 4. Thèmes personnalisés

Ouvrir **⋮ en haut → Personnaliser l'apparence** :

- **Couleur principale** : 7 palettes (vert Onde, violet, corail, bleu, rose, or et turquoise), ou saisie d'un code `#RRGGBB` personnalisé.
- **Couleur du fond** : 6 fonds sombres proposés ou code hexadécimal personnalisé. Les fonds trop clairs sont automatiquement assombris pour maintenir le contraste du texte blanc.
- **Image de fond** : utiliser le sélecteur de photos Android. L'image est échantillonnée à une résolution raisonnable, puis affichée en faible opacité derrière l'interface pour garder les textes lisibles.
- **Retirer l'image** et **Réinitialiser le thème** : retrouver immédiatement l'apparence d'origine.

Les préférences du thème restent enregistrées sur le téléphone ; la bibliothèque, les playlists et la lecture audio restent accessibles hors ligne. L'image choisie est référencée via son URI Android ; si elle est déplacée ou que son accès est retiré, le lecteur conserve le fond uni.

### Compatibilité et validation

- `applicationId fr.ondes.player`, SQLite v3, playlists et favoris existants conservés ; mise à jour sans désinstallation si la signature APK reste la même.
- Le script `compiler_sur_androidide.sh` garde le JDK 17 installé dans ton environnement PHONE AS et exclut les `.trashed-*.java` des sources.
- Tests Java des gestes, de l'égaliseur et des identifiants audio/vidéo ; vérifications SQL des indicateurs de playlists et des suppressions. **La compilation Android complète et les essais tactiles réels restent à valider sur ton téléphone.**

## Nouveautés V3.4 — écran noir vidéo, réglages simplifiés, minuteur flottant

### Corriger l'image noire des vidéos

Le rendu de la vidéo utilise maintenant une `TextureView` liée dynamiquement au même `MediaPlayer` que l'audio, plutôt que la `SurfaceView` précédente. Cette approche tient mieux compte du passage du lecteur réduit au plein écran et des animations de transition ; lorsque la surface redevient disponible, elle est rattachée au lecteur.

- Le lecteur affiche « Préparation de l'image vidéo… » avant la première image.
- Si aucune image n'arrive après plusieurs secondes, il affiche un message utile au lieu de rester silencieusement noir.
- Le menu **⋮ Options vidéo → Réinitialiser l'image** détache puis reconnecte l'affichage, sans perdre la file d'attente.
- Le menu **⋮ Options vidéo → Diagnostic vidéo** affiche le format MIME, la résolution signalée par Android, la disponibilité de la surface et si une image a été rendue.
- Les erreurs de décodage du moteur Android donnent maintenant un message visible. Un format non compatible peut toujours échouer : pour tester, choisir un MP4 H.264/AAC.

**Cette modification corrige une cause probable de l'écran noir, mais le décodage n'a pas encore été vérifié sur le téléphone.** Si le problème persiste, transmettre les indications de « Diagnostic vidéo » avec les détails du fichier et du modèle de téléphone.

### Égaliseur plus simple

Dans le lecteur ou via le menu ⋮ → **Égaliseur**, ouvrir **Basses et aigus** : deux curseurs faciles à comprendre avec position centrale « Neutre ». Leurs modifications sont audibles directement et enregistrées dans les préférences, y compris en changeant de morceau. Il est possible d'enregistrer des profils nommés. **Réglages avancés** conserve les bandes de fréquence détaillées, le renforcement des basses et les préréglages Android.

Les commandes simples utilisent les bandes graves (jusqu'à environ 400 Hz) et aiguës (à partir de 2 kHz) fournies par le téléphone ; si les bandes ne correspondent pas, la première et la dernière sont utilisées. Un appareil avec moins de deux bandes indépendantes ne peut pas proposer les deux curseurs.

### Minuteur flottant déplaçable

Une bulle « ◷ 14:59 » apparaît **à l'intérieur de l'application** quand le minuteur est activé. Elle actualise le temps restant et peut être déplacée en faisant glisser le doigt ; toucher ouvre la sélection du minuteur. Sa position est mémorisée et elle reste affichée par-dessus le lecteur normal et la vidéo en plein écran, tant que l'application est au premier plan. Désactiver le minuteur fait disparaître la bulle. Cela ne nécessite pas la permission spéciale « Afficher au-dessus des autres applications » : la bulle n'est **pas visible par-dessus d'autres applications**.

### Bibliothèque plus compacte

La carte de grande taille « Reprendre l'écoute » située au-dessus des titres a été retirée. Le mini-lecteur en bas continue à afficher la lecture et permet de revenir au lecteur principal.

## Ce qui était déjà présent dans la V3.3

### 1. Plus d'espace dans les listes

Le texte décoratif **« 100 % LOCAL • AUCUN COMPTE NÉCESSAIRE »** placé au bas de l'écran est supprimé. Le mini-lecteur, les onglets et la sélection multiple compacte de V3.2 restent accessibles.

### 2. Égaliseur manuel avec profils

- Démarrer une musique ou une vidéo.
- Ouvrir **⋮ > Égaliseur personnalisé** ou toucher l'icône égaliseur du lecteur.
- **Personnaliser les fréquences** : curseurs de bandes réglables, avec fréquence en Hz/kHz et gain en dB. Les changements s'appliquent immédiatement à la session sonore active.
- **Enregistrer** : donner un nom à la courbe (par exemple « Mes basses »). Les profils sont enregistrés **localement** dans les préférences et réappliqués si l'utilisateur les choisit ; le profil manuel actif est également restauré lors du changement de morceau.
- **Mes profils enregistrés** : appliquer un profil ou le supprimer.
- **Remettre à zéro** : réinitialiser toutes les bandes du mode manuel.
- Les préréglages système, l'annulation des effets et le renforcement des basses sont toujours disponibles.

Les bandes et leurs limites sont déterminées par l'implémentation `android.media.audiofx.Equalizer` fournie par **le téléphone**. Certains appareils ne proposent pas cet effet, ou ne le prennent pas en charge sur toutes les sorties audio. La fenêtre l'indique si aucune bande n'est disponible. Le réglage peut aussi dépendre d'autres effets audio du constructeur.

### 3. Vidéo locale dans la bibliothèque

La recherche indexe désormais les fichiers audio **et** les vidéos accessibles via Android MediaStore. Les vidéos portent le libellé **VIDÉO**, utilisent une miniature du fichier si elle est disponible et possèdent un identifiant distinct des musiques, même si les IDs MediaStore se ressemblent.

Dans **Titres**, utiliser les trois filtres **Tout / Musique / Vidéos**, ou le menu ⋮ → **Filtre multimédia**. Le filtre reste mémorisé après fermeture. Il s'applique aux bibliothèques par titres, artistes/albums, historiques et contenus de playlists ; la file d'attente conserve volontairement **l'ordre réel de lecture**, indépendamment du filtre.

Les vidéos peuvent être **ajoutées à une playlist, mises en favoris et placées dans la file d'attente** comme les musiques. Les playlists JSON exportées depuis cette version comportent un indicateur `isVideo`, utilisé pour réassocier correctement les médias lors de leur réimportation. L'import des anciens exports audio reste pris en charge.

### 4. Lecteur vidéo, plein écran et options

- Toucher une vidéo : lecture immédiate et ouverture de son lecteur avec l'image ; le lecteur audio reste disponible en mini-lecteur lorsque la vidéo est réduite.
- **Plein écran ⛶** : affiche la vidéo en paysage, masque les barres Android et montre les commandes tactiles.
- **✕ ou Retour** depuis le plein écran : revient au lecteur normal, sans arrêter le son.
- Boutons précédent / lecture-pause / suivant, curseur de progression et durées, y compris en plein écran.
- **♫ Audio seul** : masque l'image et conserve le son de la vidéo ; toucher de nouveau réactive l'image.
- **Vitesse** : 0,5×, 0,75×, 1×, 1,25×, 1,5× ou 2× (suivant la prise en charge par Android).
- Menu **⋮** : plein écran, audio seul, vitesse, égaliseur, ajout à une playlist.
- Quand une vidéo arrive dans une playlist mixte et que l'application est au premier plan, le lecteur vidéo s'ouvre automatiquement.

**Limite des formats** : la lecture repose sur `android.media.MediaPlayer` et les décodeurs installés sur le téléphone. Les formats courants comme MP4 (H.264/AAC) sont généralement pris en charge, mais MKV, AVI, HEVC, pistes audio particulières ou fichiers corrompus peuvent échouer selon le téléphone. Ce projet ne contient pas les codecs VLC/FFmpeg. L'audio d'une vidéo peut continuer en arrière-plan ; l'image n'est montrée que lorsque le lecteur est visible.

### 5. Autorisations Android

- Android 8–12 : accès aux médias via `READ_EXTERNAL_STORAGE`.
- Android 13 : `READ_MEDIA_AUDIO` et `READ_MEDIA_VIDEO` séparés.
- Android 14–15 : accès total ou partiel aux vidéos sélectionnées via `READ_MEDIA_VISUAL_USER_SELECTED`. Tu peux ajuster l'accès vidéo depuis le menu ⋮ → **Gérer l'accès aux vidéos**.
- Pour les commandes de lecture en notification sur Android 13+, l'application demande également l'autorisation de notifications.

**Les vidéos auxquelles Android n'a pas accordé l'accès ne peuvent pas être affichées**, même si elles existent sur le stockage. La base MediaStore peut être mise à jour avec le bouton d'actualisation. Ne demande pas l'accès aux images : seuls les fichiers vidéo et audio sont utilisés.

## Mise à jour et données existantes

Le schéma SQLite `onde_library.db` et l'identifiant d'application sont **inchangés**. Les playlists, favoris et historiques de la V3.2 doivent être conservés **si l'APK V3.3 est installé par-dessus avec une signature compatible**. En cas d'erreur de signature, n'efface pas l'ancienne application avant d'avoir exporté les playlists, faute de quoi les statistiques locales seraient perdues. Les profils d'égalisation sont stockés dans les préférences de l'application.

## Gestes conservés

Appui long sur titre = sélection persistante avec cases ; glissement à droite jusqu'au bord = lire ensuite ; glissement à gauche jusqu'au bord = fin de file ; revenir vers le centre annule le geste. Les volets du bas se réduisent et se ferment en glissant vers le bas. Le guide en quatre étapes n'apparaît automatiquement qu'une fois et peut être revu depuis ⋮.

## Vérifications et limites

La logique Basses/Aigus a été testée en Java, les XML et la structure du ZIP ont été contrôlés, et les tests existants des gestes ont été relancés. **Le build Android final et le décodage vidéo ne sont pas vérifiables ici (SDK Android absent).** Les fonctionnalités vidéo et l'égaliseur doivent donc être validés par une compilation Play ▶ et un essai sur ton téléphone.

En cas d'erreur, transmettre le bloc commençant à `> Task :app:compileDebugJavaWithJavac` ainsi que les messages qui suivent ; pour un souci de lecture, indiquer le format du fichier, le téléphone et les circonstances.

### Tests sans Android SDK

```sh
mkdir -p /tmp/onde-v34-tests
javac -d /tmp/onde-v34-tests app/src/main/java/fr/ondes/player/GestureRules.java app/src/main/java/fr/ondes/player/SheetGestureRules.java tests/GestureRulesTest.java tests/SheetGestureRulesTest.java
java -cp /tmp/onde-v34-tests GestureRulesTest
java -cp /tmp/onde-v34-tests SheetGestureRulesTest
javac -d /tmp/onde-v34-tests tests/stubs/android/net/Uri.java app/src/main/java/fr/ondes/player/Track.java tests/TrackMediaTest.java
java -cp /tmp/onde-v34-tests TrackMediaTest
```

```sh
javac -d /tmp/onde-v34-tests app/src/main/java/fr/ondes/player/ToneRules.java tests/ToneRulesTest.java
java -cp /tmp/onde-v34-tests ToneRulesTest
```
