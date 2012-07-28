/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package muscle.util.serialization;

import java.io.IOException;
import java.io.Serializable;
import muscle.util.data.MatrixTool;
import muscle.util.data.SerializableDatatype;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.XdrEncodingStream;

/**
 *
 * @author Joris Borgdorff
 */
public class XdrSerializerWrapper implements SerializerWrapper {
	private final XdrEncodingStream xdrOut;
	public final static int DEFAULT_BUFFER_SIZE=65536*9/10;
	private final static float CHUNK_SIZE_MODIFIER = .9f;
	private final float max_chunk_size;

	public XdrSerializerWrapper(XdrEncodingStream xdrOut, int buffer_size) {
		this.xdrOut = xdrOut;
		this.max_chunk_size = CHUNK_SIZE_MODIFIER*buffer_size;
	}

	@Override
	public void writeInt(int num) throws IOException {
		try {
			this.xdrOut.xdrEncodeInt(num);
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not write int", ex);
		}
	}

	@Override
	public void writeBoolean(boolean bool) throws IOException {
		try {
			this.xdrOut.xdrEncodeBoolean(bool);
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not write boolean", ex);
		}
	}

	@Override
	public void writeByteArray(byte[] bytes) throws IOException {
		this.writeValue(bytes, SerializableDatatype.BYTE_ARR);
	}

	@Override
	public void flush() throws IOException {
		try {
			this.xdrOut.endEncoding();
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not flush response", ex);
		}
	}
	
	@Override
	public void writeString(String str) throws IOException {
		try {
			this.xdrOut.xdrEncodeString(str);
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not write string", ex);
		}
	}

	@Override
	public void writeDouble(double d) throws IOException {
		try {
			this.xdrOut.xdrEncodeDouble(d);
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not write string", ex);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			this.xdrOut.close();
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not close output", ex);
		}
	}
	
	public XdrEncodingStream getXdrEncodingStream() {
		return this.xdrOut;
	}

	@Override
	public void writeValue(Serializable newValue, SerializableDatatype type) throws IOException {
		try {
			if (type.typeOf().isArray()) {
				int len = MatrixTool.lengthOfMatrix(newValue, type);
				
				// Take into account that char* is transmitted worse than expected (4 byte per char) in XDR
				int size = Math.max(len*4+20,MatrixTool.deepSizeOf(newValue, type));
				int chunks = (int)Math.ceil(size / max_chunk_size);
				xdrOut.xdrEncodeInt(chunks);
				
				if (chunks > 1) {
					xdrOut.xdrEncodeInt(len);
					
					int chunk_len = (int)Math.ceil(len / (float)chunks);
					int first_chunk_len = len - ((chunks - 1) * chunk_len);
					
					Serializable arr = MatrixTool.initializeArray(type, first_chunk_len);
					System.arraycopy(newValue, 0, arr, 0, first_chunk_len);
					write(arr, type);
					int index = first_chunk_len;
					
					if (chunk_len != first_chunk_len) {
						arr = MatrixTool.initializeArray(type, chunk_len);
					}
					for (int i = 1; i < chunks; i++) {
						xdrOut.endEncoding();
						System.arraycopy(newValue, index, arr, 0, chunk_len);
						write(arr, type);
						index += chunk_len;
					}
				} else {
					write(newValue, type);
				}
			}
			else {
				switch (type) {
					case BYTE:
						xdrOut.xdrEncodeByte((Byte)newValue);
						break;
					case SHORT:
						xdrOut.xdrEncodeShort((Short)newValue);
						break;
					case LONG:
						xdrOut.xdrEncodeLong((Long)newValue);
						break;
					case FLOAT:
						xdrOut.xdrEncodeFloat((Float)newValue);
						break;
					default:
						throw new IllegalArgumentException("Datatype " + type + " not recognized");
				}
			}
		} catch (OncRpcException ex) {
			throw new IllegalStateException("Can not write value", ex);
		}
	}
	
	private void write(Serializable newValue, SerializableDatatype type) throws OncRpcException, IOException {
		switch (type.typeOf()) {
			case STRING_ARR:
				xdrOut.xdrEncodeStringVector((String[])newValue);
				break;
			case BOOLEAN_ARR:
				xdrOut.xdrEncodeBooleanVector((boolean[])newValue);
				break;
			case SHORT_ARR:
				xdrOut.xdrEncodeShortVector((short[])newValue);
				break;
			case INT_ARR:
				xdrOut.xdrEncodeIntVector((int[])newValue);
				break;
			case LONG_ARR:
				xdrOut.xdrEncodeLongVector((long[])newValue);
				break;
			case FLOAT_ARR:
				xdrOut.xdrEncodeFloatVector((float[])newValue);
				break;
			case DOUBLE_ARR:
				xdrOut.xdrEncodeDoubleVector((double[])newValue);
				break;
			case BYTE_ARR:
				xdrOut.xdrEncodeByteVector((byte[])newValue);
				break;
			default:
				throw new IllegalArgumentException("Datatype " + type + " not recognized");
		}
	}
}
