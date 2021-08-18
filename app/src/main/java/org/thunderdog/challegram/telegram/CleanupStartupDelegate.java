package org.thunderdog.challegram.telegram;

/**
 * Date: 2/23/18
 * Author: default
 */

public interface CleanupStartupDelegate {
  /**
   * Called when component has been initialized and authorization became ready
   * */
  void onPerformStartup (boolean isAfterRestart);

  /**
   * Called when component should reset any user-related settings
   */
  void onPerformUserCleanup ();

  /**
   * Called when TDLib client instance has been restarted
   */
  void onPerformRestart ();
}
