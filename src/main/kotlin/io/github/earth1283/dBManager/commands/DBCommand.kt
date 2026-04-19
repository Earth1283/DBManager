package io.github.earth1283.dBManager.commands

import io.github.earth1283.dBManager.DBManager
import io.github.earth1283.dBManager.database.DatabaseExplorer
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DBCommand : CommandExecutor {

    private val mm = MiniMessage.miniMessage()

    private fun CommandSender.msg(message: String) {
        val audiences: BukkitAudiences = DBManager.audiences ?: run {
            // Fallback: strip MiniMessage tags and send as plain text
            sendMessage(mm.stripTags(message))
            return
        }
        audiences.sender(this).sendMessage(mm.deserialize(message))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("dbmanager.admin")) {
            sender.msg("<red>You do not have permission to use this command.</red>")
            return true
        }

        if (args.isEmpty()) {
            sender.msg("<gold><bold>DBManager Commands</bold></gold>")
            sender.msg("<yellow>/db list</yellow> <gray>— List all configured databases</gray>")
            sender.msg("<yellow>/db execute <db> <sql></yellow> <gray>— Execute SQL on a database</gray>")
            sender.msg("<yellow>/db gui</yellow> <gray>— Open the experimental chest GUI (players only)</gray>")
            sender.msg("<yellow>/db web</yellow> <gray>— Generate a one-time Web UI login link</gray>")
            return true
        }

        when (args[0].lowercase()) {
            "list" -> {
                val dbs = DBManager.connectionManager?.getAvailableDatabases() ?: emptySet()
                if (dbs.isEmpty()) {
                    sender.msg("<yellow>No databases are currently loaded.</yellow>")
                } else {
                    sender.msg("<gold>Active databases <gray>(${dbs.size}):</gray></gold>")
                    dbs.forEach { sender.msg("<green> • $it</green>") }
                }
            }

            "execute" -> {
                if (args.size < 3) {
                    sender.msg("<red>Usage: /db execute <db> <sql></red>")
                    return true
                }
                val dbName = args[1]
                val sql = args.drop(2).joinToString(" ")
                val ds = DBManager.connectionManager?.getDataSource(dbName)
                if (ds == null) {
                    sender.msg("<red>Database not found: <white>$dbName</white></red>")
                    return true
                }
                try {
                    val result = DatabaseExplorer.executeQuery(ds, sql)
                    sender.msg("<green>Query successful.</green> <gray>Rows returned: ${result.size}</gray>")
                    if (result.isNotEmpty()) {
                        // Show up to 5 rows in chat
                        result.take(5).forEach { row ->
                            sender.msg("<gray> ${row.entries.joinToString(" | ") { "<white>${it.key}</white>=<aqua>${it.value}</aqua>" }}</gray>")
                        }
                        if (result.size > 5) sender.msg("<gray> ...and ${result.size - 5} more row(s). Use the Web UI for full results.</gray>")
                    }
                } catch (e: Exception) {
                    sender.msg("<red>SQL error: <white>${e.message}</white></red>")
                }
            }

            "gui" -> {
                if (sender !is Player) {
                    sender.msg("<red>This command can only be used by players.</red>")
                    return true
                }
                sender.msg("<yellow>Opening experimental GUI...</yellow>")
                io.github.earth1283.dBManager.gui.DatabaseGUI.openDatabaseList(sender)
            }

            "web" -> {
                val ws = DBManager.webServer
                if (ws == null) {
                    sender.msg("<red>Web UI is not running.</red> <gray>Set <white>web-ui.enabled: true</white> in config.yml and reload.</gray>")
                    return true
                }
                val token = java.util.UUID.randomUUID().toString()
                ws.pendingTokens[token] = System.currentTimeMillis() + 300_000
                val port = org.bukkit.plugin.java.JavaPlugin.getPlugin(DBManager::class.java).config.getInt("web-ui.port", 8080)
                sender.msg("<green>Web UI login token generated. <gold>Valid for 5 minutes.</gold></green>")
                sender.msg("<aqua>Link: <white>http://your-server-ip:$port/?token=$token</white></aqua>")
            }

            else -> {
                sender.msg("<red>Unknown subcommand. Type <white>/db</white> for help.</red>")
            }
        }
        return true
    }
}
