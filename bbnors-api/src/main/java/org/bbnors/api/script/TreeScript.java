package org.bbnors.api.script;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Decision-tree script: branches test a condition and descend into the first accepting child;
 * leaves perform an action. Expresses "if bank open → (if has logs → deposit else close) else ..."
 * without nested if-chains in one big loop method.
 */
public abstract class TreeScript implements Script {

    /** Tree node. */
    public sealed interface Node permits Branch, Leaf {
        String name();

        boolean accept(ScriptContext ctx);
    }

    /** Inner node: accepted when its condition holds; runs its first accepting child. */
    public record Branch(String name, Predicate<ScriptContext> condition, List<Node> children) implements Node {
        public Branch {
            children = List.copyOf(children);
        }

        @Override
        public boolean accept(ScriptContext ctx) {
            return condition.test(ctx);
        }
    }

    /** Action node. */
    public record Leaf(String name, Predicate<ScriptContext> condition, ToIntFunction<ScriptContext> action) implements Node {
        @Override
        public boolean accept(ScriptContext ctx) {
            return condition.test(ctx);
        }
    }

    public static Branch branch(String name, Predicate<ScriptContext> condition, Node... children) {
        return new Branch(name, condition, List.of(children));
    }

    public static Leaf leaf(String name, Predicate<ScriptContext> condition, ToIntFunction<ScriptContext> action) {
        return new Leaf(name, condition, action);
    }

    public static Leaf leaf(String name, ToIntFunction<ScriptContext> action) {
        return new Leaf(name, c -> true, action);
    }

    private volatile String path = "";

    /** The root of the tree (called once per loop; may return a cached instance). */
    protected abstract Node root();

    protected int idleDelay() {
        return 300;
    }

    @Override
    public final int loop(ScriptContext ctx) {
        Node n = root();
        StringBuilder trail = new StringBuilder();
        while (n.accept(ctx)) {
            trail.append(trail.isEmpty() ? "" : " > ").append(n.name());
            if (n instanceof Leaf l) {
                path = trail.toString();
                return l.action().applyAsInt(ctx);
            }
            Node next = null;
            for (Node c : ((Branch) n).children()) {
                if (c.accept(ctx)) {
                    next = c;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            n = next;
        }
        path = trail + " (no leaf)";
        return idleDelay();
    }

    @Override
    public String status() {
        return path;
    }
}
