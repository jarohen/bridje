package rho.analyser;

import org.pcollections.PVector;
import rho.reader.Form;
import rho.reader.FormVisitor;
import rho.runtime.Symbol;
import rho.types.Type;

import java.util.HashMap;
import java.util.Map;

import static rho.Util.toPVector;
import static rho.types.Type.FnType.fnType;
import static rho.types.Type.SetType.setType;
import static rho.types.Type.SimpleType.*;

public class TypeAnalyser {

    private final Map<Symbol, TypeVar> typeVarEnv = new HashMap<>();

    private Type analyzeType0(Form form) {
        return form.accept(new FormVisitor<Type>() {
            @Override
            public Type visit(Form.BoolForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.StringForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.IntForm form) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.VectorForm form) {
                if (form.forms.size() == 1) {
                    return setType(analyzeType0(form.forms.get(0)));
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.SetForm form) {
                if (form.forms.size() == 1) {
                    return setType(analyzeType0(form.forms.get(0)));
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.ListForm form) {
                PVector<Form> forms = form.forms;
                Form firstForm = forms.get(0);
                if (firstForm instanceof Form.SymbolForm) {
                    Symbol sym = ((Form.SymbolForm) firstForm).sym;

                    switch (sym.sym) {
                        case "Fn":
                            int formCount = forms.size();
                            if (formCount >= 3) {
                                PVector<Type> types = forms.minus(0).stream().map(f -> analyzeType0(f)).collect(toPVector());

                                return fnType(
                                    types.subList(0, types.size() - 1),
                                    types.get(types.size() - 1));
                            }
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.SymbolForm form) {
                Symbol sym = form.sym;

                if (Character.isLowerCase(sym.sym.charAt(0))) {
                    return typeVarEnv.computeIfAbsent(sym, s -> new TypeVar());
                } else {
                    switch (sym.sym) {
                        case "Str":
                            return STRING_TYPE;
                        case "Int":
                            return INT_TYPE;
                        case "Bool":
                            return BOOL_TYPE;
                        default:
                    }
                }

                throw new UnsupportedOperationException();
            }

            @Override
            public Type visit(Form.QSymbolForm form) {
                throw new UnsupportedOperationException();
            }
        });
    }

    public static Type analyzeType(Form form) {
        return new TypeAnalyser().analyzeType0(form);
    }

}
