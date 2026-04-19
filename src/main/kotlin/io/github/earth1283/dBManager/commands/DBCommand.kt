package io.github.earth1283.dBManager.commands

import io.github.earth1283.dBManager.DBManager
import io.github.earth1283.dBManager.database.DatabaseExplorer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class DBCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("dbmanager.admin")) {
            sender.sendMessage("No permission.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("/db list - List databases")
            sender.sendMessage("/db execute <db> <sql> - Execute SQL")
            return true
        }

        when (args[0].lowercase()) {
            "list" -> {
                val dbs = DBManager.connectionManager.getAvailableDatabases()
                sender.sendMessage("Databases: ${dbs.joinToString()}")
            }
            "execute" -> {
                if (args.size < 3) {
                    sender.sendMessage("Usage: /db execute <db> <sql>")
                    return true
                }
                val dbName = args[1]
                val sql = args.drop(2).joinToString(" ")
                val ds = DBManager.connectionManager.getDataSource(dbName)
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
        }
        return true
    }
}
