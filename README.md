# TUI-VibeLauncher

An Android launcher where the home screen is a command line. This repository is a fork of [T-UI Launcher](https://github.com/fandreuz/TUI-ConsoleLauncher). It updates the launcher for Android 15 and adds an SSH client, network tools and terminal commands.

Built for a Samsung Galaxy S25. Features are tested on the Android 15 emulator (API 35). The minimum version is still Android 5.0 (API 21).

## Contents

- [Android 15 compatibility](#android-15-compatibility)
- [New commands](#new-commands)
- [SSH and servers](#ssh-and-servers)
- [Linux tools through Termux](#linux-tools-through-termux)
- [Terminal](#terminal)
- [Network](#network)
- [Notifications](#notifications)
- [Backup and startup](#backup-and-startup)
- [Appearance](#appearance)
- [Known limitations](#known-limitations)
- [Building](#building)
- [Credits](#credits)

## Android 15 compatibility

| Change | Reason |
|---|---|
| Target SDK 35, Gradle 8.14, Android Gradle Plugin 8.11, Java 17 | The previous versions don't build with a recent JDK and don't target Android 15. |
| Black background by default | The background was white on Android 15. |
| Edge-to-edge display disabled | Android 15 enables it by default, which put the text under the status bar. |
| Interactive notifications fixed | Quick reply from a T-UI notification was blocked by Android 14 and later. |
| Permissions asked only once | T-UI asked for its permissions at every start. Storage and notification access are now requested when a command needs them, or with `tui -storage` and `tui -notifaccess`. |
| System commands (`ls`, `cat`, `ping`, `ps`...) | They were blocked by a dependency on BusyBox. They now use the tools shipped with Android. |

BusyBox can no longer run on Android 10 and later: the system doesn't allow an app to execute a program it downloaded. `bbman -install` now explains this and points to Termux.

## New commands

| Command | Purpose |
|---|---|
| `ssh` | Built-in SSH client, with saved servers |
| `termux` | Runs Linux tools through the Termux app |
| `history` | Searches the command history |
| `scan` | Lists the devices on the local network |
| `ports` | Lists the open TCP ports of a machine |
| `wol` | Turns on a machine remotely (Wake-on-LAN) |
| `myip` | Shows the local IP addresses and the public one |
| `findout` | Highlights text in the terminal |
| `notif` | Shows notifications in the terminal |
| `fetch` | Shows a summary of the phone |
| `fx` | Visual effects |
| `tui -backup`, `tui -restore`, `tui -rc` | Backup, restore, startup script |

`help <command>` shows the details of each command in the app.

## SSH and servers

The SSH client works without any other app.

```
ssh root@192.168.10.100              opens a session
ssh root@192.168.10.100 uptime       runs one command and prints the result
ssh -p 2222 user@host                another port (user@host:2222 works too)
ssh -i keyfile user@host             another private key
```

- On the first connection, the server fingerprint is shown and must be confirmed. It is then saved and checked on every connection.
- Passwords are hidden while typing, including for `sudo` on the server. They are never saved in the history.
- During a session, everything typed is sent to the server. `~.` closes the session, `~c` sends Ctrl+C, `~d` sends Ctrl+D.

**SSH key**

```
ssh -keygen      creates an ed25519 key pair and shows the public key
ssh -pubkey      shows the public key
```

Once the public key is added to `~/.ssh/authorized_keys` on the server, no password is needed.

**Saved servers**

```
ssh -add srv root@192.168.10.100                    saves a server
ssh -add nas admin@192.168.10.20 -mac AA:BB:CC:DD:EE:FF
ssh srv                                             connects to a saved server
ssh -ls                                             lists the saved servers
ssh -rm srv                                         removes a server
ssh -forget 192.168.10.100                          forgets the fingerprint of a reinstalled server
```

Saved servers are shown on the home screen with their status, updated every 60 seconds:

```
● srv 6ms  ○ nas down
```

A server counts as reachable if it accepts a connection on its SSH port. The interval is set with the `servers_check_interval` option, the display with `show_servers`.

## Linux tools through Termux

Android ships only a small set of Unix commands. If [Termux](https://f-droid.org/packages/com.termux/) is installed, the commands Android lacks (`git`, `python`, `nmap`, `rsync`...) are passed to it automatically, and the result is shown in T-UI.

```
termux -setup                  guided setup (once)
git status                     passed to Termux automatically
termux pkg install -y git      installs a package
termux -open htop              opens a Termux window for full-screen programs
```

Termux must be installed from F-Droid. The Play Store version is no longer maintained.

## Terminal

**Stopping a command (Ctrl+C)**

A command that doesn't stop on its own (`ping`, `top`...) can be stopped in three ways:

- long press on the clear button of the toolbar;
- type `^c` or `~c`;
- Ctrl+C on a physical keyboard.

The command receives the same signal as with a real Ctrl+C, so `ping` prints its statistics before stopping.

**History**

The history is kept across restarts (500 commands at most).

```
history              latest commands
history ssh          commands containing "ssh"
history -clear       clears the history
```

The arrows of the toolbar browse this history.

**Completion**

The Tab button of the toolbar, or the Tab key of a physical keyboard, completes with the first suggestion. Completion covers server names, the options of the new commands and file paths.

**Pipes and redirections**

T-UI commands accept `|`, `>` and `>>`, like system commands:

```
scan | grep 192.168.10
history | grep ssh
fetch > /sdcard/Download/phone.txt
myip >> /sdcard/Download/ip.txt
```

**Search and copy**

```
findout error        highlights "error" in the terminal and scrolls to the last match
findout              removes the highlights
```

A long press on a terminal line copies it to the clipboard.

## Network

```
scan                          devices that answer on the Wi-Fi network (about 5 seconds)
ports srv                     common ports: SSH, HTTP, SMB, Proxmox, Home Assistant, Plex...
ports 192.168.10.1 1-1024     a range of ports
ports nas 22,80,443           a list of ports
wol nas                       turns on a saved machine using its MAC address
wol AA:BB:CC:DD:EE:FF         turns on a machine from its MAC address
myip                          local IP addresses and public address
```

To get the public address, `myip` asks api.ipify.org, icanhazip.com, checkip.amazonaws.com and ifconfig.me, in that order. It moves to the next one when a site is blocked by the network's DNS.

Wake-on-LAN must be enabled in the BIOS and in the network settings of the machine to turn on.

## Notifications

```
notif on        shows new notifications in the terminal
notif off       stops showing them
notif list      lists the current notifications
notif clear     dismisses the notifications that can be dismissed
```

`notif on` asks for notification access if T-UI doesn't have it yet. Filters and display formats are set with the `notifications` command.

## Backup and startup

**Backup**

```
tui -backup      creates Download/t-ui-backup-YYYYMMDD-HHMM.zip
tui -restore     opens the file picker and restores the chosen backup
```

The backup contains the settings, aliases, history, saved servers, SSH key and startup script. It matters because the T-UI folder is deleted when the app is uninstalled.

The backup contains the private SSH key: don't share it.

**Startup script**

`tui -rc` opens the `.tuirc` file in the editor. The file holds one command per line; lines starting with `#` are ignored. The commands run when T-UI starts, but not when it reloads after a settings change.

```
# example .tuirc
ssh srv uptime
notif list
```

## Appearance

```
fetch                       phone summary: model, Android, CPU, memory, storage, battery, IP
fx crt on                   scanlines, darker corners and glowing text
fx typing on                output is shown one character at a time
fx speed 3                  animation speed, in milliseconds per character (default 6)
fx boot off                 turns off the boot screen
theme -preset amber         amber monitor
theme -preset matrix        green on black
theme -preset c64           Commodore 64 blue
```

When T-UI starts, the boot screen shows the phone's CPU, memory, storage, network and battery. It is on by default.

During the typing animation, large output (more than 1,500 characters waiting) is shown at once so the display doesn't fall behind.

## Known limitations

- **SSH**: the T-UI terminal shows lines of text. Full-screen programs (`vim`, `htop`, `nano`) don't work in an SSH session. Use `termux -open ssh user@host` instead.
- **Termux background commands**: the result arrives all at once, when the command ends. Termux limits it to 100 KB. These commands can't read input and can't be stopped with Ctrl+C.
- **Ctrl+C as root**: Ctrl+C has no effect on commands started after `su`.
- **`scan`**: it covers at most 254 addresses (a /24 network) around the phone's address. Devices that answer neither ping nor ports 22, 80, 443 and 445 are not listed.
- **CRT effect**: it adds a layer drawn over the whole screen. Its effect on battery life hasn't been measured.

## Building

Requirements: JDK 17 or later, Android SDK 35.

```
./gradlew assembleFdroidRelease
```

The APK is written to `app/build/outputs/apk/fdroid/release/`.

To sign the release build with your own key, add these lines to `local.properties`, which is not versioned:

```
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without them, the release build is signed with the debug key.

More build, install and debugging commands are in [COMMANDS.md](COMMANDS.md).

## Credits

- Original project: [T-UI Launcher](https://github.com/fandreuz/TUI-ConsoleLauncher), Francesco Andreuzzi, MIT license.
- T-UI community: [Reddit](https://www.reddit.com/r/tui_launcher/), [Telegram](https://t.me/tuilauncher), [wiki](https://github.com/Andre1299/TUI-ConsoleLauncher/wiki).
- Libraries: [JSch](https://github.com/mwiede/jsch) (SSH), [Bouncy Castle](https://www.bouncycastle.org/) (cryptography), [OkHttp](https://github.com/square/okhttp), [CompareString2](https://github.com/fAndreuzzi/CompareString2), [HTML cleaner](http://htmlcleaner.sourceforge.net/), [JsonPath](https://github.com/json-path/JsonPath), [jsoup](https://github.com/jhy/jsoup/).

License: MIT, see [LICENSE](LICENSE).
