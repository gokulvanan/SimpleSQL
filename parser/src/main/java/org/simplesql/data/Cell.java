package org.simplesql.data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public interface Cell<T> extends Counter, Serializable, Comparable<Cell<T>> {

	enum SCHEMA {
		INT(IntCell.class), DOUBLE(DoubleCell.class), LONG(LongCell.class), BOOLEAN(
				BooleanCell.class), DYNAMIC(DynamicCell.class), STRING(
				StringCell.class), FLOAT(FloatCell.class), SHORT(
				ShortCell.class), BYTE(ByteCell.class);

		@SuppressWarnings("rawtypes")
		Class cellCls;

		@SuppressWarnings("rawtypes")
		SCHEMA(Class cellCls) {
			this.cellCls = cellCls;
		}

		@SuppressWarnings("rawtypes")
		public Cell newCell() {
			try {
				return (Cell) cellCls.newInstance();
			} catch (InstantiationException e) {
				RuntimeException rte = new RuntimeException(e.toString(), e);
				rte.setStackTrace(e.getStackTrace());
				throw rte;
			} catch (IllegalAccessException e) {
				RuntimeException rte = new RuntimeException(e.toString(), e);
				rte.setStackTrace(e.getStackTrace());
				throw rte;
			}
		}

	}
	
	int getDefinedWidth();

	String getName();

	T getData();

	SCHEMA getSchema();

	void setData(T dat);

	Cell<T> copy(boolean resetToDefaults);

	Object getMax();

	Object getMin();

	void putHash(HashCodeBuilder builder);

	int byteLength();

	/**
	 * 
	 * @param arr
	 * @param from
	 * @return the number of bytes writen
	 */
	int write(byte[] arr, int from);

	/**
	 * 
	 * @param arr
	 * @param from
	 * @return the number of bytes read
	 */
	int read(byte[] arr, int from);

	public void readFields(DataInput in) throws IOException;

	public void write(DataOutput out) throws IOException;

}
