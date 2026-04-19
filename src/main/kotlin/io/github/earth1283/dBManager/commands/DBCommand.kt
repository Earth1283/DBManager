package io.github.earth1283.dBManager.commands

import io.github.earth1283.dBManager.DBManager
import io.github.earth1283.dBManager.database.DatabaseExplorer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DBCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("dbmanager.admin")) {
            sender.sendMessage("No permission.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("/db list - List databases")
            sender.sendMessage("/db execute <db> <sql> - Execute SQL")
            sender.sendMessage("/db gui - Open Experimental GUI")
            sender.sendMessage("/db web - Generate Web UI login link")
            return true
        }

        when (args[0].lowercase()) {
            "list" -> {
                val dbs = DBManager.connectionManager?.getAvailableDatabases() ?: emptySet()
                sender.sendMessage("Databases: ${dbs.joinToString()}")
            }
            "execute" -> {
                if (args.size < 3) {
                    sender.sendMessage("Usage: /db execute <db> <sql>")
                    return true
                }
                val dbName = args[1]
                val sql = args.drop(2).joinToString(" ")
                val ds = DBManager.connectionManager?.getDataSource(dbName)
                if (ds == null) {
                    sender.sendMessage("Database not found: $dbName")
                    return true
                }
                try {
                    val result = DatabaseExplorer.executeQuery(ds, sql)
                    sender.sendMessage("Success. Results: ${result.take(5)}") // Truncate for chat
                } catch (e: Exception) {
                    sender.sendMessage("Error: ${e.message}")
                }
            }
            "gui" -> {
                if (sender !is Player) {
                    sender.sendMessage("Players only.")
                    return true
                }
                sender.sendMessage("§cOpening Experimental GUI...")
                io.github.earth1283.dBManager.gui.DatabaseGUI.openDatabaseList(sender)
            }
            "web" -> {
                val token = java.util.UUID.randomUUID().toString()
                // valid for 5 minutes
                DBManager.webServer?.pendingTokens?.put(token, System.currentTimeMillis() + 300000)
                val port = org.bukkit.plugin.java.JavaPlugin.getPlugin(DBManager::class.java).config.getInt("web-ui.port", 8080)
                sender.sendMessage("Web UI login token generated. Valid for 5 minutes.")
                sender.sendMessage("Link: http://your-server-ip:$port/?token=$token")
            }
        }
        return true
    }
}
