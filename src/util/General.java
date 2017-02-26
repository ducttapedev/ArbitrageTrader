package util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class General {
	public static List<String> arrayToList(String[] arr) {
		List<String> list = new ArrayList<String>();
		for(String s : arr)
			list.add(s);
		return list;
	}
	
	public static String getDate() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		sdf.setTimeZone(TimeZone.getTimeZone("EST"));
		String formattedDate = sdf.format(date);
		return formattedDate;
	}
	
	public static String getPrettyDate() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd | HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("EST"));
		String formattedDate = sdf.format(date);
		return formattedDate;
	}
	
	public static final double EPSILON = 1e-6;
}
