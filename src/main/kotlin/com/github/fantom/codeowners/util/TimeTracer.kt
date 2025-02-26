package com.github.fantom.codeowners.util

import com.github.fantom.codeowners.util.TimeTracer.LogEntry.*
import com.intellij.openapi.util.Key
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory

class TimeTracerStub(val upper: TimeTracer) {
    fun start(name: String): TimeTracer {
        val tracer = upper.nested(name)
        tracer.start()
        return tracer
    }
}

class TimeTracer(val name: String, val isRoot: Boolean = false) : AutoCloseable {
    sealed class LogEntry {
        abstract val elapsedNs: Long
        class Log(val name: String, val ns: Long) : LogEntry() {
            override val elapsedNs: Long
                get() = ns

            override fun toString(): String {
                return "$name took $ns ns\n"
            }
        }
        class Nested(val tracer: TimeTracer) : LogEntry() {
            override val elapsedNs: Long
                get() = tracer.nanoTime

            override fun toString() = "${tracer}\n"
        }
    }
    private val logs: MutableList<LogEntry> = mutableListOf()

    private val sw = StopWatch()

    fun start() {
        sw.start()
    }

    fun stop() {
        sw.stop()
    }

    val nanoTime
        get() = sw.nanoTime

    fun log(name: String) {
        logs.add(Log(name, sw.nanoTime - (logs.lastOrNull()?.elapsedNs ?: 0)))
    }

    fun nested(name: String): TimeTracer {
        val nestedTracer = TimeTracer(name)
        logs.add(Nested(nestedTracer))
        return nestedTracer
    }

    fun nested() = TimeTracerStub(this)

    override fun toString(): String {
        val sb = StringBuilder("$name took ${sw.nanoTime} ns\n")
        logs.forEach {
            sb.append(it.toString().prependIndent("  "))
        }
        return sb.toString()
    }

    override fun close() {
        stop()
        if (isRoot) {
            logger.trace(toString())
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(TimeTracer::class.java)

        fun <T> wrap(name: String, f: (TimeTracer) -> T): T {
            val tracer = TimeTracer(name, true)
            tracer.start()
            return tracer.use {
                f(it)
            }
        }
    }
}

object TimeTracerKey : Key<TimeTracer>("TimeTracer")

inline fun <T> withNullableCloseable(closeable: AutoCloseable?, f: () -> T): T {
    return if (closeable == null) {
        f()
    } else {
        closeable.use { f() }
    }
}
