package io.numaproj.pulsar.config;

/**
 * Resolves process environment values by name. Production uses; tests may substitute
 * a custom lookup (e.g. map-backed) without mocking System.getenv(String)}, as 
 * mokito cannot mock System.getenv(String).
 */
@FunctionalInterface
public interface EnvLookup {

    String get(String name);
    static EnvLookup system() {
        return System::getenv;
    }
}
