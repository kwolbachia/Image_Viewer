# Image_Viewer
ImageJ / Fiji set of plugins and macros providing user friendly handling and visualization of microscopy images
<img height="500" alt="image" src="https://github.com/user-attachments/assets/5d1fe837-7a65-48a7-98b9-51f66579c5cd" />

# Installation
- in Fiji, add the __Image Viewer__ [Update Site](https://imagej.net/update-sites/following). That's it.
- For ImageJ, download this github repository       
Then in your imageJ app folder : place the **Image Viewer** folder on the ``plugins`` folder and the **Image_Viewer_Toolset.ijm** file on the ``macros /toolsets/`` folder

All commands and plugins are located in the ``Plugins > Image Viewer`` menu.       
However, The easiest way to access commands is from the toolbar menu you can find as ``Image_Viewer_Toolset`` under the red `>>` menu in the ImageJ window:            
![Image Viewer Toolset](https://github.com/imagej/imagej.github.io/blob/main/media/Image-Viewer/Image-Viewer-Toolset.png?raw=true){:width="300px"}    
This will install a "View" menu in your Toolbar will all Image Viewer commands!     

Note :     
If you like these tools so much you need to get them installed at every starts, just copy this macro code and past it at the end of your 
- `Fiji/macros/StartupMacros.fiji.ijm` for Fiji       
- `ImageJ/macros/StartupMacros.txt` for ImageJ

  
```java
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
```
