var SOURCE_IMAGE_TITLE = "";
var SOURCE_IMAGE_SIZE = 0;
var LAST_CLICK_TIME = 0;

// As often, this tool is derived from Jerome Mutterer's script <3

macro "Compare Tool - C000 L707f L808f L23d3 Ld3dc Ldc2c L2c23" {
	if (is_double_click()) {
		SOURCE_IMAGE_TITLE = getTitle();
		getDimensions(width, height, channels, slices, frames);
		SOURCE_IMAGE_SIZE = width * height;
		exit();
	}
	else curtain_Tool();
}

macro "Compare Tool Options"{
	Dialog.createNonBlocking("Compare Tool (Curtain)");
	Dialog.addMessage("Double click on source image");
	Dialog.addMessage("Click and drag on another image (of same size) to compare");
	Dialog.show();
}


function curtain_Tool() {
	if (SOURCE_IMAGE_TITLE == "") SOURCE_IMAGE_TITLE = getTitle();
	if (getTitle() == SOURCE_IMAGE_TITLE) exit();
	getDimensions(width, height, channels, slices, frames);
	if (width*height != SOURCE_IMAGE_SIZE) exit();
	getCursorLoc(last_x, y, z, flags);
	setBatchMode(true);
	id = getImageID();
	while (flags&16>0) {
		setKeyDown("none");
		selectImage(id);
		getCursorLoc(x, y, z, flags);
		if (x != last_x) {
			if (x < 0) x = 0;
			selectWindow(SOURCE_IMAGE_TITLE);
			makeRectangle(x, 0, width-x, height);
			Stack.getPosition(channel, slice, frame);
			getDimensions(width, height, channels, slices, frames);
			if (channels > 1) Stack.getDisplayMode(mode);
			if 		(bitDepth()==24) 		run("Duplicate..."," ");
			else if (channels==1) 			run("Duplicate...", "title=part channels=&channels slices=&slice frames=&frame");
			else if (mode!="composite") 	run("Duplicate...", "title=part channels=channel slices=&slice frames=&frame");
			else 							run("Duplicate...", "duplicate title=part slices=&slice frames=&frame");
			run("RGB Color", " ");
			rename("part2");	
			selectImage(id);
			run("Add Image...", "image=part2 x="+ x +" y=0 opacity=100"); //zero
			while (Overlay.size>1) Overlay.removeSelection(0);
			close("part");
			close("part2");
			last_x = x;
			wait(10);
		}
	}
	selectWindow(SOURCE_IMAGE_TITLE);
	run("Select None");
	selectImage(id);
	Overlay.remove;
}

function is_double_click() {
	double_click = false;
	click_time = getTime(); // in ms
	if (click_time - LAST_CLICK_TIME < 200) double_click = true;
	LAST_CLICK_TIME = click_time;
	return double_click;
}