package io.realworld.app.web.rules

import io.ktor.server.engine.ConnectorType
import io.realworld.app.config.SERVER_PORT
import io.realworld.app.config.setup
import io.realworld.app.web.util.HttpUtil
import java.net.Socket
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

class AppRule : ExternalResource() {
    private val app = setup(false)
    lateinit var http: HttpUtil
    val port = app.environment.connectors.find { it.type == ConnectorType.HTTP }?.port ?: SERVER_PORT

    override fun before() {
        app.start()
        waitForServer()
        http = HttpUtil(port)
    }

    override fun after() {
        app.stop(500, 500, TimeUnit.MILLISECONDS)
    }

    private fun waitForServer() {
        repeat(20) {
            try {
                Socket("127.0.0.1", port).use { return }
            } catch (ignored: Exception) {
                TimeUnit.MILLISECONDS.sleep(100)
            }
        }
        throw IllegalStateException("Server did not start on port $port.")
    }
}
