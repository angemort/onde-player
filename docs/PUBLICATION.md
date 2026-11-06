# Publier une version Onde Player

1. **Une seule fois**, créer une clé Android de publication (fichier `.jks`) et la sauvegarder hors GitHub. Ne pas utiliser une clé debug.
2. Dans GitHub → Settings → Secrets and variables → Actions, définir : `ONDE_KEYSTORE_BASE64` (contenu base64 du `.jks`), `ONDE_KEYSTORE_PASSWORD`, `ONDE_KEY_ALIAS`, `ONDE_KEY_PASSWORD`. Ne jamais les mettre dans les sources ou les journaux.
3. Mettre à jour `versionCode` et `versionName` dans `app/build.gradle`. `versionCode` doit augmenter à chaque version.
4. Créer et pousser un tag `v<versionName>` ; GitHub Actions compile, signe et publie une Release avec `OndePlayer-<versionName>.apk`.
5. Après la Release, l'Action publie `update.json` à la racine. L'application l'interroge au plus une fois par 24h et propose le téléchargement. **Android demande toujours une confirmation pour installer.**

Le certificat de signature doit rester identique pour les futures versions. Si les versions précédentes ont été installées en debug avec une clé différente, une migration directe sera impossible. Conserver des exports de playlists avant de changer de clé.

La compilation debug de vérification est lancée pour chaque push sur `main`, sans secret. Elle fournit un artefact de test mais ne publie aucune Release.
