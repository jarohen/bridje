package brj

import brj.runtime.Symbol

internal class FormParser(forms: List<Form>) {
    var forms = forms
        private set

    fun expectForm(): Form {
        val ret = forms.firstOrNull() ?: TODO("expected form, none found")
        forms = forms.drop(1)
        return ret
    }

    fun <F : Form> expectForm(clazz: Class<F>): F {
        val form = expectForm()
        if (!clazz.isInstance(form)) throw RuntimeException("Wrong form type")
        return clazz.cast(form)
    }

    fun <R> rest(parse: FormParser.() -> R): List<R> {
        val res = mutableListOf<R>()

        while (forms.isNotEmpty()) {
            res.add(parse())
        }

        return res
    }

    fun <R> maybe(parse: FormParser.() -> R?): R? {
        return try {
            val parser = FormParser(forms)
            parser.parse()?.let { res ->
                forms = parser.forms
                res
            }
        } catch (e: Exception) {
            null
        }
    }

    fun expectEnd() {
        if (forms.isNotEmpty()) TODO("expected end of list, found forms, ${forms.first().loc}")
    }

    internal fun <R> or(vararg parses: FormParser.() -> R?): R? {
        parses.forEach {
            val parser = FormParser(forms)
            val ret = parser.it()?.let { res ->
                forms = parser.forms
                res
            }
            if (ret != null) return ret
        }
        return null
    }
}


internal fun FormParser.expectSymbol(): Symbol {
    val form = expectForm()
    if (form !is SymbolForm) throw RuntimeException("Expected symbol, got $form")
    return form.sym
}

internal fun FormParser.expectKeyword(): Symbol {
    val form = expectForm()
    if (form !is KeywordForm) throw RuntimeException("Expected keyword, got $form")
    return form.sym
}

internal fun <R> parseForms(forms: List<Form>, parse: FormParser.() -> R): R {
    return parse(FormParser(forms))
}

