package rho.analyser;

import org.junit.Test;
import org.pcollections.HashTreePMap;
import rho.reader.Form;
import rho.runtime.Env;

import static org.junit.Assert.assertEquals;
import static rho.Util.vectorOf;
import static rho.analyser.ActionExpr.DefExpr.defExpr;
import static rho.analyser.Analyser.analyse;
import static rho.analyser.ValueExpr.CallExpr.callExpr;
import static rho.analyser.ValueExpr.GlobalVarExpr.globalVarExpr;
import static rho.analyser.ValueExpr.IfExpr.ifExpr;
import static rho.analyser.ValueExpr.IntExpr.intExpr;
import static rho.analyser.ValueExpr.LetExpr.LetBinding.letBinding;
import static rho.analyser.ValueExpr.LetExpr.letExpr;
import static rho.analyser.ValueExpr.LocalVarExpr.localVarExpr;
import static rho.analyser.ValueExpr.VarCallExpr.varCallExpr;
import static rho.analyser.ValueExpr.VectorExpr.vectorExpr;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.reader.Form.VectorForm.vectorForm;
import static rho.runtime.Symbol.symbol;
import static rho.runtime.VarUtil.PLUS_ENV;
import static rho.runtime.VarUtil.PLUS_VAR;

public class AnalyserTest {

    @Test
    public void resolvesPlusCall() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2))), analyse(env, listForm(symbolForm("+"), intForm(1), intForm(2))));
    }

    @Test
    public void resolvesPlusValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));
        assertEquals(globalVarExpr(PLUS_VAR), analyse(env, symbolForm("+")));
    }

    @Test
    public void analysesLet() throws Exception {
        Expr expr = analyse(null, listForm(symbolForm("let"), vectorForm(symbolForm("x"), intForm(4), symbolForm("y"), intForm(3)), vectorForm(symbolForm("x"), symbolForm("y"))));

        ValueExpr.VectorExpr body = (ValueExpr.VectorExpr) ((ValueExpr.LetExpr) expr).body;
        LocalVar xLocalVar = ((ValueExpr.LocalVarExpr) body.exprs.get(0)).localVar;
        LocalVar yLocalVar = ((ValueExpr.LocalVarExpr) body.exprs.get(1)).localVar;

        assertEquals(
            letExpr(
                vectorOf(
                    letBinding(xLocalVar, intExpr(4)),
                    letBinding(yLocalVar, intExpr(3))),
                vectorExpr(vectorOf(localVarExpr(xLocalVar), localVarExpr(yLocalVar)))),
            expr);
    }

    @Test
    public void analysesIf() throws Exception {
        assertEquals(
            ifExpr(ValueExpr.BoolExpr.boolExpr(true), intExpr(1), intExpr(2)),
            analyse(null, listForm(symbolForm("if"), Form.BoolForm.boolForm(true), intForm(1), intForm(2))));
    }

    @Test
    public void analysesLocalCallExpr() throws Exception {
        Expr expr = analyse(PLUS_ENV, listForm(symbolForm("let"), vectorForm(symbolForm("x"), symbolForm("+")), listForm(symbolForm("x"), intForm(1), intForm(2))));

        ValueExpr.CallExpr body = (ValueExpr.CallExpr) ((ValueExpr.LetExpr) expr).body;
        LocalVar xLocalVar = ((ValueExpr.LocalVarExpr) body.params.get(0)).localVar;

        assertEquals(
            letExpr(
                vectorOf(
                    letBinding(xLocalVar, globalVarExpr(PLUS_VAR))),
                callExpr(vectorOf(localVarExpr(xLocalVar), intExpr(1), intExpr(2)))),
            expr);

    }

    @Test
    public void analysesDefValue() throws Exception {
        Env env = new Env(HashTreePMap.singleton(symbol("+"), PLUS_VAR));

        assertEquals(defExpr(symbol("x"), varCallExpr(PLUS_VAR, vectorOf(intExpr(1), intExpr(2)))), analyse(env, listForm(symbolForm("def"), symbolForm("x"), listForm(symbolForm("+"), intForm(1), intForm(2)))));
    }
}