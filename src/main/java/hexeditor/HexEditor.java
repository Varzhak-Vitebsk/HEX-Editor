package hexeditor;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.ArrayList;

public class HexEditor {
    private JFrame mainFrame = new JFrame("FileLoader");
    private JTextPane hexArea = new JTextPane();
    private JTextPane symbolArea = new JTextPane();
    private JScrollPane hexAreaScrollPane = new JScrollPane(hexArea);
    private JScrollPane symbolAreaScrollPane = new JScrollPane(symbolArea);
    private JLabel fileLine = new JLabel("0");
    private JLabel fileLineMessage = new JLabel("File line: ");
    private DocumentChangeListener documentChangeListener;

    private Path currentFile;
    private Path tempFile;
    private int currentFileRightOffset = 0;
    private int currentFileLeftOffset = 0;
    private static final String NEW_FILE_POSTFIX = ".hexn";
    private static final String TEMP_FILE_POSTFIX = ".tmp";

    private static int screenCenterX = 1;
    private static int screenCenterY = 1;
    private static GraphicsDevice gd;

    private AttributeSet caretIndication;
    private AttributeSet basicIndication;

    private int previousSymbolDot = 0;
    private int previousHexDot = 0;
    private int previousSymbolMark = 0;
    private int previousHexMark = 0;
    private int previousHexLength = 0;
    private int previousHexScrollValue = 0;
    private int previousSymbolScrollValue = 0;

    private int hexAreaScrollRowSize = 0;
    private int hexAreaScrollRowsInView = 0;

    private boolean isLoading = false;
    private boolean eventBlock = false;

    private static final int FRAME_MIN_WIDTH = 640;
    private static final int FRAME_MIN_HEIGHT = 480;
    private static final int AREA_RAW_SYMBOLS_IN_LINE = 16;
    private static final int HEX_AREA_SYMBOLS_FOR_RAW = 3;
    private static final int HEX_AREA_SYMBOLS_IN_LINE = AREA_RAW_SYMBOLS_IN_LINE * HEX_AREA_SYMBOLS_FOR_RAW;
    private static final int AREA_NUMBER_OF_LINES = 25;
    private static final int AREA_SYMBOLS_AS_NUMBER = 32;
    private static final int AREA_SYMBOLS_AS_BOX = 126;
    private static final char[] ENABLED_KEYS = getEnabledKeys();
    private static final char[] DISABLED_KEYS = getDisabledKeys();

    private ArrayList<Integer> symbolAreaRows = new ArrayList<>(AREA_NUMBER_OF_LINES + 1);

    private static char[] getEnabledKeys(){
        char[] keys = new char[16];
        int index= 0;
        for (int c = KeyEvent.VK_0; c <= KeyEvent.VK_9; ++c, ++ index) keys[index] = (char)c;
        for (int c = KeyEvent.VK_A; c <= KeyEvent.VK_F; ++c, ++ index) keys[index] = (char)c;
        return keys;
    }
    private static char[] getDisabledKeys(){
        char[] keys = new char[78];
        int index= 0;
        for (int c = 0x0067; c <= 0x007e; ++c, ++ index) keys[index] = (char)c; //24
        for (int c = 0x003a; c <= 0x0060; ++c, ++ index) keys[index] = (char)c; //39
        for (int c = 0x0021; c <= 0x002f; ++c, ++ index) keys[index] = (char)c; //15
        return keys;
    }

    public HexEditor(){
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        mainFrame.setMinimumSize(new Dimension(FRAME_MIN_WIDTH, FRAME_MIN_HEIGHT));

        screenCenterX = gd.getDisplayMode().getWidth() / 2;
        screenCenterY = gd.getDisplayMode().getHeight() / 2;
        int frameWidth  = Math.max(FRAME_MIN_WIDTH, screenCenterX);
        int frameHeight = Math.max(FRAME_MIN_HEIGHT, screenCenterY);
        int frameOffsetLeft = screenCenterX - frameWidth / 2;
        int frameOffsetTop = screenCenterY - frameHeight / 2;
        mainFrame.setSize(new Dimension(frameWidth, frameHeight));
        mainFrame.setLocation(frameOffsetLeft, frameOffsetTop);
//        mainFrame.setResizable(false);

        JPanel back_panel = new JPanel(new GridBagLayout());
        back_panel.setBackground(Color.WHITE);
        createStandardUI(back_panel);
        mainFrame.add(back_panel);

        StyleContext styleContext = StyleContext.getDefaultStyleContext();
        caretIndication = styleContext.addAttribute(
                SimpleAttributeSet.EMPTY,
                StyleConstants.Background, Color.GRAY);
        basicIndication = styleContext.addAttribute(
                SimpleAttributeSet.EMPTY,
                StyleConstants.Background, Color.WHITE);
    }

    public static void main(String[] args) {
        try {
            HexEditor editor = new HexEditor();
            editor.startEditor();
        } catch (RuntimeException e) {
            System.exit(1);
        }
    }

    public void startEditor(){
        mainFrame.setVisible(true);
    }

    private void createStandardUI(JPanel back_panel){
        GridBagConstraints constraints= new GridBagConstraints();

        JButton openFileButton = new JButton("Open file");
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 5, 5, 5);
        openFileButton.addActionListener(event -> {
            final JFileChooser fc = new JFileChooser();
            int returnVal = fc.showOpenDialog(getMainFrame());
            if(returnVal == JFileChooser.APPROVE_OPTION)
                loadFile(fc.getSelectedFile().toPath());});
        back_panel.add(openFileButton, constraints);

        JButton saveFileButton = new JButton("Save");
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
//        saveFileButton.addActionListener(new SaveFileDialogActionListener());
        back_panel.add(saveFileButton, constraints);

        constraints.gridy = 2;
        constraints.weightx = 2D;
        constraints.gridwidth = 10;
        constraints.fill = GridBagConstraints.BOTH;
        hexAreaScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        hexAreaScrollPane.setPreferredSize(new Dimension(300, 289 + 4));
        hexArea.setEditable(true);
        hexArea.setBorder(BorderFactory.createLineBorder(Color.black));
        PaneFocusListener focusListener = new PaneFocusListener(this);
        hexArea.addCaretListener(e -> handleCaretMovement(e));
        hexArea.addFocusListener(focusListener);
        hexAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> handleHexPaneScroll(e));
        back_panel.add(hexAreaScrollPane, constraints);

        InputMap areaInputMap = new InputMap();
        areaInputMap.setParent(hexArea.getInputMap(JComponent.WHEN_FOCUSED));
        for (char c: DISABLED_KEYS) {
            areaInputMap.put(KeyStroke.getKeyStroke(c), "none");
        }
        hexArea.setInputMap(JComponent.WHEN_FOCUSED, areaInputMap);

        constraints.weightx = 1D;
        constraints.fill = GridBagConstraints.BOTH;
        symbolAreaScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        symbolAreaScrollPane.setPreferredSize(new Dimension(250, 289 + 4));
        symbolArea.setEditable(true);
        symbolArea.setBorder(BorderFactory.createLineBorder(Color.black));
        symbolArea.addCaretListener(e -> handleCaretMovement(e));
        symbolArea.addFocusListener(focusListener);
        symbolAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(event -> handleSymbolPaneScroll(event));
        back_panel.add(symbolAreaScrollPane, constraints);

        back_panel.add(fileLineMessage);
        back_panel.add(fileLine);

        documentChangeListener = new DocumentChangeListener(this);
    }

    public JFrame getMainFrame(){
        return mainFrame;
    }

    public void loadFile(Path path){
        if (Files.isReadable(path)){
            isLoading = true;
            this.currentFile = path;
            copyToTemporaryFile(currentFile);
            currentFileLeftOffset = 0;
            currentFileRightOffset = AREA_NUMBER_OF_LINES * AREA_RAW_SYMBOLS_IN_LINE;
            previousSymbolDot = 0;
            previousHexDot = 0;
            previousSymbolMark = 0;
            previousHexMark = 0;
            previousHexScrollValue = 0;
            hexAreaScrollRowsInView = 0;
            hexAreaScrollRowSize = 0;
            previousHexLength = 0;
            for (CaretListener listener: hexArea.getCaretListeners())
                hexArea.removeCaretListener(listener);
            for (CaretListener listener: symbolArea.getCaretListeners())
                symbolArea.removeCaretListener(listener);
            for (AdjustmentListener listener: hexAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners())
                hexAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(listener);
            for (AdjustmentListener listener: symbolAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners())
                symbolAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(listener);
            StyledDocument hexDocument = new DefaultStyledDocument();
            StyledDocument symbolDocument = new DefaultStyledDocument();
            loadFileSection(tempFile, currentFileLeftOffset, hexDocument, symbolDocument);
            hexDocument.addDocumentListener(documentChangeListener);
            hexArea.setStyledDocument(hexDocument);
            symbolArea.setStyledDocument(symbolDocument);
            symbolAreaRows.add(symbolDocument.getLength());
            previousHexScrollValue = hexAreaScrollPane.getVerticalScrollBar().getValue();
            isLoading = false;
            hexArea.setCaretPosition(0);
            hexAreaScrollPane.getVerticalScrollBar().setValue(0);
            symbolArea.setCaretPosition(0);
            symbolAreaScrollPane.getVerticalScrollBar().setValue(0);
            hexArea.addCaretListener(e -> handleCaretMovement(e));
            symbolArea.addCaretListener(e -> handleCaretMovement(e));
            hexAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> handleHexPaneScroll(e));
            symbolAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> handleSymbolPaneScroll(e));
        }
    }

    private void copyToTemporaryFile(Path source){
        try {
            if (tempFile == null) {
                tempFile = Files.createTempFile(null, NEW_FILE_POSTFIX + TEMP_FILE_POSTFIX);
                tempFile.toFile().deleteOnExit();
                tempFile.toFile().setWritable(true);
            }
            Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex){
            System.err.println(ex);
        }
    }

    private void fileScreenMove(int rowsAdded){
        isLoading = true;
        changeFileOffset(rowsAdded);
        previousHexDot = hexArea.getCaretPosition();
        for (CaretListener listener: hexArea.getCaretListeners())
            hexArea.removeCaretListener(listener);
        for (CaretListener listener: symbolArea.getCaretListeners())
            symbolArea.removeCaretListener(listener);
        for (AdjustmentListener listener: hexAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners())
            hexAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(listener);
        for (AdjustmentListener listener: symbolAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners())
            symbolAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(listener);
        StyledDocument hexDocument = new DefaultStyledDocument();
        StyledDocument symbolDocument = new DefaultStyledDocument();
        loadFileSection(tempFile, currentFileLeftOffset, hexDocument, symbolDocument);
        hexDocument.addDocumentListener(documentChangeListener);
        hexArea.setStyledDocument(hexDocument);
        previousHexLength = hexDocument.getLength();
        symbolArea.setStyledDocument(symbolDocument);
        isLoading = false;
        hexArea.addCaretListener(e -> handleCaretMovement(e));
        symbolArea.addCaretListener(e -> handleCaretMovement(e));
        hexAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> handleHexPaneScroll(e));
        symbolAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> handleSymbolPaneScroll(e));
    }

    private void loadFileSection(Path file, long fileOffset, StyledDocument hexDocument, StyledDocument symbolDocument){
        try{
            SeekableByteChannel byteChannel = Files.newByteChannel(file);
            ByteBuffer buffer = ByteBuffer.allocate(AREA_NUMBER_OF_LINES * AREA_RAW_SYMBOLS_IN_LINE);
            byteChannel = byteChannel.position(fileOffset);

            if(byteChannel.read(buffer) > 0) {
                StyleContext styleContext = StyleContext.getDefaultStyleContext();
                AttributeSet foreground;
                AttributeSet hexFont = styleContext.addAttribute(
                        SimpleAttributeSet.EMPTY,
                        StyleConstants.FontFamily, Font.MONOSPACED);
                int index = 1;
                int position = 0;
                symbolAreaRows = new ArrayList<>(AREA_NUMBER_OF_LINES + 1);
                symbolAreaRows.add(position);
                try{
                    for(byte v: buffer.array()){
                        // breaking when exceed file size
                        if (fileOffset + index >= byteChannel.size()){
                            if (hexDocument.getLength() % HEX_AREA_SYMBOLS_FOR_RAW == 0)
                                hexDocument.remove(hexDocument.getLength() - 1, 1);
                            break;
                        }
                        int c = v < 0 ? v & 0xff: v; //unsighning byte

                        String hexSymbol = Integer.toHexString(c);
                        if (hexSymbol.length() == 1) hexSymbol = "0" + hexSymbol;

                        if (index > 1 && (index - 1) % AREA_RAW_SYMBOLS_IN_LINE == 0){
                            hexDocument.insertString(hexDocument.getLength()
                                    , "\n"
                                    , hexFont);
                            symbolDocument.insertString(symbolDocument.getLength()
                                    , "\n"
                                    , hexFont);
//                            ++position;
                            symbolAreaRows.add(position);
                        }

                        String hexLineEnding = "";
                        int hexOffset = hexDocument.getLength();
                        hexDocument.insertString(hexOffset
                                , hexSymbol + (index % AREA_RAW_SYMBOLS_IN_LINE == 0 ? hexLineEnding : " ")
                                , hexFont);

                        String symbol;
                        int symbolAttributeOffset = symbolDocument.getLength();
                        if (c < AREA_SYMBOLS_AS_NUMBER) {
                            symbol = "\\" + c;
                            foreground = styleContext.addAttribute(
                                    SimpleAttributeSet.EMPTY,
                                    StyleConstants.Foreground, Color.BLUE);
                            ++symbolAttributeOffset;
                        } else if (c > AREA_SYMBOLS_AS_BOX) {
                            symbol = "\u25FB";
                            foreground = styleContext.addAttribute(
                                    SimpleAttributeSet.EMPTY,
                                    StyleConstants.Foreground, new Color(0, 100, 0));
                        } else {
                            symbol = String.valueOf((char) c);
                            foreground = styleContext.addAttribute(
                                    SimpleAttributeSet.EMPTY,
                                    StyleConstants.Foreground, Color.BLACK);
                        }

                        String symbolLineEnding = "";
                        int symbolOffset = symbolDocument.getLength();
                        symbolDocument.insertString(symbolOffset
                                , symbol + symbolLineEnding
                                , null);
                        symbolDocument.setCharacterAttributes(symbolAttributeOffset
                                , symbolAttributeOffset + symbol.length()
                                , foreground, true);
                        ++index;
                        position = symbolDocument.getLength() + 1;
                    }
                } catch (BadLocationException ex){
                    System.err.println("Some issue with text inserting:\\n " + ex);
                } finally {
                    if (byteChannel != null) {
                        byteChannel.close();
                    }
                }
            }
        } catch(IOException ex) {
            System.err.println("Some issue with file stream:\\n " + ex);
        }
    }

    public void handleCaretMovement(CaretEvent e){
        if (isLoading) return;


        //blocking handling another event while handling current event
        if (eventBlock){
            eventBlock = false;
            return;
        }
        if (hexArea.getStyledDocument().getLength() == 0) return;
        if (symbolArea.getStyledDocument().getLength() == 0) return;
        if (e.getSource().equals(hexArea)){
            //when CaretEvent fired before DocumentEvent after document change
            if (hexArea.getStyledDocument().getLength() != previousHexLength) return;

            symbolAreaScrollPane.getVerticalScrollBar().setValue(hexAreaScrollPane.getVerticalScrollBar().getValue());
            moveCaretOnSymbolPane(e);
            if ((e.getDot() / (HEX_AREA_SYMBOLS_IN_LINE)) == (AREA_NUMBER_OF_LINES - 1)
                    && currentFileLeftOffset  < (currentFile.toFile().length() -  AREA_NUMBER_OF_LINES * AREA_RAW_SYMBOLS_IN_LINE)){

                //one row upper visible section for ScrollBar value > 0
                fileScreenMove(AREA_NUMBER_OF_LINES - 2);
                eventBlock = true;
                hexArea.setCaretPosition(2 * HEX_AREA_SYMBOLS_IN_LINE - 1);
                return;
            }
            if ((e.getDot() / (HEX_AREA_SYMBOLS_IN_LINE)) == 0 && currentFileLeftOffset > 0){

                //one row upper visible section for ScrollBar value > 0
                fileScreenMove(-(AREA_NUMBER_OF_LINES - 2));
                eventBlock = true;
                hexArea.setCaretPosition((AREA_NUMBER_OF_LINES - 2) * HEX_AREA_SYMBOLS_IN_LINE);
            }
        }
        else{
            hexAreaScrollPane.getVerticalScrollBar().setValue(symbolAreaScrollPane.getVerticalScrollBar().getValue());
            moveCaretOnHexPane(e);
        }
        fileLine.setText(String.valueOf((currentFileLeftOffset + hexArea.getCaretPosition() / HEX_AREA_SYMBOLS_FOR_RAW)
                / AREA_RAW_SYMBOLS_IN_LINE));
    }

    private void moveCaretOnHexPane(CaretEvent e){
        StyledDocument hexDocument = hexArea.getStyledDocument();
        int documentRowNumber;
        for(documentRowNumber = 0;  documentRowNumber < symbolAreaRows.size() - 1; ++documentRowNumber){
            if(symbolAreaRows.get(documentRowNumber + 1) >= e.getDot()) break;
        }
        int dot = e.getDot() - symbolAreaRows.get(documentRowNumber);
        int mark = HEX_AREA_SYMBOLS_FOR_RAW;
        int symbolCode;
        try {
            String hexAreaRow = hexDocument.getText(documentRowNumber * HEX_AREA_SYMBOLS_IN_LINE
                    , Math.min(HEX_AREA_SYMBOLS_IN_LINE
                            , Math.abs(documentRowNumber * HEX_AREA_SYMBOLS_IN_LINE - hexDocument.getLength())));
            int index = 0;
            int offset = 0;
            while(index < hexAreaRow.length() ){
                symbolCode = Integer.parseInt(hexAreaRow.substring(index, index + 2), 16);
                offset += symbolCode < AREA_SYMBOLS_AS_NUMBER ? ("\\" + symbolCode).length() : 1;
                if(offset >= dot) break;
                index += HEX_AREA_SYMBOLS_FOR_RAW;
            }
            dot = documentRowNumber * HEX_AREA_SYMBOLS_IN_LINE + index;
        } catch (BadLocationException ex){
            System.err.println(ex);
        }
        hexDocument.setCharacterAttributes(dot , mark, caretIndication, false);
        if (dot != previousHexDot)
            hexDocument.setCharacterAttributes(previousHexDot, previousHexMark, basicIndication, false);
        previousHexDot = dot;
        previousHexMark = mark;
    }

    private void moveCaretOnSymbolPane(CaretEvent e){
        StyledDocument symbolDocument;
        StyledDocument hexDocument;
        symbolDocument = symbolArea.getStyledDocument();
        hexDocument = hexArea.getStyledDocument();
        int documentRowNumber = e.getDot() / (HEX_AREA_SYMBOLS_IN_LINE);
        int dot = e.getDot() / HEX_AREA_SYMBOLS_FOR_RAW;
        int mark = 1;
        int symbolCode;
        try {
            String hexAreaRow = hexDocument.getText(documentRowNumber * HEX_AREA_SYMBOLS_IN_LINE
                    , Math.min(HEX_AREA_SYMBOLS_IN_LINE
                            , Math.abs(documentRowNumber * HEX_AREA_SYMBOLS_IN_LINE - hexDocument.getLength())));
            int index = 0;
            int offset = 0;
            while(index < (dot - documentRowNumber * AREA_RAW_SYMBOLS_IN_LINE) * HEX_AREA_SYMBOLS_FOR_RAW
                    && index < hexAreaRow.length() ){
                symbolCode = Integer.parseInt(hexAreaRow.substring(index, index + 2), 16);
                offset += symbolCode < AREA_SYMBOLS_AS_NUMBER ? ("\\" + symbolCode).length() : 1;
                index += HEX_AREA_SYMBOLS_FOR_RAW;
            }
//            dot = offset + symbolAreaRows.get(documentRowNumber).getOffset();
            dot = offset + symbolAreaRows.get(documentRowNumber);
            symbolCode = Integer.parseInt(hexAreaRow.substring(index, index + 2), 16);
            mark += symbolCode < AREA_SYMBOLS_AS_NUMBER ? ("\\" + symbolCode).length() - 1 : 0; //several symbols in symbolArea may count as one
        } catch (BadLocationException ex){
            System.err.println(ex);
        }
        symbolDocument.setCharacterAttributes(dot, mark, caretIndication, false);
        if (dot != previousSymbolDot)
            symbolDocument.setCharacterAttributes(previousSymbolDot, previousSymbolMark, basicIndication, false);
        previousSymbolDot = dot;
        previousSymbolMark = mark;
    }

    public void handlePaneFocusLost(FocusEvent e){
        if (isLoading) return;
        if (hexArea.getStyledDocument().getLength() == 0) return;
        if (symbolArea.getStyledDocument().getLength() == 0) return;
        if (e.getSource().equals(symbolArea)){
            hexArea.getStyledDocument().setCharacterAttributes(previousHexDot, previousHexMark, basicIndication, false);
//            previousHexDot = 0;
//            previousHexMark = 0;
        }
        else {
            symbolArea.getStyledDocument().setCharacterAttributes(previousSymbolDot
                    , previousSymbolMark, basicIndication, false);
//            previousSymbolDot = 0;
//            previousSymbolMark = 0;
        }
    }

    public void handleHexPaneScroll(AdjustmentEvent e){
        if (isLoading) return;
        if (hexArea.getStyledDocument().getLength() == 0) return;
        if (symbolArea.getStyledDocument().getLength() == 0) return;
        if (e.getValueIsAdjusting()) return;

        if (hexAreaScrollRowSize == 0) {
            hexAreaScrollRowSize = hexAreaScrollPane.getVerticalScrollBar().getMaximum() / AREA_NUMBER_OF_LINES;
            hexAreaScrollRowsInView = hexAreaScrollPane.getVerticalScrollBar().getVisibleAmount() / hexAreaScrollRowSize;
        }

        if(hexArea.hasFocus()) {
            symbolAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(
                    symbolAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners()[0]);
            symbolAreaScrollPane.getVerticalScrollBar().setValue(hexAreaScrollPane.getVerticalScrollBar().getValue());
            if (symbolAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners().length == 0)
                symbolAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(event -> handleSymbolPaneScroll(event));
        }
        previousHexScrollValue = hexAreaScrollPane.getVerticalScrollBar().getValue();
    }

    public void handleSymbolPaneScroll(AdjustmentEvent e){
        if(isLoading) return;
        if(hexArea.getStyledDocument().getLength() == 0) return;
        if(symbolArea.getStyledDocument().getLength() == 0) return;
        if(symbolArea.hasFocus()) {
            hexAreaScrollPane.getVerticalScrollBar().removeAdjustmentListener(
                    hexAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners()[0]);
            hexAreaScrollPane.getVerticalScrollBar().setValue(symbolAreaScrollPane.getVerticalScrollBar().getValue());
            if (hexAreaScrollPane.getVerticalScrollBar().getAdjustmentListeners().length == 0)
                hexAreaScrollPane.getVerticalScrollBar().addAdjustmentListener(event -> handleHexPaneScroll(event));
        }
        previousSymbolScrollValue = symbolAreaScrollPane.getVerticalScrollBar().getValue();
    }

    private void changeFileOffset(int linesNumber){
        currentFileLeftOffset = currentFileLeftOffset + linesNumber * AREA_RAW_SYMBOLS_IN_LINE > 0
                ? currentFileLeftOffset + linesNumber * AREA_RAW_SYMBOLS_IN_LINE
                : 0;
    }

    public void handleDocumentChange(DocumentEvent e){
        isLoading = true;
        if (e.getType() == DocumentEvent.EventType.INSERT) {
            tempFileByteInsertion(e);
        }
        if (e.getType() == DocumentEvent.EventType.REMOVE) {
            tempFileByteRemoval(e);
        }
        int dot = hexArea.getCaretPosition();
        fileScreenMove(0);
        isLoading = false;
        hexArea.setCaretPosition(dot);
    }

    private void tempFileByteInsertion(DocumentEvent e)  {

        if (tempFile == null) return;

        try {
            int fileOffset = currentFileLeftOffset + e.getOffset() / HEX_AREA_SYMBOLS_FOR_RAW;
            StringBuffer symbol = new StringBuffer();
            int changeByte = 1;

            //synchronizing position of a new symbol in hexDocument and position of hex representation of byte
            switch (e.getOffset() % HEX_AREA_SYMBOLS_FOR_RAW){
                case 0:
//                    offset = e.getOffset();
                    symbol.append(e.getDocument().getText(e.getOffset(), 3));
                    symbol.delete(1, 2);
                    break;
                case 1:
                    symbol.append(e.getDocument().getText(e.getOffset() - 1, 2));
                    break;
                case 2:
                    symbol.append(e.getDocument().getText(e.getOffset(), 1));
                    symbol.append("0");
                    ++fileOffset;
                    changeByte = 0;
                    break;
            }

            byte symbolCode = (byte) Integer.parseInt(symbol.toString(), 16);
            byte[] data = {symbolCode};
            ByteBuffer newData = ByteBuffer.wrap(data);

            Path oldTempFile = tempFile;

            Path newTempFile = Files.createTempFile(null, NEW_FILE_POSTFIX + TEMP_FILE_POSTFIX);
            newTempFile.toFile().deleteOnExit();
            newTempFile.toFile().setWritable(true);

            copyFilePartially(oldTempFile, newTempFile, 0, fileOffset, newData);
            copyFilePartially(oldTempFile, newTempFile, fileOffset + changeByte, oldTempFile.toFile().length());

            tempFile = newTempFile;
            Files.deleteIfExists(oldTempFile);

        } catch (IOException ex){
            ex.printStackTrace();
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void tempFileByteRemoval(DocumentEvent e)  {

        if (tempFile == null) return;

        try {
            int fileOffset = e.getOffset();

            fileOffset = currentFileLeftOffset + fileOffset / HEX_AREA_SYMBOLS_FOR_RAW;

            Path oldTempFile = tempFile;

            Path newTempFile = Files.createTempFile(null, NEW_FILE_POSTFIX + TEMP_FILE_POSTFIX);
            newTempFile.toFile().deleteOnExit();
            newTempFile.toFile().setWritable(true);

            copyFilePartially(oldTempFile, newTempFile, 0, fileOffset);
            copyFilePartially(oldTempFile, newTempFile, fileOffset + 1, oldTempFile.toFile().length());

            tempFile = newTempFile;
            Files.deleteIfExists(oldTempFile);

        } catch (IOException ex){
            ex.printStackTrace();
        }
    }

    private void copyFilePartially(Path source, Path target, long start, long end){
        copyFilePartially(source, target, start, end, null);
    }

    private void copyFilePartially(Path source, Path target, long start, long end, ByteBuffer newData){
        try {
            SeekableByteChannel sourceByteChannel = Files.newByteChannel(source, StandardOpenOption.READ);
            SeekableByteChannel targetByteChannel = Files.newByteChannel(target, StandardOpenOption.APPEND);
            ByteBuffer buffer = ByteBuffer.allocate((int)(end - start)); //for test purpose
            sourceByteChannel = sourceByteChannel.position(start);
            if (sourceByteChannel.read(buffer) > 0){
                buffer.position(0);
                while (buffer.hasRemaining()){
                    targetByteChannel.write(buffer);
                }
            }
            if (newData != null)
                while (newData.hasRemaining()){
                    targetByteChannel.write(newData);
                }
            sourceByteChannel.close();
            targetByteChannel.close();
        } catch (IOException ex){
            System.err.println(ex);
        }
    }
}