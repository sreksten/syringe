package jakarta.enterprise.invoke;

/**
 * Minimal Java 8 compatible stub of CDI Invoker (CDI 4.0+).
 * Included to allow compilation on Java 8 where upstream API classes
 * are compiled for newer bytecode levels.
 */
@FunctionalInterface
public interface Invoker<T, R> {
    R invoke(T instance, Object... parameters) throws Exception;
}
