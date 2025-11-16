save_All_Images_Dialog();

function save_All_Images_Dialog() {
	if (nImages()==0) exit();
	Dialog.createNonBlocking("Save all images as");
	Dialog.addChoice("format", newArray("tiff", "jpeg", "gif", "raw", "avi", "bmp", "png", "pgm", "lut", "selection", "results", "text"), "tiff");
	Dialog.show();
	format = Dialog.getChoice();
	folder = getDirectory("Choose a Directory");
	print("path: " + folder);
	for (i=0; i<nImages; i++) {
        selectImage(i+1);
        title = getTitle;
        saveAs(format, folder + title);
        print(title + " saved");
	}
	print("done");
}