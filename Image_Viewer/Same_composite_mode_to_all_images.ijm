if (nImages==0) exit();
if (!is("composite")) exit();
id = getImageID();
Stack.getDisplayMode(mode);
composite_Type = Property.get("CompositeProjection");

for (i=0; i<nImages; i++) {
    selectImage(i+1);
	if (!is("composite")) continue; //ingnore single channel images
	Stack.setDisplayMode(mode);
	Property.set("CompositeProjection", composite_Type);
	updateDisplay();
}
selectImage(id);
