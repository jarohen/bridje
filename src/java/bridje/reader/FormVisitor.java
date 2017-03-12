package bridje.reader;

public interface FormVisitor<T> {

    T visit(Form.BoolForm form);

    T visit(Form.StringForm form);

    T visit(Form.IntForm form);

    T visit(Form.VectorForm form);

    T visit(Form.SetForm form);

    T visit(Form.RecordForm form);

    T visit(Form.ListForm form);

    T visit(Form.SymbolForm form);
}