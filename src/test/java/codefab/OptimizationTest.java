package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.Checker;
import codefab.checker.ConstantFolder;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import codefab.executor.Environment;
import codefab.executor.Executor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the two pre-execution optimizations (shared-contracts §9) using Test
 * Doubles, ported from {@code complete}'s {@code 482ba0a} to the main structure
 * per spec {@code _workspace/08_distance_resolver_spec.md} §C:
 *
 * <ul>
 *   <li><b>Static binding</b>: a resolved access jumps straight to the computed
 *       scope distance instead of walking the whole chain. main records distance
 *       on the mutable {@code Expr.Variable/Assign.distance} field (no
 *       {@code CheckResult}/locals map), so assertions read {@code read.distance}
 *       after {@code new Checker(diags).check(stmts)}. A counting
 *       {@link Environment} subclass overrides the {@code step()} seam to count
 *       enclosing hops, showing distance-based {@code getAt} performs exactly
 *       {@code distance} hops while the legacy {@code get(token)} chain walk
 *       performs more.</li>
 *   <li><b>Constant folding</b>: a loop-body constant expression collapses from N
 *       {@code Binary} nodes to a single {@code Literal} (0 runtime ops) via the
 *       independent {@link ConstantFolder} pass, while semantics are preserved
 *       and a {@code 1/0} sub-expression is NOT folded away (its runtime
 *       {@code Division by zero.} is still reported).</li>
 * </ul>
 *
 * <p>The pipeline is wired by hand here: Checker (diagnostics + distance fields)
 * then {@code new ConstantFolder().fold(stmts)}, matching the facade order
 * (§6). distance is preserved across the fold because the folder copies it on
 * Assign rebuild and returns the same Variable instance (§3, §9-1).
 */
class OptimizationTest {

    // --- pipeline helpers --------------------------------------------------

    private List<Stmt> parse(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        List<Token> tokens = new Scanner(src, diags).scanTokens();
        List<Stmt> stmts = new Parser(tokens, diags).parse();
        assertTrue(diags.isEmpty(), () -> "assembler produced diagnostics: " + diags);
        return stmts;
    }

    /**
     * Runs the resolver: parses, then Checker.check records distance onto the
     * Variable/Assign nodes in place. Returns the same statement list (distance
     * lives on the nodes, main has no CheckResult/locals map).
     */
    private List<Stmt> resolve(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        List<Stmt> stmts = parse(src);
        new Checker(diags).check(stmts);
        assertTrue(diags.isEmpty(), () -> "checker produced diagnostics: " + diags);
        return stmts;
    }

    /** Resolver + constant folding pass, mirroring the facade pipeline (§6). */
    private List<Stmt> resolveAndFold(String src) {
        return new ConstantFolder().fold(resolve(src));
    }

    // --- AST traversal helpers ---------------------------------------------

    /** Finds the first Variable node whose name matches, anywhere in the program. */
    private Expr.Variable findVariable(List<Stmt> program, String name) {
        VariableFinder f = new VariableFinder(name);
        for (Stmt s : program) {
            s.accept(f);
            if (f.found != null) return f.found;
        }
        return f.found;
    }

    /** Counts Binary nodes reachable from a statement list. */
    private int countBinary(List<Stmt> program) {
        BinaryCounter c = new BinaryCounter();
        for (Stmt s : program) s.accept(c);
        return c.count;
    }

    /** Counts Literal nodes whose value equals the given double. */
    private int countLiteral(List<Stmt> program, double value) {
        LiteralCounter c = new LiteralCounter(value);
        for (Stmt s : program) s.accept(c);
        return c.count;
    }

    // =======================================================================
    // 1) STATIC BINDING
    // =======================================================================

    /**
     * distance-field assertion: a local variable read from inside three nested
     * blocks resolves to a distance equal to the nesting depth (3). If this
     * value is wrong the bug is in the Checker's resolver (static binding).
     */
    @Test
    void resolverComputesDistanceEqualToNestingDepth() {
        // local 'x' declared in the outermost block; read 3 scopes deeper.
        String src =
                "{ var x = 1;" +       // scope depth 0 (declares x)
                "  {" +                 // depth 1
                "    {" +               // depth 2
                "      {" +             // depth 3 -> reads x here
                "        print x;" +
                "      }" +
                "    }" +
                "  }" +
                "}";
        List<Stmt> program = resolve(src);
        Expr.Variable read = findVariable(program, "x");
        assertNotNull(read, "expected a Variable node for x in the program");
        assertEquals(3, read.distance,
                "x is declared 3 enclosing scopes above the read; "
                        + "a wrong value implies a resolver (Checker) bug");
    }

    /**
     * A name the Checker's scope stack never declares (the native {@code Array},
     * or a binding carried over from an earlier REPL run) is NOT resolved, so its
     * distance stays at the sentinel -1 and the Executor falls back to the chain
     * lookup that reaches the builtin scope (§8-0, §9-1).
     */
    @Test
    void unresolvableReferencesAreAbsentFromLocals() {
        List<Stmt> program = resolve("{ { var a = Array(2); print a; } }");
        Expr.Variable arrayRef = findVariable(program, "Array");
        assertNotNull(arrayRef, "expected a Variable node referencing the native Array");
        assertEquals(-1, arrayRef.distance,
                "Array is never declared in any user scope, so its distance must stay "
                        + "-1 and be looked up via the Executor fallback chain (builtin)");
    }

    /**
     * Test Double (direct unit): build builtin -> global -> A -> B -> C by hand
     * and read a binding three scopes up. distance-based getAt(3, ...) must take
     * EXACTLY 3 hops through the step() seam (no full chain walk), whereas the
     * legacy get(token) chain walk for the same value takes more hops.
     *
     * <p>Ported: main's getAt/assign/ancestor route every enclosing step through
     * the protected {@code step()} seam, which the CountingEnvironment overrides.
     */
    @Test
    void distanceAccessTakesExactlyDistanceHopsWhereasChainWalkTakesMore() {
        AtomicInteger hops = new AtomicInteger();

        Environment global = new CountingEnvironment(null, hops);
        global.define("v", 42.0);
        Environment a = new CountingEnvironment(global, hops);
        Environment b = new CountingEnvironment(a, hops);
        Environment c = new CountingEnvironment(b, hops);
        // v lives in global, which is 3 hops above c.

        // distance-based read at distance 3 -> exactly 3 hops.
        hops.set(0);
        Object viaDistance = c.getAt(3, "v");
        assertEquals(42.0, viaDistance);
        assertEquals(3, hops.get(),
                "getAt(3,...) must walk exactly 3 enclosing hops, never the whole chain");

        // a closer binding at distance 0 must take ZERO hops.
        c.define("w", 7.0);
        hops.set(0);
        assertEquals(7.0, c.getAt(0, "w"));
        assertEquals(0, hops.get(), "a current-scope binding must take 0 hops");

        // legacy chain walk for the same global 'v' must take MORE hops than the
        // resolved access (it searches scope by scope until it finds it).
        hops.set(0);
        Object viaChain = c.get(token("v"));
        assertEquals(42.0, viaChain);
        assertTrue(hops.get() >= 3,
                "legacy get() walks the chain hop by hop; expected >= 3, got " + hops.get());
        assertTrue(hops.get() > 0,
                "the legacy chain walk should incur hops that the resolved access avoids");
    }

    /**
     * Test Double (integration): inject a counting global Environment into a full
     * run. The main Executor requires globals whose enclosing is a builtin scope
     * (§8-0 constructor guard), so the CountingEnvironment is built directly on a
     * builtin parent and used as globals. The pipeline (Checker + ConstantFolder)
     * is wired by hand. After execution, the resolved program never walks the
     * chain -- every access is depth 0 or resolved, so step() is never invoked.
     */
    @Test
    void resolvedRunNeverWalksEnclosingChain() {
        AtomicInteger hops = new AtomicInteger();
        // globals must sit on a builtin parent for the Executor constructor guard.
        Environment globals = new CountingEnvironment(Environment.newBuiltinScope(), hops);

        String src =
                "var total = 0;" +
                "for (var i = 0; i < 3; i = i + 1) { total = total + i; }" +
                "print total;";

        List<Stmt> program = resolveAndFold(src);

        CollectingOutputSink sink = new CollectingOutputSink();
        // reset after the (uncounted) setup; count only execution hops.
        hops.set(0);
        new Executor(sink, globals).execute(program);

        assertEquals(List.of("3"), sink.lines(),
                "0+1+2 = 3 -- semantics must be preserved");
        assertEquals(0, hops.get(),
                "every access is resolved (distance 0 / global), so the enclosing "
                        + "chain seam is never traversed during execution");
    }

    // =======================================================================
    // 2) CONSTANT FOLDING
    // =======================================================================

    /**
     * The spec's loop-body constant expression collapses from N Binary nodes to a
     * single Literal (value 5.0). We compare the Binary count of the raw parse
     * against the folded program, isolating the constant sub-expression.
     */
    @Test
    void constantSubexpressionFoldsToSingleLiteralFive() {
        String src =
                "var total=0;" +
                "for (var i=0;i<3;i=i+1){" +
                "  total = total + (1 - 2*3*4*5/6 + 7+8+9) % 1000 % 30;" +
                "} print total;";

        // Before folding: the constant part contributes many Binary nodes.
        List<Stmt> raw = parse(src);
        int rawBinaries = countBinary(raw);

        // The pure-constant sub-expression has these Binary ops:
        //   2*3, *4, *5, /6, 1-_, _+7, +8, +9, (..)%1000, %30  -> 10 binaries,
        // plus a Grouping around them. Just assert there are several before.
        assertTrue(rawBinaries >= 10,
                "raw AST should contain the unfolded constant Binary ops; got " + rawBinaries);

        // After folding: the constant chunk is one Literal(5.0). The only Binary
        // nodes left are the ones touching variables: total+_ , i<3 , i+1.
        List<Stmt> folded = resolveAndFold(src);
        int foldedBinaries = countBinary(folded);

        assertEquals(3, foldedBinaries,
                "only variable-bearing Binary nodes survive folding "
                        + "(total + <lit>, i < 3, i + 1); the constant expr became a Literal");
        assertTrue(foldedBinaries < rawBinaries,
                "folding must strictly reduce Binary node count ("
                        + rawBinaries + " -> " + foldedBinaries + ")");

        // and that surviving Literal is exactly 5.0.
        assertEquals(1, countLiteral(folded, 5.0),
                "the constant sub-expression must collapse to a single Literal(5.0)");
    }

    /** Semantics preserved end-to-end: total = 5 * 3 = 15. */
    @Test
    void foldedProgramPreservesSemantics() {
        String src =
                "var total=0;" +
                "for (var i=0;i<3;i=i+1){" +
                "  total = total + (1 - 2*3*4*5/6 + 7+8+9) % 1000 % 30;" +
                "} print total;";
        RunResult r = new CodeFab().run(src);
        assertTrue(r.success(), () -> "run failed: " + r.diagnostics());
        assertEquals(List.of("15"), r.output(),
                "adding the folded constant 5 three times yields 15");
    }

    /**
     * A 1/0 sub-expression must NOT be folded away: folding may not change runtime
     * semantics, so the program must still fail at runtime with Division by zero.
     */
    @Test
    void divisionByZeroIsNotFoldedAwayAndStillFaultsAtRuntime() {
        RunResult r = new CodeFab().run("var x = 1/0; print x;");
        assertFalse(r.success(), "1/0 must surface as a runtime fault, not be silently folded");
        assertTrue(r.diagnostics().stream()
                        .anyMatch(d -> d.stage == Diagnostic.Stage.RUNTIME
                                && d.message.contains("Division by zero.")),
                () -> "expected a RUNTIME 'Division by zero.' diagnostic; got " + r.diagnostics());
        assertTrue(r.output().isEmpty(),
                "the runtime fault precedes the print, so nothing is emitted");
    }

    // =======================================================================
    // 3) INTEGRATION REGRESSIONS (distance preserved across the pipeline)
    // =======================================================================

    /**
     * Closure scenario: a function read of an enclosing local must still produce
     * the right value after distance resolution + folding. The makeAdder pattern
     * captures 'n' from the enclosing function scope; calling the returned-style
     * accumulation must remain correct.
     */
    @Test
    void closureCapturePreservesSemanticsAfterResolution() {
        String src =
                "var base = 10;" +
                "Func add(x) { return base + x; }" +
                "print add(5);" +
                "print add(7);";
        RunResult r = new CodeFab().run(src);
        assertTrue(r.success(), () -> "run failed: " + r.diagnostics());
        assertEquals(List.of("15", "17"), r.output(),
                "the function body's read of the captured global 'base' must resolve "
                        + "and execute correctly after distance binding");
    }

    /**
     * REPL cross-run regression (CodeFabSession): a function declared in run 1
     * and called in run 2 must keep its body's distances valid. distance is baked
     * onto the AST nodes (not a per-run map), so the run-1 function still resolves
     * its own locals/captures when invoked in run 2 (§9-1, CLAUDE.md 2026-06-05).
     */
    @Test
    void crossRunFunctionCallPreservesDistance() {
        CodeFabSession session = new CodeFabSession();

        RunResult r1 = session.run("Func twice(n) { return n + n; }");
        assertTrue(r1.success(), () -> "run 1 failed: " + r1.diagnostics());

        RunResult r2 = session.run("print twice(21);");
        assertTrue(r2.success(), () -> "run 2 failed: " + r2.diagnostics());
        assertEquals(List.of("42"), r2.output(),
                "the function declared in run 1 must still resolve its parameter "
                        + "distance when called in run 2 (distance is on the AST node)");
    }

    // --- test doubles & visitors -------------------------------------------

    private static Token token(String lexeme) {
        return new Token(TokenType.IDENTIFIER, lexeme, null, 1);
    }

    /**
     * Counting {@link Environment} that increments a shared counter on every
     * enclosing hop by overriding the protected {@code step()} seam. Production
     * code routes every chain traversal through {@code step()}, so this observes
     * the exact number of hops without altering behaviour.
     */
    private static final class CountingEnvironment extends Environment {
        private final AtomicInteger hops;

        CountingEnvironment(Environment enclosing, AtomicInteger hops) {
            super(enclosing);
            this.hops = hops;
        }

        @Override
        protected Environment step() {
            hops.incrementAndGet();
            return super.step();
        }
    }

    /** Visits an AST to find the first Variable node with a given name. */
    private static final class VariableFinder extends BaseWalker {
        private final String name;
        Expr.Variable found;

        VariableFinder(String name) {
            this.name = name;
        }

        @Override
        public Void visitVariable(Expr.Variable expr) {
            if (found == null && expr.name.lexeme.equals(name)) found = expr;
            return null;
        }
    }

    /** Counts Binary nodes. */
    private static final class BinaryCounter extends BaseWalker {
        int count;

        @Override
        public Void visitBinary(Expr.Binary expr) {
            count++;
            expr.left().accept(this);
            expr.right().accept(this);
            return null;
        }
    }

    /** Counts Literal nodes equal to a target double value. */
    private static final class LiteralCounter extends BaseWalker {
        private final double target;
        int count;

        LiteralCounter(double target) {
            this.target = target;
        }

        @Override
        public Void visitLiteral(Expr.Literal expr) {
            if (expr.value() instanceof Double && (double) expr.value() == target) count++;
            return null;
        }
    }

    /**
     * A no-op recursive AST walker; subclasses override the node(s) they care
     * about. Default Expr visits recurse into children; default Stmt visits
     * recurse into nested statements and expressions. Adapted to the main AST:
     * record accessors ({@code expr.left()}), public fields on the class nodes
     * ({@code expr.name}, {@code expr.value}), and the {@code visitArrayGet}/
     * {@code visitArraySet} array-node visitors.
     */
    private static class BaseWalker implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
        // --- expressions ---
        @Override public Void visitLiteral(Expr.Literal expr) { return null; }
        @Override public Void visitVariable(Expr.Variable expr) { return null; }

        @Override
        public Void visitAssign(Expr.Assign expr) {
            expr.value.accept(this);
            return null;
        }

        @Override
        public Void visitUnary(Expr.Unary expr) {
            expr.right().accept(this);
            return null;
        }

        @Override
        public Void visitBinary(Expr.Binary expr) {
            expr.left().accept(this);
            expr.right().accept(this);
            return null;
        }

        @Override
        public Void visitLogical(Expr.Logical expr) {
            expr.left().accept(this);
            expr.right().accept(this);
            return null;
        }

        @Override
        public Void visitGrouping(Expr.Grouping expr) {
            expr.expression().accept(this);
            return null;
        }

        @Override
        public Void visitCall(Expr.Call expr) {
            expr.callee().accept(this);
            for (Expr a : expr.arguments()) a.accept(this);
            return null;
        }

        @Override
        public Void visitArrayGet(Expr.ArrayGet expr) {
            expr.array().accept(this);
            expr.index().accept(this);
            return null;
        }

        @Override
        public Void visitArraySet(Expr.ArraySet expr) {
            expr.array().accept(this);
            expr.index().accept(this);
            expr.value().accept(this);
            return null;
        }

        // --- statements ---
        @Override
        public Void visitExpressionStmt(Stmt.ExpressionStmt stmt) {
            stmt.expression().accept(this);
            return null;
        }

        @Override
        public Void visitPrintStmt(Stmt.PrintStmt stmt) {
            stmt.expression().accept(this);
            return null;
        }

        @Override
        public Void visitVarStmt(Stmt.VarStmt stmt) {
            if (stmt.initializer() != null) stmt.initializer().accept(this);
            return null;
        }

        @Override
        public Void visitBlockStmt(Stmt.BlockStmt stmt) {
            for (Stmt s : stmt.statements()) s.accept(this);
            return null;
        }

        @Override
        public Void visitIfStmt(Stmt.IfStmt stmt) {
            stmt.condition().accept(this);
            stmt.thenBranch().accept(this);
            if (stmt.elseBranch() != null) stmt.elseBranch().accept(this);
            return null;
        }

        @Override
        public Void visitForStmt(Stmt.ForStmt stmt) {
            if (stmt.initializer() != null) stmt.initializer().accept(this);
            if (stmt.condition() != null) stmt.condition().accept(this);
            if (stmt.increment() != null) stmt.increment().accept(this);
            stmt.body().accept(this);
            return null;
        }

        @Override
        public Void visitWhileStmt(Stmt.WhileStmt stmt) {
            stmt.condition().accept(this);
            stmt.body().accept(this);
            return null;
        }

        @Override
        public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
            for (Stmt s : stmt.body()) s.accept(this);
            return null;
        }

        @Override
        public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
            if (stmt.value() != null) stmt.value().accept(this);
            return null;
        }
    }
}
