# Publier Onde Player depuis GitHub (gratuit)

## 1. Première configuration : une clé de signature permanente

Dans le terminal Android IDE sur le téléphone, Java 17 est installé dans `/home/test/.local/jdk-17`. Pour générer une clé locale sans sudo :

```bash
"$HOME/.local/jdk-17/bin/keytool" -genkeypair \
  -keystore "$HOME/onde-player-release.jks" \
  -alias onde-player -keyalg RSA -keysize 3072 -validity 10000
```

Le mot de passe est demandé sans être affiché. **Conserver le fichier .jks et ses mots de passe en sauvegarde privée et durable.** Sans cette clé, il sera impossible de signer des mises à jour compatibles.

Pour obtenir le texte à coller dans le secret GitHub :

```bash
base64 -w0 "$HOME/onde-player-release.jks"
```

Ne jamais partager cette sortie dans un chat ni la publier dans le dépôt. Créer dans GitHub → **Settings → Secrets and variables → Actions** les quatre secrets suivants :

| Nom | Contenu |
|---|---|
| `ONDE_KEYSTORE_BASE64` | La sortie base64 du fichier .jks |
| `ONDE_KEYSTORE_PASSWORD` | Mot de passe du .jks |
| `ONDE_KEY_ALIAS` | `onde-player` |
| `ONDE_KEY_PASSWORD` | Mot de passe de la clé (si distinct) |

## 2. Publier une nouvelle version (un clic)

1. Mettre à jour `versionCode` (strictement croissant) et `versionName` dans `app/build.gradle`.
2. Dans GitHub → **Actions** → **Android - Release signée** → **Run workflow**, choisir `main`, puis confirmer.
3. Le workflow compile l'APK **signé**, crée automatiquement le tag `v<versionName>`, publie la Release GitHub et met à jour `update.json`.
4. L'application lit automatiquement `https://raw.githubusercontent.com/angemort/onde-player/main/update.json` au maximum une fois par 24 heures, puis propose le téléchargement. Android confirme toujours l'installation.

Le dépôt est public et ne contient aucune clé de signature. L'APK de test généré par **Android - compilation de vérification** n'est **pas** celui utilisé pour les mises à jour. Ne pas le distribuer comme une Release officielle.

## 3. Conserver les données de l'application

Une application déjà installée avec la signature *debug* d'AndroidIDE ne peut pas nécessairement être remplacée par une APK signée avec cette nouvelle clé. Sauvegarder/exporter les playlists avant toute migration de signature. Les statistiques locales peuvent être perdues si l'application doit être désinstallée.

Les versions **officielles** futures pourront se mettre à jour normalement si l'identifiant `fr.ondes.player` et la même clé de signature sont conservés.
