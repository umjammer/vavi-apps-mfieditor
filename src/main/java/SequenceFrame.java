/*
 * Copyright (c) 2007 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;


/**
 * SequenceFrame.
 * 
 * @author <a href="mailto:vavivavi@yahoo.co.jp">Naohide Sano</a> (nsano)
 * @version 0.00 070618 nsano initial version <br>
 */
public class SequenceFrame {

    Sequence sequence;

    JFrame frame;

    JPanel panel;

    SequenceFrame(Sequence sequence) {
        this.sequence = sequence;

        frame = new JFrame("Sequence");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        panel = new MyPanel();
        panel.setBackground(Color.white);
        panel.setPreferredSize(new Dimension(800 * 4, 600));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(panel);
        frame.getContentPane().add(scrollPane);

        frame.pack();
        frame.setVisible(true);
    }

    /** */
    class MyPanel extends JPanel {
        /** */
        public void paint(Graphics g) {
            Map<String, MidiEvent> noteOnEvents = new HashMap<String, MidiEvent>();

//          int resolution = sequence.getResolution();
            int width = getSize().width;
            long[] ticks = new long[sequence.getTracks().length];
            for (int t = 0; t < sequence.getTracks().length; t++) {
                Track track = sequence.getTracks()[t];
                MidiEvent event = track.get( track.size() - 1);
                ticks[t] = event.getTick();
            }
            Arrays.sort(ticks);
            long maxTick = ticks[ticks.length - 1];
            // x : tick = width : maxTick

//System.err.println("resolution: " + resolution);
            for (int t = 0; t < sequence.getTracks().length; t++) {
                Track track = sequence.getTracks()[t];
                for (int e = 0; e < track.size(); e++) {
                    MidiEvent event = track.get(e);
                    long tick = event.getTick();
//System.err.printf("[%02d] %06d: %s\n", t, tick, event);
                    // 現在の Tick 値にある Midi メッセージを取り出す
                    MidiMessage message = event.getMessage();

                    if (message instanceof ShortMessage) {
                        ShortMessage shortMessage = (ShortMessage) message;
                        int channel = shortMessage.getChannel();
                        int command = shortMessage.getCommand();
                        int data1 = shortMessage.getData1();
//                      int data2 = shortMessage.getData2();
                        String key = channel + ":" + data1;
                        if (command == ShortMessage.NOTE_ON) {
                            noteOnEvents.put(key, event);
                        } else if (command == ShortMessage.NOTE_OFF) {
                            MidiEvent noteOnEvent = noteOnEvents.get(key);
                            if (noteOnEvent != null) {
                                long noteOnTick = noteOnEvent.getTick();
//                              ShortMessage noteOnMessage = (ShortMessage) noteOnEvent.getMessage();
                                int y = channel* 88 + data1; 
                                int x1 = (int) ((float) noteOnTick * width / maxTick);
                                int x2 = (int) ((float) tick * width / maxTick);
                                g.setColor(Color.black);
                                g.drawLine(x1, y, x2, y);
                            } else {
                                System.err.println("no pair: " + key);
                            }
                        }
                    } else if (message instanceof SysexMessage) {
//                      SysexMessage sysexMessage = (SysexMessage) message;
//                      byte[] data = sysexMessage.getData();
                    } else if (message instanceof MetaMessage) {
//                      MetaMessage metaMessage = (MetaMessage) message;
//                      int type = metaMessage.getType();
//                      byte[] data = metaMessage.getData();
                    }
                }
            }
        }
    }
}

/* */
