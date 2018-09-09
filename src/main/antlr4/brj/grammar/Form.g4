grammar Form;

WHITESPACE: [\p{White_Space},] -> skip;
SYMBOL_PART: [\p{alpha}] ~[()[\]{}#\p{White_Space},]* ;
SYMBOL: SYMBOL_PART | SYMBOL_PART '/' SYMBOL_PART | '+' | '-' | '*' | '/';
KEYWORD: ':' SYMBOL_PART | ':' SYMBOL_PART '/' SYMBOL_PART;

INT: ('-' | '+')? [0-9]+ ('.' [0-9]+)?;
BIG_INT: INT [nN];

FLOAT: ('-' | '+')? [0-9]+ ('.' [0-9]+)?;
BIG_FLOAT: FLOAT [mM];

BOOLEAN: 'true' | 'false';

STRING: '"' ( '\\"' | '\\r' | '\\n' | '\\t' | '\\\\' | . )*? '"';

file : form* EOF;

form : BOOLEAN # Boolean
     | STRING # String
     | SYMBOL # Symbol
     | KEYWORD # Keyword
     | BIG_INT # BigInt
     | INT # Int
     | BIG_FLOAT # BigFloat
     | FLOAT # Float
     | '(' form* ')' # List
     | '[' form* ']' # Vector
     | '#{' form* '}' # Set
     | '{' form* '}' # Record
     | '\'' form # Quote
     | '~' form # Unquote
     | '~@' form # UnquoteSplicing;