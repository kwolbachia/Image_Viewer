enhance_All_Images_Contrasts();

function enhance_All_Images_Contrasts() {
	if (nImages()==0) exit();
	showStatus("Enhance all contrasts");
	for (i=0; i<nImages ; i++) {			
		selectImage(i+1);
		enhance_All_Channels();
		showProgress(i/nImages);
	}
}


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
