package hexeditor;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DocumentChangeListener implements DocumentListener {

    private HexEditor editor;

    DocumentChangeListener(HexEditor editor){
        this.editor = editor;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        editor.handleDocumentChange(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        editor.handleDocumentChange(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {

    }
}
