package org.simplesql.parser;

import org.simplesql.data.AggregateStore;
import org.simplesql.data.DataSource;
import org.simplesql.data.KeyParser;

public interface SQLExecutor {

	/**
	 * 
	 * @param source
	 *            DataSource to read cells from
	 *            
	 * @param keyParser keyParser
	 * 
	 * @param store
	 *            AggregateStore The AggregateStore implementation to use to
	 *            store aggregate data.
	 * @param progressListener
	 *            Progress to update progress to.
	 */
	@SuppressWarnings("rawtypes")
	void pump(DataSource source, AggregateStore store, Progress progressListener);

	/**
	 * 
	 * Each Progress update will be sent an object of this type. Contains the
	 * values about the execution progress.
	 * 
	 */
	public static class ProgressContext {

		int recordsCompleted;

		public int getRecordsCompleted() {
			return recordsCompleted;
		}

		public void setRecordsCompleted(int recordsCompleted) {
			this.recordsCompleted = recordsCompleted;
		}

	}

	/**
	 * Interface to listen to progress of the SQLExecutor as it processes data.
	 * 
	 * 
	 */
	public static interface Progress {

		void update(ProgressContext ctx);

	}

}