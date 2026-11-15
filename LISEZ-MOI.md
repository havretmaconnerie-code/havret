# Havret Chantier – application Android

Application de chantier pour Havret Étanchéité : métrés, analyse photo, liste de matériel, chiffrage complet (matériel, main-d'œuvre, logistique, marge), planning daté jour par jour, plan des palettes et contrôles.

## Obtenir le fichier à installer (APK) – sans rien installer sur l'ordinateur

1. Créez un compte gratuit sur **github.com**.
2. Cliquez sur **New repository**, nommez-le `havret-chantier`, choisissez **Private**, puis **Create repository**.
3. Cliquez sur **uploading an existing file** et glissez **tout le contenu** de ce dossier (les dossiers `app`, `.github` et les fichiers). Cliquez sur **Commit changes**.
   - Si le dossier `.github` n'apparaît pas (dossier caché) : ouvrez l'onglet **Actions** → **set up a workflow yourself**, collez le contenu du fichier `.github/workflows/build.yml`, puis **Commit**.
4. Ouvrez l'onglet **Actions** : la construction démarre seule (5 minutes environ). Quand elle est verte, cliquez dessus et téléchargez **HavretChantier-apk** en bas de la page.
5. Décompressez : vous obtenez `HavretChantier.apk`. Envoyez-le sur votre téléphone (e-mail, Drive, câble).
6. Sur le téléphone, ouvrez le fichier et acceptez **Installer des applications inconnues** pour l'application qui l'ouvre.

Les mises à jour : remplacez les fichiers sur GitHub, une nouvelle APK est construite ; installez-la par-dessus, vos chantiers sont conservés.

## Avec Android Studio (alternative)

Ouvrez ce dossier dans Android Studio, puis **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Analyse photo

Dans l'application : ⚙ → **Analyse photo (IA)** → collez votre clé API créée sur **console.anthropic.com** (rubrique API Keys, avec un peu de crédit). Chaque analyse coûte quelques centimes. La clé reste sur le téléphone.

## Vos données

Les chantiers et paramètres sont enregistrés sur le téléphone. Utilisez **Sauvegarder mes chantiers** (écran d'accueil) pour en envoyer une copie par e-mail ou WhatsApp, et **Restaurer** pour les récupérer sur un autre téléphone.

## Modifier l'application

Toute l'interface est dans `app/src/main/assets/index.html`. Les prix et rendements par défaut sont en haut du script (bloc `DEF`), mais le plus simple est de les régler dans l'application (⚙).

Le fichier `app/havret.keystore` signe l'application : gardez-le, il permet d'installer les mises à jour par-dessus l'ancienne version.
