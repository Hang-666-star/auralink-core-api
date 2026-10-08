package com.auralink.creation.provider;

public interface ProviderSafeDiagnostic<S extends Enum<S>, C extends Enum<C>, R> {
   S validationStage();

   C validationCode();

   R responseShape();
}
