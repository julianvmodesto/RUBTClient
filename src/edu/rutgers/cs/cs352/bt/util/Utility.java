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
	public static byte[] getFileInBytes(final RandomAccessFile file)
			throws IOException {
		final byte[] bytes = new byte[(int) file.length()];
		file.readFully(bytes);
		return bytes;
	}

	/**
	 * 
	 * @param byteArr
	 * @return
	 */
	public static String bytesToHexStr(final byte[] byteArr) {

		final char[] charArr = new char[byteArr.length * 2];
		for (int i = 0; i < byteArr.length; i++) {
			final int val = (byteArr[i] & 0xFF);
			final int charLoc = i << 1;
			charArr[charLoc] = Utility.HEX_CHARS[val >>> 4];
			charArr[charLoc + 1] = Utility.HEX_CHARS[val & 0x0F];
		}
		final String hexString = new String(charArr);

		return hexString;
	}

	/**
	 * 
	 * @param byteArr
	 * @return
	 */
	public static String bytesToURL(final byte[] byteArr) {

		final String hexString = Utility.bytesToHexStr(byteArr);

		final int len = hexString.length();
		final char[] charArr = new char[len + (len / 2)];
		int i = 0;
		int j = 0;
		while (i < len) {
			charArr[j++] = '%';
			charArr[j++] = hexString.charAt(i++);
			charArr[j++] = hexString.charAt(i++);
		}
		return new String(charArr);
	}

	/**
	 * Sets the bit in the byte array.
	 * 
	 * @param arr
	 *            the byte array to change
	 * @param bit
	 *            the bit to set
	 * @return
	 */
	public static byte[] setBit(final byte[] arr, final int bit) {
		final int index = bit / 8; // Get the index of the array for the byte
									// with
		// this bit
		final int bitPosition = bit % 8; // Position of this bit in a byte

		final byte B = arr[index];

		arr[index] = (byte) (B | (1 << bitPosition));

		return arr;

	}

	/**
	 * Clears the bit in the byte array.
	 * 
	 * @param arr
	 *            the byte array to change
	 * @param bit
	 *            the bit to clear
	 */
	public static byte[] resetBit(final byte[] arr, final int bit) {
		final int index = bit / 8; // Get the index of the array for the byte
									// with
		// this bit
		final int bitPosition = bit % 8; // Position of this bit in a byte

		byte B = arr[index];

		arr[index] = (B &= ~(1 << bitPosition));
		return arr;
	}

	public static boolean isSetBit(final byte[] data, final int pos) {
		final int posByte = pos / 8;
		final int posBit = 7 - (pos % 8);
		final byte valByte = data[posByte];
		final int valInt = (valByte >> (8 - (posBit + 1))) & 0x0001;
		return valInt == 1;
	}

}
