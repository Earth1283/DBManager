package io.github.earth1283.dBManager

import io.github.earth1283.dBManager.database.ConnectionManager
import org.bukkit.plugin.java.JavaPlugin

class DBManager : JavaPlugin() {
    companion object {
        lateinit var connectionManager: ConnectionManager
            private set
    }

    override fun onEnable() {
        saveDefaultConfig()
        connectionManager = ConnectionManager(dataFolder)
        connectionManager.loadFromConfig(config.getConfigurationSection("databases"))
        logger.info("DBManager initialized ${connectionManager.getAvailableDatabases().size} pools.")
    }

    override fun onDisable() {
        if (::connectionManager.isInitialized) {
            connectionManager.closeAll()
        }
    }
}
