package rho.reader;

import org.junit.Test;
import rho.Panic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static rho.reader.Form.IntForm.intForm;
import static rho.reader.Form.ListForm.listForm;
import static rho.reader.Form.QSymbolForm.qSymbolForm;
import static rho.reader.Form.SetForm.setForm;
import static rho.reader.Form.StringForm.stringForm;
import static rho.reader.Form.SymbolForm.symbolForm;
import static rho.reader.Form.VectorForm.vectorForm;
import static rho.reader.Location.loc;
import static rho.reader.Range.range;

public class FormReaderTest {

    @Test
    public void readsString() throws Exception {
        Form form = FormReader.read(LCReader.fromString("\"Hello world!\""));

        assertNotNull(form);
        assertEquals(new Form.StringForm(null, "Hello world!"), form);
        assertEquals(range(loc(1, 1), loc(1, 15)), form.range);
    }

    @Test
    public void readsStringWithEscapes() throws Exception {
        Form form = FormReader.read(LCReader.fromString("\"He\\tllo\\n\n\\\"world\\\"!\""));

        assertNotNull(form);
        assertEquals(new Form.StringForm(null, "He\tllo\n\n\"world\"!"), form);
    }

    @Test
    public void slurpsWhitespace() throws Exception {
        Form form = FormReader.read(LCReader.fromString("  \n   \"Hello world!\""));

        assertNotNull(form);
        assertEquals(stringForm("Hello world!"), form);
        assertEquals(range(loc(2, 4), loc(2, 18)), form.range);
    }

    @Test
    public void readsPositiveInt() throws Exception {
        Form form = FormReader.read(LCReader.fromString("1532"));
        assertNotNull(form);
        assertEquals(intForm(1532), form);
        assertEquals(range(loc(1, 1), loc(1, 5)), form.range);
    }

    @Test
    public void readsNegativeInt() throws Exception {
        Form form = FormReader.read(LCReader.fromString("-1532"));
        assertNotNull(form);
        assertEquals(intForm(-1532), form);
        assertEquals(range(loc(1, 1), loc(1, 6)), form.range);
    }

    @Test(expected = Panic.class)
    public void barfsOnInvalidNumber() throws Exception {
        FormReader.read(LCReader.fromString("-15f32"));
    }

    @Test
    public void readsVector() throws Exception {
        Form form = FormReader.read(LCReader.fromString("[\"Hello\", \"world!\"]"));

        assertNotNull(form);
        assertEquals(vectorForm(stringForm("Hello"), stringForm("world!")), form);
        assertEquals(range(loc(1, 1), loc(1, 20)), form.range);
    }

    @Test
    public void readsSet() throws Exception {
        Form form = FormReader.read(LCReader.fromString("^[\"Hello\", \"world!\"]"));

        assertNotNull(form);
        assertEquals(setForm(stringForm("Hello"), stringForm("world!")), form);
        assertEquals(range(loc(1, 1), loc(1, 21)), form.range);
    }

    @Test
    public void readsSymbol() throws Exception {
        Form form = FormReader.read(LCReader.fromString("foo"));

        assertNotNull(form);
        assertEquals(symbolForm("foo"), form);
        assertEquals(range(loc(1, 1), loc(1, 4)), form.range);
    }

    @Test
    public void readsQSymbol() throws Exception {
        Form form = FormReader.read(LCReader.fromString("foo/bar"));

        assertNotNull(form);
        assertEquals(qSymbolForm("foo", "bar"), form);
        assertEquals(range(loc(1, 1), loc(1, 8)), form.range);
    }

    @Test
    public void readsList() throws Exception {
        Form form = FormReader.read(LCReader.fromString("(+ 1 2)"));

        assertNotNull(form);
        assertEquals(listForm(symbolForm("+"), intForm(1), intForm(2)), form);
        assertEquals(range(loc(1, 1), loc(1, 8)), form.range);
    }

    @Test
    public void readsEOLComment() throws Exception {
        Form form = FormReader.read(LCReader.fromString("[\"Hello\", ; comment \n\"world!\"]"));

        assertNotNull(form);
        assertEquals(vectorForm(stringForm("Hello"), stringForm("world!")), form);
        assertEquals(range(loc(1, 1), loc(2, 10)), form.range);
    }
}