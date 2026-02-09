// Kevin Terretaz
// kevinterretaz@gmail.com
/*
20260125
different slider ranges for every channel
initial values based on current display ranges of new image.
better compatibility for 32bit images
right click on slider to set value manually
dynamic slider range to fit new value

I heavilly used AI to learn java
So I guess I have to thank the entire world of programmers that published their code since the begining of coding
I would have never be able to build these without it.

unlicense :
This is free and unencumbered software released into the public domain. Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
In jurisdictions that recognize copyright laws, the author or authors of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of relinquishment in perpetuity of all present and future rights to this software under copyright law.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
For more information, please refer to <http://unlicense.org/>
*/ 
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.LutLoader;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.LUT;
import javax.swing.*;
import javax.swing.border.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Color;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;  
// new Channels_and_Contrast().run()
// null
public class Channels_and_Contrast implements PlugIn {
    private static final String FRAME_TITLE = "Channels & Contrast";
    final String save_Loc = IJ.getDirectory("luts")+"/LUT_Palette_Manager.csv";
    final int LUT_COUNT = 5;    
    public static final String LOC_KEY = "Channels_and_Contrast.loc";

    int N_CHANNELS, BIT_DEPTH, LAST_MODE, LAST_CHANNEL;
    double[] SLIDERS_RANGE_MIN, SLIDERS_RANGE_MAX;
    JFrame FRAME;
    JPanel MAIN_PANEL;
    JLabel[] MIN_LABELS, MAX_LABELS;
    JSlider[] MIN_SLIDERS, MAX_SLIDERS;
    JCheckBox[] CHECKBOXES;
    TitledBorder[] CHANNEL_BORDERS;
    JToggleButton GRAYSCALE_BUTTON, COLOR_BUTTON, COMPOSITE_BUTTON;
    ButtonGroup COMPOSITE_MODE_GROUP;
    ImagePlus IMP = null;
    LUT[] LAST_LUTS, LUTS;
    boolean UPDATING = false;
    javax.swing.Timer TIMER;
    long LAST_UPDATE = 0;
    long REFRESH_INTERVAL = 500;
    boolean PLUGIN_LOCKED = false;
    boolean[] LAST_ACTIVE_CHANNELS;
    String[] MORE_MENU = new String[] {"Same contrast to all opened images", "Split Channels", "Merge Channels...", "Arrange Channels...","Channels Tool...", "Brightness/Contrast..."};
    double SATURATED_PIXELS = Prefs.get("Channels_and_Contrast.saturated", 0.1);
    private boolean ERROR_STATE = false;
    private long LAST_ERROR_TIME = 0;
    private static final long RETRY_INTERVAL = 2000;
    private static final int SLIDER_SCALE = 1000; // For 32-bit decimal precision
    
    boolean debug = false;

    public void run(String arg) {
        // If the plugin is already running, just bring it to front
        for (Frame open_Frame : JFrame.getFrames()) {
            if (FRAME_TITLE.equals(open_Frame.getTitle()) && open_Frame.isVisible()) {
                open_Frame.toFront();
                return;
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    create_Frame();
                }
                catch (Throwable t) {
                    IJ.handleException(t);
                }
            }
        });
        // set a TIMER to listen for any relevant changes to update the UI
        // also check if the plugin is still opened to prevent error loops if the main ui broke
        TIMER = new javax.swing.Timer(50, new ActionListener() {
            public void actionPerformed(ActionEvent e) {boolean is_Plugin_Running = false;
                for (Frame open_Frame : JFrame.getFrames()) {
                    if (FRAME_TITLE.equals(open_Frame.getTitle()) && open_Frame.isVisible()) {
                        is_Plugin_Running = true;
                        break;
                    }
                }
                if (!is_Plugin_Running) {
                    TIMER.stop();
                }
                if (ERROR_STATE) {
                    long now = System.currentTimeMillis();
                    if (now - LAST_ERROR_TIME >= RETRY_INTERVAL) ERROR_STATE = false;
                    else return;
                }
                try {
                    if (check_Active_Image()){
                        if (!UPDATING && something_Changed()) update_UI();
                    }
                } catch (Throwable t) {
                    IJ.handleException(t);
                    ERROR_STATE = true;
                    LAST_ERROR_TIME = System.currentTimeMillis();
                }
            }
        });
        TIMER.start();
    }

    void create_Frame() {
        FRAME = new JFrame("Channels & Contrast");
        FRAME.setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        FRAME.setLayout(new BorderLayout());
        MAIN_PANEL = new JPanel();
        MAIN_PANEL.setLayout(new BoxLayout(MAIN_PANEL, BoxLayout.Y_AXIS));
        FRAME.add(MAIN_PANEL, BorderLayout.CENTER);
        check_Active_Image();
        Point loc = Prefs.getLocation(LOC_KEY);
        if (loc!=null) FRAME.setLocation(loc);
        // TODO add a check if the saved loc fits the screen size
        else GUI.centerOnImageJScreen(FRAME);
        FRAME.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        FRAME.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                TIMER.stop();
                Prefs.saveLocation(LOC_KEY, FRAME.getLocation());
            }
        });
        FRAME.setVisible(true);
    }
    
    // check if a new image as been selected, if it's compatible with the plugin
    // if not, put the FRAME in idle state.
    boolean check_Active_Image() {
        ImagePlus imp = WindowManager.getCurrentImage();
        // do nothing when a plugin is already working on image
        if (imp != null) {
            if (imp.isLocked() || imp.isLockedByAnotherThread()){
                UPDATING = true;
                return false;
            }
        }
        else UPDATING = false;
        boolean no_Image, is_RGB, is_weird_Stack;
        no_Image = (imp == null);
        is_RGB = imp == null ? false : (imp.getBitDepth() == 24);
        is_weird_Stack = imp == null ? false : (imp.getNChannels()>1 && !imp.isComposite());

        if (no_Image || is_RGB || is_weird_Stack) {
            if (PLUGIN_LOCKED) return false;
            IMP = imp;
            MAIN_PANEL.removeAll();
            UPDATING = false;
            FRAME.setSize(300, 80);
            FRAME.setResizable(false);
            JLabel label = new JLabel("Waiting for a supported image");
            JPanel temp = new JPanel();
            temp.add(label);
            MAIN_PANEL.add(temp);
            MAIN_PANEL.revalidate();
            MAIN_PANEL.repaint();
            PLUGIN_LOCKED = true;
            LAST_LUTS = null;
            return false;
        }
        if (imp != null && imp != IMP) {
            long now = System.currentTimeMillis();
            if (now - LAST_UPDATE < REFRESH_INTERVAL) return false; // in case a script is switching rapidly through opened images
            LAST_UPDATE = now;
            IMP = imp;
            PLUGIN_LOCKED = false;
            FRAME.setResizable(true);
            setup_Current_Image();
        }
        return true;
    }
    // save current state for the something_Changed() method to compare
    void save_Current_State() {
        if (IMP.isComposite()) LAST_LUTS = ((CompositeImage)IMP).getLuts();
        else LAST_LUTS = ((ImagePlus)IMP).getLuts();
        LUTS = LAST_LUTS;
        BIT_DEPTH = IMP.getBitDepth();
        LAST_MODE = IMP.getCompositeMode();
        LAST_CHANNEL = IMP.getC();
        if (IMP.isComposite()) {
            boolean[] active = ((CompositeImage)IMP).getActiveChannels();
            LAST_ACTIVE_CHANNELS = active.clone();
        }
    }

    // detect any relevant change in the current image to update the plugin
    boolean something_Changed() {
        boolean something_Changed = false;
        String whats_detected = "";
        // no Image or RGB
        if (PLUGIN_LOCKED) return false; 
        // something cooking
        if (IMP.isLocked() || IMP.isLockedByAnotherThread()) return false;
        // display mode
        if (IMP.getCompositeMode() != LAST_MODE) {
            something_Changed = true;
            whats_detected += " display mode changed";
        }
        // current channel
        if (IMP.getC() != LAST_CHANNEL) {
            something_Changed = true;
            whats_detected += " current channel changed";
        }
        // visible channels
        if (IMP.isComposite()) 
            if (((CompositeImage)IMP).getActiveChannels() != LAST_ACTIVE_CHANNELS) {
                something_Changed = true;
                whats_detected += " active channels changed";
            }
        // channel count changed
        if (IMP.getNChannels() != N_CHANNELS) {
            something_Changed = true;
            whats_detected += " channel count changed";
        }
        // bit depth changed
        if (IMP.getBitDepth() != BIT_DEPTH) {
            something_Changed = true;
            whats_detected += " bit depth changed";
        }
        LUT[] luts = null;
        if (IMP.isComposite()) luts = ((CompositeImage)IMP).getLuts();
        else luts = ((ImagePlus)IMP).getLuts();
        for (int k=0; k<luts.length; k++) {
            // new contrast
            if (luts[k].min != LAST_LUTS[k].min || luts[k].max != LAST_LUTS[k].max) {
                something_Changed = true;
                whats_detected += " contrast changed";
            }
            // new LUT
            if (get_Channel_Color(k, luts) != get_Channel_Color(k, LAST_LUTS)) {
                something_Changed = true;
                whats_detected += " LUTs changed";
            }
        }
        if (debug && something_Changed) IJ.log(whats_detected);
        return something_Changed;
    }

    void setup_Current_Image() {
        // Setup
        UPDATING = true;
        MAIN_PANEL.removeAll();
        N_CHANNELS = IMP.getNChannels();
        BIT_DEPTH = IMP.getBitDepth();
        MIN_SLIDERS = new JSlider[N_CHANNELS];
        MAX_SLIDERS = new JSlider[N_CHANNELS];
        MIN_LABELS = new JLabel[N_CHANNELS];
        MAX_LABELS = new JLabel[N_CHANNELS];
        CHECKBOXES = new JCheckBox[N_CHANNELS];
        CHANNEL_BORDERS = new TitledBorder[N_CHANNELS];
        SLIDERS_RANGE_MIN = new double[N_CHANNELS];
        SLIDERS_RANGE_MAX = new double[N_CHANNELS];
        if (IMP.isComposite()) 
            LUTS = ((CompositeImage)IMP).getLuts();
        else LUTS = ((ImagePlus)IMP).getLuts();
        get_Sliders_Range();
        // build UI
        if (N_CHANNELS > 1) MAIN_PANEL.add(get_Display_Mode_Panel());
        for (int channel = 0; channel < N_CHANNELS; channel++) MAIN_PANEL.add(get_Channel_Panel(channel));
        MAIN_PANEL.add(get_Bottom_Panel());
        UPDATING = false;
        update_UI();
        MAIN_PANEL.revalidate();
        MAIN_PANEL.repaint();
        FRAME.pack();
        save_Current_State();
    }


    void update_UI(){
        // big changes?
        if (IMP.getNChannels() != N_CHANNELS || IMP.getBitDepth() != BIT_DEPTH) {
            if (debug) IJ.log("big change update_UI");
            setup_Current_Image();
            return;
        }
        
        // Sliders
        UPDATING = true;
        LUT[] luts = null;
        if (IMP.isComposite()) luts = ((CompositeImage)IMP).getLuts();
        else luts = ((ImagePlus)IMP).getLuts();
        for (int channel = 0; channel < N_CHANNELS; channel++) {
            // update slider ranges
            double current_range_min = (BIT_DEPTH == 32)? SLIDERS_RANGE_MIN[channel] * SLIDER_SCALE : SLIDERS_RANGE_MIN[channel];
            double current_range_max = (BIT_DEPTH == 32)? SLIDERS_RANGE_MAX[channel] * SLIDER_SCALE : SLIDERS_RANGE_MAX[channel];
            MIN_SLIDERS[channel].setMinimum((int)current_range_min);
            MIN_SLIDERS[channel].setMaximum((int)current_range_max);
            MAX_SLIDERS[channel].setMinimum((int)current_range_min);
            MAX_SLIDERS[channel].setMaximum((int)current_range_max);
            double lut_min = luts[channel].min;
            double lut_max = luts[channel].max;
            // adapt range to current contrast
            if (lut_min < SLIDERS_RANGE_MIN[channel]) {
                double new_range_min = (BIT_DEPTH == 32)? lut_min * SLIDER_SCALE : lut_min;
                SLIDERS_RANGE_MIN[channel] = lut_min;
                MAX_SLIDERS[channel].setMinimum((int)new_range_min);
            }
            if (lut_max > SLIDERS_RANGE_MAX[channel]) {
                double new_range_max = (BIT_DEPTH == 32)? lut_max * SLIDER_SCALE : lut_max;
                SLIDERS_RANGE_MAX[channel] = lut_max;
                MAX_SLIDERS[channel].setMaximum((int)new_range_max);
            }
            // Sync slider/label controls to updated values
            if (BIT_DEPTH == 32) {
                MIN_SLIDERS[channel].setValue((int)(lut_min * SLIDER_SCALE));
                MIN_LABELS[channel].setText(String.format("Min: %.2f", lut_min));
                MAX_SLIDERS[channel].setValue((int)(lut_max * SLIDER_SCALE));
                MAX_LABELS[channel].setText(String.format("Max: %.2f", lut_max));
            } else {
                MIN_SLIDERS[channel].setValue((int)lut_min);
                MIN_LABELS[channel].setText("Min: " + (int)lut_min);
                MAX_SLIDERS[channel].setValue((int)lut_max);
                MAX_LABELS[channel].setText("Max: " + (int)lut_max);
            }
            
            CHANNEL_BORDERS[channel].setTitleColor(get_Channel_Color(channel, luts));
            MAIN_PANEL.repaint();
        }
        // CHECKBOXES
        if (IMP.isComposite()) {
            boolean[] active = ((CompositeImage)IMP).getActiveChannels();
            for (int i=0; i<CHECKBOXES.length; i++) CHECKBOXES[i].setSelected(active[i]);
        // Composite buttons
            int mode = IMP.getCompositeMode();
            GRAYSCALE_BUTTON.setForeground(UIManager.getColor("Button.foreground"));
            GRAYSCALE_BUTTON.setBackground(UIManager.getColor("Button.background"));
            COLOR_BUTTON.setForeground(UIManager.getColor("Button.foreground"));
            COLOR_BUTTON.setBackground(UIManager.getColor("Button.background"));
            COMPOSITE_BUTTON.setForeground(UIManager.getColor("Button.foreground"));
            COMPOSITE_BUTTON.setBackground(UIManager.getColor("Button.background"));
            if (mode == IJ.GRAYSCALE) {
                GRAYSCALE_BUTTON.setSelected(true);
                GRAYSCALE_BUTTON.setBackground(new Color(255,180,0));
                GRAYSCALE_BUTTON.setForeground(new Color(0,0,0));
            }
            if (mode == IJ.COLOR) {
                COLOR_BUTTON.setSelected(true);
                COLOR_BUTTON.setBackground(new Color(255,180,0));
                COLOR_BUTTON.setForeground(new Color(0,0,0));
            }
            if (mode == IJ.COMPOSITE) {
                COMPOSITE_BUTTON.setSelected(true);
                COMPOSITE_BUTTON.setBackground(new Color(255,180,0));
                COMPOSITE_BUTTON.setForeground(new Color(0,0,0));
            }        
        }
        UPDATING = false;
        save_Current_State();
    }

    JPanel get_Channel_Panel(int channel) {
        final int channel_Index = channel;
        double lut_min = LUTS[channel].min;
        double lut_max = LUTS[channel].max;
        double min = Math.max(SLIDERS_RANGE_MIN[channel], Math.min(SLIDERS_RANGE_MAX[channel], lut_min));
        double max = Math.max(SLIDERS_RANGE_MIN[channel], Math.min(SLIDERS_RANGE_MAX[channel], lut_max));
        if (min > max) min = max;
        Color color = get_Channel_Color(channel, LUTS);
        
        int slider_min_val, slider_max_val, min_val, max_val;
        if (BIT_DEPTH == 32) {
            slider_min_val = (int)(SLIDERS_RANGE_MIN[channel] * SLIDER_SCALE);
            slider_max_val = (int)(SLIDERS_RANGE_MAX[channel] * SLIDER_SCALE);
            min_val = (int)(min * SLIDER_SCALE);
            max_val = (int)(max * SLIDER_SCALE);
        } else {
            slider_min_val = (int)SLIDERS_RANGE_MIN[channel];
            slider_max_val = (int)SLIDERS_RANGE_MAX[channel];
            min_val = (int)min;
            max_val = (int)max;
        }
        
        // MIN
        MIN_SLIDERS[channel] = new JSlider(JSlider.HORIZONTAL, slider_min_val, slider_max_val, min_val);
        MIN_SLIDERS[channel].setPaintTicks(false);
        MIN_SLIDERS[channel].setPaintLabels(false);
        if (BIT_DEPTH == 32) {
            MIN_LABELS[channel] = new JLabel(String.format("Min: %.2f", min));
        } else {
            MIN_LABELS[channel] = new JLabel("Min: " + (int)min);
        }
        MIN_LABELS[channel].setPreferredSize(new Dimension(80, (int)MIN_LABELS[channel].getPreferredSize().height));
        MIN_SLIDERS[channel].addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int new_Min;
                    int value = ((JSlider)e.getSource()).getValue();
                    if (BIT_DEPTH == 32) {
                        double userInput = IJ.getNumber("Set min: ", (value / (double)SLIDER_SCALE));
                        if (userInput == IJ.CANCELED) return;
                        new_Min = (int)(userInput * SLIDER_SCALE);  // Convert back to slider scale
                    } else {
                        new_Min = (int)IJ.getNumber("Set min: ", value);
                        if (new_Min == IJ.CANCELED) return;
                    }
                    int scale = (BIT_DEPTH == 32) ? SLIDER_SCALE : 1;
                    if (new_Min < SLIDERS_RANGE_MIN[channel] * scale) {
                        SLIDERS_RANGE_MIN[channel] = (BIT_DEPTH == 32) ? new_Min / (double)SLIDER_SCALE : new_Min;
                        ((JSlider)e.getSource()).setMinimum(new_Min);
                    }             
                    ((JSlider)e.getSource()).setValue(new_Min);
                }
            }
        });
        MIN_SLIDERS[channel].addMouseWheelListener(e -> {
            int step = (slider_max_val - slider_min_val) / 500;
            if (step < 1) step = 1;
            ((JSlider)e.getSource()).setValue(((JSlider)e.getSource()).getValue() - (e.getWheelRotation() * step));
        });
        MIN_SLIDERS[channel].addChangeListener(e -> {
            if (UPDATING) return;
            for (int i = 0; i < MIN_SLIDERS.length; i++) {
                if (((JSlider)e.getSource()) == MIN_SLIDERS[i]) {
                    if (BIT_DEPTH == 32) {
                        double val = ((JSlider)e.getSource()).getValue() / (double)SLIDER_SCALE;
                        MIN_LABELS[channel].setText(String.format("Min: %.2f", val));
                    } else MIN_LABELS[channel].setText("Min: " + ((JSlider)e.getSource()).getValue());
                    adjust_Contrast(i);
                    break;
                }
            }
        });
        // MAX
        MAX_SLIDERS[channel] = new JSlider(JSlider.HORIZONTAL, slider_min_val, slider_max_val, max_val);
        MAX_SLIDERS[channel].setPaintTicks(false);
        MAX_SLIDERS[channel].setPaintLabels(false);
        if (BIT_DEPTH == 32) {
            MAX_LABELS[channel] = new JLabel(String.format("Max: %.2f", max));
        } else {
            MAX_LABELS[channel] = new JLabel("Max: " + (int)max);
        }
        MAX_LABELS[channel].setPreferredSize(new Dimension(80, (int)MAX_LABELS[channel].getPreferredSize().height));
        MAX_SLIDERS[channel].addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int new_Max;
                    int value = ((JSlider)e.getSource()).getValue();
                    if (BIT_DEPTH == 32) {
                        double userInput = IJ.getNumber("Set max: ", (value / (double)SLIDER_SCALE));
                        if (userInput == IJ.CANCELED) return;
                        new_Max = (int)(userInput * SLIDER_SCALE);  // Convert back to slider scale
                    } else {
                        new_Max = (int)IJ.getNumber("Set max: ", value);
                        if (new_Max == IJ.CANCELED) return;
                    }
                    int scale = (BIT_DEPTH == 32) ? SLIDER_SCALE : 1;
                    if (new_Max > SLIDERS_RANGE_MAX[channel] * scale) {
                        SLIDERS_RANGE_MAX[channel] = (BIT_DEPTH == 32) ? new_Max / (double)SLIDER_SCALE : new_Max;
                        ((JSlider)e.getSource()).setMaximum(new_Max);
                    }             
                    ((JSlider)e.getSource()).setValue(new_Max);
                }
            }
        });
        MAX_SLIDERS[channel].addMouseWheelListener(e -> {
            int step = (slider_max_val - slider_min_val) / 500;
            if (step < 1) step = 1;
            ((JSlider)e.getSource()).setValue(((JSlider)e.getSource()).getValue() - (e.getWheelRotation() * step));
        });
        MAX_SLIDERS[channel].addChangeListener(e -> {
            if (UPDATING) return;
            for (int i = 0; i < MAX_SLIDERS.length; i++) {
                if (((JSlider)e.getSource()) == MAX_SLIDERS[i]) {
                    if (BIT_DEPTH == 32) {
                        double val = ((JSlider)e.getSource()).getValue() / (double)SLIDER_SCALE;
                        MAX_LABELS[channel].setText(String.format("Max: %.2f", val));
                    } else {
                        MAX_LABELS[channel].setText("Max: " + ((JSlider)e.getSource()).getValue());
                    }
                    adjust_Contrast(i);
                    break;
                }
            }
        });
        // BUTTONS
        JButton auto_Button = new JButton("Auto");
        auto_Button.setMargin(new Insets(2, 2, 2, 2));
        auto_Button.setMaximumSize(new Dimension(80, 20));
        auto_Button.setToolTipText("<html>Enhance active channel : only based on current slice.<br>"+
                "Adjust the contrast to " + SATURATED_PIXELS + "% of saturated pixels.<br>"+
                "You can change the % value in the plugin options");
        auto_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    public void run() {
                        if (IMP.isComposite()) IMP.setC(channel_Index+1);
                        IJ.run("Enhance Contrast...", "saturated=" + SATURATED_PIXELS);
                    }
                }).start();
            }
        });
        JButton minmax_Button = new JButton("Min/Max");
        minmax_Button.setMargin(new Insets(2, 2, 2, 2));
        minmax_Button.setMaximumSize(new Dimension(80, 20));
        minmax_Button.setToolTipText("<html>Resets contrast to channel min and max.<br>If multiple slices or frames, based on the entire channel stack.");
        minmax_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    public void run() {
                        if (IMP.isComposite()) IMP.setC(channel_Index+1);
                        IJ.run("Reset Display", "channel=" + (channel_Index+1));
                    }
                }).start();
            }
        });
        // CHECKBOXES
        if (IMP.isComposite()) {
            boolean[] act = ((CompositeImage)IMP).getActiveChannels();
            CHECKBOXES[channel] = new JCheckBox("  ", act[channel]);
            CHECKBOXES[channel].addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                    if (((CompositeImage)IMP).getMode()==IJ.COMPOSITE) {
                        boolean[] active = ((CompositeImage)IMP).getActiveChannels();
                        active[channel_Index] = ((JCheckBox)e.getSource()).isSelected();
                    }
                    else IMP.setC(channel_Index+1);
                    IMP.updateAndDraw();
                    update_UI();
                }
            });
        }
        // ASSEMBLE
        JPanel channel_Panel = new JPanel(new BorderLayout());
        CHANNEL_BORDERS[channel] = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),("Channel " + (channel+1)));
        CHANNEL_BORDERS[channel].setTitleColor(color);
        channel_Panel.setBorder(CHANNEL_BORDERS[channel]);
        JPanel sliders_Panel = new JPanel(new GridLayout(2,1));
        JPanel min_Panel = new JPanel(new BorderLayout());
        min_Panel.add(MIN_LABELS[channel], BorderLayout.WEST);
        min_Panel.add(MIN_SLIDERS[channel], BorderLayout.CENTER);
        JPanel max_Panel = new JPanel(new BorderLayout());
        max_Panel.add(MAX_LABELS[channel], BorderLayout.WEST);
        max_Panel.add(MAX_SLIDERS[channel], BorderLayout.CENTER);
        sliders_Panel.add(min_Panel);
        sliders_Panel.add(max_Panel);
        JPanel buttons_Panel = new JPanel();
        buttons_Panel.setLayout(new BoxLayout(buttons_Panel, BoxLayout.Y_AXIS));
        buttons_Panel.add(auto_Button);
        buttons_Panel.add(Box.createRigidArea(new Dimension(0, 3)));
        buttons_Panel.add(minmax_Button);
        if (IMP.isComposite()) channel_Panel.add(CHECKBOXES[channel], BorderLayout.WEST);
        channel_Panel.add(sliders_Panel, BorderLayout.CENTER);
        channel_Panel.add(buttons_Panel, BorderLayout.EAST);
        return channel_Panel;
    }

    void adjust_Contrast(int index) {
        if (UPDATING || IMP == null) return;
        // Ensure correct channel is selected if in composite (not IJ.COMPOSITE mode)
        if (IMP.isComposite() 
                && IMP.getCompositeMode() != IJ.COMPOSITE 
                && IMP.getChannel() != index+1 ) 
            IMP.setC(index+1);
        // Get min/max from channel sliders
        double min, max;
        if (BIT_DEPTH == 32) {
            min = MIN_SLIDERS[index].getValue() / (double)SLIDER_SCALE;
            max = MAX_SLIDERS[index].getValue() / (double)SLIDER_SCALE;
        } else {
            min = (double)MIN_SLIDERS[index].getValue();
            max = (double)MAX_SLIDERS[index].getValue();
        }
        // Prevent min exceeding max
        if (min >= max) min = max;
        // If composite & not grayscale: update LUT for this channel, apply to image
        // the grayscale mode is broken and shows current LUT instead with this method.
        if (IMP.isComposite() && IMP.getCompositeMode() != IJ.GRAYSCALE){  
            LUTS[index] = new LUT(LUTS[index].getColorModel(), min, max);
            ((CompositeImage)IMP).setLuts(LUTS);
        }
        // Otherwise, set display range directly for non-composite or grayscale
        else {
            IMP.setDisplayRange(min, max);
        }
        IMP.updateChannelAndDraw();
        save_Current_State();
        update_UI();
    }

    void get_Sliders_Range(){
        // Use current display range per channel instead of image stats        
        for (int i = 0; i < N_CHANNELS; i++) {
            double current_min = LUTS[i].min;
            double current_max = LUTS[i].max;
            
            if (BIT_DEPTH == 8) {
                SLIDERS_RANGE_MIN[i] = Math.min(0, current_min);
                SLIDERS_RANGE_MAX[i] = Math.max(255, current_max);
            } 
            else if (BIT_DEPTH == 16) {
                SLIDERS_RANGE_MIN[i] = Math.min(0, current_min);
                // Set slider max based on current display max, with sensible defaults
                if (current_max <= 255) {
                    SLIDERS_RANGE_MAX[i] = 255;
                } else if (current_max <= 4095) {
                    SLIDERS_RANGE_MAX[i] = 4095;
                } else if (current_max <= 16383) {
                    SLIDERS_RANGE_MAX[i] = 16383;
                } else {
                    SLIDERS_RANGE_MAX[i] = Math.max(65535, current_max);;
                }
            } 
            else if (BIT_DEPTH == 32) {
                // For 32-bit, use current display range with some padding
                SLIDERS_RANGE_MIN[i] = Math.floor(current_min);
                SLIDERS_RANGE_MAX[i] = Math.ceil(current_max);
                if (SLIDERS_RANGE_MIN[i] > SLIDERS_RANGE_MAX[i]) {
                    SLIDERS_RANGE_MIN[i] = 0;
                    SLIDERS_RANGE_MAX[i] = 1;
                }
            }
        }
    }

    JPanel get_Display_Mode_Panel() {
        JPanel composite_Mode_Panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        composite_Mode_Panel.setBorder(BorderFactory.createEtchedBorder());
        GRAYSCALE_BUTTON = new JToggleButton("Grayscale");
        GRAYSCALE_BUTTON.setToolTipText("Displays individual channels in grayscale");
        COLOR_BUTTON = new JToggleButton("Color");
        COLOR_BUTTON.setToolTipText("Displays individual channels with their current LUTs");
        COMPOSITE_BUTTON = new JToggleButton("Composite");
        COMPOSITE_BUTTON.setToolTipText("Displays the sum of all channels as a composite overlay");
        COMPOSITE_MODE_GROUP = new ButtonGroup();
        COMPOSITE_MODE_GROUP.add(COLOR_BUTTON);
        COMPOSITE_MODE_GROUP.add(COMPOSITE_BUTTON);
        COMPOSITE_MODE_GROUP.add(GRAYSCALE_BUTTON);
        GRAYSCALE_BUTTON.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)IMP).setDisplayMode(IJ.GRAYSCALE);
                save_Current_State();update_UI(); }
        });
        COLOR_BUTTON.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)IMP).setDisplayMode(IJ.COLOR);
                save_Current_State();update_UI(); }
        });
        COMPOSITE_BUTTON.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)IMP).setDisplayMode(IJ.COMPOSITE);
                save_Current_State();update_UI(); }
        });
        composite_Mode_Panel.add(COMPOSITE_BUTTON);
        composite_Mode_Panel.add(COLOR_BUTTON);
        composite_Mode_Panel.add(GRAYSCALE_BUTTON);
        return composite_Mode_Panel;
    }
    
    JPanel get_Bottom_Panel(){
        JPanel bottom_Panel = new JPanel(new BorderLayout());
        JPanel east_Panel = new JPanel();
        east_Panel.add(get_Palettes_Button());
        east_Panel.add(get_Fav_LUTs_Button());
        east_Panel.add(get_More_Button());
        if (N_CHANNELS > 1) bottom_Panel.add(get_All_Buttons_Panel(), BorderLayout.WEST);
        bottom_Panel.add(east_Panel, BorderLayout.EAST);
        return bottom_Panel;
    }

    JPanel get_All_Buttons_Panel() {
        JButton all_Minmax_Button = new JButton("Min/Max all");
        all_Minmax_Button.setMargin(new Insets(2, 5, 2, 5));
        all_Minmax_Button.setToolTipText("<html>Resets contrast to channels min and max.<br>If multiple slices or frames, based on entire channel stack.");
        all_Minmax_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    public void run() {
                       UPDATING = true;
                       ((CompositeImage)IMP).setDisplayMode(IJ.COMPOSITE);
                       IJ.run("Reset Display", "channel=0");
                       UPDATING = false;
                    }
                }).start();
            }
        });
        JButton all_Auto_Button = new JButton("Auto all");
        all_Auto_Button.setMargin(new Insets(2, 10, 2, 10));
        all_Auto_Button.setToolTipText("<html>Enhance all channels : only based on current slice.<br>"+
                "Adjust the contrast to " + SATURATED_PIXELS + "% of saturated pixels.<br>"+
                "You can change the % value in the plugin options");
        all_Auto_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    public void run() {
                        if (IMP == null) return;
                        UPDATING = true;
                        int N_CHANNELS = IMP.getNChannels();
                        int previous_Channel = IMP.getC();
                        for (int channel = 1; channel <= N_CHANNELS; channel++) {
                            IJ.showProgress((double) (channel / N_CHANNELS));
                            IMP.setC(channel);
                            IJ.run("Enhance Contrast...", "saturated=" + SATURATED_PIXELS);
                        }
                        IMP.setC(previous_Channel);
                        IMP.updateAndDraw();
                        IJ.showProgress((double)1.0);
                        UPDATING = false;
                    }
                }).start();
            }
        });
        JButton bug = new JButton("bug");
        bug.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                debugging();
            }
        });
        JPanel all_Button_Panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        all_Button_Panel.add(all_Auto_Button);
        all_Button_Panel.add(all_Minmax_Button);
        if (debug) all_Button_Panel.add(bug);
        return all_Button_Panel;
    }

    private void debugging() {
        IJ.log("");
        IJ.log("IMP " + IMP);
        IJ.log("LAST_CHANNEL " + LAST_CHANNEL);
        IJ.log("BIT_DEPTH " + BIT_DEPTH);
        IJ.log("UPDATING " + UPDATING);
        IJ.log("SLIDERS_RANGE_MIN " + SLIDERS_RANGE_MIN);
        IJ.log("SLIDERS_RANGE_MAX " + SLIDERS_RANGE_MAX);
        for (int i = 0; i < N_CHANNELS; i++) IJ.log("MAX_SLIDERS " + i + " " + MAX_SLIDERS[i].getValue());
        for (int i = 0; i < N_CHANNELS; i++) IJ.log("MIN_SLIDERS " + i + " " + MIN_SLIDERS[i].getValue());
    }

    public JButton get_Palettes_Button() { 
        JButton show_LUT_Sets_Button = new JButton(make_4_Mini_LUTs_Icon(40, 16));
        show_LUT_Sets_Button.setMargin(new Insets(2, 2, 2, 2));
        show_LUT_Sets_Button.setToolTipText("LUT Palettes");
        show_LUT_Sets_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean no_Sets = false;
                Object[][] sets = load_Sets_From_File(save_Loc);
                if (sets == null || sets.length == 0) no_Sets = true;
                JPopupMenu popup = new JPopupMenu();
                JPanel palettes_Panel = new JPanel();
                if (!no_Sets) {
                    palettes_Panel.setLayout(new BoxLayout(palettes_Panel, BoxLayout.Y_AXIS));
                    for (int i = 0; i < sets.length; i++) {
                        JPanel icons_Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
                        icons_Row.setBackground(Color.darkGray);
                        icons_Row.setToolTipText((String)sets[i][0]);
                        java.util.List<String> lut_Names = new java.util.ArrayList<String>();
                        final java.util.List<JLabel> icons_List = new java.util.ArrayList<JLabel>();
                        for (int c = 1; c <= LUT_COUNT; c++) {
                            Object icon_Obj = sets[i][c];
                            String lut_Name = (icon_Obj instanceof ImageIcon) ? get_LUT_Name_At(icon_Obj) : "Grays";
                            lut_Names.add(lut_Name);
                            ImageIcon icon = (icon_Obj instanceof ImageIcon) ? (ImageIcon)icon_Obj : null;
                            if (icon != null) {
                                JLabel label = new JLabel(icon);
                                icons_List.add(label);
                                icons_Row.add(label);
                            }
                        }
                        // MouseListener handles both drag-to-swap and apply on click
                        final int row_Index = i;
                        icons_Row.setTransferHandler(null); // disable any internal Swing drag support
                        icons_Row.addMouseListener(new MouseAdapter() {
                            int pressed_Index = -1;
                            @Override
                            public void mousePressed(MouseEvent event) {
                                for (int k = 0; k < icons_List.size(); k++) {
                                    if (icons_List.get(k).getBounds().contains(event.getPoint())) {
                                        pressed_Index = k;
                                        break;
                                    }
                                }
                            }
                            @Override
                            public void mouseReleased(MouseEvent event) {
                                if (pressed_Index == -1) return;
                                for (int k = 0; k < icons_List.size(); k++) {
                                    if (icons_List.get(k).getBounds().contains(event.getPoint())) {
                                        if (SwingUtilities.isLeftMouseButton(event) && pressed_Index == k) {
                                            // Click: apply LUTs, using the CURRENT lut_Names!
                                            ImagePlus imp = WindowManager.getCurrentImage();
                                            if (imp == null) return;
                                            int N_CHANNELS = imp.getNChannels();
                                            for (int ch = 0; ch < N_CHANNELS; ch++) {
                                                String lut_Name = (ch < lut_Names.size()) ? lut_Names.get(ch) : "Grays";
                                                apply_LUT(imp, lut_Name, ch + 1);
                                            }
                                            if (imp.isComposite()) ((CompositeImage)IMP).setDisplayMode(IJ.COMPOSITE);
                                            imp.updateAndDraw();
                                        } else if (pressed_Index != k) {
                                            // Drag & swap
                                            Icon temp_Icon = icons_List.get(pressed_Index).getIcon();
                                            icons_List.get(pressed_Index).setIcon(icons_List.get(k).getIcon());
                                            icons_List.get(k).setIcon(temp_Icon);
                                            String temp_Name = lut_Names.get(pressed_Index);
                                            lut_Names.set(pressed_Index, lut_Names.get(k));
                                            lut_Names.set(k, temp_Name);
                                            Object temp_Obj = sets[row_Index][pressed_Index + 1];
                                            sets[row_Index][pressed_Index + 1] = sets[row_Index][k + 1];
                                            sets[row_Index][k + 1] = temp_Obj;
                                            icons_Row.repaint();
                                            export_Lut_Sets_From_Sets_Array(sets, IJ.getDirectory("luts")+"/LUT_Palette_Manager.csv");
                                        }
                                        break;
                                    }
                                }
                                pressed_Index = -1;
                            }
                            @Override
                            public void mouseEntered(MouseEvent evt) {
                                icons_Row.setBackground(new Color(220, 240, 255));
                            }
                            @Override
                            public void mouseExited(MouseEvent evt) {
                                icons_Row.setBackground(Color.darkGray);
                            }
                        });
                        palettes_Panel.add(icons_Row);
                    }
                }
                JMenuItem open_Mana = new JMenuItem("Open LUTs manager");
                open_Mana.addActionListener(event -> IJ.run("LUTs Manager"));
                open_Mana.setEnabled(true);
                if (!no_Sets) popup.add(palettes_Panel);
                popup.add(open_Mana);
                popup.show(show_LUT_Sets_Button, 10, show_LUT_Sets_Button.getHeight());
            }
        });
        return show_LUT_Sets_Button;
    }

    public JButton get_Fav_LUTs_Button() { 
        JButton fav_LUTs_Button = new JButton(make_LUT_Palette_Icon(40, 16));
        fav_LUTs_Button.setToolTipText("Favorite LUTs");
        fav_LUTs_Button.setMargin(new Insets(2, 2, 2, 2));
        fav_LUTs_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPopupMenu popup = new JPopupMenu();
                    JPanel fav_Luts_Panel = new JPanel();
                    fav_Luts_Panel.setLayout(new BoxLayout(fav_Luts_Panel, BoxLayout.Y_AXIS));
                    String[] favorite_Luts = Prefs.get("LUTs_Finder.Favorites", "Grays").split(",");
                    Arrays.sort(favorite_Luts);
                    for (int i = 0; i < favorite_Luts.length; i++) {
                        final String name = favorite_Luts[i];
                        if (LutLoader.getLut(name) == null) continue;
                        JPanel icons_Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
                        icons_Row.setBackground(Color.darkGray);
                        icons_Row.setToolTipText(name);
                        icons_Row.add(new JLabel(get_LUT_Icon(name)));
                        icons_Row.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent evt) {
                                ImagePlus imp = WindowManager.getCurrentImage();
                                apply_LUT(imp, name, imp.getChannel());
                                imp.updateAndDraw();
                            }
                            @Override
                            public void mouseEntered(MouseEvent evt) {
                                icons_Row.setBackground(new Color(220, 240, 255));
                            }
                            @Override
                            public void mouseExited(MouseEvent evt) {
                                icons_Row.setBackground(Color.darkGray);
                            }
                        });
                        fav_Luts_Panel.add(icons_Row);
                    }
                JMenuItem manager = new JMenuItem("Open LUTs Manager");
                manager.addActionListener(event -> IJ.run("LUTs Manager"));
                popup.add(fav_Luts_Panel);
                popup.add(manager);
                popup.show(fav_LUTs_Button, 10, fav_LUTs_Button.getHeight());
            }
        });
        return fav_LUTs_Button;
    }

    public JButton get_More_Button() {
        JButton more_Button = new JButton("More >>");
        more_Button.setMargin(new Insets(2, 2, 2, 2));
        more_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPopupMenu popup = new JPopupMenu();
                JMenuItem options = new JMenuItem("Auto-contrast option");
                options.addActionListener(event -> show_Options_Dialog());
                popup.add(options);                
                JMenuItem range = new JMenuItem("Set sliders range");
                range.addActionListener(event -> set_Range_Dialog());
                // range.setEnabled(BIT_DEPTH != 32);
                popup.add(range);
                popup.addSeparator();
                for (int i = 0; i < MORE_MENU.length; i++) {
                    final String command = MORE_MENU[i];
                    JMenuItem item = new JMenuItem(command);
                    item.addActionListener(event -> IJ.doCommand(command));
                    popup.add(item);
                }
                popup.show(more_Button, 5, more_Button.getHeight());
            }
        });
        return more_Button;
    }

    public void show_Options_Dialog() {
        GenericDialog dialog = new GenericDialog("Auto-contrast option");
        dialog.addSlider("Auto-contrast saturated pixels %", 0.0, 1.5, SATURATED_PIXELS, 0.1);
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }
        SATURATED_PIXELS = dialog.getNextNumber();
        Prefs.set("Channels_and_Contrast.saturated", (double)SATURATED_PIXELS);
    }
    
    public void set_Range_Dialog() {
        if (BIT_DEPTH == 32) {IJ.error("not possible for 32 bit images"); return;}
        String[] ranges = new String[] {"Automatic", "8-bit (0-255)", "10-bit (0-1023)", "12-bit (0-4095)", "14-bit (0-16383)", "15-bit (0-32767)", "16-bit (0-65535)"};
        GenericDialog dialog = new GenericDialog("Set Range");
        dialog.addChoice("Contrast sliders range:", ranges, "Automatic");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }
        String choice = dialog.getNextChoice();
        int range_Min = 0;
        int range_Max = 255;
        if (choice == ranges[0]) { setup_Current_Image(); return; }
        if (choice == ranges[1]) { range_Min = 0; range_Max = 255; }
        if (choice == ranges[2]) { range_Min = 0; range_Max = 1023; }
        if (choice == ranges[3]) { range_Min = 0; range_Max = 4095; }
        if (choice == ranges[4]) { range_Min = 0; range_Max = 16383; }
        if (choice == ranges[5]) { range_Min = 0; range_Max = 32767; }
        if (choice == ranges[6]) { range_Min = 0; range_Max = 65535; }
        for (int channel = 0; channel < N_CHANNELS; channel++) {
            SLIDERS_RANGE_MAX[channel] = range_Max;
            SLIDERS_RANGE_MIN[channel] = range_Min;
        }
        update_UI();
    }

    
    public Color get_Channel_Color(int i, LUT[] luts) {
        if (luts==null || LAST_MODE==IJ.GRAYSCALE)
            return Color.black;
        IndexColorModel cm = luts[i];
        if (cm==null)
            return Color.black;
        int index = cm.getMapSize() - 25;
        int r = cm.getRed(index);
        int g = cm.getGreen(index);
        int b = cm.getBlue(index);
        if (r<150 || g<150 || b<150) return new Color(r, g, b);
        else {
            index = cm.getMapSize() - 100;
            r = cm.getRed(index);
            g = cm.getGreen(index);
            b = cm.getBlue(index);
            return new Color(r, g, b);
        }
    }
    
    public Object[][] load_Sets_From_File(String file_Path) {
        File file = new File(file_Path);
        if (!file.exists()) return null;
        try {
            java.util.List<Object[]> set_List = new java.util.ArrayList<Object[]>();
            BufferedReader file_Reader = new BufferedReader(new FileReader(file));
            String line = file_Reader.readLine(); // skip header
            while ((line = file_Reader.readLine()) != null) {
                String[] csv_Values = parse_CSV_Line(line);
                Object[] lut_Set = new Object[LUT_COUNT + 1];
                lut_Set[0] = csv_Values[0];
                for (int i = 1; i <= LUT_COUNT; i++) {
                    lut_Set[i] = (i < csv_Values.length && csv_Values[i] != null && !csv_Values[i].isEmpty())
                        ? get_LUT_Set_Icon(csv_Values[i]) : null;
                }
                for (int i = 1; i <= LUT_COUNT; ) {
                    if (lut_Set[i] == null) {
                        int start_Null = i;
                        while (i <= LUT_COUNT && lut_Set[i] == null) i++;
                        int end_Null = i - 1;
                        if (start_Null > 1 && i <= LUT_COUNT && lut_Set[start_Null - 1] != null && lut_Set[i] != null) {
                            for (int j = start_Null; j <= end_Null; j++) {
                                lut_Set[j] = get_LUT_Set_Icon("Grays");
                            }
                        }
                    } else {
                        i++;
                    }
                }
                set_List.add(lut_Set);
            }
            file_Reader.close();
            return set_List.isEmpty() ? null : set_List.toArray(new Object[0][0]);
        } catch (Exception ex) {
            IJ.error("Auto load LUT sets failed:\n" + ex);
            return null;
        }
    }

    public String[] parse_CSV_Line(String line) {
        java.util.List<String> list = new java.util.ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0; i<line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\"') {
                if (inQuotes && i+1<line.length() && line.charAt(i+1)=='\"') {
                    sb.append('\"'); i++;
                } else inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                list.add(sb.toString()); sb.setLength(0);
            } else sb.append(ch);
        }
        list.add(sb.toString());
        return list.toArray(new String[0]);
    }

    public void export_Lut_Sets_From_Sets_Array(Object[][] sets, String save_Loc) {
        try {
            FileWriter fw = new FileWriter(save_Loc);
            fw.write("SetName");
            for (int i = 1; i <= LUT_COUNT; i++) fw.write(",LUT" + i);
            fw.write("\n");
            for (int row = 0; row < sets.length; row++) {
                // Set name (column zero)
                fw.write("\"" + sets[row][0].toString().replace("\"", "\"\"") + "\"");
                // LUTs: columns 1...LUT_COUNT
                for (int col = 1; col <= LUT_COUNT; col++) {
                    String lut_Name = get_LUT_Name_At(sets[row][col]);
                    if (lut_Name == null) fw.write(",");
                    else fw.write(",\"" + lut_Name.replace("\"", "\"\"") + "\"");
                }
                fw.write("\n");
            }
            fw.close();
        } catch (Exception ex) {
            IJ.error("Export LUT sets failed:\n" + ex);
        }
    }
    
    public void apply_LUT(ImagePlus imp, String lut_Name, int channel) {
        LUT[] luts = imp.getLuts();
        double min = luts[channel-1].min;
        double max = luts[channel-1].max;
        luts[channel-1] = new LUT(LutLoader.getLut(lut_Name), min, max);
        if (!imp.isComposite()) imp.setLut(luts[0]);
        else ((CompositeImage)imp).setLuts(luts);
    }

    public static Icon make_4_Mini_LUTs_Icon(int width, int height) {
        int stripes = 4;
        IndexColorModel[] lut_List = new IndexColorModel[] {
            LutLoader.getLut("Cyan Hot"),
            LutLoader.getLut("Orange Hot"),
            LutLoader.getLut("Magenta Hot"),
            LutLoader.getLut("Green Fire Blue")
            };
        int stripe_Height = height / stripes;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int s = 0; s < stripes; s++) {
            IndexColorModel lut = lut_List[s];
            int y0 = s * stripe_Height;
            for (int x = 0; x < width; x++) {
                int lut_Index = x * (150 - 1) / (width - 1);
                Color lut_Color = new Color(lut.getRed(lut_Index), lut.getGreen(lut_Index), lut.getBlue(lut_Index));
                g.setColor(lut_Color);
                g.drawLine(x, y0, x, y0 + stripe_Height - 1);
            }
        }
        g.dispose();
        return new ImageIcon(img);
    }

    public static Icon make_LUT_Palette_Icon(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int x = 0; x < width; x++) {
            float ratio = (float)x / (width - 1);
            Color color = new Color(ratio, ratio, ratio);
            g.setColor(color);
            g.drawLine(x, 0, x, height - 1);
        }
        g.dispose();
        return new ImageIcon(img);
    }

    public ImageIcon get_LUT_Set_Icon(final String lut_Name) {
        if (lut_Name==null) return null;
        ImagePlus imp = IJ.createImage("LUT_icon", "8-bit ramp", 60, 20, 1);
        ImageProcessor ip = imp.getProcessor();
        ip.setColorModel(LutLoader.getLut(lut_Name));
        ImageIcon icon = new ImageIcon(ip.getBufferedImage());
        icon.setDescription(lut_Name);
        return icon;
    }

    public ImageIcon get_LUT_Icon(String lut_Name) {
        ImagePlus lut_Image = IJ.createImage("LUT icon", "8-bit ramp", 150, 20, 1);
        ImageProcessor ip = lut_Image.getProcessor();

        ip.setColorModel(LutLoader.getLut(lut_Name));

        for (int x = 0; x < 150; x++) {
            int gray_Value = Math.round(((float)x / 150) * 255);
            int shifted_Value = gray_Value + 20;
            for (int y = 15; y < 19; y++) {
                ip.putPixel(x, y, (x % 3 == 0) ? gray_Value : shifted_Value); // Stripes
            }
        }
        return new ImageIcon(ip.getBufferedImage());
    }

    public String get_LUT_Name_At(Object cell) {
        if (cell instanceof ImageIcon) {
            String desc = ((ImageIcon)cell).getDescription();
            return desc != null ? desc : "Grays";
        }
        return null;
    }
}
