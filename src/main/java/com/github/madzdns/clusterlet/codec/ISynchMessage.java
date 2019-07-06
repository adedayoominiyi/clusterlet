package com.github.madzdns.clusterlet.codec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface ISynchMessage {

	public void serialize(DataOutputStream out) throws IOException;
	
	public void deserialize(DataInputStream in) throws IOException;
}