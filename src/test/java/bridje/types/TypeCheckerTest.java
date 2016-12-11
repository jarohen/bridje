package bridje.types;

import bridje.Panic;
import bridje.analyser.Expr;
import bridje.analyser.LocalVar;
import bridje.runtime.DataType;
import bridje.runtime.DataTypeConstructor;
import org.junit.Test;
import org.pcollections.Empty;
import org.pcollections.PVector;

import static bridje.Util.vectorOf;
import static bridje.analyser.ExprUtil.*;
import static bridje.runtime.Symbol.symbol;
import static bridje.runtime.VarUtil.PLUS_VAR;
import static bridje.types.Type.FnType.fnType;
import static bridje.types.Type.SetType.setType;
import static bridje.types.Type.SimpleType.*;
import static bridje.types.Type.VectorType.vectorType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeCheckerTest {

    @Test
    public void typesVector() throws Exception {
        assertEquals(vectorType(STRING_TYPE), TypeChecker.typeExpr(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), stringExpr(null, "World")))).type);
    }

    @Test(expected = Panic.class)
    public void failsMixedVector() throws Exception {
        TypeChecker.typeExpr(vectorExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesSet() throws Exception {
        assertEquals(setType(INT_TYPE), TypeChecker.typeExpr(setExpr(null, vectorOf(intExpr(null, 16), intExpr(null, 9)))).type);
    }

    @Test(expected = Panic.class)
    public void failsMixedSet() throws Exception {
        TypeChecker.typeExpr(setExpr(null, vectorOf(stringExpr(null, "Hello"), intExpr(null, 535))));
    }

    @Test
    public void typesPlusCall() throws Exception {
        assertEquals(INT_TYPE, TypeChecker.typeExpr(varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))).type);
    }

    @Test
    public void typesPlusValue() throws Exception {
        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), TypeChecker.typeExpr(globalVarExpr(null, PLUS_VAR)).type);
    }

    @Test
    public void typesIfExpr() throws Exception {
        assertEquals(STRING_TYPE, TypeChecker.typeExpr(ifExpr(null, boolExpr(null, false), stringExpr(null, "is true"), stringExpr(null, "is false"))).type);
    }

    @Test
    public void typesLet() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        LocalVar y = new LocalVar(symbol("y"));

        Expr.LetExpr<Void> letExpr = letExpr(null,
            vectorOf(
                letBinding(x, intExpr(null, 4)),
                letBinding(y, intExpr(null, 3))),
            vectorExpr(null, vectorOf(localVarExpr(null, x), localVarExpr(null, y))));

        assertEquals(vectorType(INT_TYPE), TypeChecker.typeExpr(letExpr).type);
    }

    @Test
    public void typesPolymorphicLet() throws Exception {
        LocalVar id = new LocalVar(symbol("id"));
        LocalVar a = new LocalVar(symbol("a"));
        LocalVar b = new LocalVar(symbol("b"));

        Expr.LetExpr<Void> letExpr = letExpr(null,
            vectorOf(
                letBinding(id, fnExpr(null, vectorOf(a), new Expr.LocalVarExpr<>(null, null, a))),
                letBinding(b, callExpr(null, vectorOf(localVarExpr(null, id), stringExpr(null, "unused"))))),
            callExpr(null, vectorOf(localVarExpr(null, id), intExpr(null, 4))));

        Expr<Type> typedExpr = TypeChecker.typeExpr(letExpr);
        assertEquals(INT_TYPE, typedExpr.type);
        assertEquals(STRING_TYPE, ((Expr.LetExpr<Type>) typedExpr).bindings.get(1).expr.type);
    }

    @Test
    public void typesCallExpr() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));
        Expr<Type> typedLetExpr = TypeChecker.typeExpr(
            letExpr(null,
                vectorOf(
                    letBinding(x, globalVarExpr(null, PLUS_VAR))),
                callExpr(null, vectorOf(localVarExpr(null, x), intExpr(null, 1), intExpr(null, 2)))));

        assertEquals(INT_TYPE, typedLetExpr.type);

        Expr<Type> typedBodyExpr = ((Expr.LetExpr<Type>) typedLetExpr).body;
        Expr.CallExpr<Type> typedCallExpr = (Expr.CallExpr<Type>) typedBodyExpr;

        assertEquals(fnType(vectorOf(INT_TYPE, INT_TYPE), INT_TYPE), typedCallExpr.exprs.get(0).type);
    }

    @Test
    public void typesDef() throws Exception {
        assertEquals(INT_TYPE, ((Expr.DefExpr<Type>) TypeChecker.typeExpr(defExpr(null, symbol("x"), varCallExpr(null, PLUS_VAR, vectorOf(intExpr(null, 1), intExpr(null, 2)))))).body.type);
    }

    @Test
    public void typesInlineFn() throws Exception {
        LocalVar x = new LocalVar(symbol("x"));

        assertEquals(
            fnType(vectorOf(INT_TYPE), INT_TYPE),
            TypeChecker.typeExpr(
                fnExpr(null,
                    vectorOf(x),
                    callExpr(null,
                        vectorOf(
                            globalVarExpr(null, PLUS_VAR),
                            localVarExpr(null, x),
                            localVarExpr(null, x))))).type);
    }

    @Test
    public void typesTypeDef() throws Exception {
        assertEquals(ENV_IO, TypeChecker.typeExpr(new Expr.TypeDefExpr<>(null, null, symbol("double"), fnType(vectorOf(INT_TYPE), INT_TYPE))).type);
    }

    @Test
    public void typesSimpleUnion() throws Exception {
        Expr.DefDataExpr<Type> expr = (Expr.DefDataExpr<Type>) TypeChecker.typeExpr(
            new Expr.DefDataExpr<>(null,
                null, new DataType<>(null, symbol("SimpleUnion"),
                Empty.vector(), vectorOf(
                new DataTypeConstructor.VectorConstructor<>(null, symbol("AnInt"), vectorOf(INT_TYPE)),
                new DataTypeConstructor.VectorConstructor<>(null, symbol("AString"), vectorOf(STRING_TYPE)),
                new DataTypeConstructor.ValueConstructor<>(null, symbol("Neither"))))));

        assertEquals(ENV_IO, expr.type);

        DataType<Type> dataType = expr.dataType;
        PVector<DataTypeConstructor<Type>> constructors = dataType.constructors;

        DataTypeType simpleUnionType = new DataTypeType(symbol("SimpleUnion"), null);

        assertEquals(simpleUnionType, dataType.type);
        assertEquals(fnType(vectorOf(INT_TYPE), simpleUnionType), constructors.get(0).type);
        assertEquals(fnType(vectorOf(STRING_TYPE), simpleUnionType), constructors.get(1).type);
        assertEquals(simpleUnionType, constructors.get(2).type);
    }

    @Test
    public void typesPolymorphicDataType() throws Exception {
        DataTypeType dataTypeType = new DataTypeType(symbol("Foo"), null);
        TypeVar a = new TypeVar();
        AppliedType appliedType = new AppliedType(dataTypeType, vectorOf(a));

        Expr.DefDataExpr<Type> expr = (Expr.DefDataExpr<Type>) TypeChecker.typeExpr(new Expr.DefDataExpr<>(null, null,
            new DataType<>(null, symbol("Foo"), vectorOf(a),
                vectorOf(new DataTypeConstructor.VectorConstructor<>(null, symbol("FooCons"), vectorOf(a, appliedType))))));

        assertTrue(appliedType.alphaEquivalentTo(expr.dataType.type));
        assertTrue(new FnType(vectorOf(a, appliedType), appliedType).alphaEquivalentTo(expr.dataType.constructors.get(0).type));
    }
}