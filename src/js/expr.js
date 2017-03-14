var im = require('immutable');
var Record = im.Record;

var Expr = Record({range: null});
var BoolExpr = Record({bool: null});
var StringExpr = Record({str: null});
var IntExpr = Record({int: null});
var FloatExpr = Record({float: null});
var VectorExpr = Record({exprs: null});
var RecordEntry = Record({key: null, value: null});
var RecordExpr = Record({entries: null});
var SetExpr = Record({exprs: null});

Expr.prototype.toString = function() {return this.expr.toString();};

BoolExpr.prototype.toBool = function() {return `(BoolExpr "${this.bool}")`;};
StringExpr.prototype.toString = function() {return `(StringExpr "${this.str}")`;};
IntExpr.prototype.toString = function() {return `(IntExpr ${this.int})`;};
FloatExpr.prototype.toString = function() {return `(FloatExpr ${this.float})`;};
VectorExpr.prototype.toString = function() {return `(VectorExpr ${this.exprs.join(' ')})`;};
RecordEntry.prototype.toString = function() {return `${this.key} ${this.value}`;};
RecordExpr.prototype.toString = function() {return `(RecordExpr ${this.entries.join(', ')})`;};
SetExpr.prototype.toString = function() {return `(SetExpr ${this.exprs.join(' ')})`;};