enhance_All_Channels();

function enhance_All_Channels() {
	if (nImages()==0) exit();
	getDimensions(width, height, channels, slices, frames);
	Stack.getPosition(channel, slice, frame);
	for (i = 1; i <= channels; i++) {
		Stack.setPosition(i, slice, frame);
		run("Enhance Contrast", "saturated=0.1");	
	}
	Stack.setPosition(channel, slice, frame);
	updateDisplay();
	call("ij.plugin.frame.ContrastAdjuster.update");
}