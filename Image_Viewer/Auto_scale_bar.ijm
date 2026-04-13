/*
Kevin Terretaz - kevinterretaz@gmail.com
no AI for this

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

var REMOVE_SCALEBAR_TEXT = get_Permanent_Pref("auto_scale_bar", "remove_text", "false");
var FACTOR = parseInt(get_Permanent_Pref("auto_scale_bar", "size_factor", 70));

auto_Scale_Bar();

// Add scale bar to image in 1-2-5 series size
// adapted from Aleš Kladnik here: https://forum.image.sc/t/automatic-scale-bar-in-fiji-imagej/60774
function auto_Scale_Bar(){
	if (nImages()==0) exit();
	if ( Overlay.size > 0) {run("Remove Overlay"); exit();}
	color = "White";
	if (is_inverting_LUT()) color = "Black";
	// approximate size of the scale bar relative to image width :
	scalebar_Size = 0.13;
	getPixelSize(unit, pixel_Width, pixel_Height);
	if (unit == "pixels") exit("Image not spatially calibrated");
	// image width in measurement units
	shortest_Image_Edge = pixel_Width * minOf(Image.width, Image.height);  
	// initial scale bar length in measurement units :
	scalebar_Length = 1;            
	// recursively calculate a 1-2-5 series until the length reaches scalebar_Size
	// 1-2-5 series is calculated by repeated multiplication with 2.3, rounded to one significant digit
	while (scalebar_Length < shortest_Image_Edge * scalebar_Size) 
		scalebar_Length = round((scalebar_Length*2.3)/(Math.pow(10,(floor(Math.log10(abs(scalebar_Length*2.3)))))))*(Math.pow(10,(floor(Math.log10(abs(scalebar_Length*2.3))))));
	if (REMOVE_SCALEBAR_TEXT) {
		scalebar_Settings_String = " height=" + minOf(Image.width, Image.height)/FACTOR + " color="+color+" hide overlay";
		print("Scale Bar length = " + scalebar_Length);
	}
	else scalebar_Settings_String = " height=" + minOf(Image.width, Image.height)/FACTOR +
	 " font=" + minOf(Image.width, Image.height)/(FACTOR/2) +
	  " color="+color+" bold overlay";
	run("Scale Bar...", "width=&scalebar_Length " + scalebar_Settings_String);
	string_To_Recorder("run(\"Scale Bar...\", \"width=" + scalebar_Length  + scalebar_Settings_String + "\"");
}
function set_Permanent_Pref(name, index, value) {
	call("ij.Prefs.set", name + "." + index, value);
}

function get_Permanent_Pref(name, index, default_value) {
	return call("ij.Prefs.get", name + "." + index, default_value);
}

function string_To_Recorder(string) {
	if (isOpen("Recorder")) call("ij.plugin.frame.Recorder.recordString",string + "\n");
}

function is_inverting_LUT() {
	if (bitDepth()==24) return false;
	getLut(reds, greens, blues);
	if (reds[0] + greens[0] + blues[0] > 20) return true;
	else return false;
}