package org.folio.services;

/**
 * Startup Service Interface
 */
public interface StartupService {

  /**
   * Initializes all registered active subscribers
   */
  void initSubscribers();
}
