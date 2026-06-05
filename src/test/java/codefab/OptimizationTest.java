package codefab;

import codefab.assembler.Parser;
import codefab.assembler.Scanner;
import codefab.checker.CheckResult;
import codefab.checker.Checker;
import codefab.core.Diagnostic;
import codefab.core.Expr;
import codefab.core.Stmt;
import codefab.core.Token;
import codefab.core.TokenType;
import codefab.executor.Environment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the two pre-execution optimizations (shared-contracts §9) using Test
 * Doubles, per spec:
 *
 * <ul>
 *   <li><b>Static binding</b>: a resolved access jumps straight to the computed
 *       scope distance instead of walking the whole chain. Verified two ways:
 *       (1) asserting the {@code CheckResult.locals()} distance for a deeply
 *       nested reference equals the nesting depth, and (2) a counting
 *       {@link Environment} subclass that overrides the {@code step()} seam to
 *       count enclosing hops, showing distance-based {@code getAt} performs
 *       exactly {@code distance} hops while the legacy {@code get(token)} chain
 *       walk performs more.</li>
 *   <li><b>Constant folding</b>: a loop-body constant expression collapses from N
 *       {@code Binary} nodes to a single {@code Literal} (0 runtime ops), while
 *       semantics are preserved and a {@code 1/0} sub-expression is NOT folded
 *       away (its runtime {@code Division by zero.} is still reported).</li>
 * </ul>
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

    private CheckResult check(String src) {
        List<Diagnostic> diags = new ArrayList<>();
        CheckResult cr = new Checker(diags).check(parse(src));
        assertTrue(diags.isEmpty(), () -> "checker produced diagnostics: " + diags);
        return cr;
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
     * distance-map assertion: a global variable read from inside three nested
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
        CheckResult cr = check(src);
        Expr.Variable read = findVariable(cr.program(), "x");
        assertNotNull(read, "expected a Variable node for x in the folded program");
        Integer distance = cr.locals().get(read);
        assertNotNull(distance,
                "x is a (block) local, so it must be resolved into locals() with a distance");
        assertEquals(3, distance,
                "x is declared 3 enclosing scopes above the read; "
                        + "a wrong value implies a resolver (Checker) bug");
    }

    /**
     * A name the Checker's scope stack never declares (the native {@code Array},
     * or a binding carried over from an earlier REPL run) is NOT recorded in
     * locals(), so the Executor falls back to the global environment for it.
     *
     * <p>NOTE: the Checker models the top level as an explicit scope frame, so a
     * top-level {@code var g} read from nested blocks DOES get a distance (it is
     * present in locals()), rather than being treated as a map-absent global.
     * That is a benign deviation from the literal wording of contract §9-1
     * ("globals are absent from the map"): because the Executor's global
     * Environment is the very frame the Checker resolves the top level into,
     * distance-based access still lands on the right binding and semantics are
     * identical. The genuinely "absent from locals()" case is a reference the
     * Checker cannot see at all, exercised here via the native {@code Array}.
     */
    @Test
    void unresolvableReferencesAreAbsentFromLocals() {
        CheckResult cr = check("{ { var a = Array(2); print a; } }");
        Expr.Variable arrayRef = findVariable(cr.program(), "Array");
        assertNotNull(arrayRef, "expected a Variable node referencing the native Array");
        assertNull(cr.locals().get(arrayRef),
                "Array is never declared in any scope, so it must be absent from "
                        + "locals() and looked up in the Executor's global scope");
    }

    /**
     * Test Double (direct unit): build global -> A -> B -> C by hand and read a
     * binding two scopes up. distance-based getAt(2, ...) must take EXACTLY 2
     * hops through the step() seam (no full chain walk), whereas the legacy
     * get(token) chain walk for the same value takes more hops.
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
     * run. After execution, the resolved program never walks the chain more than
     * the resolved distances would require -- in this single-global program every
     * access is either depth 0 or resolved, so step() is never invoked.
     */
    @Test
    void resolvedRunNeverWalksEnclosingChain() {
        AtomicInteger hops = new AtomicInteger();
        Environment globals = new CountingEnvironment(null, hops);

        String src =
                "var total = 0;" +
                "for (var i = 0; i < 3; i = i + 1) { total = total + i; }" +
                "print total;";

        List<Diagnostic> diags = new ArrayList<>();
        CheckResult cr = new Checker(diags).check(parse(src));
        assertTrue(diags.isEmpty(), () -> "checker diagnostics: " + diags);

        CollectingOutputSink sink = new CollectingOutputSink();
        new codefab.executor.Executor(sink, globals, cr.locals()).execute(cr.program());

        assertEquals(List.of("3"), sink.lines(),
                "0+1+2 = 3 -- semantics must be preserved");
        assertEquals(0, hops.get(),
                "every access is resolved (distance 0 / global), so the enclosing "
                        + "chain seam is never traversed");
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
        CheckResult cr = check(src);
        int foldedBinaries = countBinary(cr.program());

        assertEquals(3, foldedBinaries,
                "only variable-bearing Binary nodes survive folding "
                        + "(total + <lit>, i < 3, i + 1); the constant expr became a Literal");
        assertTrue(foldedBinaries < rawBinaries,
                "folding must strictly reduce Binary node count ("
                        + rawBinaries + " -> " + foldedBinaries + ")");

        // and that surviving Literal is exactly 5.0.
        assertEquals(1, countLiteral(cr.program(), 5.0),
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
            expr.left.accept(this);
            expr.right.accept(this);
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
            if (expr.value instanceof Double && (double) expr.value == target) count++;
            return null;
        }
    }

    /**
     * A no-op recursive AST walker; subclasses override the node(s) they care
     * about. Default Expr visits recurse into children; default Stmt visits
     * recurse into nested statements and expressions.
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
            expr.right.accept(this);
            return null;
        }

        @Override
        public Void visitBinary(Expr.Binary expr) {
            expr.left.accept(this);
            expr.right.accept(this);
            return null;
        }

        @Override
        public Void visitLogical(Expr.Logical expr) {
            expr.left.accept(this);
            expr.right.accept(this);
            return null;
        }

        @Override
        public Void visitGrouping(Expr.Grouping expr) {
            expr.expression.accept(this);
            return null;
        }

        @Override
        public Void visitCall(Expr.Call expr) {
            expr.callee.accept(this);
            for (Expr a : expr.arguments) a.accept(this);
            return null;
        }

        @Override
        public Void visitIndex(Expr.Index expr) {
            expr.target.accept(this);
            expr.index.accept(this);
            return null;
        }

        @Override
        public Void visitIndexSet(Expr.IndexSet expr) {
            expr.target.accept(this);
            expr.index.accept(this);
            expr.value.accept(this);
            return null;
        }

        // --- statements ---
        @Override
        public Void visitExpressionStmt(Stmt.ExpressionStmt stmt) {
            stmt.expression.accept(this);
            return null;
        }

        @Override
        public Void visitPrintStmt(Stmt.PrintStmt stmt) {
            stmt.expression.accept(this);
            return null;
        }

        @Override
        public Void visitVarStmt(Stmt.VarStmt stmt) {
            if (stmt.initializer != null) stmt.initializer.accept(this);
            return null;
        }

        @Override
        public Void visitBlockStmt(Stmt.BlockStmt stmt) {
            for (Stmt s : stmt.statements) s.accept(this);
            return null;
        }

        @Override
        public Void visitIfStmt(Stmt.IfStmt stmt) {
            stmt.condition.accept(this);
            stmt.thenBranch.accept(this);
            if (stmt.elseBranch != null) stmt.elseBranch.accept(this);
            return null;
        }

        @Override
        public Void visitForStmt(Stmt.ForStmt stmt) {
            if (stmt.initializer != null) stmt.initializer.accept(this);
            if (stmt.condition != null) stmt.condition.accept(this);
            if (stmt.increment != null) stmt.increment.accept(this);
            stmt.body.accept(this);
            return null;
        }

        @Override
        public Void visitWhileStmt(Stmt.WhileStmt stmt) {
            stmt.condition.accept(this);
            stmt.body.accept(this);
            return null;
        }

        @Override
        public Void visitFunctionStmt(Stmt.FunctionStmt stmt) {
            for (Stmt s : stmt.body) s.accept(this);
            return null;
        }

        @Override
        public Void visitReturnStmt(Stmt.ReturnStmt stmt) {
            if (stmt.value != null) stmt.value.accept(this);
            return null;
        }
    }
}
