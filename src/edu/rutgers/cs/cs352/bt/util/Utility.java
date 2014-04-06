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



	public static void setBit(byte[] arr, int bit) {		
		int index = bit / 8; // Get the index of the array for the byte with
		// this bit
		int bitPosition = bit % 8; // Position of this bit in a byte

		byte B = arr[index];

		arr[index] = (byte) (B | (1 << bitPosition));
	}

	public static boolean isSetBit(byte[] data, int pos) {
		int posByte = pos/8; 
		int posBit = pos%8;
		byte valByte = data[posByte];
		int valInt = valByte>>(8-(posBit+1)) & 0x0001;
		return valInt == 1;
	}

}
