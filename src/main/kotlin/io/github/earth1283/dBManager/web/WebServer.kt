package io.github.earth1283.dBManager.web

import io.github.earth1283.dBManager.DBManager
import io.github.earth1283.dBManager.database.DatabaseExplorer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.http.content.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebServer(private val port: Int) {
    private var server: NettyApplicationEngine? = null

    // In-memory token storage (Token -> Expiry Timestamp) — ConcurrentHashMap for thread safety
    val pendingTokens: MutableMap<String, Long> = ConcurrentHashMap()
    val activeSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader("Authorization")
            }
            routing {
                // Serve static files
                static("/") {
                    resources("web")
                    defaultResource("web/index.html")
                }

                // API Routes
                route("/api") {
                    // Auth endpoint — must be resolved before the auth interceptor below
                    get("/auth/verify") {
                        val token = call.request.queryParameters["token"]
                        if (token != null && pendingTokens.containsKey(token)) {
                            if (System.currentTimeMillis() < pendingTokens[token]!!) {
                                pendingTokens.remove(token)
                                val sessionId = UUID.randomUUID().toString()
                                activeSessions.add(sessionId)
                                call.respond(mapOf("success" to true, "sessionId" to sessionId))
                                return@get
                            }
                            pendingTokens.remove(token)
                        }
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
                    }

                    // Auth interceptor for all other /api/* routes
                    intercept(ApplicationCallPipeline.Plugins) {
                        // Skip auth for the verify endpoint itself
                        if (call.request.uri.startsWith("/api/auth/")) return@intercept

                        val authHeader = call.request.headers["Authorization"]
                        val sessionToken = authHeader?.removePrefix("Bearer ")
                        if (sessionToken == null || !activeSessions.contains(sessionToken)) {
                            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                            finish()
                        }
                    }

                    get("/databases") {
                        val dbs = DBManager.connectionManager?.getAvailableDatabases() ?: emptySet()
                        call.respond(dbs)
                    }

                    get("/databases/{db}/tables") {
                        val db = call.parameters["db"] ?: return@get
                        val ds = DBManager.connectionManager?.getDataSource(db)
                        if (ds == null) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "DB not found"))
                            return@get
                        }
                        val tables = DatabaseExplorer.getTables(ds)
                        call.respond(tables)
                    }

                    post("/databases/{db}/query") {
                        val db = call.parameters["db"] ?: return@post
                        val ds = DBManager.connectionManager?.getDataSource(db)
                        if (ds == null) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "DB not found"))
                            return@post
                        }
                        val sql = call.receiveText()
                        try {
                             val result = DatabaseExplorer.executeQuery(ds, sql)
                             call.respond(result)
                        } catch (e: Exception) {
                             call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                        }
                    }

                    // Filesystem browser — lists subdirectories and .db/.sqlite files under the server root
                    get("/fs/browse") {
                        val serverRoot = java.io.File(".").canonicalFile
                        val pathParam = call.request.queryParameters["path"] ?: ""
                        val target = if (pathParam.isEmpty()) serverRoot
                                     else java.io.File(serverRoot, pathParam).canonicalFile

                        if (!target.canonicalPath.startsWith(serverRoot.canonicalPath)) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied: path outside server directory"))
                            return@get
                        }
                        if (!target.exists() || !target.isDirectory) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Directory not found"))
                            return@get
                        }

                        val children = target.listFiles() ?: emptyArray()
                        val dirs = children
                            .filter { it.isDirectory && !it.name.startsWith(".") }
                            .sortedBy { it.name }
                            .map { mapOf("name" to it.name, "rel" to it.relativeTo(serverRoot).path) }
                        val files = children
                            .filter { it.isFile && it.extension.lowercase() in listOf("db", "sqlite", "sqlite3") }
                            .sortedBy { it.name }
                            .map { mapOf("name" to it.name, "abs" to it.absolutePath, "size" to it.length()) }
                        val parentRel: String? = if (target == serverRoot) null
                                                 else target.parentFile.relativeTo(serverRoot).path

                        call.respond(mapOf(
                            "current" to target.relativeTo(serverRoot).path,
                            "parent" to parentRel,
                            "dirs" to dirs,
                            "files" to files
                        ))
                    }

                    // Add a new dynamic connection
                    post("/connections") {
                        val body = call.receive<Map<String, Any>>()
                        val name = (body["name"] as? String)?.trim()
                        if (name.isNullOrEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Connection name is required"))
                            return@post
                        }
                        if (DBManager.connectionManager?.getAvailableDatabases()?.contains(name) == true) {
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to "A connection named '$name' already exists"))
                            return@post
                        }
                        val cm = DBManager.connectionManager ?: run {
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "ConnectionManager unavailable"))
                            return@post
                        }
                        val type = (body["type"] as? String)?.lowercase() ?: "sqlite"
                        val ok = when (type) {
                            "sqlite" -> {
                                val path = body["path"] as? String
                                if (path.isNullOrEmpty()) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "SQLite file path is required"))
                                    return@post
                                }
                                cm.addSqliteConnection(name, path)
                            }
                            "mysql", "mariadb", "postgresql" -> {
                                val host     = (body["host"] as? String) ?: "localhost"
                                val port     = (body["port"] as? Double)?.toInt()
                                               ?: (body["port"] as? Int)
                                               ?: if (type == "postgresql") 5432 else 3306
                                val database = (body["database"] as? String) ?: ""
                                val username = (body["username"] as? String) ?: ""
                                val password = (body["password"] as? String) ?: ""
                                cm.addRemoteConnection(name, type, host, port, database, username, password)
                            }
                            else -> {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown type '$type'"))
                                return@post
                            }
                        }
                        if (ok) call.respond(mapOf("success" to true, "name" to name))
                        else call.respond(HttpStatusCode.InternalServerError,
                                          mapOf("error" to "Connection failed — check server logs for details"))
                    }

                    // Remove a dynamic connection
                    delete("/connections/{name}") {
                        val name = call.parameters["name"] ?: return@delete
                        val cm = DBManager.connectionManager
                        if (cm == null || !cm.removeConnection(name)) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Connection '$name' not found"))
                            return@delete
                        }
                        call.respond(mapOf("success" to true))
                    }

                    // Create a new database on an existing remote connection (MySQL / PostgreSQL)
                    post("/databases/create") {
                        val body = call.receive<Map<String, Any>>()
                        val via    = (body["via"]    as? String)?.trim()
                        val dbname = (body["dbname"] as? String)?.trim()
                        if (via.isNullOrEmpty() || dbname.isNullOrEmpty()) {
                            call.respond(HttpStatusCode.BadRequest,
                                mapOf("error" to "'via' (existing connection name) and 'dbname' are required"))
                            return@post
                        }
                        // Validate name to prevent SQL injection (letters, digits, underscore only)
                        if (!dbname.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                            call.respond(HttpStatusCode.BadRequest,
                                mapOf("error" to "Database name must start with a letter or underscore and contain only letters, digits, and underscores"))
                            return@post
                        }
                        val ds = DBManager.connectionManager?.getDataSource(via) ?: run {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Connection '$via' not found"))
                            return@post
                        }
                        try {
                            ds.connection.use { conn ->
                                conn.createStatement().use { stmt -> stmt.execute("CREATE DATABASE $dbname") }
                            }
                            call.respond(mapOf("success" to true))
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Unknown error")))
                        }
                    }
                }
            }
        }
        server?.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
