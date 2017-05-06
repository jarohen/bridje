const {Map, Record, List} = require('immutable');

const BoolExpr = Record({range: null, bool: null});
const StringExpr = Record({range: null, str: null});
const IntExpr = Record({range: null, int: null});
const FloatExpr = Record({range: null, float: null});
const VectorExpr = Record({range: null, exprs: null});
const SetExpr = Record({range: null, exprs: null});
const RecordEntry = Record({key: null, value: null});
const RecordExpr = Record({range: null, entries: null});

const IfExpr = Record({range: null, testExpr: null, thenExpr: null, elseExpr: null});

const LocalVarExpr = Record({range: null, name: null, localVar: null});
const VarExpr = Record({range: null, var: null, alias: null});
const JSGlobalExpr = Record({range: null, path: null});

const BindingPair = Record({localVar: null, expr: null});
const LetExpr = Record({range: null, bindings: null, body: null});
const LoopExpr = Record({range: null, bindings: null, body: null});
const RecurExpr = Record({range: null, bindings: null});

const FnExpr = Record({range: null, params: null, body: null});
const CallExpr = Record({range: null, exprs: null});

const MatchClause = Record({range: null, var: null, alias: null, expr: null});
const MatchExpr = Record({range: null, expr: null, clauses: null});

const QuotedExpr = Record({range: null, form: null});

const DefExpr = Record({range: null, sym: null, params: null, body: null});
const DefMacroExpr = Record({range: null, sym: null, params: null, body: null});
const DefDataExpr = Record({range: null, name: null, type: null, params: null, keys: null});

BoolExpr.prototype.toString = function() {return `(BoolExpr ${this.bool})`;};
BoolExpr.prototype.exprType = 'bool';
BoolExpr.prototype.subExprs = function() {return List.of(this);};

StringExpr.prototype.toString = function() {return `(StringExpr "${this.str}")`;};
StringExpr.prototype.exprType = 'string';
StringExpr.prototype.subExprs = function() {return List.of(this);};

IntExpr.prototype.toString = function() {return `(IntExpr ${this.int})`;};
IntExpr.prototype.exprType = 'int';
IntExpr.prototype.subExprs = function() {return List.of(this);};

FloatExpr.prototype.toString = function() {return `(FloatExpr ${this.float})`;};
FloatExpr.prototype.exprType = 'float';
FloatExpr.prototype.subExprs = function() {return List.of(this);};

VectorExpr.prototype.toString = function() {return `(VectorExpr ${this.exprs.join(' ')})`;};
VectorExpr.prototype.exprType = 'vector';
VectorExpr.prototype.subExprs = function() {return this.exprs.flatMap(e => e.subExprs());};

SetExpr.prototype.toString = function() {return `(SetExpr ${this.exprs.join(' ')})`;};
SetExpr.prototype.exprType = 'set';
SetExpr.prototype.subExprs = function() {return this.exprs.flatMap(e => e.subExprs());};

RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordExpr.prototype.toString = function() {return `(RecordExpr {${this.entries.join(', ')})}`;};
RecordExpr.prototype.exprType = 'record';
RecordExpr.prototype.subExprs = function() {return List.of(this).concat(this.entries.flatMap(e => e.value.subExprs()));};

IfExpr.prototype.toString = function() {return `(IfExpr ${this.testExpr} ${this.thenExpr} ${this.elseExpr})`;};
IfExpr.prototype.exprType = 'if';
IfExpr.prototype.subExprs = function() {
  return List.of(this).concat(this.testExpr.subExprs(), this.thenExpr.subExprs(), this.elseExpr.subExprs());
};

LocalVarExpr.prototype.toString = function() {return `(LocalVarExpr ${this.name})`;};
LocalVarExpr.prototype.exprType = 'localVar';
LocalVarExpr.prototype.subExprs = function() {return List.of(this);};

VarExpr.prototype.toString = function() {return `(VarExpr ${this.var.ns}/${this.var.name})`;};
VarExpr.prototype.exprType = 'var';
VarExpr.prototype.subExprs = function() {return List.of(this);};

JSGlobalExpr.prototype.toString = function() {return `(JSGlobal ${this.path.join('.')})`;};
JSGlobalExpr.prototype.exprType = 'jsGlobal';
JSGlobalExpr.prototype.subExprs = function() {return List.of(this);};

BindingPair.prototype.toString = function() {return `${this.localVar.name} ${this.expr}`;};

LetExpr.prototype.toString = function() {return `(LetExpr [${this.bindings.join(', ')}] ${this.body})`;};
LetExpr.prototype.exprType = 'let';
LetExpr.prototype.subExprs = function() {
  return List.of(this).concat(this.bindings.flatMap(e => e.expr.subExprs()), this.body.subExprs());
};

LoopExpr.prototype.toString = function() {return `(LoopExpr [${this.bindings.join(', ')}] ${this.body})`;};
LoopExpr.prototype.exprType = 'loop';
LoopExpr.prototype.subExprs = function() {
  return List.of(this).concat(this.bindings.flatMap(e => e.expr.subExprs()), this.body.subExprs());
};

RecurExpr.prototype.toString = function() {return `(RecurExpr ${this.bindings.join(', ')})`;};
RecurExpr.prototype.exprType = 'recur';
RecurExpr.prototype.subExprs = function() {
  return List.of(this).concat(this.bindings.flatMap(e => e.expr.subExprs()));
};


FnExpr.prototype.toString = function() {return `(FnExpr (${this.params.map(p => p.name).join(' ')}) ${this.body})`;};
FnExpr.prototype.exprType = 'fn';
FnExpr.prototype.subExprs = function() {return List.of(this).concat(this.body.subExprs());};

CallExpr.prototype.toString = function() {return `(CallExpr ${this.exprs.join(' ')})`;};
CallExpr.prototype.exprType = 'call';
CallExpr.prototype.subExprs = function() {return this.exprs.flatMap(e => e.subExprs());};

MatchClause.prototype.toString = function() {return `${this.var.ns}/${this.var.name} ${this.expr}`;};
MatchExpr.prototype.toString = function() {return `(MatchExpr ${this.expr} ${this.clauses.join(' ')})`;};
MatchExpr.prototype.exprType = 'match';
MatchExpr.prototype.subExprs = function() {return List.of(this).concat(this.expr.subExprs, this.clauses.map(c => c.expr.subExprs()));};

QuotedExpr.prototype.toString = function() {return `(QuotedExpr ${this.form})`;};
QuotedExpr.prototype.exprType = 'quoted';
QuotedExpr.prototype.subExprs = function() {return List.of(this);};

DefExpr.prototype.toString = function() {return `(DefExpr ${this.sym} ${this.body})`;};
DefExpr.prototype.exprType = 'def';
DefExpr.prototype.subExprs = function() {return List.of(this).concat(this.body.subExprs());};

DefMacroExpr.prototype.toString = function() {return `(DefMacroExpr (${this.sym} ${this.params.map(lv => lv.name).join(' ')}) ${this.body})`;};
DefMacroExpr.prototype.exprType = 'defmacro';
DefMacroExpr.prototype.subExprs = function() {return List.of(this).concat(this.body.subExprs());};

DefDataExpr.prototype.toString = function() {
  switch(this.type) {
  case 'value': return `(DefDataExpr ${this.name})`;
  case 'vector': return `(DefDataExpr (${this.name} ${this.params.join(' ')}))`;
  case 'record': return `(DefDataExpr (${this.name} #{${this.keys.join(' ')}}))`;
  default: throw('invalid defdata type');
  }
};
DefDataExpr.prototype.exprType = 'defdata';
DefDataExpr.prototype.subExprs = function() {return List.of(this);};

module.exports = {
  BoolExpr, StringExpr, IntExpr, FloatExpr,
  VectorExpr, SetExpr,
  RecordEntry, RecordExpr,
  IfExpr, LocalVarExpr, VarExpr, JSGlobalExpr,
  LetExpr, BindingPair,
  LoopExpr, RecurExpr,
  FnExpr, CallExpr,
  MatchClause, MatchExpr,
  QuotedExpr,
  DefExpr, DefMacroExpr, DefDataExpr
};
