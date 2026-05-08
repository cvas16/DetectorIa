package com.securepass.vision;

import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Envuelve un ejecutor existente para proporcionar un método {@link #shutdown} que permite
 * la cancelación posterior de los runnables enviados.
 */
public class ScopedExecutor implements Executor {

  private final Executor executor;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  public ScopedExecutor(@NonNull Executor executor) {
    this.executor = executor;
  }

  @Override
  public void execute(@NonNull Runnable command) {
    // Retorna temprano si este objeto ha sido cerrado.
    if (shutdown.get()) {
      return;
    }
    executor.execute(
        () -> {
          // Verifica de nuevo en caso de que se haya cerrado en el ínterin.
          if (shutdown.get()) {
            return;
          }
          command.run();
        });
  }

  /**
   * Después de llamar a este método, ningún runnable que haya sido enviado o se envíe
   * posteriormente comenzará a ejecutarse.
   *
   * <p>Los runnables que ya han comenzado a ejecutarse continuarán.
   */
  public void shutdown() {
    shutdown.set(true);
  }
}
