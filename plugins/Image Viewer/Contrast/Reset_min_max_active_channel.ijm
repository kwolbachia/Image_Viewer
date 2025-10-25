if (nImages()==0) exit();
if (bitDepth()==24) exit();
Stack.getPosition(channel, slice, frame);
run("Reset Display", "channel=" + channel);
