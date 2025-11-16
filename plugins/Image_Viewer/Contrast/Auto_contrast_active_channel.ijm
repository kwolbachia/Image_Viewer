
if (nImages()==0) exit();
if (bitDepth() == 24) run("Enhance True Color Contrast", "saturated=0.1"); 
else run("Enhance Contrast", "saturated=0.1");