package hexeditor;

import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

public class PaneCaretListner implements CaretListener {
    private HexEditor editor;

    public PaneCaretListner(HexEditor editor){
        super();
        this.editor = editor;
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        editor.handleCaretMovement(e);
    }
}
