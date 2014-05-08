package edu.rutgers.cs.cs352.bt.util;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A utility class primarily containing bit operation and manipulation methods.
 * 
 * @author Julian Modesto
 * 
 */
public class Utility {

	private final static char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * Converts a file into a byte array representation.
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
	 * Converts a byte array to a hex characters in a string.
	 * 
	 * @param byteArr
	 *            the byte array to convert
	 * @return the string of hex characters that represents the byte array
	 */
	protected static String bytesToHexStr(final byte[] byteArr) {

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
	 * Converts a byte array to an escaped URL for a BT tracker announce.
	 * 
	 * @param byteArr
	 *            the byte array to convert
	 * @return the URL-converted byte array
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
	 * Sets a bit in the byte array.
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
	 * Clears a bit in the byte array.
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

	/**
	 * Determines whether a particular bit is set in a byte array.
	 * 
	 * @param data
	 *            the byte array to query
	 * @param pos
	 *            the position of the bit in the byte
	 * @return true if the bit is 1, else 0
	 */
	public static boolean isSetBit(final byte[] data, final int pos) {
		final int posByte = pos / 8;
		final int posBit = 7 - (pos % 8);
		final byte valByte = data[posByte];
		final int valInt = (valByte >> (8 - (posBit + 1))) & 0x0001;
		return valInt == 1;
	}

}
