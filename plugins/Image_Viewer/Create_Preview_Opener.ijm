//create a montage with snapshots of all opened images (virtual or not)
//in their curent state.  Will close all but the montage.
// added sort image names / order
make_Preview_Opener();

function make_Preview_Opener() {
	if (nImages == 0) exit();
	Dialog.createNonBlocking("Make Preview Opener");
	Dialog.addMessage("Creates a montage with snapshots of all opened images (virtual or not).\n" +
		"This will close all but the montage. Are you sure?");
	Dialog.addHelp("https://imagej.net/plugins/image-viewer");
	Dialog.show();
	setBatchMode(1);
	n_Opened_Images = nImages();
	paths_List = "";
	titles = newArray(0);
	concat_Options = "open ";
	for (i=0; i<n_Opened_Images ; i++) {
		selectImage(i+1);
		if (i==0) {
			source_Folder = getDirectory("image"); 
			File.setDefaultDir(source_Folder);
		}
		titles[i] = getTitle();
	}
	Array.sort(titles);
	for (i=0; i<n_Opened_Images; i++) {
		selectWindow(titles[i]);
		paths_List += getTitle() +",,";
		if (!is("Virtual Stack") && bitDepth()!=24) {
			getDimensions(width, height, channels, slices, frames);
			if (slices * frames != 1) {
				getLut(reds,greens,blues);
				getMinAndMax(min, max);
				run("Z Project...", "projection=[Max Intensity] all");
				setLut(reds, greens, blues);
				setMinAndMax(min, max);
			}
		}
		rgb_Snapshot();
		run("Scale...", "x=- y=- width=400 height=400 interpolation=Bilinear average create");
		rename("image"+i);
		concat_Options +=  "image"+i+1+"=[image"+i+"] ";
	}
	run("Concatenate...", concat_Options);
	run("Make Montage...", "scale=1");
	rename("Preview Opener");
	infos = getMetadata("Info");
	setMetadata("Info", paths_List + "\n" + infos);
	close("\\Others");
	setBatchMode(0);
	saveAs("tiff", source_Folder + "_Preview Opener");
}

function rgb_Snapshot(){
	if (nImages()==0) exit();
	title = getTitle();
	Stack.getPosition(channel, slice, frame);
	getDimensions(width, height, channels, slices, frames);
	if (channels > 1) Stack.getDisplayMode(mode);
	setBatchMode(1);
	if 		(bitDepth()==24) 		run("Duplicate..."," ");
	else if (channels==1) 			run("Duplicate...", "title=toClose channels=&channels slices=&slice frames=&frame");
	else if (mode!="composite") 	run("Duplicate...", "title=toClose channels=channel slices=&slice frames=&frame");
	else 							run("Duplicate...", "duplicate title=toClose slices=&slice frames=&frame");
	run("RGB Color", "keep");
	unique_Rename("rgb_" + title);
	close("toClose");
	setOption("Changes", 0);	
//	setBatchMode(0);
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
