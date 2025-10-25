var viewer_Menu = newMenu("Image Viewer Menu Tool",
	newArray( 
		// "Switch composite mode",
		"Channels and Contrast",
		"LUTs Manager",
		"Multi Tool",
		"-",
		"Split View (multi-channel montage)",
		"Auto scale bar",
		"Create Preview Opener",
		"Image Viewer options",
		"-",
		"Auto contrast all images",
		"Auto contrast all channels",
		"Auto contrast active channel",
		"Reset min max all images",
		"Reset min max all channels",
		"Reset min max active channel",
		"Same contrast to all opened images",
		"-",
		"Save all opened images as"
	)
);

macro "Image Viewer Menu Tool - C000 T0c12v T6c10i  T8c10e Tec10w" {
	command = getArgument(); 
	run(command); 
}
