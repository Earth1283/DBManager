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
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.http.content.*
import java.util.UUID

class WebServer(private val port: Int) {
    private var server: NettyApplicationEngine? = null
    
    // In-memory token storage (Token -> Expiry Timestamp)
    val pendingTokens = mutableMapOf<String, Long>()
    val activeSessions = mutableSetOf<String>()

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
                }
            }
        }
        server?.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
