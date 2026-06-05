package codefab.executor;

/**
 * A fixed-size array runtime value. Backing storage is an {@code Object[]} that
 * starts entirely {@code null}. Bounds and type checking of indices lives in the
 * {@link Executor} (the array itself only stores and retrieves by validated
 * integer slot).
 */
public final class CodeFabArray {
    private final Object[] elements;

    public CodeFabArray(int size) {
        this.elements = new Object[size]; // all slots start as null
    }

    public int size() {
        return elements.length;
    }

    public Object get(int index) {
        return elements[index];
    }

    public void set(int index, Object value) {
        elements[index] = value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Executor.stringify(elements[i]));
        }
        return sb.append("]").toString();
    }
}
