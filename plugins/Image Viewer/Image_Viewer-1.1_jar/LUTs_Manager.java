/*
Kevin Terretaz
kevinterretaz@gmail.com
251025

I heavilly used OpenAI GPT thanks to the github playground access
whith prompts like 
"here's where I am, why the heck does it not work like I want?"
"can I make a series of toggle buttons that would change the sorting of my table?"
"I give up, please make me a method that generates a yellow star icon."
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
import ij.CompositeImage;
import ij.process.*;
import ij.plugin.LutLoader;
import ij.plugin.PlugIn;
import ij.gui.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import javax.swing.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.awt.image.IndexColorModel;
import javax.swing.JFrame.*;
import java.io.*;   

public class LUTs_Manager implements PlugIn {
    public static final String FRAME_TITLE = "LUTs Manager";
    public static final String LOC_KEY = "LUTsManager.loc";

    @Override
    public void run(String arg) {
        for (Frame open_Frame : JFrame.getFrames()) {
            if (FRAME_TITLE.equals(open_Frame.getTitle())&& open_Frame.isVisible()) {
                open_Frame.toFront();
                return;
            }
        }
        SwingUtilities.invokeLater(() -> create_and_show_GUI());
    }

    public void create_and_show_GUI() {
        JPanel sets_Panel = get_LUT_Set_Manager_Panel();
        JPanel finder_Panel = get_LUT_Finder_Panel();
        JSplitPane split_Pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sets_Panel, finder_Panel);
        split_Pane.setResizeWeight(0.4);

        JFrame frame = new JFrame("LUTs Manager");
        frame.setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        frame.setLayout(new BorderLayout());
        frame.add(split_Pane, BorderLayout.CENTER);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                export_Lut_Sets();
                Prefs.saveLocation(LOC_KEY, frame.getLocation());
            }
        });
        Point loc = Prefs.getLocation(LOC_KEY);
        if (loc!=null) frame.setLocation(loc);
        else GUI.centerOnImageJScreen(frame);
        frame.setSize(760, 600);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------
    //      LUTs Finder
    //--------------------------------------------------------------------------------------------------------------------------------------
    
    public static final String[] COLUMN_NAMES = new String[]{"Fav", "LUT Name", "Preview", "Estimated Description", "Colors"};
    public static final String PREF_KEY = "LUTs_Finder.Favorites";
    public JTextField search_Bar;
    public TableRowSorter<TableModel> table_Sorter;
    public List<JToggleButton> color_Buttons;
    public String[] color_Names = new String[] {"cyan", "blue", "magenta", "red", "orange", "yellow", "green"};

    public JPanel get_LUT_Finder_Panel() {
        String[] lut_List = IJ.getLuts();
        Object[][] table_Data = new Object[lut_List.length][5];
        Set<String> fav_Set = get_Favorites();
            color_Buttons = new ArrayList<JToggleButton>();
            for (int i = 0; i < lut_List.length; i++) {
                String lut_Name = lut_List[i];
                IndexColorModel lut = LutLoader.getLut(lut_Name);
                if (lut == null) {
                    IJ.log("The LUT " + lut_Name + " was not found");
                    continue;
                }
                table_Data[i] = new Object[]{fav_Set.contains(lut_Name), lut_Name, get_Lut_Icon(lut_Name), get_Lut_Infos(lut_Name), get_Lut_Colors(lut_Name)};
            }
            DefaultTableModel table_Model = get_Table_Model(table_Data);
            table_Model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getColumn() == 0 && e.getType() == TableModelEvent.UPDATE) {
                    Set<String> favs = new HashSet<String>();
                    for (int row = 0; row < table_Model.getRowCount(); row++) {
                        if (Boolean.TRUE.equals(table_Model.getValueAt(row, 0))) {
                            favs.add((String) table_Model.getValueAt(row, 1)); // LUT Name
                        }
                    }
                    save_Favorites(favs);
                }
            }
        });
        JTable table = new JTable(table_Model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                java.awt.Point point = event.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);
                if (column == 2) {
                    column = 1;
                }
                String tooltip = "";
                Object value = getValueAt(row, column);
                if (value instanceof Boolean){
                    if ((Boolean)value == true) tooltip = "Favorite";
                    else if ((Boolean)value == false) tooltip = "Add to favorite LUTs";
                }
                else if (value != null) tooltip = value.toString();
                return tooltip;
            }
        };
        // Key Listener
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (table.getRowCount() > 0) {
                    int next_Row;
                    if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
                    next_Row = table.getSelectedRow();
                    // if Enter
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        apply_LUT(table);
                    }
                }
            }
        });
        // Mouse Listener for Double Click
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {  // Double-click detected
                    apply_LUT(table);
                }
            }
        });
        table.setDragEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setTransferHandler(get_Transfer_Handler());
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setCellRenderer(new Star_Renderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(125);
        table.getColumnModel().getColumn(2).setPreferredWidth(258);
        table.getColumnModel().getColumn(2).setMinWidth(258);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
//        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(3));
        // Search Panel
        JPanel search_Panel = new JPanel();
        // Filter bar
        search_Bar = new JTextField(15);
        search_Bar.setToolTipText("<html>Filter list by one or more properties (name, description)<br>add a '!' prefix to remove properties from the results.");
        table_Sorter = new TableRowSorter<>(table_Model);
        table_Sorter.toggleSortOrder(0);
        table_Sorter.toggleSortOrder(0);
        table.setRowSorter(table_Sorter);
        search_Bar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (table.getRowCount() > 0) {
                    int next_Row;
                    if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
                    next_Row = table.getSelectedRow();
                    // if Enter
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) apply_LUT(table);
                    // if up
                    if (e.getKeyCode() == KeyEvent.VK_UP) {
                        next_Row = table.getSelectedRow() - 1;
                        if (next_Row > -1) table.setRowSelectionInterval(next_Row, next_Row);
                    }
                    // if down
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        next_Row = table.getSelectedRow() + 1;
                        if (next_Row < table.getRowCount()) table.setRowSelectionInterval(next_Row, next_Row);
                    }
                }
            }
        });
        search_Bar.getDocument().addDocumentListener(get_Filter_Listener());
        search_Panel.add(new JLabel("LUTs finder                Search:"));
        search_Panel.add(search_Bar);
        for (String color : color_Names) {
            JToggleButton btn = new JToggleButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(getBackground());
                    g.fillRect(0, 0, getWidth(), getHeight());
                    super.paintComponent(g);
                }
            };
            btn.setPreferredSize(new Dimension(18, 18));
            btn.setBackground(get_Color(color));
            btn.setToolTipText(color.substring(0, 1).toUpperCase() + color.substring(1));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createLineBorder(Color.white, 1));
            btn.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (btn.isSelected()) {
                        btn.setBorder(BorderFactory.createLineBorder(Color.black, 2));
                    } else {
                        btn.setBorder(BorderFactory.createLineBorder(Color.white, 1));
                    }
                }
            });
            color_Buttons.add(btn);
            search_Panel.add(btn);
            btn.addActionListener(e -> update_Filter());
        }
    // clear button
        JButton reset_Button = new JButton("Clear filters");
        reset_Button.setMargin(new Insets(2, 2, 2, 2));
        reset_Button.addActionListener(e -> {
            search_Bar.setText("");
            for (int i = 0; i < color_Buttons.size(); i++) color_Buttons.get(i).setSelected(false);
            update_Filter();
        });
        reset_Button.setToolTipText("Remove filters");
        search_Panel.add(reset_Button);
    // grey button
        JButton grey_Button = new JButton("Gray check");
        grey_Button.setMargin(new Insets(2, 2, 2, 2));
        grey_Button.setToolTipText("Compare current LUT to gray");
        grey_Button.addMouseListener(new MouseAdapter() {
            LUT lut;
            public void mousePressed(MouseEvent e) {
                ImagePlus image = WindowManager.getCurrentImage();
                if (image == null) return;
                if (image.getBitDepth() == 24) return;
                lut = image.getProcessor().getLut();
                IJ.run("Grays");
            }
            public void mouseReleased(MouseEvent e) {
                ImagePlus image = WindowManager.getCurrentImage();
                if (image == null) return;
                if (image.getBitDepth() == 24) return;
                if (image.isComposite()) {
                    CompositeImage composite_Image = (CompositeImage) image;
                    composite_Image.setChannelLut(lut);
                } else {
                    image.getProcessor().setLut(lut);
                }
                image.updateAndDraw();
            }
        });
        search_Panel.add(grey_Button);
    // invert button
        JButton invert_Button = new JButton("Invert LUT");
        invert_Button.setMargin(new Insets(2, 2, 2, 2));
        invert_Button.addActionListener(e -> {
            ImagePlus image = WindowManager.getCurrentImage();
            if (image == null) return;
            if (image.getBitDepth() == 24) return;
            IJ.run("Invert LUT");
        });
        search_Panel.add(invert_Button);

        JPanel finder_Panel = new JPanel();
        finder_Panel.setLayout(new BorderLayout());
        finder_Panel.add(search_Panel, BorderLayout.NORTH);
        finder_Panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return finder_Panel;
    }

    public Set<String> get_Favorites() {
        String favs = Prefs.get(PREF_KEY, "");
        Set<String> fav_Set = new HashSet<String>();
        if (!favs.trim().isEmpty()) {
            fav_Set.addAll(Arrays.asList(favs.split(",")));
        }
        return fav_Set;
    }

    public void save_Favorites(Set<String> fav_Set) {
        String favs = String.join(",", fav_Set);
        Prefs.set(PREF_KEY, favs);
        Prefs.savePreferences();
    }
    public Color get_Color(String name) {
        switch (name) {
            case "red": return new Color(255, 80, 0);
            case "green": return new Color(100, 200, 0);
            case "blue": return new Color(0, 120, 255);
            case "cyan": return new Color(50, 230, 230);
            case "magenta": return new Color(220, 50, 255);
            case "yellow": return new Color(240, 200, 0);
            case "orange": return new Color(255, 130, 0);
            default: return Color.GRAY;
        }
    }

    public static void apply_LUT(JTable table) {
        if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
        int selected_Row = table.getSelectedRow();
        String selected_LUT = (String) table.getValueAt(selected_Row, 1);
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            ImagePlus lut_Image = IJ.createImage(WindowManager.makeUniqueName(selected_LUT), "8-bit ramp", 256, 32, 1);
            lut_Image.show();
            IJ.run(selected_LUT);
            return;
        }
        if (!imp.isRGB()) IJ.run(selected_LUT);
        else {
            ImagePlus lut_Image = IJ.createImage(WindowManager.makeUniqueName(selected_LUT), "8-bit ramp", 256, 32, 1);
            lut_Image.show();
            IJ.run(selected_LUT);
        }
    }

    public static ImageIcon get_Lut_Icon(String lut_Name) {
        ImagePlus lut_Image = IJ.createImage("LUT icon", "8-bit ramp", 256, 24, 1);
        ImageProcessor ip = lut_Image.getProcessor();
        ip.setColorModel(LutLoader.getLut(lut_Name));
        for (int x = 0; x < 256; x++) {
            int gray_Value = x;
            int shifted_Value = (gray_Value + 20);
            for (int y = 15; y < 23; y++) {
                int value = (x % 3 == 0 || x % 3 == 3) ? gray_Value : shifted_Value;
                ip.putPixel(x, y, value); // Stripes
            }
        }
        return new ImageIcon(ip.getBufferedImage());
    }

    public static String get_Lut_Colors(String lut_Name) {
        java.util.List<String> colors = new java.util.ArrayList<>();
        IndexColorModel lut = LutLoader.getLut(lut_Name);
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        lut.getBlues(blues);
        int step = 256 / 20;
        for (int i = 0; i < 256; i += step) {
            int red = reds[i] & 255;
            int green = greens[i] & 255;
            int blue = blues[i] & 255;
            String color_Name = get_Color_Name(red, green, blue);
            if (color_Name != null && !colors.contains(color_Name)) {
                colors.add(color_Name);
            }
        }
        return String.join(", ", colors);
    }

    public static String get_Color_Name(int r, int g, int b) {
        float[] hsv = Color.RGBtoHSB(r, g, b, null);
        float hue = hsv[0] * 360;  // Hue is between 0 and 1, so multiply by 360
        float saturation = hsv[1];  // Saturation between 0 and 1
        float value = hsv[2];  // Brightness (value) between 0 and 1
        // Detect black, gray, and white
        if (value < 0.05f) return "black";
        if (saturation < 0.15 && value >= 0.05 && value <= 0.9) return "gray";
        if (value > 0.9 && saturation < 0.12) return "white";
        // Classify color based on hue
        if ((hue >= 0 && hue <= 15) || (hue >= 340 && hue <= 360)) return "red";
        if (hue > 15 && hue <= 45) return "orange";
        if (hue > 45 && hue <= 75) return "yellow";
        if (hue > 75 && hue <= 150) return "green";
        if (hue > 150 && hue <= 200) return "cyan";
        if (hue > 200 && hue <= 260) return "blue";
        if (hue > 260 && hue < 340) return "magenta";
        return null;
    }

    public static String get_Lut_Infos(String lut_Name) {
        IndexColorModel lut = LutLoader.getLut(lut_Name);
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        lut.getBlues(blues);
        int[] lut_Luminance = get_Lutinance(reds, greens, blues);
        // Linearity
        String is_Linear = "Linear";
        int step = 20;
        java.util.List<Integer> luminance_Trend = new java.util.ArrayList<>();
        int n_Luminance_Shift = 0;
        if (lut_Luminance[2] >= lut_Luminance[0]) {
            for (int i = step; i < 256; i += step) {
                luminance_Trend.add((lut_Luminance[i] >= lut_Luminance[i - step]) ? 1 : -1);
            }
        } else {
            for (int i = step; i < 256; i += step) {
                luminance_Trend.add((lut_Luminance[i] <= lut_Luminance[i - step]) ? 1 : -1);
            }
        }
        int previous_Trend = luminance_Trend.get(0);
        for (int i = 1; i < luminance_Trend.size(); i++) {
            if (!luminance_Trend.get(i).equals(previous_Trend)) {
                is_Linear = "Non-uniform";
                n_Luminance_Shift++;
                previous_Trend = luminance_Trend.get(i);
            }
        }
        // Circularity
        String is_Cyclic = "Cyclic";
        int tolerance = 15;
        if (!(is_Near(reds[0], reds[255], tolerance) &&
            is_Near(greens[0], greens[255], tolerance) &&
            is_Near(blues[0], blues[255], tolerance))) {
            is_Cyclic = null;
        }
        // Isoluminance
        String is_Isoluminant = "Isoluminant";
        for (int i = 0; i < 256; i++) {
            if (Math.abs(lut_Luminance[i] - lut_Luminance[255]) > 40) {
                is_Isoluminant = null;
                break;
            }
        }
        if (is_Isoluminant != null) is_Linear = "Linear";
        if (lut_Name.equalsIgnoreCase("Blue")) is_Isoluminant = null; // pure blue is so dark..
        // Diverging
        String is_Diverging = "Diverging";
        if (is_Linear=="Linear") is_Diverging = null;
        else {
            for (int i = 0; i < 128; i++) {
                if ((Math.abs(lut_Luminance[i] - lut_Luminance[255 - i]) > 50)) {
                    is_Diverging = null;
                    break;
                }
            }
        }
        // Colorfulness
        String is_Multicolor = "Multicolor";
        String colors_String = get_Lut_Colors(lut_Name);
        String[] colors_Array = colors_String.split(", ");
        int color_Count = 0;
        for (String color : colors_Array) {
            if (!color.equalsIgnoreCase("black") && !color.equalsIgnoreCase("white") && !color.equalsIgnoreCase("gray")) {
                color_Count++;
            }
        }
        if (color_Count <= 1) is_Multicolor = "Monochrome";
        // Basic LUT
        String[] classic_LUTs = new String[]{"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Grays", "HiLo"};
        String is_Classic = null;
        for(int i = 0; i < classic_LUTs.length; i++) {
            if (classic_LUTs[i].equals(lut_Name)) is_Classic = "Basic";
        }
        // Build Description String
        StringBuilder infos = new StringBuilder();
        if (is_Classic != null)
            infos.append(is_Classic).append(", ");
        infos.append(is_Linear);
        if (is_Diverging != null && is_Isoluminant == null)
            infos.append(", ").append(is_Diverging);
        if (is_Isoluminant != null && lut_Luminance[0] != 0)
            infos.append(", ").append(is_Isoluminant);
        if (is_Cyclic != null)
            infos.append(", ").append(is_Cyclic);
        if (is_Isoluminant == null && is_Cyclic == null && is_Diverging == null)
            infos.append(", ").append((lut_Luminance[255] > lut_Luminance[0]) ? "ascending" : "inverted");
        infos.append(", ").append(IJ.pad(lut_Luminance[0],3)).append(".").append(IJ.pad(lut_Luminance[255],3)); // Add luminance bounds
//        infos.append(", ").append(is_Multicolor);
        return infos.toString();
    }
    
    public static int[] get_Lutinance(byte[] reds, byte[] greens, byte[] blues) {
        int[] lutinance = new int[256];
        for (int i = 0; i < 256; i++) {
            int[] rgb = new int[] {reds[i] & 0xFF, greens[i] & 0xFF, blues[i] & 0xFF};
            lutinance[i] = get_Luminance(rgb);
        }
        return lutinance;
    }

    public static int get_Luminance(int[] rgb) {
        float[] rgb_Weight = new float[] {0.299f, 0.587f, 0.114f};
        float luminance = 0f;
        for (int i = 0; i < 3; i++) {
            luminance += rgb[i] * rgb_Weight[i];
        }
        return (int)Math.round(luminance);
    }

    public static boolean is_Near(int a, int b, int tolerance) {
        return Math.abs(((256 + a) % 256) - ((256 + b) % 256)) <= tolerance;
    }

    public DocumentListener get_Filter_Listener() {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update_Filter(); }
            @Override public void removeUpdate(DocumentEvent e) { update_Filter(); }
            @Override public void changedUpdate(DocumentEvent e) { update_Filter(); }
        };
    }

    public void update_Filter() {
        String text = search_Bar.getText();
        Set<String> active_Colors = new HashSet<String>();
        for (int i = 0; i < color_Buttons.size(); i++) {
            if (color_Buttons.get(i).isSelected()) {
                active_Colors.add(color_Names[i]);
            }
        }
        if (text.trim().isEmpty() && active_Colors.isEmpty()) {
            table_Sorter.setRowFilter(null);
        } else {
            String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
            table_Sorter.setRowFilter(new RowFilter<Object, Object>() {
                @Override
                public boolean include(Entry<? extends Object, ? extends Object> entry) {
                    // Text filter
                    for (String word : words) {
                        boolean exclude = word.startsWith("!");
                        String keyword = exclude ? word.substring(1) : word;
                        boolean wordFound = false;
                        for (int column = 0; column < entry.getValueCount()-1; column++) {
                            String value = entry.getStringValue(column).toLowerCase();
                            if (exclude) {
                                if (value.contains(keyword.toLowerCase())) {
                                    return false;
                                }
                            } else {
                                if (value.contains(keyword.toLowerCase())) {
                                    wordFound = true;
                                    break;
                                }
                            }
                        }
                        if (!exclude && !wordFound) {
                            return false;
                        }
                    }
                    // Color filter (exclusive)
                    if (!active_Colors.isEmpty()) {
                        String colors = entry.getStringValue(4).toLowerCase();
                        List<String> lut_Colors = Arrays.asList(colors.split("\\s*,\\s*"));
                        boolean has_Allowed_Color = false;
                        for (String color : lut_Colors) {
                            if (active_Colors.contains(color)) {
                                has_Allowed_Color = true;
                            } else if (!color.equals("black") && !color.equals("white") && !color.equals("gray") && !color.equals("grey")) {
                                // Found a color that is NOT allowed
                                return false;
                            }
                        }
                        // Must have at least one allowed color
                        if (!has_Allowed_Color) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }
    }

    public static TransferHandler get_Transfer_Handler() {
        return new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                JTable table = (JTable) c;
                int row = table.getSelectedRow();
                if (row != -1) {
                    String lut_Name = table.getValueAt(row, 1).toString();
                    return new StringSelection(lut_Name);
                }
                return null;
            }
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        };
    }

    public static DefaultTableModel get_Table_Model(Object[][] table_Data) {
        return new DefaultTableModel(table_Data, COLUMN_NAMES) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Boolean.class;
                else return column == 2 ? ImageIcon.class : Object.class;
            }
        };
    }

    static class Star_Renderer extends DefaultTableCellRenderer {
        static final ImageIcon Filled_Star_Icon = drawStar(Color.ORANGE, true);
        static final ImageIcon Empty_Star_Icon = drawStar(Color.GRAY, false);

        static ImageIcon drawStar(Color color, boolean filled) {
            int w = 16, h = 16;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Polygon star = new Polygon();
            double cx = w / 2.0, cy = h / 2.0, r = w / 2.0 - 2;
            for (int i=0; i<5; i++) {
                double ang1 = Math.PI/2 + i * 2 * Math.PI / 5;
                double ang2 = ang1 + Math.PI / 5;
                star.addPoint((int)(cx + r * Math.cos(ang1)), (int)(cy - r * Math.sin(ang1)));
                star.addPoint((int)(cx + r * 0.5 * Math.cos(ang2)), (int)(cy - r * 0.5 * Math.sin(ang2)));
            }
            if (filled) {
                g.setColor(color);
                g.fillPolygon(star);
            } else {
                g.setColor(color);
                g.drawPolygon(star);
            }
            g.dispose();
            return new ImageIcon(img);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setOpaque(true);
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setIcon(Boolean.TRUE.equals(value) ? Filled_Star_Icon : Empty_Star_Icon);
            return label;
        }
    }


    //--------------------------------------------------------------------------------------------------------------------------------------
    //      LUTs Palettes
    //--------------------------------------------------------------------------------------------------------------------------------------
    final String save_Loc = IJ.getDirectory("luts")+"/LUT_Palette_Manager.csv";
    final int LUT_COUNT = 5;
    DefaultTableModel lut_Sets_Table_model;

    public JPanel get_LUT_Set_Manager_Panel() {
        Object[][] lut_Sets = load_Sets_From_File(save_Loc);
        if (lut_Sets == null) lut_Sets = get_Default_LUT_Sets();
        String[] column_Names = new String[LUT_COUNT+1];
        column_Names[0] = "Palette Name";
        for (int i=1; i<=LUT_COUNT; i++) column_Names[i] = "Channel " + i;
        DefaultTableModel model = new DefaultTableModel(lut_Sets, column_Names) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) { return (col > 0) ? ImageIcon.class : String.class; }
        };
        lut_Sets_Table_model = model;
        JTable sets_Table = new JTable(model){
            @Override
            public String getToolTipText(MouseEvent event) {
                java.awt.Point point = event.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);
                String tooltip = "";
                Object value = getValueAt(row, column);
                if (column == 0) tooltip = value.toString();
                else if (value != null) tooltip = ((ImageIcon)value).getDescription();
                else if (value == null) tooltip = "Grays";
                return tooltip;
            }
        };
        sets_Table.getTableHeader().setReorderingAllowed(false);
        for (int c=1; c<=LUT_COUNT; c++) {
            sets_Table.getColumnModel().getColumn(c).setCellRenderer(get_LUT_Cell_Renderer());
            sets_Table.getColumnModel().getColumn(c).setMinWidth(125);
        }
        sets_Table.setRowHeight(25);        
        sets_Table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setup_LUT_Cell_Drag_Drop(sets_Table, model);

        // Right-click popup for row up/down
        sets_Table.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int row = sets_Table.rowAtPoint(e.getPoint());
                    int col = sets_Table.columnAtPoint(e.getPoint());
                    if (row >= 0 && row < sets_Table.getRowCount()) {
                        sets_Table.setRowSelectionInterval(row, row);
                        show_Row_Popup(e, sets_Table, model, row, col);
                    }
                }
            }
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) apply_Selected_LUT_Set(sets_Table, model);
            }
        });

        sets_Table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "applySet");
        sets_Table.getActionMap().put("applySet", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { apply_Selected_LUT_Set(sets_Table, model); }
        });

        JButton apply_Btn = new JButton("Apply Palette");
        apply_Btn.setMargin(new Insets(2, 2, 2, 2));
        apply_Btn.addActionListener(e -> apply_Selected_LUT_Set(sets_Table, model));
        
        JButton apply_To_All_Btn = new JButton("Apply to all images");
                apply_To_All_Btn.setMargin(new Insets(2, 2, 2, 2));
        apply_To_All_Btn.addActionListener(e -> {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp==null) return;
            int[] wList = WindowManager.getIDList();
            for (int i=0; i<wList.length; i++) {
                IJ.selectWindow(wList[i]);
                apply_Selected_LUT_Set(sets_Table, model);
            }
        });

        JButton add_Btn = new JButton("New");
        add_Btn.setMargin(new Insets(2, 10, 2, 10));
        add_Btn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(sets_Table, "Name:");
            if (name != null && !name.trim().isEmpty()) {
                Object[] new_Set = new Object[LUT_COUNT+1];
                new_Set[0] = name;
                for (int i=1; i<=LUT_COUNT; i++) new_Set[i] = null;
                model.addRow(new_Set);
            }
        });

        JButton rename_Btn = new JButton("Rename");
        rename_Btn.setMargin(new Insets(2, 2, 2, 2));
        rename_Btn.addActionListener(e -> {
            int row = sets_Table.getSelectedRow();
            if (row >=0) {
                String name = JOptionPane.showInputDialog(sets_Table, "New Name:", model.getValueAt(row, 0));
                if (name != null && !name.trim().isEmpty()) model.setValueAt(name, row, 0);
            }
        });

        JButton delete_Btn = new JButton("Delete");
        delete_Btn.setMargin(new Insets(2, 4, 2, 4));
        delete_Btn.addActionListener(e -> {
            int row = sets_Table.getSelectedRow();
            if (row>=0) {
                int confirm = JOptionPane.showConfirmDialog(sets_Table, "Delete selected Palette?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) model.removeRow(row);
            }
        });

        // about button
        String help_Text = "<html><body style='background-color: #454545; color: #ffffff;'>" +
        "<h1 style='margin:6px 0 3px 0;'>LUT Manager</h1>" +
        "<h3 style='margin:4px 0 2px 0;'>Multichannel palettes</h3>" +
        "<ul style='margin:2px 0 6px 12px; padding:0;'>" +
        "  <li>Add a LUT to a palette by drag and drop from the LUTs Finder</li>" +
        "  <li>Change order of LUTs in a palette also by drag and drop</li>" +
        "  <li>Right click on a palette to move it up and down or to remove a LUT</li>" +
        "  <li>In a palette, empty channels will default to <strong>Grays</strong></li>" +
        "</ul>" +
        "<h3 style='margin:4px 0 2px 0;'>LUTs Finder</h3>" +
        "<p style='margin:2px 0 2px 0;'><strong>Applying a LUT:</strong><br>" +
        "  Double-Click on a LUT name or press <b>Enter</b> to apply the selected LUT to your image." +
        "</p>" +
        "<p style='margin:2px 0 2px 0;'><strong>About LUTs preview bands:</strong><br>" +
        "  The LUT images display bands of small value shift.<br>" +
        "  Check for uniformity in color transitions for good contrast visibility." +
        "</p>" +
        "<p><strong>Description:</strong></p>" +
        "<ul style='margin:2px 0 6px 12px; padding:0;'>" +
        "  <li><b>Basic:</b> Identifies classic 'pure' LUTs (Red, Green, Blue, Cyan, etc.).</li>" +
        "  <li><b>Linear, Non-uniform:</b> Whether the perceptual brightness progression is linear.</li>" +
        "  <li><b>Diverging:</b> Transitions from one color through a neutral midpoint to another color.</li>" +
        "  <li><b>Isoluminant:</b> Changes in color but keeps the luminance consistent across the LUT.</li>" +
        "  <li><b>Cyclic:</b> If the first and last colors are the same.</li>" +
        "</ul>";
        JButton help_Btn = new JButton("About");
        help_Btn.addActionListener(e -> new HTMLDialog("About", help_Text, false));
        
        JPanel top_Panel = new JPanel(new BorderLayout());
        JPanel button_Panel = new JPanel();
        button_Panel.add(apply_Btn);
        button_Panel.add(apply_To_All_Btn);
        button_Panel.add(add_Btn);
        button_Panel.add(rename_Btn);
        button_Panel.add(delete_Btn);
        button_Panel.add(help_Btn);
        top_Panel.add(new JLabel("   Multichannel palettes"), BorderLayout.WEST);
        top_Panel.add(button_Panel, BorderLayout.EAST);

        JPanel sets_Panel = new JPanel(new BorderLayout());
        sets_Panel.add(top_Panel, BorderLayout.NORTH);
        sets_Panel.add(new JScrollPane(sets_Table), BorderLayout.CENTER);
        return sets_Panel;
    }

    // --- Utility methods ---

    public Object[][] get_Default_LUT_Sets() {
        Object[][] sets = new Object[][] {
            make_LUT_Set("CMY", new String[]{"Cyan","Magenta","Yellow",null,null}),
        };
        return sets;
    }

    public Object[] make_LUT_Set(String name, String[] lut_Names) {
        Object[] set = new Object[LUT_COUNT+1];
        set[0] = name;
        for (int i=1; i<=LUT_COUNT; i++) set[i] = (i<=lut_Names.length && lut_Names[i-1]!=null) ? get_LUT_Set_Icon(lut_Names[i-1]) : null;
        return set;
    }

    public ImageIcon get_LUT_Set_Icon(final String lut_Name) {
        if (lut_Name==null) return null;
        ImagePlus imp = IJ.createImage("LUT_icon", "8-bit ramp", 120, 22, 1);
        ImageProcessor ip = imp.getProcessor();
        ip.setColorModel(LutLoader.getLut(lut_Name));
        ImageIcon icon = new ImageIcon(ip.getBufferedImage());
        icon.setDescription(lut_Name);
        return icon;
    }

    public TableCellRenderer get_LUT_Cell_Renderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable sets_Table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                if (value instanceof ImageIcon) {
                    JLabel label = new JLabel((ImageIcon)value);
                    label.setOpaque(true);
                    label.setBackground(isSelected ? sets_Table.getSelectionBackground() : sets_Table.getBackground());
                    return label;
                }
                JLabel label = new JLabel();
                label.setOpaque(true);
                label.setBackground(isSelected ? sets_Table.getSelectionBackground() : sets_Table.getBackground());
                return label;
            }
        };
    }

    public String get_LUT_Name_At(Object cell) {
        if (cell instanceof ImageIcon) {
            String desc = ((ImageIcon)cell).getDescription();
            return desc != null ? desc : "Grays";
        }
        return null;
    }

    public void apply_Selected_LUT_Set(JTable sets_Table, DefaultTableModel model) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp==null) return;
        if (imp.getBitDepth()==24) return;
        if (imp.getNChannels()>1 && !imp.isComposite()) return; // HSB stack or like
        int row = sets_Table.getSelectedRow();
        if (row < 0) row = 0;
        String[] lut_Names = new String[LUT_COUNT];
        for (int c=1; c<=LUT_COUNT; c++) {
            String lut_Name = get_LUT_Name_At(model.getValueAt(row, c));
            lut_Names[c-1] = (lut_Name != null) ? lut_Name : "Grays";
        }
        int nChannels = imp.getNChannels();
        for (int i=0; i<nChannels; i++) {
            String lut_Name = (i<LUT_COUNT) ? lut_Names[i] : "Grays";
            apply_LUT_Set(imp, lut_Name, i+1);
        }
        imp.updateAndDraw();
    }

    public void apply_LUT_Set(ImagePlus imp, String lut_Name, int channel) {
        LUT[] luts = imp.getLuts();
        double min = luts[channel-1].min;
        double max = luts[channel-1].max;
        luts[channel-1] = new LUT(LutLoader.getLut(lut_Name), min, max);
        if (!imp.isComposite()) imp.setLut(luts[0]);
        else ((CompositeImage)imp).setLuts(luts);
    }

    public Object[][] load_Sets_From_File(String file_Path) {
        File file = new File(file_Path);
        if (!file.exists()) return null;
        try {
            java.util.List<Object[]> list = new java.util.ArrayList<Object[]>();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] vals = parse_CSV_Line(line);
                Object[] set = new Object[LUT_COUNT + 1];
                set[0] = vals[0];
                for (int i = 1; i <= LUT_COUNT; i++) {
                    if (i < vals.length && vals[i] != null && !vals[i].isEmpty()) {
                        set[i] = get_LUT_Set_Icon(vals[i]);
                    } else {
                        set[i] = null;
                    }
                }
                list.add(set);
            }
            reader.close();
            if (list.isEmpty()) return null;
            return list.toArray(new Object[0][0]);
        } catch (Exception ex) {
            IJ.error("Auto load LUT sets failed:\n" + ex);
            return null;
        }
    }

    public void export_Lut_Sets() {
        try {
            FileWriter fw = new FileWriter(save_Loc);
            fw.write("SetName");
            for (int i = 1; i <= LUT_COUNT; i++) fw.write(",LUT" + i);
            fw.write("\n");
            for (int row = 0; row < lut_Sets_Table_model.getRowCount(); row++) {
                fw.write("\"" + lut_Sets_Table_model.getValueAt(row, 0).toString().replace("\"", "\"\"") + "\"");
                for (int col = 1; col <= LUT_COUNT; col++) {
                    String lut_Name = get_LUT_Name_At(lut_Sets_Table_model.getValueAt(row, col));
                    if (lut_Name == null) fw.write(",");
                    else fw.write(",\"" + lut_Name.replace("\"", "\"\"") + "\"");
                }
                fw.write("\n");
            }
            fw.close();
            return;
        } catch (Exception ex) {
            IJ.error("Export LUT sets failed:\n" + ex);
            return;
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
    
    public void setup_LUT_Cell_Drag_Drop(final JTable sets_table, final DefaultTableModel model) {
        // DRAG: can copy LUT name from outside; drop on LUT columns assigns LUT
        sets_table.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport support) {
                JTable.DropLocation loc = (JTable.DropLocation)support.getDropLocation();
                return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                       && loc.getColumn()>=1 && loc.getColumn()<=LUT_COUNT;
            }
            public boolean importData(TransferSupport support) {
                try {
                    JTable.DropLocation loc = (JTable.DropLocation)support.getDropLocation();
                    int row = loc.getRow();
                    int col = loc.getColumn();
                    String lutName = (String)support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    model.setValueAt(get_LUT_Set_Icon(lutName), row, col);
                    export_Lut_Sets();
                    return true;
                } catch (Exception ex) {
                    IJ.error("Drop failed:\n"+ex);
                    return false;
                }
            }
        });
        sets_table.setDragEnabled(true);
        
        // Mouse: in-row swap
        final int[] dragInfo = new int[2]; // [row, col]
        dragInfo[0] = -1;
        dragInfo[1] = -1;
        sets_table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                dragInfo[0] = sets_table.rowAtPoint(p);
                dragInfo[1] = sets_table.columnAtPoint(p);
                sets_table.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                Point p = e.getPoint();
                int dropRow = sets_table.rowAtPoint(p);
                int dropCol = sets_table.columnAtPoint(p);
                int startRow = dragInfo[0];
                int startCol = dragInfo[1];
                if (startRow == dropRow && startCol != dropCol
                    && startCol > 0 && startCol <= LUT_COUNT && dropCol > 0 && dropCol <= LUT_COUNT) {
                    Object temp1 = model.getValueAt(startRow, startCol);
                    Object temp2 = model.getValueAt(dropRow, dropCol);
                    model.setValueAt(temp2, startRow, startCol);
                    model.setValueAt(temp1, dropRow, dropCol);
                }
                dragInfo[0] = dragInfo[1] = -1;
                sets_table.setCursor(Cursor.getDefaultCursor());
                export_Lut_Sets();
            }
        });
    }
    
    // --- Right-click popup for row up/down ---
    public void show_Row_Popup(MouseEvent e, JTable table, DefaultTableModel model, int row, int col) {
        JPopupMenu popup = new JPopupMenu();
        // up
        JMenuItem up = new JMenuItem("Move palette up");
        up.addActionListener(event -> move_Row(model, row, row-1));
        up.setEnabled(row > 0);
        popup.add(up);
        // down
        JMenuItem down = new JMenuItem("Move palette down");
        down.addActionListener(event -> move_Row(model, row, row+1));
        down.setEnabled(row < model.getRowCount()-1);
        popup.add(down);
        // remove LUT
        JMenuItem remove = new JMenuItem("Remove LUT");
        remove.addActionListener(event -> {
            model.setValueAt(null, row, col);
            export_Lut_Sets();
        });
        remove.setEnabled(col > 0);
        popup.add(remove);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    public void move_Row(DefaultTableModel model, int from, int to) {
        if (to < 0 || to >= model.getRowCount() || from == to) return;
        Object[] rowData = new Object[model.getColumnCount()];
        for (int i=0; i<model.getColumnCount(); i++) rowData[i] = model.getValueAt(from, i);
        model.removeRow(from);
        model.insertRow(to, rowData);
        export_Lut_Sets();
    }
}
