package org.webservices.testrunner.suites

import org.webservices.testrunner.framework.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.UUID

suspend fun TestRunner.memcachedCachingLayerTests() = suite("Memcached Caching Layer Tests") {

val memcachedUrl = System.getenv("MEMCACHED_URL") ?: "memcached:11211"

    fun parseMemcachedUrl(url: String): Pair<String, Int> {
        val normalized = url.removePrefix("memcached://")
        val parts = normalized.split(":")
        val host = parts.firstOrNull().orEmpty().ifBlank { "memcached" }
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 11211
        return host to port
    }

    fun uniqueCacheKey(prefix: String): String = "$prefix:${System.currentTimeMillis()}:${UUID.randomUUID()}"

test("Memcached: Service is reachable") {
        val (host, port) = parseMemcachedUrl(memcachedUrl)
        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 3000
                socket.isConnected shouldBe true
            }
            println("      ✓ Memcached is reachable at $host:$port")
        } catch (e: Exception) {
            throw AssertionError("Cannot connect to Memcached at $host:$port: ${e.message}")
        }
    }

    test("Memcached: ASCII protocol SET/GET works") {
        val (host, port) = parseMemcachedUrl(memcachedUrl)
        val key = uniqueCacheKey("itest")
        val value = "hello"

        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 3000
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))

                writer.write("set $key 0 30 ${value.length}\r\n$value\r\n")
                writer.flush()
                val storedLine = reader.readLine() ?: ""
                require(storedLine == "STORED") {
                    "Expected STORED from memcached SET, got '$storedLine'"
                }

                writer.write("get $key\r\n")
                writer.flush()
                val valueHeader = reader.readLine() ?: ""
                val valueLine = reader.readLine() ?: ""
                var endLine = reader.readLine() ?: ""
                if (endLine != "END") {
                    while (endLine.isNotEmpty() && endLine != "END") {
                        endLine = reader.readLine() ?: ""
                    }
                }
                require(valueHeader.startsWith("VALUE $key")) {
                    "Expected VALUE header for key '$key', got '$valueHeader'"
                }
                require(valueLine == value) {
                    "Expected value '$value', got '$valueLine'"
                }
            }

            println("      ✓ Memcached ASCII protocol SET/GET successful")
        } catch (e: Exception) {
            throw AssertionError("Memcached SET/GET test failed: ${e.message}")
        }
    }
}
