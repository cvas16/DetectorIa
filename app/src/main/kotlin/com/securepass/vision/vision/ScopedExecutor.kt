package com.securepass.vision.vision

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Envuelve un ejecutor existente para proporcionar un método [shutdown] que permite
 * la cancelación posterior de los runnables enviados.
 */
class ScopedExecutor(private val executor: Executor) : Executor {

    private val shutdown = AtomicBoolean()

    override fun execute(command: Runnable) {
        // Retorna temprano si este objeto ha sido cerrado.
        if (shutdown.get()) {
            return
        }
        executor.execute {
            // Verifica de nuevo en caso de que se haya cerrado en el ínterin.
            if (shutdown.get()) {
                return@execute
            }
            command.run()
        }
    }

    /**
     * Después de llamar a este método, ningún runnable que haya sido enviado o se envíe
     * posteriormente comenzará a ejecutarse.
     *
     * <p>Los runnables que ya han comenzado a ejecutarse continuarán.
     */
    fun shutdown() {
        shutdown.set(true)
    }
}
