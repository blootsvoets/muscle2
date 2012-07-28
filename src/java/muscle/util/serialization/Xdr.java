/*
 * Copyright (c) 2009 - 2012 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package muscle.util.serialization;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Xdr implements XdrDecodingStream, XdrEncodingStream {

//	private final static int SIZE_OF_LONG = Long.SIZE / 8;
//	private final static int SIZE_OF_INT = Integer.SIZE / 8;

	/**
	 * Maximal size of a XDR message.
	 */
	public final static int MAX_XDR_SIZE = 512 * 1024;

	private final static Logger logger = Logger.getLogger(Xdr.class.getName());
	private final byte[] intbuffer = new byte[4];
	
	/**
	 * Byte buffer used by XDR record.
	 */
	protected final ByteBuffer buffer;
	protected final SocketChannel channel;
	private int fragmentLength;
	private boolean lastFragment;

	/**
	 * Create a new Xdr object with a buffer of given size.
	 *
	 * @param size of the buffer in bytes
	 */
	public Xdr(SocketChannel channel, int size) {
		this(channel, ByteBuffer.allocateDirect(size));
	}

	/**
	 * Create a new XDR back ended with given {@link ByteBuffer}.
	 * @param body buffer to use
	 */
	public Xdr(SocketChannel channel, ByteBuffer body) {
		this.channel = channel;
		buffer = body;
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.limit(0);
	}

	public void beginDecoding() throws IOException {
		/*
		 * Set position to the beginning of this XDR in back end buffer.
		 */
		fragmentLength = 0;
		fill(0);
		logger.log(Level.FINER, "Begin decoding {0} bytes", fragmentLength);
	}
	
	private void fill(int minimal) throws IOException {
//		if (fragmentLength < pos) {
//			throw new IllegalStateException("Can not read beyond fragment border: f="+fragmentLength+"; p=" + buffer.position() + "; l=" + buffer.limit());
//		} else
		if (fragmentLength == buffer.position()) {
			if ( lastFragment ) {
				// In case there is no more data in the current XDR record
				// (as we already saw the last fragment), throw an exception.
				throw(new IOException("Buffer underflow"));
			}
			if (fragmentLength > 0) {
				int newlim = buffer.limit() - buffer.position();
				buffer.compact();
				buffer.rewind();
				buffer.limit(newlim);
				fragmentLength = 0;
			}
			
			// Remove the header
			read(minimal + 4, true);
			buffer.get(intbuffer, 0, 4);
			
			// casting to unsigned with 0xFF.
			fragmentLength = ((intbuffer[0] & 0xFF) << 24) |
			                 ((intbuffer[1] & 0xFF) << 16) |
			                 ((intbuffer[2] & 0xFF) <<  8) |
			                  (intbuffer[3] & 0xFF);

			// XDR header is the last one if the sign is negative; the rest is the length of the fragment.
			if ((fragmentLength & 0x80000000) != 0) {
				fragmentLength &= 0x7FFFFFFF;
				lastFragment = true;
			} else {
				lastFragment = false;
			}
			
			// Sanity check on incomming fragment length: the length must
			// be at least four bytes long, otherwise this fragment does
			// not make sense. There are ONC/RPC implementations that send
			// empty trailing fragments, so we accept them here.
			// Also check for fragment lengths which are not a multiple of
			// four -- and thus are invalid.
			if ( (fragmentLength & 3) != 0 ) {
				throw(new IOException("XDR fragment length is not a multiple of four"));
			}
			if ( fragmentLength == 0 && !lastFragment ) {
				throw(new IOException("empty XDR fragment which is not a trailing fragment; header: " + Arrays.toString(intbuffer)));
			}
			
			// Add header; it will be removed in the compact later
			fragmentLength += 4;
//			System.out.println("Starting fragment length " + fragmentLength + " with buffer remaining " + buffer.remaining());
		} else {
			read(minimal, false);
		}
	}
	
	private void read(int minimal, boolean mayConfigureBlocking) throws IOException {
		// this will usually be true
		if (buffer.remaining() >= minimal) {
			return;
		}
		int remain = buffer.remaining();
//		 bytes;
//		System.out.println("Before compact: f="+fragmentLength+"; p=" + buffer.position() + "; l=" + buffer.limit());
		// Move unread data to the beginning
		fragmentLength -= buffer.position();
//		if (fragmentLength < 0) {
//			throw new IllegalStateException("Can not read beyond fragment border: f="+fragmentLength+"; p=" + buffer.position() + "; l=" + buffer.limit());
//		}
		buffer.compact();
		boolean configuredBlocking = false;
//		buffer.rewind();
		// And read unlimited data
		do {
			int bytes = channel.read(buffer);
			if (bytes > 0) {
				remain += bytes;
			} else if (bytes == 0 && mayConfigureBlocking && !configuredBlocking) {
				channel.configureBlocking(true);
				configuredBlocking = true;
			} else if (bytes == -1) {
				throw new IOException("Socket was disconnected");
			}
		} while (remain < minimal);
		if (configuredBlocking) {
			channel.configureBlocking(false);
		}
		buffer.flip();
//		System.out.println("Fragment length after read " + fragmentLength + " with buffer remaining " + buffer.remaining() + " after");
//		return remain;
	}

	public void endDecoding() throws IOException {
//		System.out.println("Fragment length before enddecoding " + fragmentLength + " at pos " + buffer.position() + " with buffer remaining " + buffer.remaining() + " before");
		

		while (fragmentLength > 0 || !lastFragment) {
			int pos = buffer.position();
			int lim = buffer.limit();
			
			if (fragmentLength < pos) {
				throw new IllegalStateException("Read beyond fragment border: f="+fragmentLength+"; p=" + pos + "; l=" + lim);
			} else if (fragmentLength > lim) {
				// Finish reading current fragment
				// This will set fragmentLength -= pos and fragmentLength <= lim
				fill(fragmentLength - pos);
			} else if (fragmentLength == 0) {
				// Read next fragment
				fill(0);
			} else {
				// Go to end of current fragment in buffer, and delete it 
				buffer.position(fragmentLength);
				buffer.compact();
				buffer.rewind();
				buffer.limit(lim - fragmentLength);
				fragmentLength = 0;
			}
		}
//		System.out.println("Fragment length after enddecoding " + fragmentLength + " at pos " + buffer.position() + " with buffer remaining " + buffer.remaining() + " after");
		
		lastFragment = false;
	}

	public void beginEncoding() {
		buffer.clear();
		// make room for the header
		buffer.position(4);
	}

	public void endEncoding() throws IOException {
		// Position is the size, -4 for the header.
		int size = buffer.position() - 4;
		intbuffer[0] = (byte)((size >>> 24) | 0x80);
		intbuffer[1] = (byte) (size >>> 16);
		intbuffer[2] = (byte) (size >>> 8);
		intbuffer[3] = (byte)  size;
		buffer.rewind();
		buffer.put(intbuffer);
		size += 4;
		buffer.position(size);
		buffer.flip();
		
		do {
			int bytes = channel.write(buffer);
			if (bytes == -1) {
				throw new IOException("Socket was disconnected");
			}
			size -= bytes;
		} while (size > 0);
		
		if (size < 0) {
			throw new IOException("Buffer overflow");
		}
	}

	/**
	 * Decodes (aka "deserializes") a "XDR int" value received from a
	 * XDR stream. A XDR int is 32 bits wide -- the same width Java's "int"
	 * data type has. This method is one of the basic methods all other
	 * methods can rely on. Because it's so basic, derived classes have to
	 * implement it.
	 *
	 * @return The decoded int value.
	 */
	public int xdrDecodeInt() throws IOException {
		fill(4);
		// Note: buffer[...] gives a byte, which is signed. So if we
        // add it to the value (which is int), it has to be widened
        // to 32 bit, so its sign is propagated. To avoid this sign
        // madness, we have to "and" it with 0xFF, so all unwanted
        // bits are cut off after sign extension.
		buffer.get(intbuffer, 0, 4);
//		int val = 
//		 = (byte)(value >>> 24);
//        intbuffer[bufferIndex++] = (byte)(value >>> 16);
//        intbuffer[bufferIndex++] = (byte)(value >>>  8);
//        intbuffer[bufferIndex++] = (byte) value;
//		logger.log(Level.FINEST, "Decoding int {0}", val);
		return ((intbuffer[0] & 0xFF) << 24) |
		       ((intbuffer[1] & 0xFF) << 16) |
		       ((intbuffer[2] & 0xFF) <<  8) |
		        (intbuffer[3] & 0xFF);
//		return buffer.getInt();
	}

	/**
	 * Get next array of integers.
	 *
	 * @return the array on integers
	 */
	public int[] xdrDecodeIntVector() throws IOException {
		int len = xdrDecodeInt();
//		logger.log(Level.FINEST, "Decoding int array with len = {0}", len);

		int[] ints = new int[len];
		for (int i = 0; i < len; i++) {
			ints[i] = xdrDecodeInt();
		}
		return ints;
	}

	/**
	 * Get next array of long.
	 *
	 * @return the array on integers
	 */
	public long[] xdrDecodeLongVector() throws IOException {

		int len = xdrDecodeInt();
		logger.log(Level.FINEST, "Decoding long array with len = {0}", len);

		long[] longs = new long[len];
		for (int i = 0; i < len; i++) {
			longs[i] = xdrDecodeLong();
		}
		return longs;
	}

	/**
	 * Decodes (aka "deserializes") a float (which is a 32 bits wide floating
	 * point entity) read from a XDR stream.
	 *
	 * @return Decoded float value.rs.
	 */
	public float xdrDecodeFloat() throws IOException {
		return Float.intBitsToFloat(xdrDecodeInt());
	}

	/**
	 * Decodes (aka "deserializes") a double (which is a 64 bits wide floating
	 * point entity) read from a XDR stream.
	 *
	 * @return Decoded double value.rs.
	 */
	public double xdrDecodeDouble() throws IOException {
		return Double.longBitsToDouble(xdrDecodeLong());
	}

	/**
	 * Decodes (aka "deserializes") a vector of doubles read from a XDR stream.
	 *
	 * @return Decoded double vector.
	 */
	public double[] xdrDecodeDoubleVector() throws IOException {
		int length = xdrDecodeInt();
		double[] value = new double[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeDouble();
		}
		return value;
	}

	/**
	 * Decodes (aka "deserializes") a vector of doubles read from a XDR stream.
	 *
	 * @param length of vector to read.
	 *
	 * @return Decoded double vector..
	 */
	public double[] xdrDecodeDoubleFixedVector(int length) throws IOException {
		double[] value = new double[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeDouble();
		}
		return value;
	}

	/**
	 * Decodes (aka "deserializes") a vector of floats read from a XDR stream.
	 *
	 * @return Decoded float vector.
	 */
	public float[] xdrDecodeFloatVector() throws IOException {
		int length = xdrDecodeInt();
		float[] value = new float[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeFloat();
		}
		return value;
	}

	/**
	 * Decodes (aka "deserializes") a vector of floats read from a XDR stream.
	 *
	 * @param length of vector to read.
	 *
	 * @return Decoded float vector.
	 */
	public float[] xdrDecodeFloatFixedVector(int length) throws IOException {
		float[] value = new float[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeFloat();
		}
		return value;
	}

	/**
	 * Get next opaque data.  The decoded data
	 * is always padded to be a multiple of four.
	 *
	 * @param buf buffer where date have to be stored
	 * @param offset in the buffer.
	 * @param len number of bytes to read.
	 */
	public void xdrDecodeOpaque(byte[] buf, int offset, int len) throws IOException {
		int padding = (4 - (len & 3)) & 3;
		fill(len + padding);
//		logger.log(Level.FINEST, "padding zeros: {0}", padding);
		buffer.get(buf, offset, len);
		buffer.position(buffer.position() + padding);
	}

	public void xdrDecodeOpaque(byte[] buf,  int len) throws IOException {
		xdrDecodeOpaque(buf, 0, len);
	}

	public byte[] xdrDecodeOpaque(int len) throws IOException {
		byte[] opaque = new byte[len];
		xdrDecodeOpaque(opaque, len);
		return opaque;
	}

	/**
	 * Decodes (aka "deserializes") a XDR opaque value, which is represented
	 * by a vector of byte values. The length of the opaque value to decode
	 * is pulled off of the XDR stream, so the caller does not need to know
	 * the exact length in advance. The decoded data is always padded to be
	 * a multiple of four (because that's what the sender does).
	 */
	public byte [] xdrDecodeDynamicOpaque() throws IOException {
		int length = xdrDecodeInt();
		byte [] opaque = new byte[length];
		if ( length != 0 ) {
			xdrDecodeOpaque(opaque, 0, length);
		}
		return opaque;
	}

	/**
	 * Get next String.
	 *
	 * @return decoded string
	 */
	public String xdrDecodeString() throws IOException {
		String ret;

		int len = xdrDecodeInt();
		logger.log(Level.FINEST, "Decoding string with len = {0}", len);

		if (len > 0) {
			byte[] bytes = new byte[len];
			xdrDecodeOpaque(bytes, 0, len);
			ret = new String(bytes);
		} else {
			ret = "";
		}

		return ret;
	}

	public boolean xdrDecodeBoolean() throws IOException {
		int bool = xdrDecodeInt();
		return bool != 0;
	}

	/**
	 * Decodes (aka "deserializes") a long (which is called a "hyper" in XDR
	 * babble and is 64&nbsp;bits wide) read from a XDR stream.
	 *
	 * @return Decoded long value.
	 */
	public long xdrDecodeLong() throws IOException {
		return (((long)xdrDecodeInt()) << 32) | (long)xdrDecodeInt();
	}

	public ByteBuffer xdrDecodeByteBuffer() throws IOException {
		int len = this.xdrDecodeInt();
		int padding = (4 - (len & 3)) & 3;
		fill(len+padding);

	   /*
		* as of grizzly 2.2.1 toByteBuffer returns a ByteBuffer view of
		* the backended heap. To be able to use rewind, flip and so on
		* we have to use slice of it.
		*/
		ByteBuffer slice = buffer.slice();
		slice.rewind();
		slice.limit(len);
		buffer.position(buffer.position() + len + padding);
		return slice;
	}

	/**
	 * Decodes (aka "deserializes") a vector of bytes, which is nothing more
	 * than a series of octets (or 8 bits wide bytes), each packed into its very
	 * own 4 bytes (XDR int). Byte vectors are decoded together with a
	 * preceeding length value. This way the receiver doesn't need to know the
	 * length of the vector in advance.
	 *
	 * @return The byte vector containing the decoded data.
	 */
	public byte[] xdrDecodeByteVector() throws IOException {
		int length = xdrDecodeInt();
		if (length > 0) {
			byte[] bytes = new byte[length];
			for (int i = 0; i < length; ++i) {
				bytes[i] = xdrDecodeByte();
			}
			return bytes;
		} else {
			return new byte[0];
		}
	}

	/**
	 * Decodes (aka "deserializes") a vector of bytes, which is nothing more
	 * than a series of octets (or 8 bits wide bytes), each packed into its very
	 * own 4 bytes (XDR int).
	 *
	 * @param length of vector to read.
	 *
	 * @return The byte vector containing the decoded data.
	 */
	public byte[] xdrDecodeByteFixedVector(int length) throws IOException {
		if (length > 0) {
			byte[] bytes = new byte[length];
			for (int i = 0; i < length; ++i) {
				bytes[i] = xdrDecodeByte();
			}
			return bytes;
		} else {
			return new byte[0];
		}
	}

	/**
	 * Decodes (aka "deserializes") a byte read from this XDR stream.
	 *
	 * @return Decoded byte value.
	 */
	public byte xdrDecodeByte() throws IOException {
		fill(4);
		buffer.get(intbuffer, 0, 4);
		return intbuffer[3];
	}

	/**
	 * Decodes (aka "deserializes") a short (which is a 16 bit quantity) read
	 * from this XDR stream.
	 *
	 * @return Decoded short value.
	 */
	public short xdrDecodeShort() throws IOException {
		return (short) xdrDecodeInt();
	}

	/**
	 * Decodes (aka "deserializes") a vector of short integers read from a XDR
	 * stream.
	 *
	 * @return Decoded vector of short integers..
	 */
	public short[] xdrDecodeShortVector() throws IOException {
		int length = xdrDecodeInt();
		short[] value = new short[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeShort();
		}
		return value;
	}

	/**
	 * Decodes (aka "deserializes") a vector of short integers read from a XDR
	 * stream.
	 *
	 * @param length of vector to read.
	 *
	 * @return Decoded vector of short integers.
	 */
	public short[] xdrDecodeShortFixedVector(int length) throws IOException {
		short[] value = new short[length];
		for (int i = 0; i < length; ++i) {
			value[i] = xdrDecodeShort();
		}
		return value;
	}
	////////////////////////////////////////////////////////////////////////////
	//
	//		 Encoder
	//
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Encodes (aka "serializes") a "XDR int" value and writes it down a
	 * XDR stream. A XDR int is 32 bits wide -- the same width Java's "int"
	 * data type has. This method is one of the basic methods all other
	 * methods can rely on.
	 */
	public void xdrEncodeInt(int value) {
//		logger.log(Level.FINEST, "Encode int {0}", value);
		buffer.putInt(value);
	}

	/**
	 * Returns the {@link ByteBuffer} that backs this xdr.
	 *
	 * <p>Modifications to this xdr's content will cause the returned
	 * buffer's content to be modified, and vice versa.
	 *
	 * @return The {@link ByteBuffer} that backs this xdr
	 */
	public ByteBuffer asBuffer() {
		return buffer;
	}

	/**
	 * Encodes (aka "serializes") a vector of ints and writes it down
	 * this XDR stream.
	 *
	 * @param values int vector to be encoded.
	 *
	 */
	public void xdrEncodeIntVector(int[] values) {
		logger.log(Level.FINEST, "Encode int array");
		buffer.putInt(values.length);
		for (int value: values) {
			buffer.putInt( value );
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of longs and writes it down
	 * this XDR stream.
	 *
	 * @param values long vector to be encoded.
	 *
	 */
	public void xdrEncodeLongVector(long[] values) {
		logger.log(Level.FINEST, "Encode long array");
		buffer.putInt(values.length);
		for (long value : values) {
			buffer.putLong(value);
		}
	}

	/**
	 * Encodes (aka "serializes") a float (which is a 32 bits wide floating
	 * point quantity) and write it down this XDR stream.
	 *
	 * @param value Float value to encode.
	 */
	public void xdrEncodeFloat(float value) {
		xdrEncodeInt(Float.floatToIntBits(value));
	}

	/**
	 * Encodes (aka "serializes") a double (which is a 64 bits wide floating
	 * point quantity) and write it down this XDR stream.
	 *
	 * @param value Double value to encode.
	 */
	public void xdrEncodeDouble(double value) {
		xdrEncodeLong(Double.doubleToLongBits(value));
	}

	/**
	 * Encodes (aka "serializes") a vector of floats and writes it down this XDR
	 * stream.
	 *
	 * @param value float vector to be encoded.
	 */
	public void xdrEncodeFloatVector(float[] value) {
		int size = value.length;
		xdrEncodeInt(size);
		for (int i = 0; i < size; i++) {
			xdrEncodeFloat(value[i]);
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of floats and writes it down this XDR
	 * stream.
	 *
	 * @param value float vector to be encoded.
	 * @param length of vector to write. This parameter is used as a sanity
	 * check.
	 */
	public void xdrEncodeFloatFixedVector(float[] value, int length) {
		if (value.length != length) {
			throw (new IllegalArgumentException("array size does not match protocol specification"));
		}
		for (int i = 0; i < length; i++) {
			xdrEncodeFloat(value[i]);
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of doubles and writes it down this
	 * XDR stream.
	 *
	 * @param value double vector to be encoded.
	 */
	public void xdrEncodeDoubleVector(double[] value) {
		int size = value.length;
		xdrEncodeInt(size);
		for (int i = 0; i < size; i++) {
			xdrEncodeDouble(value[i]);
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of doubles and writes it down this
	 * XDR stream.
	 *
	 * @param value double vector to be encoded.
	 * @param length of vector to write. This parameter is used as a sanity
	 * check.
	 */
	public void xdrEncodeDoubleFixedVector(double[] value, int length) {
		if (value.length != length) {
			throw (new IllegalArgumentException("array size does not match protocol specification"));
		}
		for (int i = 0; i < length; i++) {
			xdrEncodeDouble(value[i]);
		}
	}

	/**
	 * Encodes (aka "serializes") a string and writes it down this XDR stream.
	 *
	 */
	public void xdrEncodeString(String string) {
		logger.log(Level.FINEST, "Encode String:  {0}", string);
		if( string == null ) {
			string = "";
		}
		xdrEncodeDynamicOpaque(string.getBytes());
	}

	private static final byte [] paddingZeros = { 0, 0, 0, 0 };

	/**
	 * Encodes (aka "serializes") a XDR opaque value, which is represented
	 * by a vector of byte values. Only the opaque value is encoded, but
	 * no length indication is preceeding the opaque value, so the receiver
	 * has to know how long the opaque value will be. The encoded data is
	 * always padded to be a multiple of four. If the length of the given byte
	 * vector is not a multiple of four, zero bytes will be used for padding.
	 */
	public void xdrEncodeOpaque(byte[] bytes, int offset, int len) {
		logger.log(Level.FINEST, "Encode Opaque, len = {0}", len);
		int padding = (4 - (len & 3)) & 3;
		buffer.put(bytes, offset, len);
		buffer.put(paddingZeros, 0, padding);
	}

	public void xdrEncodeOpaque(byte[] bytes, int len) {
		xdrEncodeOpaque(bytes, 0, len);
	}

	/**
	 * Encodes (aka "serializes") a XDR opaque value, which is represented
	 * by a vector of byte values. The length of the opaque value is written
	 * to the XDR stream, so the receiver does not need to know
	 * the exact length in advance. The encoded data is always padded to be
	 * a multiple of four to maintain XDR alignment.
	 *
	 */
	public void xdrEncodeDynamicOpaque(byte [] opaque) {
		xdrEncodeInt(opaque.length);
		xdrEncodeOpaque(opaque, 0, opaque.length);
	}

	public void xdrEncodeBoolean(boolean bool) {
		xdrEncodeInt( bool ? 1 : 0);
	}

	/**
	 * Encodes (aka "serializes") a long (which is called a "hyper" in XDR
	 * babble and is 64&nbsp;bits wide) and write it down this XDR stream.
	 */
	public void xdrEncodeLong(long value) {
	   buffer.putLong(value);
	}

	public void xdrEncodeByteBuffer(ByteBuffer buf) {
		buf.flip();
		int len = buf.remaining();
		int padding = (4 - (len & 3)) & 3;
		xdrEncodeInt(len);
		buffer.put(buf);
		buffer.position(buffer.position() + padding);
	}

	/**
	 * Encodes (aka "serializes") a vector of bytes, which is nothing more than
	 * a series of octets (or 8 bits wide bytes), each packed into its very own
	 * 4 bytes (XDR int). Byte vectors are encoded together with a preceeding
	 * length value. This way the receiver doesn't need to know the length of
	 * the vector in advance.
	 *
	 * @param value Byte vector to encode.
	 */
	public void xdrEncodeByteVector(byte[] value) {
		int length = value.length; // well, silly optimizations appear here...
		xdrEncodeInt(length);
		if (length != 0) {
			//
			// For speed reasons, we do sign extension here, but the higher bits
			// will be removed again when deserializing.
			//
			for (int i = 0; i < length; ++i) {
				xdrEncodeByte(value[i]);
			}
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of bytes, which is nothing more than
	 * a series of octets (or 8 bits wide bytes), each packed into its very own
	 * 4 bytes (XDR int).
	 *
	 * @param value Byte vector to encode.
	 * @param length of vector to write. This parameter is used as a sanity
	 * check.
	 */
	public void xdrEncodeByteFixedVector(byte[] value, int length) {
		if (value.length != length) {
			throw (new IllegalArgumentException("array size does not match protocol specification"));
		}
		if (length != 0) {
			//
			// For speed reasons, we do sign extension here, but the higher bits
			// will be removed again when deserializing.
			//
			for (int i = 0; i < length; ++i) {
				xdrEncodeByte(value[i]);
			}
		}
	}

	/**
	 * Encodes (aka "serializes") a byte and write it down this XDR stream.
	 *
	 * @param value Byte value to encode.
	 *
	 * @throws OncRpcException if an ONC/RPC error occurs.
	 * @throws IOException if an I/O error occurs.
	 */
	public void xdrEncodeByte(byte value) {
		//
		// For speed reasons, we do sign extension here, but the higher bits
		// will be removed again when deserializing.
		//
		intbuffer[0] = intbuffer[1] = intbuffer[2] = 0;
		intbuffer[4] = value;
		buffer.put(intbuffer, 0, 4);
	}

	/**
	 * Encodes (aka "serializes") a short (which is a 16 bits wide quantity) and
	 * write it down this XDR stream.
	 *
	 * @param value Short value to encode.
	 */
	public void xdrEncodeShort(short value) {
		xdrEncodeInt((int) value);
	}

	/**
	 * Encodes (aka "serializes") a vector of short integers and writes it down
	 * this XDR stream.
	 *
	 * @param value short vector to be encoded.
	 */
	public void xdrEncodeShortVector(short[] value) {
		int size = value.length;
		xdrEncodeInt(size);
		for (int i = 0; i < size; i++) {
			xdrEncodeShort(value[i]);
		}
	}

	/**
	 * Encodes (aka "serializes") a vector of short integers and writes it down
	 * this XDR stream.
	 *
	 * @param value short vector to be encoded.
	 * @param length of vector to write. This parameter is used as a sanity
	 * check.
	 */
	public void xdrEncodeShortFixedVector(short[] value, int length) {
		if (value.length != length) {
			throw (new IllegalArgumentException("array size does not match protocol specification"));
		}
		for (int i = 0; i < length; i++) {
			xdrEncodeShort(value[i]);
		}
	}
	
	
	@Override
	public void xdrEncodeBooleanVector(boolean[] bool) {
		logger.log(Level.FINEST, "Encode bool array");
		buffer.putInt(bool.length);
		for (boolean value: bool) {
			xdrEncodeBoolean( value );
		}
	}

	@Override
	public void xdrEncodeStringVector(String[] str) {
		logger.log(Level.FINEST, "Encode String array");
		buffer.putInt(str.length);
		for (String value: str) {
			xdrEncodeString( value );
		}
	}

	public void close() {
		// nop
	}

	@Override
	public boolean[] xdrDecodeBooleanVector() throws IOException {
		int len = xdrDecodeInt();
		logger.log(Level.FINEST, "Decoding bool array with len = {0}", len);

		boolean[] arr = new boolean[len];
		for (int i = 0; i < len; i++) {
			arr[i] = xdrDecodeBoolean();
		}
		return arr;
	}

	@Override
	public String[] xdrDecodeStringVector() throws IOException {
		int len = xdrDecodeInt();
		logger.log(Level.FINEST, "Decoding String array with len = {0}", len);

		String[] arr = new String[len];
		for (int i = 0; i < len; i++) {
			arr[i] = xdrDecodeString();
		}
		return arr;
	}
}
