/*
 * 
 */

package muscle.util.serialization;

import java.io.Serializable;
import muscle.util.data.SerializableData;
import muscle.util.data.SerializableDatatype;

/**
 *
 * @author Joris Borgdorff
 */
public class SerializableDataConverter<T extends Serializable> implements DataConverter<T,SerializableData> {
	private SerializableDatatype type = null;
	
	@Override
	public SerializableData serialize(T data) {
		if (type == null || !type.getDataClass().isInstance(data)) {
			type = SerializableData.inferDatatype(data);
		}
		return SerializableData.valueOf(data, type);
	}

	@Override
	@SuppressWarnings("unchecked")
	public T deserialize(SerializableData data) {
		return (T)data.getValue();
	}

	@Override
	public T copy(T data) {
		if (type == null || !type.getDataClass().isInstance(data)) {
			type = SerializableData.inferDatatype(data);
		}
		return SerializableData.createIndependent(data, type);
	}
}
