if (nImages()==0) exit();
if (bitDepth()==24) exit();
showStatus("Reset all contrasts");
for (i=0; i<nImages ; i++) {			
	selectImage(i+1);
    run("Reset Display", "channels=0");
	showProgress(i/nImages);	
}