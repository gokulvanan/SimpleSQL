package org.simplesql;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.codehaus.janino.ExpressionEvaluator;
import org.simplesql.data.AggregateStore;
import org.simplesql.data.Cell;
import org.simplesql.data.DataSink;
import org.simplesql.data.DataSource;
import org.simplesql.data.Key;
import org.simplesql.data.util.FileDataSource;
import org.simplesql.data.util.STDINDataSource;
import org.simplesql.data.util.SelectTransform;
import org.simplesql.om.ClientInfoTemplate.Projection;
import org.simplesql.om.data.StorageManager;
import org.simplesql.om.data.stores.KratiStoreManager;
import org.simplesql.om.projection.ProjectionLexer;
import org.simplesql.om.projection.ProjectionParser;
import org.simplesql.om.projection.ProjectionParser.projection_return;
import org.simplesql.om.util.ProjectionKeyUtil;
import org.simplesql.parser.SQLCompiler;
import org.simplesql.parser.SQLExecutor;
import org.simplesql.parser.SimpleSQLCompiler;
import org.simplesql.schema.TableDef;

/**
 * 
 * Arguments: <br/>
 * <ul>
 * <li>Hbase config</li>
 * <li>scan config</li>
 * <li>input parse</li>
 * <li>schema</li>
 * <li>sql statement</li>
 * </ul>
 * 
 * Reads input from an HBase table.<br/>
 * The table scan filter, start and stop keys are found from the SQL statement.<br/>
 * These are passed to the first argument (scan config):<br/>
 * <p/>
 * <b>Scan config:</b><br/>
 * Is a script written in java syntax, to setup and configure the scanner.<br/>
 * <p/>
 * Each record is read from the table and then input is passed to the
 * "input parse" argument
 * <p/>
 * <b>Input parse:</b><br/>
 * Should return the values as an Object array the order and size of the Object
 * array<br/>
 * must be exactly the same order as the columns in the schema.<br/>
 * 
 * 
 * 
 * 
 */
public class HBaseAggregator {

	static File workingDir;
	static Configuration conf;

	public static void main(String[] args) throws Throwable {

		CommandLine line = new GnuParser().parse(getOptions(), args);

		if (!(line.hasOption("table") && line.hasOption("conf")
				&& line.hasOption("scanconfig") && line.hasOption("schema") && line
					.hasOption("sql"))) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("", getOptions());

			return;
		}

		final String table = line.getOptionValue("table");
		final String confDir = line.getOptionValue("conf");
		final String scanconfig = line.getOptionValue("scanconfig");
		final String schema = line.getOptionValue("schema");
		final String parse = line.getOptionValue("parse");
		final String sql = line.getOptionValue("sql");

		conf = new PropertiesConfiguration(new File(confDir,
				"aggregate.properties"));

		String importStr = "import java.util.*; import org.apache.hadoop.hbase.util.*; import org.apache.hadoop.hbase.client.*; import org.apache.hadoop.hbase.filter.*;\n";

		ExpressionEvaluator scanExpr = new ExpressionEvaluator(importStr
				+ scanconfig, Object.class, new String[] { "scan", "exec" },
				new Class[] { Scan.class, SQLExecutor.class });
		

		final ExpressionEvaluator parseExpr = new ExpressionEvaluator(importStr
				+ parse, Object[].class, new String[] { "res" },
				new Class[] { Result.class });

		org.apache.hadoop.conf.Configuration hadoopconfig = new org.apache.hadoop.conf.Configuration();
		hadoopconfig.addResource(confDir + "/hbase-site.xml");
		org.apache.hadoop.conf.Configuration hconfig = HBaseConfiguration
				.create(hadoopconfig);
		hconfig.set("hbase.zookeeper.quorum",
				conf.getString("hbase.zookeeper.quorum"));

		workingDir = new File("./" + System.currentTimeMillis());
		workingDir.mkdirs();

		HTable htable = new HTable(hconfig, table);

		Scan scan = new Scan();

		try {

			final Projection projection = createProjection(schema);
			final TableDef tableDef = createSchema(projection);

			final Cell.SCHEMA[] schemas = ProjectionKeyUtil
					.createSCHEMA(tableDef);

			final ExecutorService execService = Executors.newCachedThreadPool();
			final SQLCompiler compiler = new SimpleSQLCompiler(execService);

			final SQLExecutor exec = compiler.compile(tableDef, sql);

			// we configure the scan object here
			scanExpr.evaluate(new Object[] { scan, exec });

			final ResultScanner scanner = htable.getScanner(scan);

			try {
				// setup a result scanner parsing
				final DataSource dataSource = new DataSource() {

					@Override
					public Iterator<Object[]> iterator() {
						return new ResultScannerIterator(parseExpr, scanner);
					}

					@Override
					public long getEstimatedSize() {
						return 0;
					}
				};

				final StorageManager manager = getStorageManager(schemas,
						workingDir);
				AggregateStore storage = null;

				try {
					storage = manager.newAggregateStore(projection, exec);

					long startReading = System.currentTimeMillis();

					System.out.println("Reading data");
					exec.pump(dataSource, storage, null);
					System.out.println("Read in: "
							+ (System.currentTimeMillis() - startReading)
							+ "ms");

					final BufferedWriter out = new BufferedWriter(
							new OutputStreamWriter(System.out));

					try {
						storage.write(new DataSink() {

							public boolean fill(Key key, Cell<?>[] data) {
								try {
									final int len = data.length;
									for (int i = 0; i < len; i++) {
										if (i != 0)
											out.append(",");

										out.append(data[i].getData().toString());
									}
									out.append("\n");
								} catch (IOException exp) {
									throw new RuntimeException(exp.toString(),
											exp);
								}

								return true;

							}
						});
					} finally {
						out.close();
					}
				} catch (Throwable t) {
					t.printStackTrace();
				} finally {
					if (storage != null)
						storage.close();
					if (manager != null)
						manager.close();
				}

			} finally {
				scanner.close();
			}

			execService.shutdown();
			execService.awaitTermination(2, TimeUnit.SECONDS);
			execService.shutdownNow();
		} finally {
			htable.close();
			FileUtils.deleteDirectory(workingDir);
		}

	}

	private static StorageManager getStorageManager(Cell.SCHEMA[] schemas,
			File workingDir) throws Throwable {
		// return new CachedStoreManager(new BerkeleyStorageManager(new
		// DBManager(
		// workingDir)), 500, null);
		return new KratiStoreManager(conf.subset("krati"), workingDir, schemas);
	}

	private static Projection createProjection(String line) throws Throwable {
		ProjectionLexer lexer = new ProjectionLexer(new ANTLRStringStream(line));
		ProjectionParser parser = new ProjectionParser(new CommonTokenStream(
				lexer));
		projection_return preturn = parser.projection();
		return preturn.builder.build();
	}

	private static TableDef createSchema(Projection projection) {
		return ProjectionKeyUtil.createTableDefNoIds(projection);
	}

	@SuppressWarnings("static-access")
	private static final Options getOptions() {

		Option table = OptionBuilder.withArgName("table").hasArg()
				.withDescription("HBase table name").create("table");
		Option confDir = OptionBuilder
				.withArgName("conf")
				.hasArg()
				.withDescription(
						"Configuration directory where hbase-site.xml exist")
				.create("conf");
		Option scanConfig = OptionBuilder
				.withArgName("scanconfig")
				.hasArg()
				.withDescription(
						"Scan configuration script (importing java.util.*, org.apache.hadoop.hbase.util.*, org.apache.hadoop.hbase.client.*,  org.apache.hadoop.hbase.filter.*)")
				.create("scanconfig");
		Option parse = OptionBuilder
				.withArgName("parse")
				.hasArg()
				.withDescription(
						"Input parse (importing java.util.*, org.apache.hadoop.hbase.util.*, org.apache.hadoop.hbase.client.*,  org.apache.hadoop.hbase.filter.*)")
				.create("parse");

		Option schema = OptionBuilder.withArgName("schema").hasArg()
				.withDescription("Simple sql schema").create("schema");

		Option sql = OptionBuilder.withArgName("sql").hasArg()
				.withDescription("Simple sql query").create("sql");

		Options opts = new Options();
		opts.addOption(table);
		opts.addOption(confDir);
		opts.addOption(scanConfig);
		opts.addOption(parse);
		opts.addOption(schema);
		opts.addOption(sql);

		return opts;
	}

	static class ResultScannerIterator implements Iterator<Object[]> {

		final ResultScanner scanner;
		final ExpressionEvaluator eval;

		Result result;

		public ResultScannerIterator(ExpressionEvaluator eval,
				ResultScanner scanner) {
			this.eval = eval;
			this.scanner = scanner;
		}

		@Override
		public boolean hasNext() {

			try {
				return (result = scanner.next()) != null;
			} catch (IOException e) {
				RuntimeException rte = new RuntimeException(e.toString(), e);
				throw rte;
			}
		}

		@Override
		public Object[] next() {
			try {
				return (Object[]) eval.evaluate(new Object[] { result });
			} catch (InvocationTargetException e) {
				RuntimeException rte = new RuntimeException(e.toString(), e);
				throw rte;
			}
		}

		@Override
		public void remove() {
		}

	}

}