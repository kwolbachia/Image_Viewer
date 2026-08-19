// 1.8

var viewer_Menu = newMenu("Image Viewer Menu Tool",
	newArray( 
		"Channels and Contrast",
		"Multi Tool",
		"LUTs Manager",
		"Create Preview Opener",
		"-",
		"Apply first LUT Palette",
		"Split View (multi-channel montage)",
		"Auto scale bar",
		"-",
		"Auto contrast all images",
		"Reset min max all images",
		"Same contrast to all opened images",
		"Same composite mode to all images",
		"Save all opened images as",
		"-",
		"Image Viewer options",
		"Image Viewer online help"
	)
);

macro "Image Viewer Menu Tool - N20C000 T0c15v T8c10i  Tac10e Tfc10w" {
	command = getArgument(); 
	run(command); 
}
