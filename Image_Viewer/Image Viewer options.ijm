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
REMOVE_SCALEBAR_TEXT = get_Permanent_Pref("auto_scale_bar", "remove_text", "true");
FACTOR = parseInt(get_Permanent_Pref("auto_scale_bar", "size_factor", 70));
SATURATED = get_Permanent_Pref("Channels_and_Contrast", "saturated", 0.1);

Dialog.createNonBlocking("Image Viewer options");
Dialog.addSlider("Auto-contrast saturated pixels %", 0.0, 1.5, SATURATED);
Dialog.addCheckbox("Remove text under auto scale bar?", REMOVE_SCALEBAR_TEXT);
Dialog.addSlider("Auto scale bar size factor (low = big)", 0, 100, FACTOR);
Dialog.show();
set_Permanent_Pref("Channels_and_Contrast", "saturated", Dialog.getNumber());
set_Permanent_Pref("auto_scale_bar", "remove_text", Dialog.getCheckbox());
set_Permanent_Pref("auto_scale_bar", "size_factor", Dialog.getNumber());


function set_Permanent_Pref(name, index, value) {
	call("ij.Prefs.set", name + "." + index, value);
}

function get_Permanent_Pref(name, index, default_value) {
	return call("ij.Prefs.get", name + "." + index, default_value);
}
