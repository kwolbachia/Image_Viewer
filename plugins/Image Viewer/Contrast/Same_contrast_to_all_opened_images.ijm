
propagate_Contrasts_All_Images();

function propagate_Contrasts_All_Images(){
	if (nImages()==0) exit();
	if (bitDepth()==24) exit();
	Stack.getPosition(channel, slice, frame);
	getDimensions(width, height, channels, slices, frames);
	mins = newArray(channels);
	maxs = newArray(channels);
	if (channels > 1){
		for(i=0; i<channels; i++){
			Stack.setChannel(i+1);
			getMinAndMax(mins[i], maxs[i]);
		}
		Stack.setChannel(channel);
		updateDisplay();
	}
	else getMinAndMax(mins[0], maxs[0]);
	
	for (i = 0; i < nImages; i++) {
		if (bitDepth() != 24) {
			selectImage(i+1);
			getDimensions(width, height, channels, slices, frames);
			if (channels>1){
				for(k=0; k<channels; k++){
					Stack.setChannel(k+1);
					setMinAndMax(mins[k], maxs[k]);
				}
				updateDisplay();
			}
			else setMinAndMax(mins[0], maxs[0]);
		}
	}
}