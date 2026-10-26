# TUI-VibeLauncher

Lanceur Android en mode terminal : l'écran d'accueil est une ligne de commande. Ce dépôt est un fork de [T-UI Launcher](https://github.com/fandreuz/TUI-ConsoleLauncher). Il le met à jour pour Android 15 et lui ajoute un client SSH, des outils réseau et des commandes de terminal.

Développé pour un Samsung Galaxy S25. Les fonctions sont testées sur l'émulateur Android 15 (API 35). La version minimale reste Android 5.0 (API 21).

## Sommaire

- [Compatibilité Android 15](#compatibilité-android-15)
- [Nouvelles commandes](#nouvelles-commandes)
- [SSH et serveurs](#ssh-et-serveurs)
- [Outils Linux via Termux](#outils-linux-via-termux)
- [Terminal](#terminal)
- [Réseau](#réseau)
- [Notifications](#notifications)
- [Sauvegarde et démarrage](#sauvegarde-et-démarrage)
- [Apparence](#apparence)
- [Limites connues](#limites-connues)
- [Compilation](#compilation)
- [Crédits](#crédits)

## Compatibilité Android 15

| Changement | Raison |
|---|---|
| SDK cible 35, Gradle 8.14, Android Gradle Plugin 8.11, Java 17 | Les versions précédentes ne compilent pas avec un JDK récent et ne ciblent pas Android 15. |
| Fond noir par défaut | Le fond était blanc sur Android 15. |
| Affichage bord à bord désactivé | Android 15 l'impose par défaut, ce qui faisait passer le texte sous la barre d'état. |
| Notifications interactives corrigées | La réponse rapide depuis une notification T-UI était bloquée par Android 14 et plus. |
| Autorisations demandées une seule fois | T-UI redemandait les autorisations à chaque démarrage. Le stockage et l'accès aux notifications se demandent maintenant au moment où une commande en a besoin, ou avec `tui -storage` et `tui -notifaccess`. |
| Commandes système (`ls`, `cat`, `ping`, `ps`...) | Elles étaient bloquées par une dépendance à BusyBox. Elles utilisent maintenant les outils fournis par Android. |

BusyBox ne peut plus être exécuté sur Android 10 et plus : le système interdit d'exécuter un programme téléchargé par une application. `bbman -install` l'explique désormais et renvoie vers Termux.

## Nouvelles commandes

| Commande | Rôle |
|---|---|
| `ssh` | Client SSH intégré, avec serveurs enregistrés |
| `termux` | Exécution d'outils Linux via l'application Termux |
| `history` | Recherche dans l'historique des commandes |
| `scan` | Liste des appareils du réseau local |
| `ports` | Ports TCP ouverts d'une machine |
| `wol` | Allumage d'une machine à distance (Wake-on-LAN) |
| `myip` | Adresses IP locales et adresse publique |
| `findout` | Surlignage d'un texte dans le terminal |
| `notif` | Notifications dans le terminal |
| `fetch` | Résumé du téléphone |
| `fx` | Effets visuels |
| `tui -backup`, `tui -restore`, `tui -rc` | Sauvegarde, restauration, script de démarrage |

`help <commande>` affiche le détail de chaque commande dans l'application.

## SSH et serveurs

Le client SSH fonctionne sans application supplémentaire.

```
ssh root@192.168.10.100              ouvre une session
ssh root@192.168.10.100 uptime       exécute une commande et affiche le résultat
ssh -p 2222 user@host                autre port (user@host:2222 fonctionne aussi)
ssh -i fichier user@host             autre clé privée
```

- À la première connexion, l'empreinte du serveur s'affiche et doit être confirmée. Elle est ensuite enregistrée et vérifiée à chaque connexion.
- Les mots de passe sont masqués à la saisie, y compris pour `sudo` sur le serveur. Ils ne sont jamais enregistrés dans l'historique.
- Pendant une session, tout ce qui est tapé part vers le serveur. `~.` ferme la session, `~c` envoie Ctrl+C, `~d` envoie Ctrl+D.

**Clé SSH**

```
ssh -keygen      crée une paire de clés ed25519 et affiche la clé publique
ssh -pubkey      affiche la clé publique
```

Une fois la clé publique ajoutée dans `~/.ssh/authorized_keys` sur le serveur, la connexion se fait sans mot de passe.

**Serveurs enregistrés**

```
ssh -add srv root@192.168.10.100                    enregistre un serveur
ssh -add nas admin@192.168.10.20 -mac AA:BB:CC:DD:EE:FF
ssh srv                                             se connecte au serveur enregistré
ssh -ls                                             liste les serveurs
ssh -rm srv                                         supprime un serveur
ssh -forget 192.168.10.100                          oublie l'empreinte d'un serveur réinstallé
```

Les serveurs enregistrés apparaissent sur l'écran d'accueil avec leur état, mis à jour toutes les 60 secondes :

```
● srv 6ms  ○ nas down
```

Un serveur est considéré comme joignable s'il accepte une connexion sur son port SSH. L'intervalle se règle avec l'option `servers_check_interval`, l'affichage avec `show_servers`.

## Outils Linux via Termux

Android ne fournit qu'un jeu réduit de commandes Unix. Si [Termux](https://f-droid.org/packages/com.termux/) est installé, les commandes absentes d'Android (`git`, `python`, `nmap`, `rsync`...) lui sont transmises automatiquement, et le résultat s'affiche dans T-UI.

```
termux -setup                  configuration guidée (une seule fois)
git status                     transmis automatiquement à Termux
termux pkg install -y git      installe un paquet
termux -open htop              ouvre une fenêtre Termux pour les programmes plein écran
```

Termux doit être installé depuis F-Droid. La version du Play Store n'est plus maintenue.

## Terminal

**Interrompre une commande (Ctrl+C)**

Une commande qui ne s'arrête pas d'elle-même (`ping`, `top`...) s'interrompt de trois façons :

- un appui long sur le bouton d'effacement de la barre d'outils ;
- taper `^c` ou `~c` ;
- Ctrl+C sur un clavier physique.

La commande reçoit le même signal qu'avec un vrai Ctrl+C. `ping` affiche donc ses statistiques avant de s'arrêter.

**Historique**

L'historique est conservé entre les redémarrages (500 commandes au maximum).

```
history              dernières commandes
history ssh          commandes contenant "ssh"
history -clear       efface l'historique
```

Les flèches de la barre d'outils parcourent cet historique.

**Complétion**

Le bouton Tab de la barre d'outils, ou la touche Tab d'un clavier physique, complète avec la première suggestion. La complétion couvre les noms de serveurs, les options des nouvelles commandes et les chemins de fichiers.

**Tuyaux et redirections**

Les commandes T-UI acceptent `|`, `>` et `>>`, comme les commandes système :

```
scan | grep 192.168.10
history | grep ssh
fetch > /sdcard/Download/telephone.txt
myip >> /sdcard/Download/ip.txt
```

**Recherche et copie**

```
findout erreur       surligne "erreur" dans le terminal et fait défiler jusqu'à la dernière occurrence
findout              retire le surlignage
```

Un appui long sur une ligne du terminal la copie dans le presse-papiers.

## Réseau

```
scan                          appareils qui répondent sur le réseau Wi-Fi (environ 5 secondes)
ports srv                     ports courants : SSH, HTTP, SMB, Proxmox, Home Assistant, Plex...
ports 192.168.10.1 1-1024     une plage de ports
ports nas 22,80,443           une liste de ports
wol nas                       allume une machine enregistrée avec son adresse MAC
wol AA:BB:CC:DD:EE:FF         allume une machine à partir de son adresse MAC
myip                          adresses IP locales et adresse publique
```

Pour connaître l'adresse publique, `myip` interroge les sites api.ipify.org, icanhazip.com, checkip.amazonaws.com et ifconfig.me, dans cet ordre. Il passe au suivant si l'un d'eux est bloqué par le DNS du réseau.

Le Wake-on-LAN doit être activé dans le BIOS et dans la configuration réseau de la machine à allumer.

## Notifications

```
notif on        affiche les nouvelles notifications dans le terminal
notif off       arrête l'affichage
notif list      liste les notifications en cours
notif clear     efface les notifications qui peuvent l'être
```

`notif on` demande l'accès aux notifications si T-UI ne l'a pas encore. Les filtres et les formats d'affichage se règlent avec la commande `notifications`.

## Sauvegarde et démarrage

**Sauvegarde**

```
tui -backup      crée Download/t-ui-backup-AAAAMMJJ-HHMM.zip
tui -restore     ouvre le sélecteur de fichiers et restaure la sauvegarde choisie
```

La sauvegarde contient les réglages, les alias, l'historique, les serveurs enregistrés, la clé SSH et le script de démarrage. Elle est utile parce que le dossier de T-UI est effacé à la désinstallation de l'application.

La sauvegarde contient la clé SSH privée : elle ne doit pas être partagée.

**Script de démarrage**

`tui -rc` ouvre le fichier `.tuirc` dans l'éditeur. Ce fichier contient une commande par ligne, et les lignes commençant par `#` sont ignorées. Les commandes s'exécutent au démarrage de T-UI, mais pas lors d'un rechargement après un changement de réglage.

```
# exemple de .tuirc
ssh srv uptime
notif list
```

## Apparence

```
fetch                       résumé du téléphone : modèle, Android, processeur, mémoire, stockage, batterie, IP
fx crt on                   lignes de balayage, coins assombris et texte lumineux
fx typing on                la sortie s'affiche caractère par caractère
fx speed 3                  vitesse de l'animation, en millisecondes par caractère (6 par défaut)
fx boot off                 désactive l'écran de démarrage
theme -preset amber         moniteur ambre
theme -preset matrix        vert sur noir
theme -preset c64           bleu Commodore 64
```

L'écran de démarrage affiche au lancement de T-UI le processeur, la mémoire, le stockage, le réseau et la batterie du téléphone. Il est activé par défaut.

Pendant l'animation de frappe, une sortie volumineuse (plus de 1 500 caractères en attente) s'affiche directement, pour ne pas prendre de retard.

## Limites connues

- **SSH** : le terminal T-UI affiche des lignes de texte. Les programmes plein écran (`vim`, `htop`, `nano`) ne fonctionnent pas dans une session SSH. Il faut utiliser `termux -open ssh user@host`.
- **Termux, commandes en arrière-plan** : le résultat arrive en une fois, à la fin de la commande. Il est limité à 100 Ko par Termux. Ces commandes ne peuvent pas lire de saisie et ne peuvent pas être interrompues par Ctrl+C.
- **Ctrl+C en mode root** : Ctrl+C n'agit pas sur les commandes lancées après `su`.
- **`scan`** : il couvre au maximum 254 adresses (un réseau /24) autour de l'adresse du téléphone. Les appareils qui ne répondent ni au ping ni sur les ports 22, 80, 443 et 445 n'apparaissent pas.
- **Effet CRT** : il ajoute un calque dessiné en permanence au-dessus de l'écran. Son effet sur l'autonomie n'a pas été mesuré.

## Compilation

Prérequis : JDK 17 ou plus récent, Android SDK 35.

```
./gradlew assembleFdroidRelease
```

L'APK est produit dans `app/build/outputs/apk/fdroid/release/`.

Pour signer la version release avec votre propre clé, ajoutez ces lignes dans `local.properties`, qui n'est pas versionné :

```
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Sans ces lignes, la version release est signée avec la clé de débogage.

## Crédits

- Projet d'origine : [T-UI Launcher](https://github.com/fandreuz/TUI-ConsoleLauncher), Francesco Andreuzzi, licence MIT.
- Communauté T-UI : [Reddit](https://www.reddit.com/r/tui_launcher/), [Telegram](https://t.me/tuilauncher), [wiki](https://github.com/Andre1299/TUI-ConsoleLauncher/wiki).
- Bibliothèques : [JSch](https://github.com/mwiede/jsch) (SSH), [Bouncy Castle](https://www.bouncycastle.org/) (cryptographie), [OkHttp](https://github.com/square/okhttp), [CompareString2](https://github.com/fAndreuzzi/CompareString2), [HTML cleaner](http://htmlcleaner.sourceforge.net/), [JsonPath](https://github.com/json-path/JsonPath), [jsoup](https://github.com/jhy/jsoup/).

Licence : MIT, voir [LICENSE](LICENSE).
