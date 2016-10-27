package rho.types;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.Panic;
import rho.analyser.LocalVar;
import rho.analyser.ValueExpr;
import rho.runtime.Env;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.LocalVar.localVar;
import static rho.analyser.ValueExpr.BoolExpr.boolExpr;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.IfExpr.ifExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;
import static rho.analyser.ValueExpr.LetExpr.letExpr;
import static rho.analyser.ValueExpr.LocalVarExpr.localVarExpr;
import static rho.analyser.ValueExpr.SetExpr.setExpr;
import static rho.analyser.ValueExpr.StringExpr.stringExpr;
import static rho.analyser.ValueExpr.VectorExpr.vectorExpr;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_VAR;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.INT_TYPE;
import static rho.types.Type.SimpleType.STRING_TYPE;
import static rho.types.Type.VectorType.vectorType;

public class TypeCheckerTest {

    @Test
    public void typesVector() throws Exception {
        assertEquals(vectorType(STRING_TYPE), TypeChecker.type(Env.env(), vectorExpr(vectorOf(stringExpr("Hello"), stringExpr("World")))));
    }

    @Test(expected = Panic.class)
    public void failsMixedVector() throws Exception {
        TypeChecker.type(Env.env(), vectorExpr(vectorOf(stringExpr("Hello"), intExpr(535))));
    }

    @Test
    public void typesSet() throws Exception {
        assertEquals(setType(INT_TYPE), TypeChecker.type(Env.env(), setExpr(vectorOf(intExpr(16), intExpr(9)))));
    }

    @Test(expected = Panic.class)
    public void failsMixedSet() throws Exception {
        TypeChecker.type(Env.env(), setExpr(vectorOf(stringExpr("Hello"), intExpr(535))));
    }

    @Test
    public void typesPlusCall() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        assertEquals(INT_TYPE, TypeChecker.type(env, callExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2)))));
    }

    @Test
    public void typesIfExpr() throws Exception {
        assertEquals(STRING_TYPE, TypeChecker.type(Env.env(), ifExpr(boolExpr(false), stringExpr("is true"), stringExpr("is false"))));
    }

    @Test
    public void typesLet() throws Exception {
        LocalVar x = localVar(symbol("x"));
        LocalVar y = localVar(symbol("y"));

        ValueExpr.LetExpr letExpr = letExpr(
            vectorOf(
                letBinding(x, intExpr(4)),
                letBinding(y, intExpr(3))),
            vectorExpr(vectorOf(localVarExpr(x), localVarExpr(y))));

        assertEquals(vectorType(INT_TYPE), TypeChecker.type(Env.env(), letExpr));
    }

}