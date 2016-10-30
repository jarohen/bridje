package rho.analyser;

public interface ExprVisitor<T> {

    T accept(ValueExpr expr);

    T accept(ActionExpr expr);
}
