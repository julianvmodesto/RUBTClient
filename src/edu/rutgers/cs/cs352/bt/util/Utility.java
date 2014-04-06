package edu.rutgers.cs.cs352.bt.util;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Utility {

	private final static char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * Converts a file into a byte array
	 * 
	 * @author Julian Modesto
	 * @param file
	 *            the RandomAccessFile to be converted
	 * @return the file as a byte array
	 * @throws IOException
	 */
	public static byte[] getFileInBytes(RandomAccessFile file)
			throws IOException {
		byte[] bytes = new byte[(int) file.length()];
		file.readFully(bytes);
		return bytes;
	}

	/**
	 * 
	 * @param byteArr
	 * @return
	 */
	public static String bytesToHexStr(byte[] byteArr) {

		char[] charArr = new char[byteArr.length * 2];
		for (int i = 0; i < byteArr.length; i++) {
			final int val = (byteArr[i] & 0xFF);
			final int charLoc = i << 1;
			charArr[charLoc] = HEX_CHARS[val >>> 4];
			charArr[charLoc + 1] = HEX_CHARS[val & 0x0F];
		}
		String hexString = new String(charArr);

		return hexString;
	}

	/**
	 * 
	 * @param byteArr
	 * @return
	 */
	public static String bytesToURL(byte[] byteArr) {

		String hexString = bytesToHexStr(byteArr);

		int len = hexString.length();
		char[] charArr = new char[len + len / 2];
		int i = 0;
		int j = 0;
		while (i < len) {
			charArr[j++] = '%';
			charArr[j++] = hexString.charAt(i++);
			charArr[j++] = hexString.charAt(i++);
		}
		return new String(charArr);
	}

	/*
	 * The following code is taken from Stack Overflow. The author has permitted
	 * copying according to Creative Commons License. A copy of the license is
	 * provided in legalcode.txt in the root folder of this project.
	 * 
	 * Author: Rohit Jain
	 * Source URL:
	 * http://stackoverflow.com/questions/18931283/checking-individual-bits-in-a-byte-array-in-java 
	 * Date: September 21, 2013
	 */
	public static boolean isSetBit(byte[] arr, int bit) {
		int index = bit / 8; // Get the index of the array for the byte with
								// this bit
		int bitPosition = bit % 8; // Position of this bit in a byte

		return (arr[index] >> bitPosition & 1) == 1;
	}
	/* End code copied from Rohit Jain. */
	
	public static void setBit(byte[] arr, int bit) {
		int index = bit / 8;
		int bitPosition = bit % 8;
		arr[index] = (byte) (arr[index] | (1 << bitPosition));
	}
}
