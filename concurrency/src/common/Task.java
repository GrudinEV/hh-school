package common;

import java.util.concurrent.ThreadLocalRandom;

public class Task implements Runnable {

  private final int iterations;

  public Task(int iterations) {
    this.iterations = iterations;
  }

  private int blackHole;

  @Override
  public void run() {
    int blackHole = 0;
    // ThreadLocalRandom caches instance of Random per thread. Random instance is expensive to create.
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < iterations; i++) {
      // can we comment this block? is it needed?
      // - it is needed, otherwise JIT will unroll the loop, and we won't spend CPU cycles here
      blackHole += random.nextInt();
      onIteration();
    }
    this.blackHole += blackHole;
  }

  protected void onIteration() {
  }

  public int getBlackHole() {
    return blackHole;
  }
}
