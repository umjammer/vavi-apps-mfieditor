/*
 * Copyright (c) 2003 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ProgressMonitorInputStream;
import javax.swing.SwingWorker;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import vavi.sound.mfi.InvalidMfiDataException;
import vavi.sound.mfi.MetaEventListener;
import vavi.sound.mfi.MetaMessage;
import vavi.sound.mfi.MfiMessage;
import vavi.sound.mfi.MfiSystem;
import vavi.sound.mfi.MidiConverter;
import vavi.sound.mfi.NoteMessage;
import vavi.sound.mfi.Sequence;
import vavi.sound.mfi.Sequencer;
import vavi.util.Debug;
import vavi.util.RegexFileFilter;


/**
 * MFi ファイルのエディタです．
 *
 * @author <a href="mailto:vavivavi@yahoo.co.jp">Naohide Sano</a> (nsano)
 * @version 0.00 010911 nsano initial version <br>
 *          0.01 031213 nsano refine file filter <br>
 */
public class MfiEditor {

    /** */
    private File file;

    /** */
    private Sequencer sequencer;

    /** */
    private Thread thread;

    private SequenceTable table;

    /** トラックセレクタ */
    private JComboBox<Integer> selector;
    /** ProgramChange だけを見せるかどうか */
    private JCheckBox onlyProgramChange;
    /** NoteOn だけを見せるかどうか */
    private JCheckBox onlyNoteOn;
    /** PitchBend だけを見せるかどうか */
    private JCheckBox onlyPitchBend;
    /** チャンネルをトラックに振り分けるかどうか */
    private JCheckBox dispatchChannel;

    /** */
    private JFrame frame;

    /** MIDI ファイルエディタを構築します． */
    private MfiEditor() {

        frame = new JFrame("MfiEditor");

        frame.setSize(new Dimension(600, 480));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // ----
        table = new SequenceTable();

        JScrollPane scrollPane = new JScrollPane(table);
        frame.getContentPane().add(scrollPane);

        // ----
        JToolBar toolBar = new JToolBar();

        //
        toolBar.add(new JLabel("Track: "));
        selector = new JComboBox<>();
        selector.addItemListener(trackListener);
        toolBar.add(selector);

        dispatchChannel = new JCheckBox(dispatchAction);
        toolBar.add(dispatchChannel);

        frame.getContentPane().add(toolBar, BorderLayout.NORTH);

        // ----
        toolBar = new JToolBar();

        JButton button = new JButton(playAction);
        toolBar.add(button);

        button = new JButton(stopAction);
        toolBar.add(button);

        frame.getContentPane().add(toolBar, BorderLayout.SOUTH);

        // ---- メニューの構築
        JMenuBar menuBar = new JMenuBar();

        JMenu menu = new JMenu("File");
        menu.add(openAction);
        menu.add(saveAction);
        menu.add(exitAction);

        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        // ---
        JDialog dialog = new JDialog(frame, "Filter", false);
//      ButtonGroup group = new ButtonGroup();
        JPanel p = new JPanel(new GridLayout(3, 1));
        onlyProgramChange = new JCheckBox(onlyTempoAction);
        p.add(onlyProgramChange);
//      group.add(onlyProgramChange);
        onlyNoteOn = new JCheckBox(onlyGe255Action);
        p.add(onlyNoteOn);
//      group.add(onlyNoteOn);
        onlyPitchBend = new JCheckBox(onlyNopAction);
        p.add(onlyPitchBend);
//      group.add(onlyPitchBend);
        dialog.getContentPane().add(p);
        dialog.pack();
        dialog.setVisible(true);

        // init midi
        try {
            sequencer = MfiSystem.getSequencer();
            sequencer.open();
            sequencer.addMetaEventListener(mel);
//          int [] r = sequencer.addControllerEventListener(cel, new int[] { 192 });
//Debug.println(r.length + ", " + r[0]);
        } catch (Exception e) {
Debug.printStackTrace(e);
            System.err.println("MfiSystem Sequencer Unavailable, exiting!");
            System.exit(1);
        }

        // frame
        frame.setVisible(true);
    }

    private TableModelListener tml = new TableModelListener() {
        public void tableChanged(TableModelEvent event) {
//Debug.println(event.getColumn());
            if (4 == event.getColumn()) {
                try {
                    Sequence sequence = sequencer.getSequence();
                    sequencer.setSequence(sequence);
                } catch (InvalidMfiDataException e) {
Debug.println(e);
                }
            }
        }
    };

    /** */
    private RegexFileFilter fileFilter = new RegexFileFilter();

    /* */ {
        fileFilter.addPattern(".*\\.(mld|MLD)");
        fileFilter.setDescription("MFi File");
    }

    /** */
    private Action openAction = new AbstractAction("Open...") {
        private JFileChooser fc = new JFileChooser();
        /* init */ {
            fc.setFileFilter(fileFilter);
            fc.setMultiSelectionEnabled(false);
        }
        public void actionPerformed(ActionEvent ev) {
            fc.setCurrentDirectory(cwd);
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    open(fc.getSelectedFile());
                    fc.setCurrentDirectory(fc.getSelectedFile());
                    cwd = fc.getCurrentDirectory();
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        }
    };

    /** */
    private Action playAction = new AbstractAction("Play") {
        public void actionPerformed(ActionEvent ev) {
            play();
        }
    };

    /** */
    private Action stopAction = new AbstractAction("Stop") {
        public void actionPerformed(ActionEvent ev) {
            stop();
        }
    };

    /** */
    private void open(File file) {
        this.file = file;
        Loader worker = new Loader();
        worker.execute();
    }

    /** */
    private void play() {
        if (thread == null) {
            thread = new Thread(new Runnable() {
                /** */
                public void run() {
                    Thread thisThread = Thread.currentThread();

                    sequencer.start();

                    while (thread == thisThread) {
                        try { Thread.sleep(99); } catch (Exception e) {}
                    }

                    sequencer.stop();
                }
            });
            thread.start();

            playAction.setEnabled(false);
            stopAction.setEnabled(true);
        }
    }

    /** */
    private void stop() {
        if (thread != null) {
            thread = null;

            sequencer.stop();

            playAction.setEnabled(true);
            stopAction.setEnabled(false);
        }
    }

    /** */
    private Action saveAction = new AbstractAction("Save") {
        private JFileChooser fc = new JFileChooser();
        /* init */ {
            fc.setFileFilter(fileFilter);
            fc.setMultiSelectionEnabled(false);
        }
        public void actionPerformed(ActionEvent ev) {
            fc.setCurrentDirectory(cwd);
            if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                file = fc.getSelectedFile();
                try {
                    Sequence sequence = sequencer.getSequence();
                    MfiSystem.write(sequence, 88, file);
                    fc.setCurrentDirectory(fc.getSelectedFile());
                    cwd = fc.getCurrentDirectory();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /** */
    private Action exitAction = new AbstractAction("Exit") {
        public void actionPerformed(ActionEvent ev) {
            System.exit(0);
        }
    };

    /** */
    private Action dispatchAction = new AbstractAction("Dispatch Channel") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            table.setDispatchChannel(dispatchChannel.isSelected());
            table.addTableModelListener(tml);
            initSelector();
        }
    };

    /** */
    SequenceTable.Filter onlyTempoFilter = new SequenceTable.Filter() {
        public boolean accept(MfiMessage message) {
            return message instanceof vavi.sound.mfi.vavi.track.TempoMessage;
        }
    };
    /** */
    SequenceTable.Filter onlyGe255filter = new SequenceTable.Filter() {
        public boolean accept(MfiMessage message) {
            return message instanceof NoteMessage &&
                   ((NoteMessage) message).getGateTime() >= 255;
        }
    };
    /** */
    SequenceTable.Filter onlyNopfilter = new SequenceTable.Filter() {
        public boolean accept(MfiMessage message) {
            return message instanceof vavi.sound.mfi.vavi.track.NopMessage;
        }
    };

    /** */
    private Action onlyTempoAction = new AbstractAction("Tempo") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyProgramChange.isSelected()) {
                table.addFilter(onlyTempoFilter);
            } else {
                table.removeFilter(onlyTempoFilter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private Action onlyGe255Action = new AbstractAction(">=255") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyNoteOn.isSelected()) {
                table.addFilter(onlyGe255filter);
            } else {
                table.removeFilter(onlyGe255filter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private Action onlyNopAction = new AbstractAction("Nop") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyPitchBend.isSelected()) {
                table.addFilter(onlyNopfilter);
            } else {
                table.removeFilter(onlyNopfilter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private void initSelector() {
        selector.removeAllItems();
        boolean[] tracks = table.getTracks();
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i]) {
                selector.addItem(new Integer(i + 1));
            }
        }
    }

    /** */
    private ItemListener trackListener = new ItemListener() {
        public void itemStateChanged(ItemEvent ev) {
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                int i = ((Integer) selector.getSelectedItem()).intValue() - 1;
                table.removeTableModelListener(tml);
                table.setTrackNumber(i);
                table.addTableModelListener(tml);
            }
        }
    };

    /** */
    private MetaEventListener mel = new MetaEventListener() {
        public void meta(MetaMessage message) {
            if (message.getType() == 47) {  // 47 is end of track
                stop();
            }
        }
    };

    /** テーブルデータのローダ */
    private class Loader extends SwingWorker<Void, Void> {
        /** テーブルデータをロードします． */
        public Void doInBackground() {

            playAction.setEnabled(false);
            stopAction.setEnabled(false);

            onlyProgramChange.setSelected(false);
            onlyNoteOn.setSelected(false);

            try {
                // ファイルの読み込み
                Sequence sequence =
                    MfiSystem.getSequence(
                            new ProgressMonitorInputStream(
                                    null,
                                    "読み込み中 " + file,
                                    new BufferedInputStream(new FileInputStream(file))));

                table.removeTableModelListener(tml);
                table.setSequence(sequence);
                table.addTableModelListener(tml);

                sequencer.setSequence(sequence);


                //----
                MidiConverter converter = MfiSystem.getMidiConverter();
                javax.sound.midi.Sequence midiSequence = converter.toMidiSequence(sequence);
                new SequenceFrame(midiSequence);

            } catch (Exception e) {
Debug.printStackTrace(e);
            }

            return null;
        }
        /** ロード終了後呼ばれます． */
        protected void done() {
            try {
                get();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

            initSelector();

            playAction.setEnabled(true);
            stopAction.setEnabled(false);

            frame.setTitle("MfiEditor - " + file.getAbsolutePath());
        }
    }

    /** */
    private static File cwd = new File(System.getProperty("user.dir"));

    //-------------------------------------------------------------------------

    /** */
    public static void main(String[] args) throws IOException {
        MfiEditor editor = new MfiEditor();
        if (args.length > 0) {
            File file = new File(args[0]);
            if (file.isDirectory()) {
                cwd = file;
            } else {
                editor.open(file);
            }
        }
    }
}

/* */
