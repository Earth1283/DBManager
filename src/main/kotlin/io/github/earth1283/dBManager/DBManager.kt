package io.github.earth1283.dBManager

import org.bukkit.plugin.java.JavaPlugin

class DBManager : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        logger.info("DBManager starting up!")
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
