package util;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class LogWriter extends PrintWriter {

	private boolean writeToFile;
	private boolean hasWritten = false;
	private String data = "";
	
	
	public LogWriter(String fileName) throws FileNotFoundException {
		super(fileName);
		writeToFile = true;
	}
	
	public LogWriter(String fileName, boolean writeToFile) throws FileNotFoundException {
		super(fileName);
		this.writeToFile = writeToFile;
	}
	
	/**
	 * Adds <code>"\n"</code> to the data to be written
	 */
	public void writeln() {
		write("\n");
	}
	
	/**
	 * Adds <code>s + "\n"</code> to the data to be written
	 * @param s
	 */
	public void writeln(String s) {
		write(s + "\n");
	}
	
	/**
	 * Adds <code>s</code> to the data to be written
	 * @param s
	 */
	public void write(String s) {
		data += s;
		hasWritten = true;
	}
	
	/**
	 * @return true if any data was written this iteration
	 */
	public boolean hasWritten() {
		return hasWritten;
	}
	
	/**
	 * If any data was added this iteration, write it
	 * @param count
	 */
	public void postIteration(int count) {
		if(writeToFile && hasWritten) {
			super.write("###################################################\n");
			super.write("Iteration " + count + " START\n");
			super.write(General.getPrettyDate() + "\n");
			super.write("###################################################\n");
			
			super.write(data + "\n");
			
			super.write("Iteration " + count + " END\n");
			super.write("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\n");
			super.write("\n");
			flush();
		}

		hasWritten = false;
		data = "";
	}
	
	
	

}
