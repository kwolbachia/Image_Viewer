
if (nImages()==0) exit();

path = getDirectory("luts")+"/LUT_Palette_Manager.csv";
// get csv
if ( File.exists(path)) first_LUT_Palette = File.openAsString(path);
else exit("Run the LUT Manager plugin and set a palette");
// split by lines
first_LUT_Palette = split(first_LUT_Palette, "\n");
// remove "" on first palette
first_LUT_Palette = replace(first_LUT_Palette[1], "\"", "");
// split LUT names by comma 
first_LUT_Palette = split(first_LUT_Palette, ",");

Dialog.createNonBlocking("Apply first Palette");
Dialog.addRadioButtonGroup("Active Image or all images?", newArray("Active image", "All opened images"), 2, 1, "Active image");
Dialog.show();
choice = Dialog.getRadioButton();

if (choice == "Active image") apply_LUTs(first_LUT_Palette);
else apply_LUTs_to_All_Images(first_LUT_Palette);


function apply_LUTs(lut_list){
	Stack.getPosition(channel,s,f);
	getDimensions(w,h,channels,s,f);
	for(i=1; i<=channels; i++){
		Stack.setChannel(i);
		getMinAndMax(min, max);
		run(lut_list[i]);
		setMinAndMax(min, max);
	}
	Stack.setChannel(channel);
	updateDisplay();
}

function apply_LUTs_to_All_Images(lut_list){
	setBatchMode(1);
	for (i=0; i<nImages; i++) {
		selectImage(i+1);
		if (bitDepth() != 24) apply_LUTs(lut_list);	
		getDimensions(width, height, channels, slices, frames);
		if (channels > 1) Stack.setDisplayMode("composite");
	}
	setBatchMode(0);
}