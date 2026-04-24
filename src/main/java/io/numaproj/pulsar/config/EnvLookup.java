package io.numaproj.pulsar.config;

/**
 * Resolves process environment variables by name.
 * Tests may substitute a map-backed implementation.
 */
@FunctionalInterface
public interface EnvLookup {

    /**
     * Returns the value of the environment variable, or null if not set.
     *
     * @param name the environment variable name
     * @return the variable's value, or null if not set
     */
    String get(String name);

    /**
     * Returns a lookup that reads environment variables from the current process.
     */
    static EnvLookup system() {
        return System::getenv;
    }
}
