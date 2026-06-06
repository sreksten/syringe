package jakarta.enterprise.invoke;

/**
 * Minimal Java 8 compatible stub of CDI InvokerBuilder (CDI 4.0+).
 * Included to allow compilation on Java 8 where upstream API classes
 * are compiled for newer bytecode levels.
 */
public interface InvokerBuilder<T> {
    InvokerBuilder<T> withInstanceLookup();
    InvokerBuilder<T> withArgumentLookup(int position);
    T build();
}
