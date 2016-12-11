package bridje.analyser;

import bridje.runtime.Symbol;
import bridje.types.Type;
import org.pcollections.Empty;
import org.pcollections.PMap;

import java.util.Optional;

class LocalTypeEnv {
    public static final LocalTypeEnv EMPTY = new LocalTypeEnv(Empty.map(), Empty.map());

    public final PMap<Symbol, Type.DataTypeType> localEnv;
    public final PMap<Symbol, Type.TypeVar> typeVarMapping;

    LocalTypeEnv(PMap<Symbol, Type.DataTypeType> localEnv, PMap<Symbol, Type.TypeVar> typeVarMapping) {
        this.localEnv = localEnv;
        this.typeVarMapping = typeVarMapping;
    }

    Optional<Type.DataTypeType> resolve(Symbol symbol) {
        return Optional.ofNullable(localEnv.get(symbol));
    }
}