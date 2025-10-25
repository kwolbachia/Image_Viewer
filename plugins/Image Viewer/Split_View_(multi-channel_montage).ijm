
// For split_View
var	COLOR_MODE = get_Permanent_Pref("split_View", "color_Mode", "Colored");
var MONTAGE_STYLE = get_Permanent_Pref("split_View", "montage_Style", "Linear");
var LABELS = get_Permanent_Pref("split_View", "labels", "No labels");
var CHANNEL_LABELS = get_Pref_Array("channel_Labels", newArray("A","B","C","D","E"));
var FONT_SIZE = 30;
var BORDER_SIZE = "Auto";
var TILES = newArray();

split_View_Dialog();

function set_Permanent_Pref(name, index, value) {
	call("ij.Prefs.set", name + "." + index, value);
}

function get_Permanent_Pref(name, index, default_value) {
	return call("ij.Prefs.get", name + "." + index, default_value);
}

function get_Pref_Array(name, default_Array){
	array = newArray();
	for (i = 0; i < default_Array.length; i++) array[i] = get_Permanent_Pref(name, i, default_Array[i]);
	return array;
}

function set_Pref_Array(name, array){
	for (i = 0; i < array.length; i++) set_Permanent_Pref(name, i, array[i]);
	return array;
}

function split_View_Dialog(){
	if (nImages == 0) exit();
	getSelectionBounds(x, y, width, height);
	getDimensions(w, h, channels, s, f);
	if (channels == 1) exit();
	if (channels > 5)  exit("5 channels max");
	auto_border_size = maxOf(round(minOf(height, width) * 0.01), 2);
	Dialog.createNonBlocking("Split View Montage");
	Dialog.addRadioButtonGroup("Channels", newArray("Colored","Grayscale"), 1, 3, COLOR_MODE);
	Dialog.addRadioButtonGroup("Montage Layout", newArray("Linear","Square","Vertical"), 1, 3, MONTAGE_STYLE);
	Dialog.addSlider("Border size (px)", 0, 50, auto_border_size);
	Dialog.addRadioButtonGroup("Labels", newArray("Add labels","No labels"), 1, 3, LABELS);
	for (i = 0; i < channels; i++) Dialog.addString("Channel " + i+1, CHANNEL_LABELS[i], 12); 
	Dialog.addNumber("Font size", FONT_SIZE);
	Dialog.show();
	COLOR_MODE = Dialog.getRadioButton();
	MONTAGE_STYLE = Dialog.getRadioButton();
	BORDER_SIZE = Dialog.getNumber();
	LABELS = Dialog.getRadioButton();
	for (i = 0; i < channels; i++) CHANNEL_LABELS[i] = Dialog.getString();
	FONT_SIZE = Dialog.getNumber();
	split_View(MONTAGE_STYLE, COLOR_MODE, LABELS);
	// save dialog prefs
	set_Permanent_Pref("split_View", "color_Mode", COLOR_MODE);
	set_Permanent_Pref("split_View", "montage_Style", MONTAGE_STYLE);
	set_Permanent_Pref("split_View", "labels", LABELS);
	set_Pref_Array("channel_Labels", CHANNEL_LABELS);
	BORDER_SIZE = "Auto";
}

function split_View(MONTAGE_STYLE, COLOR_MODE, LABELS) {
	// COLOR_MODE : "Grayscale" or "Colored" 
	// MONTAGE_STYLE : "Linear","Square" or "Vertical"
	// LABELS : "Add labels" or "No labels"
	setBatchMode(1);
	title = getTitle();
	// prepares TILES before montage :
	saveSettings();
	getDimensions(width, height, channels, slices, frames); 
	Setup_SplitView(COLOR_MODE, LABELS);
	restoreSettings();
	// Tiles assembly
	if (MONTAGE_STYLE == "Linear")		linear_SplitView();
	if (MONTAGE_STYLE == "Square")		square_SplitView();
	if (MONTAGE_STYLE == "Vertical")	vertical_SplitView();
	//output
	unique_Rename("SplitView_" + title);
	setOption("Changes", 0);
	setBatchMode("exit and display");

	function Setup_SplitView(COLOR_MODE, LABELS){
		// prepares TILES before montage : 
		// duplicate twice for overlay and splitted channels
		// convert to RGB with right colors, labels and borders
		getDimensions(width, height, channels, slices, frames);
		setBackgroundColor(255, 255, 255); //for white borders
		run("Duplicate...", "title=image duplicate");
		if ((slices > 1) && (frames == 1)) {
			frames = slices;
			slices = 1;
			Stack.setDimensions(channels, slices, frames); 
		} 
		TILES = newArray(channels + 1);
		getDimensions(width, height, channels, slices, frames);
		if (BORDER_SIZE == "Auto") BORDER_SIZE = maxOf(round(minOf(height, width) * 0.01), 2);
		FONT_SIZE = height / 9;
		run("Duplicate...", "title=split duplicate");
		run("Split Channels");
		selectWindow("image");
		Stack.setDisplayMode("composite")
		if (LABELS == "Add labels") {
			setColor("white");
			setFont("SansSerif", FONT_SIZE, "bold antialiased");
			Overlay.drawString("Merge", height/20, FONT_SIZE);
			Overlay.show;
			run("Flatten","stack");
			rename("overlay");
			TILES[0] = getTitle();
			if (BORDER_SIZE > 0) add_Borders();
			close("image");
			for (i = 1; i <= channels; i++) {
				selectWindow("C" + i + "-split");
				id = getImageID();
				getLut(reds, greens, blues); 
				setColor(reds[255], greens[255], blues[255]);
				if (COLOR_MODE == "Grayscale") {
					getMinAndMax(min, max); 
					run("Grays"); 
					setMinAndMax(min, max);
				}
				Overlay.drawString(CHANNEL_LABELS[i-1], height/20, FONT_SIZE);
				Overlay.show;
				if (slices * frames > 1) run("Flatten","stack");
				else {
					run("Flatten");
					selectImage(id);
					close();
				}
				if (BORDER_SIZE > 0) add_Borders();
				TILES[i]=getTitle();
			}
		}
		else { // without LABELS
			run("RGB Color", "frames"); 
			rename("overlay"); 
			TILES[0] = getTitle(); 
			if (BORDER_SIZE > 0) add_Borders();
			close("image");
			for (i = 1; i <= channels; i++) {
				selectWindow("C"+i+"-split");
				if (COLOR_MODE == "Grayscale") {
					getMinAndMax(min, max); 
					run("Grays"); 
					setMinAndMax(min, max);
				}
				run("RGB Color", "slices"); 
				if (BORDER_SIZE > 0) add_Borders();
				TILES[i] = getTitle();	
			}
		}
		BORDER_SIZE = "Auto";
	}

	function add_Borders(){
		run("Canvas Size...", "width=" + Image.width + BORDER_SIZE + " height=" + Image.height + BORDER_SIZE + " position=Center");
	}
	
	function get_Labels_Dialog(){
		Dialog.createNonBlocking("Provide channel names");
		for (i = 0; i < 5; i++) Dialog.addString("channel " + i+1, CHANNEL_LABELS[i], 12); 
		Dialog.addNumber("Font size", FONT_SIZE);
		Dialog.show();
		for (i = 0; i < 5; i++) CHANNEL_LABELS[i] = Dialog.getString();
		FONT_SIZE = Dialog.getNumber();
	}
	
	function square_SplitView(){
		channel_1_2 = combine_Horizontally(TILES[1], TILES[2]);
		if (channels == 2||channels == 4) channel_1_2_Overlay = combine_Horizontally(channel_1_2, TILES[0]);
		if (channels == 3){
			channel_3_Overlay = combine_Horizontally(TILES[3], TILES[0]);
			combine_Vertically(channel_1_2, channel_3_Overlay);
		}
		if (channels >= 4)	channel_3_4 = combine_Horizontally(TILES[3], TILES[4]);
		if (channels == 4)	combine_Vertically(channel_1_2_Overlay, channel_3_4);
		if (channels == 5){
			channel_1_2_3_4 = combine_Vertically(channel_1_2, channel_3_4); 	
			channel_5_Overlay =	combine_Vertically(TILES[5], TILES[0]); 
			combine_Horizontally(channel_1_2_3_4, channel_5_Overlay);
		}
	}
	
	function linear_SplitView(){
		channel_1_2 = combine_Horizontally(TILES[1], TILES[2]);
		if (channels==2) combine_Horizontally(channel_1_2, TILES[0]);
		if (channels==3){
			channel_3_Overlay = combine_Horizontally(TILES[3], TILES[0]);
			combine_Horizontally(channel_1_2, channel_3_Overlay);
		}
		if (channels>=4){
			channel_3_4 = combine_Horizontally(TILES[3], TILES[4]);
			channel_1_2_3_4 = combine_Horizontally(channel_1_2, channel_3_4);
		}
		if (channels==4) combine_Horizontally(channel_1_2_3_4, TILES[0]); 
		if (channels==5){
			channel_5_Overlay = combine_Horizontally(TILES[5], TILES[0]);
			combine_Horizontally(channel_1_2_3_4, channel_5_Overlay);
		}
	}
	
	function vertical_SplitView(){
		channel_1_2 = combine_Vertically(TILES[1], TILES[2]);
		if (channels==2) combine_Vertically(channel_1_2, TILES[0]);
		if (channels==3){
			channel_3_Overlay = combine_Vertically(TILES[3], TILES[0]);
			combine_Vertically(channel_1_2, channel_3_Overlay);
		}
		if (channels>=4){
			channel_3_4 = combine_Vertically(TILES[3], TILES[4]);
			channel_1_2_3_4	= combine_Vertically(channel_1_2, channel_3_4);
		}
		if (channels==4) combine_Vertically(channel_1_2_3_4, TILES[0]);
		if (channels==5){
			channel_5_Overlay = combine_Vertically(TILES[5], TILES[0]);
			combine_Vertically(channel_1_2_3_4, channel_5_Overlay);
		}
	}
	
	function combine_Horizontally(stack1, stack2){
		run("Combine...", "stack1=&stack1 stack2=&stack2");
		rename(stack1+"_"+stack2);
		return getTitle();
	}
	
	function combine_Vertically(stack1, stack2){
		run("Combine...", "stack1=&stack1 stack2=&stack2 combine"); //vertically
		rename(stack1+"_"+stack2);
		return getTitle();
	}
}

function unique_Rename(name) {
	final_Name = name;
	i = 1;
	while (isOpen(final_Name)) {
		final_Name = name + "_" + i;
		i++;
	}
	rename(final_Name);
}