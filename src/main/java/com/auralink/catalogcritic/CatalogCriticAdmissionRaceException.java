package com.auralink.catalogcritic;

final class CatalogCriticAdmissionRaceException extends RuntimeException {
   CatalogCriticAdmissionRaceException() {
      super("Concurrent Critic admission");
   }
}
