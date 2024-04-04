# DutchyTPAEx

This plugin is a modifed version of [DutchTPA](https://github.com/TobiasDeBruijn/DutchyTPA). I modified the original plugin for my friend's server, adding new features like waypoint system, teleport fee system and such. If you only need a simple TPA plugin, please check out the original one.

Minecraft:

- 1.16
- 1.17
- 1.18
- 1.19
- Unless something happens, everything afterwards too

I tested on my friend's server, which run the 1.20.4 server software, and it would perfectly fine. Actually unless they change the way plugin teleport a player, I think it should work no matter the version.

This is a very, very simple TPA plugin.

## Commands

- `/tpa <playername>`: Teleport to a player
- `/tpaccept`, `/tpyes`: Accept a TPA request
- `/tpdeny`, `/tpno`: Deny a TPA request

New commands in this version:

- `/tpa location list`: List the available waypoint and the teleport cost
- `/tpa location add`: Create a new waypoint at your current position
- `/tpa location remove`: Remove an existing waypoint from the waypoint list
- `/tpa location go`: Teleport to the waypoint

Told you, it's simple :)

## License

This project is licensed under

- [MIT](LICENSE-MIT)
- [Apache 2.0](LICENSE-APACHE)

At your option
