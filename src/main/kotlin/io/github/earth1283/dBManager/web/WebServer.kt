package io.github.earth1283.dBManager.web

import com.google.gson.Gson
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
import java.util.UUID

class WebServer(private val port: Int) {
    private var server: NettyApplicationEngine? = null
    
    // In-memory token storage (Token -> Expiry Timestamp)
    val pendingTokens = mutableMapOf<String, Long>()
    val activeSessions = mutableSetOf<String>()

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader("Authorization")
            }
            routing {
                // SPA Fallback
                get("/") {
                    call.respondText("DBManager Web UI (Static files not yet bundled)", ContentType.Text.Html)
                }

                // Auth Endpoints
                get("/api/auth/verify") {
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

                // Protected API Routes
                route("/api") {
                    // Primitive Auth Interceptor
                    intercept(ApplicationCallPipeline.Plugins) {
                        val authHeader = call.request.headers["Authorization"]
                        val sessionToken = authHeader?.removePrefix("Bearer ")
                        if (sessionToken == null || !activeSessions.contains(sessionToken)) {
                            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                            finish()
                        }
                    }

                    get("/databases") {
                        val dbs = DBManager.connectionManager.getAvailableDatabases()
                        call.respondText(Gson().toJson(dbs), ContentType.Application.Json)
                    }

                    get("/databases/{db}/tables") {
                        val db = call.parameters["db"] ?: return@get
                        val ds = DBManager.connectionManager.getDataSource(db)
                        if (ds == null) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "DB not found"))
                            return@get
                        }
                        val tables = DatabaseExplorer.getTables(ds)
                        call.respondText(Gson().toJson(tables), ContentType.Application.Json)
                    }

                    post("/databases/{db}/query") {
                        val db = call.parameters["db"] ?: return@post
                        val ds = DBManager.connectionManager.getDataSource(db)
                        if (ds == null) {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "DB not found"))
                            return@post
                        }
                        val sql = call.receiveText()
                        try {
                             val result = DatabaseExplorer.executeQuery(ds, sql)
                             call.respondText(Gson().toJson(result), ContentType.Application.Json)
                        } catch (e: Exception) {
                             call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
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
