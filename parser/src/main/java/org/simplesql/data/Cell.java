package org.simplesql.data;

public interface Cell<T> extends Counter {

	T getData();
	
	void setData(T dat);
	
	Cell<T> copy(boolean resetToDefaults);

	Object getMax();

	Object getMin();
	
}
