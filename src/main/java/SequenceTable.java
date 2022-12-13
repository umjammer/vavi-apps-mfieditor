/*
 * Copyright (c) 2003 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import vavi.sound.mfi.ChannelMessage;
import vavi.sound.mfi.LongMessage;
import vavi.sound.mfi.MetaMessage;
import vavi.sound.mfi.MfiEvent;
import vavi.sound.mfi.MfiMessage;
import vavi.sound.mfi.NoteMessage;
import vavi.sound.mfi.Sequence;
import vavi.sound.mfi.ShortMessage;
import vavi.sound.mfi.SysexMessage;
import vavi.sound.mfi.Track;
import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * TableModel for MFi Sequence
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 030920 nsano initial version <br>
 */
public class SequenceTable extends JTable {

    /** */
    private SequenceTableModel model;

    /** */
    public SequenceTable() {
    }

    /** */
    public void addTableModelListener(TableModelListener l) {
        model.addTableModelListener(l);
    }

    /** */
    public void removeTableModelListener(TableModelListener l) {
        if (model == null) {
            return;
        }
        model.removeTableModelListener(l);
    }

    /** table init */
    public void setSequence(Sequence sequence) {
        model = new SequenceTableModel(sequence);
Debug.println("model set: " + model);
        this.setModel(model);

        this.getColumn("delta").setCellRenderer(tcRenderer);
        this.getColumn("voice").setCellRenderer(tcRenderer);
        this.getColumn("event").setCellRenderer(tcRenderer);
        this.getColumn("data1").setCellRenderer(tcRenderer);
        this.getColumn("data2").setCellRenderer(tcRenderer);

        for (int i = 0; i < model.getColumnCount(); i++) {
            getColumn(model.getColumnName(i)).setPreferredWidth(widths[i]);
        }
    }

    /** */
    public boolean[] getTracks() {
//Debug.printStackTrace(new Exception());
        return model.getTracks();
    }

    /** トラックナンバーを返します． */
    public void setTrackNumber(int trackNumber) {
        model.setTrackNumber(trackNumber);
    }

    /** */
    public void setDispatchChannel(boolean dispatchChannel) {
        model.setDispatchChannel(dispatchChannel);
    }

    /** */
    private final Set<Filter> filters = new HashSet<>();

    /** */
    public void addFilter(Filter filter) {
        filters.add(filter);
        model.fireTableChanged(new TableModelEvent(model));
    }

    /** */
    public void removeFilter(Filter filter) {
        filters.remove(filter);
        model.fireTableChanged(new TableModelEvent(model));
    }

    /** テーブルのセルレンダラ */
    private final TableCellRenderer tcRenderer = new DefaultTableCellRenderer() {
        /** レンダラのテキストを設定します． */
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            MfiEvent event = (MfiEvent) value;

            // 現在の Tick 値にある Mfi メッセージを取り出す
            MfiMessage message = event.getMessage();

            if (isSelected) {
                super.setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                super.setForeground(table.getForeground());
                super.setBackground(table.getBackground());
                for (Filter i : filters) {
                    if (i.accept(message)) {
                        super.setBackground(Color.yellow);
                    }
                }
            }

            setFont(table.getFont());

            if (hasFocus) {
                setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
                if (table.isCellEditable(row, column)) {
                    super.setForeground(UIManager.getColor("Table.focusCellForeground"));
                    super.setBackground(UIManager.getColor("Table.focusCellBackground"));
                }
            } else {
                setBorder(noFocusBorder);
            }

            // 現在の Tick 値の取得
            int delta = message.getDelta();

            if (message instanceof ShortMessage) {
                ShortMessage msg = (ShortMessage) message;
                int status = msg.getStatus();
                int command = msg.getCommand();
                int data = msg.getData();
                switch (column) {
                case 1:    // delta
                    setText(String.valueOf(delta));
                    break;
                case 2:    // voice
                    if (message instanceof ChannelMessage) {
                        int channel = ((ChannelMessage) message).getVoice();
                        setText(String.valueOf(channel + 1));
                    } else {
                        setText("n/a");
                    }
                    break;
                case 3:    // event
                    setText(getEventName(status, command));
                    break;
                case 4:    // data1
                    if (message instanceof ChannelMessage) {
                        setText(String.valueOf(data & 0x3f));
                    } else {
                        setText(String.valueOf(data));
                    }
                    break;
                case 5:    // data2
                    setText("");
                    break;
                }
            } else if (message instanceof LongMessage) {
                LongMessage msg = (LongMessage) message;
                int status = msg.getStatus();
                int command = msg.getCommand();
                byte[] data = msg.getData();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(data.length, 32); i++) {
                    sb.append(StringUtil.toHex2(data[i]));
                    sb.append(" ");
                }
                switch (column) {
                case 1: // delta
                    setText(String.valueOf(delta));
                    break;
                case 2: // voice
                    if (message instanceof ChannelMessage) {
                        int channel = ((ChannelMessage) message).getVoice();
                        setText(String.valueOf(channel + 1));
                    } else {
                        setText("n/a");
                    }
                    break;
                case 3: // event
                    setText(getEventName(status, command));
                    break;
                case 4: // data1
                    setText(sb.toString());
                    break;
                case 5: // data2
                    setText("");
                    break;
                }
            } else if (message instanceof NoteMessage) {
                // note のデータを取り出す
                NoteMessage msg = (NoteMessage) message;
                int channel = msg.getVoice();
                int note = msg.getNote() + 45;
                int gateTime = msg.getGateTime();
                int velocity = msg.getVelocity();
                switch (column) {
                case 1:    // delta
                    setText(String.valueOf(delta));
                    break;
                case 2:    // channel
                    setText(String.valueOf(channel + 1));
                    break;
                case 3:    // event
                    setText("Note");
                    break;
                case 4:    // data1
                    setText(String.valueOf(note));
                    break;
                case 5:    // data2
                    setText(gateTime + ", " + velocity);
                    break;
                }
            } else if (message instanceof SysexMessage) {
                // Sysex のデータを取り出す
                SysexMessage msg = (SysexMessage) message;
                byte[] data = msg.getMessage();
                StringBuilder sb1 = new StringBuilder();
                for (int i = 0; i < 6; i++) {
                    sb1.append(StringUtil.toHex2(data[i]));
                    sb1.append(" ");
                }
                StringBuilder sb2 = new StringBuilder();
                for (int i = 6; i < Math.min(data.length, 32); i++) {
                    sb2.append(StringUtil.toHex2(data[i]));
                    sb2.append(" ");
                }
                switch (column) {
                case 1:    // delta
                    setText(String.valueOf(delta));
                    break;
                case 2:    // channel
                    setText("n/a");
                    break;
                case 3:    // event
                    setText("SYSX");
                    break;
                case 4:    // data1
                    setText(sb1.toString());
                    break;
                case 5:    // data2
                    setText(sb2.toString());
                    break;
                }
            } else if (message instanceof MetaMessage) {
                // MetaMessage のデータを取り出す
                MetaMessage msg = (MetaMessage) message;
                int type = msg.getType();
                byte[] data = msg.getMessage();
                StringBuilder sb = new StringBuilder();
                for (int i = 6; i < data.length; i++) {
                    sb.append(StringUtil.toHex2(data[i]));
                    sb.append(" ");
                }
                switch (column) {
                case 1:    // delta
                    setText(String.valueOf(delta));
                    break;
                case 2:    // channel
                    setText("n/a");
                    break;
                case 3:    // event
                    setText("META");
                    break;
                case 4:    // data1
                    setText(String.valueOf(type));
                    break;
                case 5:    // data2
                    setText(sb.toString());
                    break;
                }
            } else if (message instanceof vavi.sound.mfi.vavi.track.UndefinedMessage) { // TODO vavi パッケージに依存
                vavi.sound.mfi.vavi.track.UndefinedMessage msg = (vavi.sound.mfi.vavi.track.UndefinedMessage) message;
                int status = msg.getStatus();
                byte[] data = msg.getMessage();
                int command = data[2] & 0xff;
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < Math.min(data.length, 32); i++) {
                    sb.append(StringUtil.toHex2(data[i]));
                    sb.append(" ");
                }
                switch (column) {
                case 1: // delta
                    setText(String.valueOf(delta));
                    break;
                case 2: // voice
                    setText("n/a");
                    break;
                case 3: // event
                    setText("(" + getEventName(status, command) + ")");
                    break;
                case 4: // data1
                    setText(sb.toString());
                    break;
                case 5: // data2
                    setText("");
                    break;
                }
            } else if (message instanceof vavi.sound.mfi.vavi.AudioDataMessage) { // TODO vavi パッケージに依存
                vavi.sound.mfi.vavi.AudioDataMessage msg = (vavi.sound.mfi.vavi.AudioDataMessage) message;
                byte[] data = msg.getData();
                int index = msg.getAudioDataNumber();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(data.length, 32); i++) {
                    sb.append(StringUtil.toHex2(data[i]));
                    sb.append(" ");
                }
                switch (column) {
                case 1: // delta
                    setText("n/a");
                    break;
                case 2: // voice
                    if (message instanceof ChannelMessage) {
                        int channel = ((ChannelMessage) message).getVoice();
                        setText(String.valueOf(channel + 1));
                    } else {
                        setText("n/a");
                    }
                    break;
                case 3: // event
                    setText("AudioData");
                    break;
                case 4: // data1
                    setText(String.valueOf(index)); // TODO sub chunks
                    break;
                case 5: // data2
                    setText(sb.toString());
                    break;
                }
            } else {
                Debug.println("not implemented for: " + message.getClass());
            }

            return this;
        }

        /** イベント名を取得します． */
        private String getEventName(int status, int command) {
            switch (status) {
            case MfiMessage.STATUS_CLASS_A:
                switch (command) {
                default:
                    return "Class A (" + StringUtil.toHex2(command) + ")";
                }
            case MfiMessage.STATUS_CLASS_B:
                switch (command) {
                case 0x00:
                    return "AudioPlay";
                case 0x01:
                    return "AudioStop";
                case 0x80:
                    return "AudioChannelVolume";
                case 0x81:
                    return "AudioChannelPanpot";
                default:
                    return "Class B (" + StringUtil.toHex2(command) + ")";
                }
            case MfiMessage.STATUS_CLASS_C:
                switch (command) {
                default:
                    return "Class C (" + StringUtil.toHex2(command) + ")";
                }
            case MfiMessage.STATUS_NORMAL:
//System.err.println("command: " + command);
                switch (command) {
                case 176:
                    return "MasterVolume";
                case 186:
                    return "ChannelConfiguration";
                case 192:
                case 193:
                case 194:
                case 195:
                case 196:
                case 197:
                case 198:
                case 199:
                case 201:
                case 202:
                case 203:
                case 204:
                case 205:
                case 206:
                case 207:
                    return "Tempo (" + StringUtil.toHex2(command) + ")";
                case 208:
                    return "CuePoint";
                case 220:
                    return "Nop2";
                case 221:
                    return "LoopPoint";
                case 222:
                    return "Nop";
                case 223:
                    return "EndOfTrack";
                case 224:
                    return "ChangeVoice";
                case 225:
                    return "ChangeBank";
                case 226:
                    return "Volume";
                case 227:
                    return "Panpot";
                case 228:
                    return "PitchBend";
                case 229:
                    return "ChannelChange";
                case 230:
                    return "Expression";
                case 231:
                    return "PitchBendRange";
                case 234:
                    return "ModurationDepth";
                case 240:
                    return "VoiceEdit";
                case 241:
                    return "ExtendedEdit";
                case 255:
                    return "MachineDependentDepth";
                default:
                    return "Normal (" + StringUtil.toHex2(command) + ")";
                }
            default:
                return "Unknown (" + StringUtil.toHex2(status) + " " + StringUtil.toHex2(command) + ")";
            }
        }
    };

    //-------------------------------------------------------------------------

    /** */
    public interface Filter {
        boolean accept(MfiMessage message);
    }

    //-------------------------------------------------------------------------

    /** カラムの名前 */
    private static final String[] columnNames = {
        "track", "delta", "voice", "event", "data1", "data2"
    };
    /** */
    protected static final int[] widths = { 50, 50, 50, 130, 130, 200 };

    /** */
    private static class SequenceTableModel extends AbstractTableModel {

        /** */
        private final Sequence sequence;

        /** */
        private final List<MfiEvent> events;

        /** トラック番号 */
        private int trackNumber;

        /** */
        private final boolean[] tracks = new boolean[17];

        /** */
        private boolean dispatchChannel = false;
        /** */
//      private Set<Filter> filters = new HashSet<Filter>();

        /** テーブルモデルを構築します． */
        public SequenceTableModel(Sequence sequence) {

            if (sequence.getTracks().length < 1) {
                throw new IllegalArgumentException("no tracks");
            }

            this.sequence = sequence;

            events = new ArrayList<>();

            trackNumber = 0;

            initModel();
        }

        /** */
        public boolean[] getTracks() {
            return tracks;
        }

        /** トラックナンバーを返します． */
        @SuppressWarnings("unused")
        public int getTrackNumber() {
            return trackNumber;
        }

        /** トラックナンバーを返します． */
        public void setTrackNumber(int trackNumber) {
            this.trackNumber = trackNumber;

            initModel();
        }

        /** */
        public void setDispatchChannel(boolean dispatchChannel) {
            this.dispatchChannel = dispatchChannel;

            trackNumber = 0;

            initModel();
        }

        /** */
//      public void addFilter(Filter filter) {
//          this.filters.add(filter);
//          initModel();
//      }

        /** */
//      public void removeFilter(Filter filter) {
//          this.filters.remove(filter);
//          initModel();
//      }

        /** */
        private void initModel() {
            events.clear();

            if (dispatchChannel) {
                // tracks
                for (int i = 0; i < 16; i++) {
                    tracks[i] = false;
                }

                // events, tracks
                for (int t = 0; t < sequence.getTracks().length; t++) {
                    Track track = sequence.getTracks()[t];
                    for (int i = 0; i < track.size(); i++) {
                        MfiEvent event = track.get(i);
                        MfiMessage message = event.getMessage();
                        if (message instanceof NoteMessage) {
                            int voice = ((NoteMessage) message).getVoice();
                            if (t * 4 + voice == trackNumber) {
                                addEvent(event);
                            }
                            tracks[t * 4 + voice] = true;
                        } else if (message instanceof ShortMessage) {
//                          int command = ((ShortMessage) message).getCommand();
                            int data = ((ShortMessage) message).getData();
                            if (message instanceof ChannelMessage) {
                                int voice = (data & 0xc0) >> 6;
                                if (t * 4 + voice == trackNumber) {
                                    addEvent(event);
                                }
                                tracks[t * 4 + voice] = true;
                            } else {
                                if (t * 4 == trackNumber) {
                                    addEvent(event);
                                }
                            }
                        } else {
                            if (t * 4 == trackNumber) {
                                addEvent(event);
                            }
                        }
                    }
                }
            } else {
                // events
                Track track = sequence.getTracks()[trackNumber];
                for (int i = 0; i < track.size(); i++) {
                    MfiEvent event = track.get(i);

                    addEvent(event);
                }

                // tracks
                int trackSize = sequence.getTracks().length;
                for (int i = 0; i < trackSize; i++) {
                    tracks[i] = true;
                }
                for (int i = trackSize; i < 16; i++) {
                    tracks[i] = false;
                }
            }

            fireTableChanged(new TableModelEvent(this));
        }

        /** */
        private void addEvent(MfiEvent event) {
//          MfiMessage message = event.getMessage();
//          if (filters.size() == 0) {
                events.add(event);
//              return;
//          }
//          for (Filter i : filters) {
//              if (i.accept(message)) {
//                  events.add(event);
//                  return;
//              }
//          }
        }

        //---------------------------------------------------------------------

        /** カラム数を取得します． */
        public int getColumnCount() {
            return columnNames.length;
        }

        /** カラム名を取得します． */
        public String getColumnName(int columnIndex) {
            return columnNames[columnIndex];
        }

        /** 行数を取得します． */
        public int getRowCount() {
            return events.size();
        }

        /** 指定したカラム，行にある値を取得します． */
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return rowIndex;
            } else {
                return events.get(rowIndex);
            }
        }

        /** カラムのクラスを取得します． */
        public Class<?> getColumnClass(int columnIndex) {
            return getValueAt(0, columnIndex).getClass();
        }

        /** 指定したセルが編集可能かどうか． */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}

/* */
