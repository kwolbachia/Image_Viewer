REMOVE_SCALEBAR_TEXT = get_Permanent_Pref("auto_scale_bar", "remove_text", "true");
FACTOR = parseInt(get_Permanent_Pref("auto_scale_bar", "size_factor", 70));
SATURATED = get_Permanent_Pref("Channels_and_Contrast", "saturated", 0.1);

Dialog.createNonBlocking("Image Viewer ptions");
Dialog.addCheckbox("Remove text under scale bar?", REMOVE_SCALEBAR_TEXT);
Dialog.addSlider("Scale bar size factor (low = big)", 0, 100, FACTOR);
Dialog.addSlider("Auto-contrast saturated pixels %", 0.0, 1.5, SATURATED);
Dialog.show();
set_Permanent_Pref("auto_scale_bar", "remove_text", Dialog.getCheckbox());
set_Permanent_Pref("auto_scale_bar", "size_factor", Dialog.getNumber());
set_Permanent_Pref("Channels_and_Contrast", "saturated", Dialog.getNumber());


function set_Permanent_Pref(name, index, value) {
	call("ij.Prefs.set", name + "." + index, value);
}

function get_Permanent_Pref(name, index, default_value) {
	return call("ij.Prefs.get", name + "." + index, default_value);
}


