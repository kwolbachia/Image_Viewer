/*
Kevin Terretaz
kevinterretaz@gmail.com
251116 fix rectangle cmd key for macs 
260125 change how to open image from preview opener : add the possibility to double click.
260322 add cheat sheet when double click the tool 
260412 add fly mode when not stack
       add invert LUT command in fav luts panel

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
import ij.gui.*;
import ij.plugin.tool.PlugInTool;
import ij.plugin.frame.ContrastAdjuster;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.*;
import java.io.File;

 // Toolbar.addPlugInTool(new Multi_Tool())
public class Multi_Tool extends PlugInTool {
    Integer SCREEN_START_X, SCREEN_START_Y, WINDOW_START_X, WINDOW_START_Y, FLAGS, PIXEL_X0, PIXEL_Y0, PIXEL_X, PIXEL_Y;
    Integer BITDEPTH, CHANNEL, NSLICES, NFRAMES; 
    double ORIGINAL_MIN, ORIGINAL_MAX;
    boolean REMOVE_ROI, LEFT, MIDDLE, CTRL, ALT, SHIFT, REMOVE_CONTRAST_ROI;
    ImageCanvas CANVAS;
    java.awt.Rectangle AREA;
    long LAST_UPDATE = 0;
    long REFRESH_INTERVAL = 20;
    
    @Override
    public void showOptionsDialog() {
        String html =
        "<html><head><style>" +
        "table { border-collapse: collapse; width: 100%; }" +
        "th, td { border: 1px solid #888888; padding: 3px 5px; }" +
        "th { background-color: #333333; }" +
        "</style></head>" +
        "<body style='background-color: #454545; color: #eeeeee;'>" +
        "<table>" +
        "<tr><th>Mouse & Keys</th><th>Action</th></tr>" +
        "<tr><td>Left Drag (no ROI)</td><td>Move image window</td></tr>" +
        "<tr><td>Double Left Click</td><td>Maximize / minimize image window</td></tr>" +
        "<tr><td>Middle Click (wheel)</td><td>Composite display toggle / Preview opener open</td></tr>" +
        "<tr><td>Ctrl + Left Drag</td><td>Create rectangle ROI</td></tr>" +
        "<tr><td>Ctrl + Click out of ROI</td><td>Remove current ROI</td></tr>" +
        "<tr><td>Shift + Alt + Left Click</td><td>Box auto-contrast</td></tr>" +
        "<tr><td>Shift + Left Drag</td><td>Live contrast adjustment</td></tr>" +
        "<tr><td>Alt + Left Drag</td><td>Stack / frame scroll</td></tr>" +
        "<tr><td>Full Documentation</td><td><a href='https://imagej.net/plugins/image-viewer#multi-tool' style='color: #ff9955;'>Image Viewer wiki</a></td></tr>"+
        "</table></body></html>";
        new HTMLDialog("Multi Tool cheat sheet", html, false); 
    }

    @Override
    public void mouseMoved(ImagePlus imp, MouseEvent e) {
        get_FLAGS(e);
        CANVAS = imp.getWindow().getCanvas();
        PIXEL_X = CANVAS.offScreenX(e.getX());
        PIXEL_Y = CANVAS.offScreenY(e.getY());
        Roi roi = imp.getRoi();
        int handle = roi!=null?roi.isHandle((int)e.getX(), (int)e.getY()):-1;
        if (handle >= 0) CANVAS.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (imp.getTitle().matches(".*Preview Opener.*")) set_Opener_Label(imp);
    }

    @Override
    public void mousePressed(ImagePlus imp, MouseEvent e) {
        // Image infos
        CANVAS = imp.getWindow().getCanvas();
        AREA = CANVAS.getSrcRect(); // Get visible AREA
        BITDEPTH = imp.getBitDepth();
        CHANNEL =  imp.getChannel();
        NSLICES =  imp.getNSlices();
        NFRAMES =  imp.getNFrames();
        WINDOW_START_X = imp.getWindow().getX();
        WINDOW_START_Y = imp.getWindow().getY();
        // Mouse, and keys infos
        PIXEL_X0 = imp.getCanvas().offScreenX(e.getX());
        PIXEL_Y0 = imp.getCanvas().offScreenY(e.getY());
        SCREEN_START_X = e.getXOnScreen();
        SCREEN_START_Y = e.getYOnScreen();
        get_FLAGS(e);
        REMOVE_ROI = false;
        // box auto_contrast
        if ((LEFT && SHIFT && ALT && !CTRL) && BITDEPTH != 24){ // SHIFT + ALT + LEFT
            int size = 75;
            imp.setRoi((int)PIXEL_X0 - (int)(size/2), (int)PIXEL_Y0 - (int)(size/2), size, size);
            REMOVE_CONTRAST_ROI = true;
            IJ.run("Reset Display", "CHANNEL=0");
            REMOVE_ROI = true;
        }
        // Preview Opener
        if ((e.getClickCount() == 2 || MIDDLE) && imp.getTitle().matches(".*Preview Opener.*")) {
            open_From_Preview_Opener(imp);
        }
        // composite switch
        if ((MIDDLE && !SHIFT && !CTRL && !ALT) && imp.isComposite()) { // MIDDLE click on composite image
            int mode = imp.getCompositeMode();
            if (mode == IJ.COLOR || mode == IJ.GRAYSCALE) imp.setDisplayMode(IJ.COMPOSITE); // <3
            else imp.setDisplayMode(IJ.COLOR);
        }
        // Full screen
        if ((LEFT && !SHIFT && !ALT && !CTRL) && e.getClickCount() == 2 && !imp.getTitle().matches(".*Preview Opener.*")){ // double click : reversible maximize
            LEFT = false;       // prevent triggering of "move window"
            fullScreen(imp);
        }
        // roi
        Roi roi = imp.getRoi();
        int handle = roi!=null?roi.isHandle((int)e.getX(), (int)e.getY()):-1;
        if (LEFT && handle >= 0 && (roi instanceof PolygonRoi)) {
            callProtectedMethod(roi, "mouseDownInHandle", new Class[]{int.class, int.class, int.class}, new Object[]{handle, (int)e.getX(), (int)e.getY()});
            return;
        }
        //  fly mode, move the cursor to relative position
        if ((LEFT && ALT && !SHIFT && !CTRL) && NSLICES*NFRAMES==1 && handle == -1) { // ALT + LEFT NOT on Z stack 
            Point canvasLoc = CANVAS.getLocationOnScreen();
            int cursor_canvas_x = (int)(AREA.x + ((AREA.x + AREA.width  / 2.0) / imp.getWidth())  * AREA.width);
            int cursor_canvas_y = (int)(AREA.y + ((AREA.y + AREA.height / 2.0) / imp.getHeight()) * AREA.height);
            int screenX = canvasLoc.x + CANVAS.screenX(cursor_canvas_x);
            int screenY = canvasLoc.y + CANVAS.screenY(cursor_canvas_y);
            move_Mouse(new Point(screenX, screenY));
        }
        if(CTRL && handle == -1) REMOVE_ROI = true;
    }
    
    @Override
    public void mouseDragged(ImagePlus imp, MouseEvent e) {
        long now = System.currentTimeMillis();
        int window_x = e.getX();
        int window_y = e.getY();
        PIXEL_X = CANVAS.offScreenX(window_x);
        PIXEL_Y = CANVAS.offScreenY(window_y);
        Roi roi = imp.getRoi();
        
        int roi_State = roi != null ? roi.getState() : Roi.NORMAL;
        int handle = roi!=null?roi.isHandle(window_x, window_y):-1;
        Boolean hover_Roi = roi!=null?roi.contains(PIXEL_X, PIXEL_Y):false;
        // rectangle roi
        if (LEFT && !SHIFT && CTRL && !ALT && handle == -1){ // CTRL + LEFT drag
            int x = Math.min(PIXEL_X0, PIXEL_X);
            int y = Math.min(PIXEL_Y0, PIXEL_Y);
            int w = Math.abs(PIXEL_X - PIXEL_X0);
            int h = Math.abs(PIXEL_Y - PIXEL_Y0);
            imp.setRoi(new Roi(x, y, w, h));
            REMOVE_ROI = false;
            return;
        }
        //java
        if (LEFT && handle >= 0 && !SHIFT&& !CTRL) {
            callProtectedMethod(roi, "mouseDownInHandle", new Class[]{int.class, int.class, int.class}, new Object[]{handle, window_x, window_y});
            return;
        }
        if (LEFT && !SHIFT && !CTRL && !ALT && roi != null && hover_Roi) {
            callProtectedMethod(roi, "handleMouseDown", new Class[]{int.class, int.class}, new Object[]{window_x, window_y});
            return;
        }
        // Live contrast adjustment
        if ((LEFT && SHIFT && !CTRL && !ALT) && BITDEPTH != 24 && !hover_Roi) { // Shift + Drag on not rgb image 
            if (now - LAST_UPDATE < REFRESH_INTERVAL) return;
            LAST_UPDATE = now;
            imp.resetDisplayRange();
            ORIGINAL_MIN = imp.getDisplayRangeMin();
            ORIGINAL_MAX = imp.getDisplayRangeMax();
            java.awt.Point loc = CANVAS.getCursorLoc();
            int x = loc.x, y = loc.y;
            double newMax = ((x - AREA.x) / (double)AREA.width) * ORIGINAL_MAX;
            double newMin = ((AREA.height - (y - AREA.y)) / (double)AREA.height) * (ORIGINAL_MAX / 2);
            if (BITDEPTH != 32){
                if (newMax < 0) newMax = 0;
                if (newMin < 0) newMin = 0;
            }
            if (newMin > newMax) newMin = newMax;
            imp.setDisplayRange(newMin, newMax);
            imp.updateAndDraw();
            return;
        }
        // live scroll
        if ((LEFT && ALT && !SHIFT && !CTRL) && NSLICES*NFRAMES!=1 && handle == -1) { // ALT + LEFT on stack
            if (now - LAST_UPDATE < REFRESH_INTERVAL) return;
            LAST_UPDATE = now;
            java.awt.Point loc = CANVAS.getCursorLoc();
            int x = loc.x, y = loc.y;
            if (NFRAMES > 1) {
                int n = (int) (((x - AREA.x) / (double) AREA.width) * NFRAMES);
                imp.setPosition(imp.getChannel(), imp.getSlice(), n);
            } 
            else {
                int n = (int) (((x - AREA.x) / (double) AREA.width) * NSLICES);
                imp.setPosition(imp.getChannel(), n, imp.getFrame());
            }
        }
        // fly mode
        if ((LEFT && ALT && !SHIFT && !CTRL) && NSLICES*NFRAMES==1 && handle == -1) { // ALT + LEFT NOT on Z stack
            java.awt.Point loc = CANVAS.getCursorLoc();
            int x = loc.x, y = loc.y;
            double x_rate = (x - AREA.x) / (double) AREA.width;
            double y_rate = (y - AREA.y) / (double) AREA.height;
            double new_x = x_rate * imp.getWidth();
            double new_y = y_rate * imp.getHeight();
            CANVAS.setSourceRect(new Rectangle((int)(new_x - (AREA.width/2)),(int)(new_y- (AREA.height/2)),(int)AREA.width,(int)AREA.height));
            CANVAS.repaint();
        }
        // Normal drag, not on roi (move window)
        else if ((LEFT && !SHIFT && !ALT && !CTRL) && roi_State==Roi.NORMAL) {
            int x = WINDOW_START_X - SCREEN_START_X + e.getXOnScreen();
            int y = WINDOW_START_Y - SCREEN_START_Y + e.getYOnScreen();
            imp.getWindow().setLocation(x, y);
        }
    }
    // source: https://stackoverflow.com/questions/2941324/how-do-i-set-the-position-of-the-mouse-in-java
    public void move_Mouse(Point p) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();

        // Search the devices for the one that draws the specified point.
        for (GraphicsDevice device: gs) { 
            GraphicsConfiguration[] configurations =
                device.getConfigurations();
            for (GraphicsConfiguration config: configurations) {
                Rectangle bounds = config.getBounds();
                if(bounds.contains(p)) {
                    // Set point to screen coordinates.
                    Point b = bounds.getLocation(); 
                    Point s = new Point((int)(p.x - b.x), (int)(p.y - b.y));

                    try {
                        Robot r = new Robot(device);
                        r.mouseMove((int)s.x, (int)s.y);
                    } catch (AWTException e) {
                        e.printStackTrace();
                    }

                    return;
                }
            }
        }
        // Couldn't move to the point, it may be off screen.
        return;
    }

    @Override
    public void mouseReleased(ImagePlus imp, MouseEvent e) {
        if (REMOVE_ROI) imp.deleteRoi();
    }
    
    void get_FLAGS(MouseEvent e){
        FLAGS  = e.getModifiersEx();
        LEFT   = (FLAGS & MouseEvent.BUTTON1_DOWN_MASK) != 0;
        MIDDLE = (FLAGS & MouseEvent.BUTTON2_DOWN_MASK) != 0;
        SHIFT  = (FLAGS & MouseEvent.SHIFT_DOWN_MASK) != 0;
        CTRL   = (FLAGS & MouseEvent.CTRL_DOWN_MASK) != 0 || (FLAGS & MouseEvent.META_DOWN_MASK) != 0;
        ALT    = (FLAGS & MouseEvent.ALT_DOWN_MASK) != 0;
    }
    
    void open_From_Preview_Opener(ImagePlus imp) {
        String info = (String) imp.getProperty("Info");
        String[] path_List = info.split(",,");
        int rows = Integer.parseInt(imp.getProp("xMontage"));
        Point point = CANVAS.getCursorLoc();
        int line_Position = (int) Math.floor(point.y / 400);
        int row_Position = (int) Math.floor(point.x / 400);
        int index = (line_Position * rows) + row_Position;
        if (index >= path_List.length - 1) return;
        String path = IJ.getDirectory("image") + path_List[index];
        File file = new File(path);
        if (file.exists()) {
            new Thread(new Runnable() {
                public void run() {
                    if (path.endsWith(".tif") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith("jpeg")) {
                        if (!Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK)) {
                            IJ.open(path);
                        }
                        else {
                            IJ.run("TIFF Virtual Stack...", "open=[" + path + "]");
                        }
                    }
                    else {
                        IJ.run("Bio-Formats Importer", "open=[" + path + "]");
                    }
                    IJ.showStatus("opening " + path_List[index]);
                    
                }
            }).start();
        }
        else {
            IJ.showStatus("can't open " + path_List[index] + " maybe incorrect name or spaces in it?");
        }  
    }
    
    void set_Opener_Label(ImagePlus imp) {
        String previous_Label = imp.getProp("Slice_Label");
        String info = (String) imp.getProperty("Info");
        String[] path_List = info.split(",,");
        int rows = Integer.parseInt(imp.getProp("xMontage"));
        Point point = imp.getWindow().getCanvas().getCursorLoc();
        int line_Position = (int) Math.floor(point.y / 400);
        int row_Position = (int) Math.floor(point.x / 400);
        int index = (line_Position * rows) + row_Position;
        if (index >= path_List.length - 1) return;
        if (!path_List[index].equals(previous_Label)) {
            imp.setProp("Slice_Label", path_List[index]);
            imp.repaintWindow();
        }
    }

    void fullScreen(ImagePlus imp) {
        int x = 0, y = 0, w = 0, h = 0;
        ImageWindow win = imp.getWindow();
        Object is_maximized = imp.getProperty("is maximized");
        if (!(is_maximized instanceof Boolean && ((Boolean) is_maximized))) {
            Point loc = win.getLocation();
            Dimension size = win.getSize();
            x = loc.x;
            y = loc.y;
            w = size.width;
            h = size.height;
            win.maximize();
            imp.setProperty("is maximized", Boolean.TRUE);
            imp.setProperty("backup.x", x);
            imp.setProperty("backup.y", y);
            imp.setProperty("backup.w", w);
            imp.setProperty("backup.h", h);
        } else {
            imp.setProperty("is maximized", Boolean.FALSE);
            x = (Integer) imp.getProperty("backup.x");
            y = (Integer) imp.getProperty("backup.y");
            w = (Integer) imp.getProperty("backup.w");
            h = (Integer) imp.getProperty("backup.h");
            win.setLocationAndSize(x, y, w, h);
        }
        CANVAS.setSourceRect(new Rectangle(0, 0, imp.getWidth(), imp.getHeight()));
        CANVAS.repaint();
    }
    
    void callProtectedMethod(Roi roi, String methodName, Class[] paramTypes, Object[] args) {
        try {
            java.lang.reflect.Method m = roi.getClass().getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            m.invoke(roi, args);
        } catch (Exception ex) {
            // IJ.log("Could not call " + methodName + " on " + roi.getClass() + ": " + ex);
        }
    }

    @Override
    public String getToolName() {
        return "Multi Tool"; 
    }
    
    @Override
    public String getToolIcon() {
        return "N44C000D0cD0dD0eD1dD1eD1fD2aD2eD2fD3aD3bD3eD3fD4aD4bD4cD4dD4eD4fD4gD5bD5cD5dD5eD5fD5gD5hD6fD6gD6hD6iD7gD7hD7iD7jD83D84D85D86D87D88D89D8aD8bD8cD8dD8eD8fD8gD8hD8iD8jD8kD8lD92D93D9lD9mDa1Da2DamDanDb1DbnDc1DcnDd1DdnDe1De2DemDenDf2Df3DflDfmDg3Dg4Dg5Dg6Dg7Dg8Dg9DgaDgbDgcDgdDgeDgfDggDghDgiDgjDgkDh6Dh7Dh8Dh9DhaDhbDhcDi7Di8Di9DiaDicDidDj8Dj9DjaDjbDjdDjeDk9DkaDkbDkcDkdDkeDkfDlbDlcDldDleDlfDlgDmdDmeDmfDmgDmhCfffDa8Db8Dc6Dc7Dc8Dc9DcaDd8De8DibDjcC3caD94D95D96D97D98D99D9aD9bD9cD9dD9eD9fD9gD9hD9iD9jD9kDa3Da4Da5Da6Da7Da9DaaDabDacDadDaeDafDagDahDaiDajDakDalDb2Db3Db4Db5Db6Db7Db9DbaDbbDbcDbdDbeDbfDbgDbhDbiDbjDbkDblDbmDc2Dc3Dc4Dc5DcbDccDcdDceDcfDcgDchDciDcjDckDclDcmDd2Dd3Dd4Dd5Dd6Dd7Dd9DdaDdbDdcDddDdeDdfDdgDdhDdiDdjDdkDdlDdmDe3De4De5De6De7De9DeaDebDecDedDeeDefDegDehDeiDejDekDelDf4Df5Df6Df7Df8Df9DfaDfbDfcDfdDfeDffDfgDfhDfiDfjDfk";
    }
}
