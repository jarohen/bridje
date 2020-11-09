package brj

import kotlin.reflect.KClass

internal class FormParser(private var forms: List<Form>) {
    fun expectForm(): Form {
        val ret = forms.firstOrNull() ?: TODO()
        forms = forms.drop(1)
        return ret
    }

    fun <F: Form> expectForm(clazz: Class<F>): F {
        val form = expectForm()
        if (!clazz.isInstance(form)) TODO()
        return clazz.cast(form)
    }

    fun <R> rest(parse: FormParser.() -> R): List<R> {
        val res = mutableListOf<R>()

        while(forms.isNotEmpty()) {
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
        if (forms.isNotEmpty()) TODO()
    }
}

internal fun <R> FormParser.or(vararg parses: FormParser.() -> R?): R? {
    parses.forEach {
        val ret = maybe(it)
        if (ret != null) return ret
    }
    return null
}

internal fun FormParser.expectSymbol() : Symbol {
    val form = expectForm()
    if (form !is SymbolForm) TODO()
    return form.sym
}

internal fun <R> parseForms(forms: List<Form>, parse: FormParser.() -> R): R {
    return parse(FormParser(forms))
}

