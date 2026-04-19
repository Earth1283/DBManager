package io.github.earth1283.dBManager

import io.github.earth1283.dBManager.database.ConnectionManager
import org.bukkit.plugin.java.JavaPlugin

class DBManager : JavaPlugin() {
    companion object {
        var connectionManager: ConnectionManager? = null
            private set
        var webServer: io.github.earth1283.dBManager.web.WebServer? = null
            private set
    }

    override fun onEnable() {
        saveDefaultConfig()
        val manager = ConnectionManager(dataFolder)
        manager.loadFromConfig(config.getConfigurationSection("databases"))
        connectionManager = manager
        
        getCommand("dbmanager")?.setExecutor(io.github.earth1283.dBManager.commands.DBCommand())

        if (config.getBoolean("web-ui.enabled", true)) {
            val port = config.getInt("web-ui.port", 8080)
            webServer = io.github.earth1283.dBManager.web.WebServer(port)
            webServer?.start()
            logger.info("Web UI started on port $port")
        }
        
        logger.info("DBManager initialized ${connectionManager?.getAvailableDatabases()?.size ?: 0} pools.")
    }

    override fun onDisable() {
        webServer?.stop()
        connectionManager?.closeAll()
    }
}
