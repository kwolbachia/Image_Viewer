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
import ij.plugin.PlugIn;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import ij.process.LUT;

public class Reset_Display implements PlugIn {
    @Override
    public void run(String arg) {
        // Get current image - abort if null or RGB (24-bit)
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) { IJ.noImage(); return; }
        if (imp.getBitDepth() == 24) return;
        int nChannels = imp.getNChannels();
        int nSlices = imp.getNSlices();
        int nFrames = imp.getNFrames();
        int bitDepth = imp.getBitDepth();
        Roi roi = imp.getRoi();
        boolean[] active_Channels = null;
        // For composite, get active channels
        if (imp instanceof CompositeImage) active_Channels = ((CompositeImage)imp).getActiveChannels();

        // Parse macro arg for channel restriction (default: all)
        int channel_Restriction = 0; // 0 = all channels
        boolean channel_Restriction_Set = false;
        try {
            if (arg != null && !arg.isEmpty()) {
                channel_Restriction = Integer.parseInt(arg.trim());
                channel_Restriction_Set = true;
            }
        } catch (NumberFormatException e) { channel_Restriction = 0; }

        // If no valid channel given, display dialog for user selection
        if (!channel_Restriction_Set || channel_Restriction<0 || channel_Restriction>nChannels) {
            // Show dialog if not called from macro with valid argument
            GenericDialog gd = new GenericDialog("Reset Channel(s) Display");
            gd.addMessage("Resets contrast to channel min and max.");
            gd.addMessage("If multiple slices or frames, based on the entire channel stack.");
            gd.addNumericField("Channel to reset : (0 for all)", 0, 0, 5, "");
            gd.showDialog();
            if (gd.wasCanceled()) return;
            channel_Restriction = (int) gd.getNextNumber();
            if (channel_Restriction < 0 || channel_Restriction > nChannels)
                channel_Restriction = 0;
        }

        // Main loop to scan selected channel(s), all slices & frames
        for (int channel = 1; channel <= nChannels; channel++) {
            // Skip if channel not selected or not active (composite)
            if (channel_Restriction > 0 && channel != channel_Restriction) continue;
            if (active_Channels != null && !active_Channels[channel - 1]) continue;
            double channel_Min = Double.POSITIVE_INFINITY;
            double channel_Max = Double.NEGATIVE_INFINITY;

            for (int z = 1; z <= nSlices; z++)

                for (int t = 1; t <= nFrames; t++) {

                    IJ.showProgress(((double)(channel-1)*nSlices*nFrames + (z-1)*nFrames + (t-1)) / (nChannels*nSlices*nFrames));
                    int index = imp.getStackIndex(channel, z, t);
                    ImageProcessor ip = imp.getStack().getProcessor(index);
                    Pixel_Getter getter = get_Pixel_Getter(ip, bitDepth, imp.getWidth());
                    // If no ROI: scan entire image for min/max
                    if (roi == null) {
                        int len = ip.getPixelCount();
                        for (int i = 0; i < len; i++) {
                            double value = getter.get(i);
                            if (value < channel_Min) channel_Min = value;
                            if (value > channel_Max) channel_Max = value;
                        }
                    } 
                    // If ROI: restrict scan to ROI bounds and mask
                    else {
                        ip.setRoi(roi);
                        ImageProcessor mask = roi.getMask();
                        Rectangle bounds = roi.getBounds();
                        int x0 = Math.max(0, bounds.x);
                        int y0 = Math.max(0, bounds.y);
                        int x1 = Math.min(ip.getWidth(), bounds.x + bounds.width);
                        int y1 = Math.min(ip.getHeight(), bounds.y + bounds.height);
                        for (int y = y0; y < y1; y++) {
                            for (int x = x0; x < x1; x++) {
                                int mx = x - bounds.x, my = y - bounds.y;
                                if (mask == null || (mask.getPixel(mx, my) & 0xff) != 0) {
                                    double value = getter.get(x, y);
                                    if (value < channel_Min) channel_Min = value;
                                    if (value > channel_Max) channel_Max = value;
                                }
                            }
                        }
                    }
                }
            // Apply new display range 
            LUT[] luts = null;
            if (imp.isComposite()) luts = ((CompositeImage)imp).getLuts();
            else luts = ((ImagePlus)imp).getLuts();
            if (imp.isComposite() && imp.getCompositeMode() != IJ.GRAYSCALE){  
                luts[channel-1] = new LUT(luts[channel-1].getColorModel(), (double)channel_Min, (double)channel_Max);
                ((CompositeImage)imp).setLuts(luts);
            }
            else {
                imp.setDisplayRange((double)channel_Min, (double)channel_Max);
            }
        }
        imp.updateChannelAndDraw();
        IJ.showProgress(1.0);
        imp.unlock();
    }

    // Interface: fetch pixel as double, by index or x,y (works for all bit depths)
    private interface Pixel_Getter {
        double get(int i);         // 1D array
        double get(int x, int y);  // 2D coordinates
    }

    // returns correct pixel getter based on bit depth (8, 16, 32)
    private Pixel_Getter get_Pixel_Getter(final ImageProcessor ip, final int bitDepth, final int width) {
        if (bitDepth == 8) {
            final byte[] pixels = (byte[]) ip.getPixels();
            return new Pixel_Getter() {
                public double get(int i) { return pixels[i] & 0xff; }
                public double get(int x, int y) { return pixels[y * width + x] & 0xff; }
            };
        } else if (bitDepth == 16) {
            final short[] pixels = (short[]) ip.getPixels();
            return new Pixel_Getter() {
                public double get(int i) { return pixels[i] & 0xffff; }
                public double get(int x, int y) { return pixels[y * width + x] & 0xffff; }
            };
        } else { // 32-bit
            final float[] pixels = (float[]) ip.getPixels();
            return new Pixel_Getter() {
                public double get(int i) { return pixels[i]; }
                public double get(int x, int y) { return pixels[y * width + x]; }
            };
        }
    }
}
