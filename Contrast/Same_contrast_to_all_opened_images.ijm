/*
Kevin Terretaz - kevinterretaz@gmail.com
no AI for this

unlicense :
This is free and unencumbered software released into the public domain. Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
In jurisdictions that recognize copyright laws, the author or authors of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of relinquishment in perpetuity of all present and future rights to this software under copyright law.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
For more information, please refer to <http://unlicense.org/>
*/

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