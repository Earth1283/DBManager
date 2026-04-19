package io.github.earth1283.dBManager

import io.github.earth1283.dBManager.database.ConnectionManager
import org.bukkit.plugin.java.JavaPlugin

class DBManager : JavaPlugin() {
    companion object {
        lateinit var connectionManager: ConnectionManager
            private set
        var webServer: io.github.earth1283.dBManager.web.WebServer? = null
            private set
    }

    override fun onEnable() {
        saveDefaultConfig()
        connectionManager = ConnectionManager(dataFolder)
        connectionManager.loadFromConfig(config.getConfigurationSection("databases"))
        
        getCommand("dbmanager")?.setExecutor(io.github.earth1283.dBManager.commands.DBCommand())

        if (config.getBoolean("web-ui.enabled", true)) {
            val port = config.getInt("web-ui.port", 8080)
            webServer = io.github.earth1283.dBManager.web.WebServer(port)
            webServer?.start()
            logger.info("Web UI started on port $port")
        }
        
        logger.info("DBManager initialized ${connectionManager.getAvailableDatabases().size} pools.")
    }

    override fun onDisable() {
        webServer?.stop()
        if (::connectionManager.isInitialized) {
            connectionManager.closeAll()
        }
    }
}
