package hexeditor;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class PaneFocusListener extends FocusAdapter{
    private HexEditor editor;

    public PaneFocusListener(HexEditor editor){
        super();
        this.editor = editor;
    }

    @Override
    public void focusLost(FocusEvent e) {
        editor.handlePaneFocusLost(e);
    }
}
