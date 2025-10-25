/*
Kevin Terretaz
kevinterretaz@gmail.com
251025

I heavilly used OpenAI GPT thanks to the github playground access
So I guess I have to thank the entire world of programmers that published their code since the begining of java
I would have never be able to build these plugins without it.

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
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.LUT;
import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.border.*;
import ij.plugin.LutLoader;
import java.util.*;
import java.io.*;  

public class Channels_and_Contrast implements PlugIn {
    private static final String FRAME_TITLE = "Channels & Contrast";
    final String save_Loc = IJ.getDirectory("luts")+"/LUT_Palette_Manager.csv";
    final int LUT_COUNT = 5;    
    public static final String LOC_KEY = "Channels_and_Contrast.loc";

    int nChannels, bit_Depth, slider_Min, slider_Max, last_mode, last_Channel;
    JFrame frame;
    JPanel main_Panel;
    JLabel[] min_Labels, max_Labels;
    JSlider[] min_Sliders, max_Sliders;
    JCheckBox[] checkboxes;
    TitledBorder[] channel_borders;
    JToggleButton grayscale_Button, color_Button, composite_Button;
    ButtonGroup composite_Mode_Group;
    ImagePlus current_Imp=null;
    LUT[] last_luts = null;
    boolean updating = false;
    javax.swing.Timer timer;
    long last_Update = 0;
    long refresh_Interval = 500;
    Boolean plugin_Locked = false;
    boolean[] last_Active_Channels;
    String[] more_Menu = new String[] {"Split Channels", "Merge Channels...", "Arrange Channels...","Channels Tool...", "Brightness/Contrast..."};
    double saturated_Pixels = Prefs.get("Channels_and_Contrast.saturated", 0.1);
    private boolean error_State = false;
    private long last_Error_Time = 0;
    private static final long retry_Interval = 2000;

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
        // set a timer to listen for any relevant changes to update the UI
        // also check if the plugin is still opened to prevent error loops if the main ui broke
        timer = new javax.swing.Timer(50, new ActionListener() {
            public void actionPerformed(ActionEvent e) {boolean is_Plugin_Running = false;
                for (Frame open_Frame : JFrame.getFrames()) {
                    if (FRAME_TITLE.equals(open_Frame.getTitle()) && open_Frame.isVisible()) {
                        is_Plugin_Running = true;
                        break;
                    }
                }
                if (!is_Plugin_Running) {
                    timer.stop();
                }
                if (error_State) {
                    long now = System.currentTimeMillis();
                    if (now - last_Error_Time >= retry_Interval) error_State = false;
                    else return;
                }
                try {
                    check_Active_Image();
                    if (!updating && something_Changed()) update_UI();
                } catch (Throwable t) {
                    IJ.handleException(t);
                    error_State = true;
                    last_Error_Time = System.currentTimeMillis();
                }
            }
        });
        timer.start();
    }

    void create_Frame() {
        frame = new JFrame("Channels & Contrast");
        frame.setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        frame.setLayout(new BorderLayout());
        main_Panel = new JPanel();
        main_Panel.setLayout(new BoxLayout(main_Panel, BoxLayout.Y_AXIS));
        frame.add(main_Panel, BorderLayout.CENTER);
        check_Active_Image();
        Point loc = Prefs.getLocation(LOC_KEY);
        if (loc!=null) frame.setLocation(loc);
        else GUI.centerOnImageJScreen(frame);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                timer.stop();
                Prefs.saveLocation(LOC_KEY, frame.getLocation());
            }
        });
        frame.setVisible(true);
    }
    
    // check if a new image as been selected, if it's compatible with the plugin
    // if not, put the frame in idle state.
    void check_Active_Image() {
        ImagePlus imp = WindowManager.getCurrentImage();
        // do nothing when a plugin is already working on image
        if (imp != null) {
            if (imp.isLocked() || imp.isLockedByAnotherThread()){
            	updating = true;
            	return;
            }
        }
        else updating = false;
        Boolean no_Image, is_RGB, is_weird_Stack;
        no_Image = (imp == null);
        is_RGB = imp == null ? false : (imp.getBitDepth() == 24);
        is_weird_Stack = imp == null ? false : (imp.getNChannels()>1 && !imp.isComposite());

        if (no_Image || is_RGB || is_weird_Stack) {
            if (plugin_Locked) return;
            current_Imp = imp;
            main_Panel.removeAll();
            updating = false;
            frame.setSize(300, 80);
            frame.setResizable(false);
            JLabel label = new JLabel("Waiting for a supported image");
            JPanel temp = new JPanel();
            temp.add(label);
            main_Panel.add(temp);
            main_Panel.revalidate();
            main_Panel.repaint();
            plugin_Locked = true;
            last_luts = null;
            return;
        }
        if (imp != null && imp != current_Imp) {
            long now = System.currentTimeMillis();
            if (now - last_Update < refresh_Interval) return; // in case a script is switching rapidly through opened images
            last_Update = now;
            current_Imp = imp;
            plugin_Locked = false;
            frame.setResizable(true);
            setup_Current_Image();
        }
    }

    void setup_Current_Image() {
        // Setup
        updating = true;
        main_Panel.removeAll();
        nChannels = current_Imp.getNChannels();
        min_Sliders = new JSlider[nChannels];
        max_Sliders = new JSlider[nChannels];
        min_Labels = new JLabel[nChannels];
        max_Labels = new JLabel[nChannels];
        checkboxes = new JCheckBox[nChannels];
        channel_borders = new TitledBorder[nChannels];
        get_Sliders_Range();
        LUT[] luts = null;
        if (current_Imp.isComposite()) 
            luts = ((CompositeImage)current_Imp).getLuts();
        else luts = ((ImagePlus)current_Imp).getLuts();

        // build UI
        if (nChannels > 1) main_Panel.add(get_Display_Mode_Panel());
        for (int i = 0; i < nChannels; i++) main_Panel.add(get_Channel_Panel(i, luts));
        main_Panel.add(get_Bottom_Panel());
        update_UI();
        main_Panel.revalidate();
        main_Panel.repaint();
        frame.pack();
        save_Current_State();
        updating = false;
    }

    // save current state for the something_Changed() method to compare
    void save_Current_State() {
        if (current_Imp.isComposite()) last_luts = ((CompositeImage)current_Imp).getLuts();
        else last_luts = ((ImagePlus)current_Imp).getLuts();
        last_mode = current_Imp.getCompositeMode();
        last_Channel = current_Imp.getC();
        if (current_Imp.isComposite()) {
            boolean[] active = ((CompositeImage)current_Imp).getActiveChannels();
            last_Active_Channels = active.clone();
        }
    }

    // detect any relevant change in the current image to update the plugin
    boolean something_Changed() {
        // no Image or RGB
        if (plugin_Locked) return false; 
        // something cooking
        if (current_Imp.isLocked() || current_Imp.isLockedByAnotherThread()) return false;
        // display mode
        if (current_Imp.getCompositeMode() != last_mode) return true;
        // current channel
        if (current_Imp.getC() != last_Channel) return true;
        // visible channels
        if (current_Imp.isComposite()) 
            if (((CompositeImage)current_Imp).getActiveChannels() != last_Active_Channels)
                return true;
        // channel count changed
        if (current_Imp.getNChannels() != nChannels) return true;
        // bit depth changed
        if (current_Imp.getBitDepth() != bit_Depth) return true;
        LUT[] luts = null;
        if (current_Imp.isComposite()) luts = ((CompositeImage)current_Imp).getLuts();
        else luts = ((ImagePlus)current_Imp).getLuts();
        for (int k=0; k<luts.length; k++) {
            // new contrast
            if (luts[k].min != last_luts[k].min || luts[k].max != last_luts[k].max) return true;
            // new LUT
            if (get_Channel_Color(k, luts) != get_Channel_Color(k, last_luts)) return true;
        }
        return false;
    }

    void update_UI(){
    // Sliders
        LUT[] luts = null;
        if (current_Imp.getNChannels() != nChannels) { // weird cases
            setup_Current_Image();
            return;
        }
        updating = true;
        if (current_Imp.isComposite()) luts = ((CompositeImage)current_Imp).getLuts();
        else luts = ((ImagePlus)current_Imp).getLuts();
        for (int channel = 0; channel < nChannels; channel++) {
            // Clamp LUT min to slider range
            int min = (int)Math.max(slider_Min, Math.min(slider_Max, (int)luts[channel].min));
            // If LUT max exceeds current slider max, update slider max
            if ((int)luts[channel].max > slider_Max) {
                max_Sliders[channel].setMaximum((int)luts[channel].max);
                slider_Max = (int)luts[channel].max;
            }
            // Clamp LUT max to slider range
            int max = (int)Math.max(slider_Min, Math.min(slider_Max, (int)luts[channel].max));
            // Ensure min is not greater than max after adjustment
            if (min >= max) min = max;
            // Sync slider/label controls to updated values
            min_Sliders[channel].setValue(min);
            min_Labels[channel].setText("Min: " + min);
            max_Sliders[channel].setValue(max);
            max_Labels[channel].setText("Max: " + (int)luts[channel].max);
            channel_borders[channel].setTitleColor(get_Channel_Color(channel, luts));
            main_Panel.repaint();
        }
        if (current_Imp.isComposite()) {
        // Checkboxes
            boolean[] active = ((CompositeImage)current_Imp).getActiveChannels();
            for (int i=0; i<checkboxes.length; i++) checkboxes[i].setSelected(active[i]);
        // Composite buttons
            int mode = current_Imp.getCompositeMode();
            grayscale_Button.setForeground(UIManager.getColor("Button.foreground"));
            grayscale_Button.setBackground(UIManager.getColor("Button.background"));
            color_Button.setForeground(UIManager.getColor("Button.foreground"));
            color_Button.setBackground(UIManager.getColor("Button.background"));
            composite_Button.setForeground(UIManager.getColor("Button.foreground"));
            composite_Button.setBackground(UIManager.getColor("Button.background"));
            if (mode == IJ.GRAYSCALE) {
                grayscale_Button.setSelected(true);
                grayscale_Button.setBackground(new Color(255,180,0));
                grayscale_Button.setForeground(new Color(0,0,0));
            }
            if (mode == IJ.COLOR) {
                color_Button.setSelected(true);
                color_Button.setBackground(new Color(255,180,0));
                color_Button.setForeground(new Color(0,0,0));
            }
            if (mode == IJ.COMPOSITE) {
                composite_Button.setSelected(true);
                composite_Button.setBackground(new Color(255,180,0));
                composite_Button.setForeground(new Color(0,0,0));
            }        
        }
        updating = false;
        save_Current_State();
    }

    JPanel get_Display_Mode_Panel() {
        JPanel composite_Mode_Panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        composite_Mode_Panel.setBorder(BorderFactory.createEtchedBorder());
        grayscale_Button = new JToggleButton("Grayscale");
        grayscale_Button.setToolTipText("Displays individual channels in grayscale");
        color_Button = new JToggleButton("Color");
        color_Button.setToolTipText("Displays individual channels with their current LUTs");
        composite_Button = new JToggleButton("Composite");
        composite_Button.setToolTipText("Displays the sum of all channels as a composite overlay");
        composite_Mode_Group = new ButtonGroup();
        composite_Mode_Group.add(color_Button);
        composite_Mode_Group.add(composite_Button);
        composite_Mode_Group.add(grayscale_Button);
        grayscale_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)current_Imp).setDisplayMode(IJ.GRAYSCALE);
                save_Current_State();update_UI(); }
        });
        color_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)current_Imp).setDisplayMode(IJ.COLOR);
                save_Current_State();update_UI(); }
        });
        composite_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((CompositeImage)current_Imp).setDisplayMode(IJ.COMPOSITE);
                save_Current_State();update_UI(); }
        });
        composite_Mode_Panel.add(composite_Button);
        composite_Mode_Panel.add(color_Button);
        composite_Mode_Panel.add(grayscale_Button);
        return composite_Mode_Panel;
    }
    
    JPanel get_Channel_Panel(int channel, LUT[] luts) {
        final int channel_Index = channel;
        int min = (int)Math.max(slider_Min, Math.min(slider_Max, (int)luts[channel].min));
        int max = (int)Math.max(slider_Min, Math.min(slider_Max, (int)luts[channel].max));
        if (min >= max) min = max;
        Color color = get_Channel_Color(channel, luts);
    // MIN
        min_Sliders[channel] = new JSlider(JSlider.HORIZONTAL, slider_Min, slider_Max, min);
        min_Sliders[channel].setPaintTicks(false);
        min_Sliders[channel].setPaintLabels(false);
        min_Labels[channel] = new JLabel("Min: " + min);
        min_Labels[channel].setPreferredSize(new Dimension(70, (int)min_Labels[channel].getPreferredSize().height));
        min_Sliders[channel].addMouseWheelListener(e -> ((JSlider)e.getSource()).setValue((int)(((JSlider)e.getSource()).getValue() - (e.getWheelRotation() * ((slider_Max-slider_Min)/255)))));
        min_Sliders[channel].addChangeListener(e -> {
            if (updating) return;
            for (int i = 0; i < min_Sliders.length; i++) {
                if (((JSlider)e.getSource()) == min_Sliders[i]) {
                    min_Labels[channel].setText("Min: " + ((JSlider)e.getSource()).getValue());
                    adjust_Contrast(i);
                    break;
                }
            }
        });
    // MAX
        max_Sliders[channel] = new JSlider(JSlider.HORIZONTAL, slider_Min, slider_Max, max);
        max_Sliders[channel].setPaintTicks(false);
        max_Sliders[channel].setPaintLabels(false);
        max_Labels[channel] = new JLabel("Max: " + max);
        max_Labels[channel].setPreferredSize(new Dimension(70, (int)max_Labels[channel].getPreferredSize().height));
        max_Sliders[channel].addMouseWheelListener(e -> ((JSlider)e.getSource()).setValue((int)(((JSlider)e.getSource()).getValue() - (e.getWheelRotation() * ((slider_Max-slider_Min)/255)))));
        max_Sliders[channel].addChangeListener(e -> {
            if (updating) return;
            for (int i = 0; i < max_Sliders.length; i++) {
                if (((JSlider)e.getSource()) == max_Sliders[i]) {
                    max_Labels[channel].setText("Max: " + ((JSlider)e.getSource()).getValue());
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
                "Adjust the contrast to " + saturated_Pixels + "% of saturated pixels.<br>"+
                "You can change the % value in the plugin options");
        auto_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (current_Imp.isComposite()) current_Imp.setC(channel_Index+1);
                IJ.run("Enhance Contrast...", "saturated=" + saturated_Pixels);
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
                        if (current_Imp.isComposite()) current_Imp.setC(channel_Index+1);
                        IJ.run("Reset Display", "channel=" + (channel_Index+1));
                    }
                }).start();
            }
        });
    // CHECKBOXES
        if (current_Imp.isComposite()) {
            boolean[] act = ((CompositeImage)current_Imp).getActiveChannels();
            checkboxes[channel] = new JCheckBox("  ", act[channel]);
            checkboxes[channel].addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                    if (((CompositeImage)current_Imp).getMode()==IJ.COMPOSITE) {
                        boolean[] active = ((CompositeImage)current_Imp).getActiveChannels();
                        active[channel_Index] = ((JCheckBox)e.getSource()).isSelected();
                    }
                    else current_Imp.setC(channel_Index+1);
                    current_Imp.updateAndDraw();
                    update_UI();
                }
            });
        }
    // ASSEMBLE
        JPanel channel_Panel = new JPanel(new BorderLayout());
        channel_borders[channel] = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),("Channel " + (channel+1)));
        channel_borders[channel].setTitleColor(color);
        channel_Panel.setBorder(channel_borders[channel]);
        JPanel sliders_Panel = new JPanel(new GridLayout(2,1));
        JPanel min_Panel = new JPanel(new BorderLayout());
        min_Panel.add(min_Labels[channel], BorderLayout.WEST);
        min_Panel.add(min_Sliders[channel], BorderLayout.CENTER);
        JPanel max_Panel = new JPanel(new BorderLayout());
        max_Panel.add(max_Labels[channel], BorderLayout.WEST);
        max_Panel.add(max_Sliders[channel], BorderLayout.CENTER);
        sliders_Panel.add(min_Panel);
        sliders_Panel.add(max_Panel);
        JPanel buttons_Panel = new JPanel();
        buttons_Panel.setLayout(new BoxLayout(buttons_Panel, BoxLayout.Y_AXIS));
        buttons_Panel.add(auto_Button);
        buttons_Panel.add(Box.createRigidArea(new Dimension(0, 3)));
        buttons_Panel.add(minmax_Button);
        if (current_Imp.isComposite()) channel_Panel.add(checkboxes[channel], BorderLayout.WEST);
        channel_Panel.add(sliders_Panel, BorderLayout.CENTER);
        channel_Panel.add(buttons_Panel, BorderLayout.EAST);
        return channel_Panel;
    }

    JPanel get_Bottom_Panel(){
        JPanel bottom_Panel = new JPanel(new BorderLayout());
        JPanel east_Panel = new JPanel();
        east_Panel.add(get_Palettes_Button());
        east_Panel.add(get_Fav_LUTs_Button());
        east_Panel.add(get_More_Button());
        if (nChannels > 1) bottom_Panel.add(get_All_Buttons_Panel(), BorderLayout.WEST);
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
                       updating = true;
                       ((CompositeImage)current_Imp).setDisplayMode(IJ.COMPOSITE);
                       IJ.run("Reset Display", "channel=0");
                       updating = false;
                    }
                }).start();
            }
        });
        JButton all_Auto_Button = new JButton("Auto all");
        all_Auto_Button.setMargin(new Insets(2, 10, 2, 10));
        all_Auto_Button.setToolTipText("<html>Enhance all channels : only based on current slice.<br>"+
                "Adjust the contrast to " + saturated_Pixels + "% of saturated pixels.<br>"+
                "You can change the % value in the plugin options");
        all_Auto_Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (current_Imp == null) return;
                int nChannels = current_Imp.getNChannels();
                int previous_Channel = current_Imp.getC();
                for (int channel = 1; channel <= nChannels; channel++) {
                    current_Imp.setC(channel);
                    IJ.run("Enhance Contrast...", "saturated=" + saturated_Pixels);
                }
                current_Imp.setC(previous_Channel);
            }
        });
        JPanel all_Button_Panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        all_Button_Panel.add(all_Auto_Button);
        all_Button_Panel.add(all_Minmax_Button);
        return all_Button_Panel;
    }

    public JButton get_Palettes_Button(){ 
        JButton show_LUT_Sets_Button = new JButton(make_4_Mini_LUTs_Icon(40, 16));
        show_LUT_Sets_Button.setMargin(new Insets(2, 2, 2, 2));
        show_LUT_Sets_Button.setToolTipText("LUT Palettes");
        show_LUT_Sets_Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Boolean no_Sets = false;
                Object[][] sets = load_Sets_From_File(save_Loc);
                if (sets == null || sets.length == 0) no_Sets = true;
                JPopupMenu popup = new JPopupMenu();
                JPanel palettes_Panel = new JPanel();
                if (!no_Sets){
                    palettes_Panel.setLayout(new BoxLayout(palettes_Panel, BoxLayout.Y_AXIS));

                    for (int i = 0; i < sets.length; i++) {
                        JPanel icons_Row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
                        icons_Row.setBackground(Color.darkGray);
                        icons_Row.setToolTipText((String)sets[i][0]);
                        java.util.List<String> lut_Names = new java.util.ArrayList<String>();

                        for (int c = 1; c <= LUT_COUNT; c++) {
                            Object icon_Obj = sets[i][c];
                            String lut_Name = (icon_Obj instanceof ImageIcon) ? get_LUT_Name_At(icon_Obj) : "Grays";
                            lut_Names.add(lut_Name);
                            ImageIcon icon = (icon_Obj instanceof ImageIcon) ? (ImageIcon)icon_Obj : null;
                            if (icon != null) icons_Row.add(new JLabel(icon));
                        }
                        final String[] lut_Names_Array = lut_Names.toArray(new String[0]);
                        icons_Row.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent evt) {
                                ImagePlus imp = WindowManager.getCurrentImage();
                                if (imp == null) return;
                                int nChannels = imp.getNChannels();
                                for (int ch = 0; ch < nChannels; ch++) {
                                    String lut_Name = (ch < lut_Names_Array.length) ? lut_Names_Array[ch] : "Grays";
                                    apply_LUT(imp, lut_Name, ch + 1);
                                }
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
                popup.add(range);
                popup.addSeparator();
                for (int i = 0; i < more_Menu.length; i++) {
                    final String command = more_Menu[i];
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
        dialog.addSlider("Auto-contrast saturated pixels %", 0.0, 1.5, saturated_Pixels, 0.1);
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }
        saturated_Pixels = dialog.getNextNumber();
        Prefs.set("Channels_and_Contrast.saturated", (double)saturated_Pixels);
    }
    
    public void set_Range_Dialog() {
        if (bit_Depth == 32) {IJ.error("not possible for 32 bit images"); return;}
        String[] ranges = new String[] {"Automatic", "8-bit (0-255)", "10-bit (0-1023)", "12-bit (0-4095)", "14-bit (0-16383)", "15-bit (0-32767)", "16-bit (0-65535)"};
        GenericDialog dialog = new GenericDialog("Set Range");
        dialog.addChoice("Contrast sliders range:", ranges, "Automatic");
        dialog.showDialog();
        if (dialog.wasCanceled()) {
            return;
        }
        String choice = dialog.getNextChoice();
        if (choice == ranges[0]) { setup_Current_Image(); return; }
        if (choice == ranges[1]) { slider_Min = 0; slider_Max = 255; }
        if (choice == ranges[2]) { slider_Min = 0; slider_Max = 1023; }
        if (choice == ranges[3]) { slider_Min = 0; slider_Max = 4095; }
        if (choice == ranges[4]) { slider_Min = 0; slider_Max = 16383; }
        if (choice == ranges[5]) { slider_Min = 0; slider_Max = 32767; }
        if (choice == ranges[6]) { slider_Min = 0; slider_Max = 65535; }
        for (int channel = 0; channel < nChannels; channel++) 
            max_Sliders[channel].setMaximum(slider_Max);
        update_UI();
    }

    void adjust_Contrast(int index) {
        if (updating || current_Imp == null) return;
        // Ensure correct channel is selected if in composite (not IJ.COMPOSITE mode)
        if (current_Imp.isComposite() 
                && current_Imp.getCompositeMode() != IJ.COMPOSITE 
                && current_Imp.getChannel() != index+1 ) 
            current_Imp.setC(index+1);
        // Get min/max from channel sliders
        int min = min_Sliders[index].getValue();
        int max = max_Sliders[index].getValue();
        // Prevent min exceeding max
        if (min >= max) min = max;
        // Retrieve LUTs for this image
        LUT[] luts = null;
        if (current_Imp.isComposite()) 
            luts = ((CompositeImage)current_Imp).getLuts();
        else 
            luts = ((ImagePlus)current_Imp).getLuts();
        // If composite & not grayscale: update LUT for this channel, apply to image
        // the grayscale mode is broken and shows current LUT instead with this method.
        if (current_Imp.isComposite() && current_Imp.getCompositeMode() != IJ.GRAYSCALE){  
            luts[index] = new LUT(luts[index].getColorModel(), (double)min, (double)max);
            ((CompositeImage)current_Imp).setLuts(luts);
        }
        // Otherwise, set display range directly for non-composite or grayscale
        else {
            current_Imp.setDisplayRange((double)min, (double)max);
        }
        current_Imp.updateChannelAndDraw();
        save_Current_State();
        update_UI();
    }

    void get_Sliders_Range(){
        // Estimate good slider_Min/slider_Max for slider
        bit_Depth = current_Imp.getBitDepth();
        if (bit_Depth == 8) {
            slider_Min = 0;
            slider_Max = 255;
        } 
        else if (bit_Depth == 16) {
            ImageStatistics stats = new StackStatistics(current_Imp);
            slider_Min = 0;
            slider_Max = 65535;
            if (stats.max <= 16383) slider_Max = 16383;
            if (stats.max <= 4095) slider_Max = 4095;
            if (stats.max <= 255) slider_Max = 255;
        } 
        else if (bit_Depth == 32) {
            ImageStatistics stats = new StackStatistics(current_Imp);
            slider_Min = (int)Math.floor(stats.min);
            slider_Max = (int)Math.ceil(stats.max);
            if (slider_Min >= slider_Max) {
                slider_Min = 0;
                slider_Max = 255;
            }
        }
    }
    
    public Color get_Channel_Color(int i, LUT[] luts) {
        if (luts==null || last_mode==IJ.GRAYSCALE)
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
