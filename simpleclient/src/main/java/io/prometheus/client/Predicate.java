package io.prometheus.client;

/**
 * To be replaced with Java 8's {@code java.util.function.Predicate} once we drop support for older Java versions.
 */
public interface Predicate<T> {
    boolean test(T t);
}