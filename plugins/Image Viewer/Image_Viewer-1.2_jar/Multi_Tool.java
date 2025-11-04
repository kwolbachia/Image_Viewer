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
import ij.gui.*;
import ij.plugin.tool.PlugInTool;
import ij.plugin.frame.ContrastAdjuster;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.*;
import java.io.File;

public class Multi_Tool extends PlugInTool {
    Integer screen_start_X, screen_start_Y, window_Start_X, window_Start_Y, flags, pixel_x0, pixel_y0, pixel_x, pixel_y;
    Integer bitDepth, channel, nSlices, nFrames; 
    double original_Min, original_Max;
    boolean remove_Roi, left, middle, ctrl, alt, shift, remove_contrast_Roi;
    ImageCanvas canvas;
    java.awt.Rectangle area;
    long lastUpdate = 0;
    long refreshInterval = 20;

    @Override
    public void mouseMoved(ImagePlus imp, MouseEvent e) {
        get_Flags(e);
        canvas = imp.getWindow().getCanvas();
        pixel_x = canvas.offScreenX(e.getX());
        pixel_y = canvas.offScreenY(e.getY());
        Roi roi = imp.getRoi();
        int handle = roi!=null?roi.isHandle((int)e.getX(), (int)e.getY()):-1;
        if (handle >= 0) canvas.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (imp.getTitle().matches(".*Preview Opener.*")) set_Opener_Label(imp);
    }

    @Override
    public void mousePressed(ImagePlus imp, MouseEvent e) {
        // Image infos
        canvas = imp.getWindow().getCanvas();
        area = canvas.getSrcRect(); // Get visible area
        bitDepth = imp.getBitDepth();
        channel =  imp.getChannel();
        nSlices =  imp.getNSlices();
        nFrames =  imp.getNFrames();
        window_Start_X = imp.getWindow().getX();
        window_Start_Y = imp.getWindow().getY();
        // Mouse, and keys infos
        pixel_x0 = imp.getCanvas().offScreenX(e.getX());
        pixel_y0 = imp.getCanvas().offScreenY(e.getY());
        screen_start_X = e.getXOnScreen();
        screen_start_Y = e.getYOnScreen();
        get_Flags(e);
        remove_Roi = false;
        // box auto_contrast
        if ((left && shift && alt && !ctrl) && bitDepth != 24){ // shift + alt + left
            int size = 75;
            imp.setRoi((int)pixel_x0 - (int)(size/2), (int)pixel_y0 - (int)(size/2), size, size);
            remove_contrast_Roi = true;
            IJ.run("Reset Display", "channel=0");
            remove_Roi = true;
        }
        // Preview Opener
        if ((middle && !shift && !ctrl && !alt) && imp.getTitle().matches(".*Preview Opener.*")) { // middle click on a Preview Opener
            open_From_Preview_Opener(imp);
        }
        // composite switch
        if ((middle && !shift && !ctrl && !alt) && imp.isComposite()) { // middle click on composite image
            int mode = imp.getCompositeMode();
            if (mode == IJ.COLOR || mode == IJ.GRAYSCALE) imp.setDisplayMode(IJ.COMPOSITE); // <3
            else imp.setDisplayMode(IJ.COLOR);
        }
        // Full screen
        if ((left && !shift && !alt && !ctrl) &&e.getClickCount()==2){ // double click : reversible maximize
            left = false;       // prevent triggering of "move window"
            fullScreen(imp);
        }
        Roi roi = imp.getRoi();
        int handle = roi!=null?roi.isHandle((int)e.getX(), (int)e.getY()):-1;
        if (left && handle >= 0 && (roi instanceof PolygonRoi)) {
            callProtectedMethod(roi, "mouseDownInHandle", new Class[]{int.class, int.class, int.class}, new Object[]{handle, (int)e.getX(), (int)e.getY()});
            return;
        }
        if(ctrl && handle == -1) remove_Roi = true;
    }
    
    @Override
    public void mouseDragged(ImagePlus imp, MouseEvent e) {
        long now = System.currentTimeMillis();
        int window_x = e.getX();
        int window_y = e.getY();
        pixel_x = canvas.offScreenX(window_x);
        pixel_y = canvas.offScreenY(window_y);
        Roi roi = imp.getRoi();
        
        int roi_State = roi != null ? roi.getState() : Roi.NORMAL;
        int handle = roi!=null?roi.isHandle(window_x, window_y):-1;
        Boolean hover_Roi = roi!=null?roi.contains(pixel_x, pixel_y):false;
        // rectangle roi
        if (left && !shift && ctrl && !alt && handle == -1){ // ctrl + left drag
            int x = Math.min(pixel_x0, pixel_x);
            int y = Math.min(pixel_y0, pixel_y);
            int w = Math.abs(pixel_x - pixel_x0);
            int h = Math.abs(pixel_y - pixel_y0);
            imp.setRoi(new Roi(x, y, w, h));
            remove_Roi = false;
            return;
        }
        //java
        if (left && handle >= 0 && !shift&& !ctrl) {
            callProtectedMethod(roi, "mouseDownInHandle", new Class[]{int.class, int.class, int.class}, new Object[]{handle, window_x, window_y});
            return;
        }
        if (left && !shift && !ctrl && !alt && roi != null && hover_Roi) {
            callProtectedMethod(roi, "handleMouseDown", new Class[]{int.class, int.class}, new Object[]{window_x, window_y});
            return;
        }
        // Live contrast adjustment
        if ((left && shift && !ctrl && !alt) && bitDepth != 24 && !hover_Roi) { // Shift + Drag on not rgb image 
            if (now - lastUpdate < refreshInterval) return;
            lastUpdate = now;
            imp.resetDisplayRange();
            original_Min = imp.getDisplayRangeMin();
            original_Max = imp.getDisplayRangeMax();
            java.awt.Point loc = canvas.getCursorLoc();
            int x = loc.x, y = loc.y;
            double newMax = ((x - area.x) / (double)area.width) * original_Max;
            double newMin = ((area.height - (y - area.y)) / (double)area.height) * (original_Max / 2);
            if (bitDepth != 32){
                if (newMax < 0) newMax = 0;
                if (newMin < 0) newMin = 0;
            }
            if (newMin > newMax) newMin = newMax;
            imp.setDisplayRange(newMin, newMax);
            imp.updateAndDraw();
            return;
        }
        // live scroll
        if ((left && alt && !shift && !ctrl) && nSlices*nFrames!=1 && handle == -1) { // alt + left on stack
            if (now - lastUpdate < refreshInterval) return;
            lastUpdate = now;
            java.awt.Point loc = canvas.getCursorLoc();
            int x = loc.x, y = loc.y;
            if (nFrames > 1) {
                int n = (int) (((x - area.x) / (double) area.width) * nFrames);
                imp.setPosition(imp.getChannel(), imp.getSlice(), n);
            } 
            else {
                int n = (int) (((x - area.x) / (double) area.width) * nSlices);
                imp.setPosition(imp.getChannel(), n, imp.getFrame());
            }
        }
        // Normal drag, not on roi (move window)
        else if ((left && !shift && !alt && !ctrl) && roi_State==Roi.NORMAL) {
            int x = window_Start_X - screen_start_X + e.getXOnScreen();
            int y = window_Start_Y - screen_start_Y + e.getYOnScreen();
            imp.getWindow().setLocation(x, y);
        }
    }
    
    @Override
    public void mouseReleased(ImagePlus imp, MouseEvent e) {
        if (remove_Roi) imp.deleteRoi();
    }
    
    void get_Flags(MouseEvent e){
        flags  = e.getModifiersEx();
        left   = (flags & MouseEvent.BUTTON1_DOWN_MASK) != 0;
        middle = (flags & MouseEvent.BUTTON2_DOWN_MASK) != 0;
        shift  = (flags & MouseEvent.SHIFT_DOWN_MASK) != 0;
        ctrl   = (flags & MouseEvent.CTRL_DOWN_MASK) != 0;
        alt    = (flags & MouseEvent.ALT_DOWN_MASK) != 0;
    }
    
    void open_From_Preview_Opener(ImagePlus imp) {
        String info = (String) imp.getProperty("Info");
        String[] path_List = info.split(",,");
        int rows = Integer.parseInt(imp.getProp("xMontage"));
        Point point = canvas.getCursorLoc();
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
        canvas.setSourceRect(new Rectangle(0, 0, imp.getWidth(), imp.getHeight()));
        canvas.repaint();
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


