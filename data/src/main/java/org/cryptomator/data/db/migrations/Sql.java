package org.cryptomator.data.db.migrations;

import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnConstraint.NOT_NULL;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnConstraint.PRIMARY_KEY;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.BOOLEAN;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.INTEGER;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.TEXT;
import static java.lang.String.format;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

//TODO Use precompiled statements for all?
//TODO Compatibility of Rename table with new androids
//https://developer.android.com/reference/android/database/sqlite/package-summary.html -- API 26 -> 3.18
public class Sql {

	public static SqlInsertBuilder insertInto(String table) {
		return new SqlInsertBuilder(table);
	}

	public static SqlAlterTableBuilder alterTable(String table) {
		return new SqlAlterTableBuilder(table);
	}

	public static SqlDropTableBuilder dropTable(String table) {
		return new SqlDropTableBuilder(table);
	}

	public static SqlCreateTableBuilder createTable(String table) {
		return new SqlCreateTableBuilder(table);
	}

	public static SqlDeleteBuilder deleteFrom(String table) {
		return new SqlDeleteBuilder(table);
	}

	public static SqlUniqueIndexBuilder createUniqueIndex(String indexName) {
		return new SqlUniqueIndexBuilder(indexName);
	}

	public static SqlDropIndexBuilder dropIndex(String index) {
		return new SqlDropIndexBuilder(index);
	}

	public static SqlUpdateBuilder update(String tableName) {
		return new SqlUpdateBuilder(tableName);
	}

	public static SqlQueryBuilder query(String table) {
		return new SqlQueryBuilder(table);
	}

	public static Criterion eq(final String value) {
		return (column, whereClause, whereArgs) -> {
			whereClause.append('"').append(column).append("\" = ?");
			whereArgs.add(value);
		};
	}

	public static Criterion isNull() {
		return (column, whereClause, whereArgs) -> whereClause.append('"').append(column).append("\" IS NULL");
	}

	public static Criterion eq(final Long value) {
		return (column, whereClause, whereArgs) -> whereClause.append('"').append(column).append("\" = ").append(value);
	}

	public static ValueHolder toString(final String value) {
		return (column, contentValues) -> contentValues.put(column, value);
	}

	public static ValueHolder toLong(final Long value) {
		return (column, contentValues) -> contentValues.put(column, value);
	}

	public static ValueHolder toInteger(final Integer value) {
		return (column, contentValues) -> contentValues.put(column, value);
	}

	public static ValueHolder toNull() {
		return (column, contentValues) -> contentValues.putNull(column);
	}

	public interface ValueHolder {

		void put(String column, ContentValues contentValues);

	}

	public interface Criterion {

		void appendTo(String column, StringBuilder whereClause, List<String> whereArgs);
	}

	public static class SqlQueryBuilder {

		private final String tableName;
		private final StringBuilder whereClause = new StringBuilder();
		private final List<String> whereArgs = new ArrayList<>();

		private List<String> columns = new ArrayList<>();

		public SqlQueryBuilder(String tableName) {
			this.tableName = tableName;
		}

		public SqlQueryBuilder columns(List<String> columns) {
			this.columns = columns;
			return this;
		}

		public SqlQueryBuilder where(String column, Criterion criterion) {
			if (whereClause.length() > 0) {
				whereClause.append(" AND ");
			}
			criterion.appendTo(column, whereClause, whereArgs);
			return this;
		}

		public Cursor executeOn(SupportSQLiteDatabase db) {
			if(columns == null || columns.isEmpty()) {
				throw new IllegalArgumentException();
			}
			if(tableName == null || tableName.trim().isEmpty()) {
				throw new IllegalArgumentException();
			}

			StringBuilder query = new StringBuilder().append("SELECT (");
			appendColumns(query, columns.toArray(new String[0]), null, false);
			query.append(") FROM ").append('"').append(tableName).append('"').append(" WHERE ").append(whereClause);

			return db.query(query.toString(), whereArgs.toArray());
		}
	}

	public static class SqlUpdateBuilder {

		private final String tableName;

		private final StringBuilder whereClause = new StringBuilder();
		private final List<String> whereArgs = new ArrayList<>();
		private final ContentValues contentValues = new ContentValues();

		public SqlUpdateBuilder(String tableName) {
			this.tableName = tableName;
		}

		public SqlUpdateBuilder set(String column, ValueHolder value) {
			value.put(column, contentValues);
			return this;
		}

		public SqlUpdateBuilder where(String column, Criterion criterion) {
			if (whereClause.length() > 0) {
				whereClause.append(" AND ");
			}
			criterion.appendTo(column, whereClause, whereArgs);
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			if (contentValues.size() == 0) {
				throw new IllegalStateException("At least one value must be set");
			}
			//TODO Error handling in replacement of SQLiteDatabase#insertOrThrow, all-null-handling
			// ... Handling of everything that was changed since the prior implementation
			db.update(tableName, SQLiteDatabase.CONFLICT_FAIL, contentValues, whereClause.toString(), whereArgs.toArray(new String[whereArgs.size()]));
		}

	}

	public static class SqlDropIndexBuilder {

		private final String index;

		private SqlDropIndexBuilder(String index) {
			this.index = index;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.execSQL(format("DROP INDEX \"%s\"", index));
		}

	}

	public static class SqlUniqueIndexBuilder {

		private final String indexName;
		private final StringBuilder columns = new StringBuilder();
		private String table;

		private SqlUniqueIndexBuilder(String indexName) {
			this.indexName = indexName;
		}

		public SqlUniqueIndexBuilder on(String table) {
			this.table = table;
			return this;
		}

		public SqlUniqueIndexBuilder asc(String column) {
			if (columns.length() > 0) {
				columns.append(',');
			}
			columns.append('"').append(column).append('"').append(" ASC");
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.execSQL(format("CREATE UNIQUE INDEX \"%s\" ON \"%s\" (%s)", indexName, table, columns));
		}
	}

	public static class SqlDropTableBuilder {

		private final String table;

		private SqlDropTableBuilder(String table) {
			this.table = table;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.execSQL(format("DROP TABLE \"%s\"", table));
		}

	}

	public static class SqlAlterTableBuilder {

		private final String table;
		private String newName;

		private SqlAlterTableBuilder(String table) {
			this.table = table;
		}

		public SqlAlterTableBuilder renameTo(String newName) {
			this.newName = newName;
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.execSQL(format("ALTER TABLE \"%s\" RENAME TO \"%s\"", table, newName));
		}
	}

	public static class SqlInsertSelectBuilder {

		private final String table;
		private final String[] selectedColumns;
		private final StringBuilder joinClauses = new StringBuilder();
		private String[] columns;
		private String sourceTableName;

		private SqlInsertSelectBuilder(String table, String[] columns) {
			this.table = table;
			this.columns = columns;
			this.selectedColumns = columns;
		}

		public SqlInsertSelectBuilder from(String sourceTableName) {
			this.sourceTableName = sourceTableName;
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			StringBuilder query = new StringBuilder().append("INSERT INTO \"").append(table).append("\" (");
			appendColumns(query, columns, sourceTableName, false);
			query.append(") SELECT ");
			appendColumns(query, selectedColumns, sourceTableName, true);
			query.append(" FROM \"").append(sourceTableName).append('"');
			query.append(joinClauses);
			db.execSQL(query.toString());
		}

		public SqlInsertSelectBuilder join(String targetTable, String sourceColumn) {
			sourceColumn = sourceColumn.replace(".", "\".\"");
			joinClauses.append(" JOIN \"") //
					.append(targetTable) //
					.append("\" ON \"") //
					.append(sourceColumn) //
					.append("\" = \"") //
					.append(targetTable) //
					.append("\".\"_id\" ");

			return this;
		}

		public SqlInsertSelectBuilder columns(String... columns) {

			if (columns.length != selectedColumns.length) {
				throw new IllegalStateException("Number of columns must match number of selected values");
			}

			this.columns = columns;

			return this;
		}
	}

	public static class SqlCreateTableBuilder {

		private final String table;
		private final StringBuilder columns = new StringBuilder();

		private final StringBuilder foreignKeys = new StringBuilder();

		private SqlCreateTableBuilder(String table) {
			this.table = table;
		}

		public SqlCreateTableBuilder id() {
			column("_id", INTEGER, PRIMARY_KEY);
			return this;
		}

		public SqlCreateTableBuilder optionalText(String name) {
			column(name, TEXT);
			return this;
		}

		public SqlCreateTableBuilder requiredText(String name) {
			column(name, TEXT, NOT_NULL);
			return this;
		}

		public SqlCreateTableBuilder optionalInt(String name) {
			column(name, INTEGER);
			return this;
		}

		public SqlCreateTableBuilder requiredInt(String name) {
			column(name, INTEGER, NOT_NULL);
			return this;
		}

		public SqlCreateTableBuilder optionalBool(String name) {
			column(name, BOOLEAN);
			return this;
		}

		public SqlCreateTableBuilder requiredBool(String name) {
			column(name, BOOLEAN, NOT_NULL);
			return this;
		}

		public SqlCreateTableBuilder column(String name, ColumnType type, ColumnConstraint... contraints) {
			if (columns.length() > 0) {
				columns.append(',');
			}
			columns.append(name).append(' ').append(type.getText());
			for (ColumnConstraint constraint : contraints) {
				columns.append(' ').append(constraint.getText());
			}
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.execSQL(format("CREATE TABLE \"%s\" (%s%s)", table, columns, foreignKeys));
		}

		public SqlCreateTableBuilder foreignKey(String column, String targetTable, ForeignKeyBehaviour... behaviours) {
			foreignKeys //
					.append(", CONSTRAINT FK_") //
					.append(column) //
					.append("_") //
					.append(targetTable) //
					.append(" FOREIGN KEY (") //
					.append(column) //
					.append(") REFERENCES ") //
					.append(targetTable) //
					.append("(_id)");

			for (ForeignKeyBehaviour behaviour : behaviours) {
				foreignKeys.append(" ").append(behaviour.getText());
			}

			return this;
		}

		public enum ForeignKeyBehaviour {
			ON_DELETE_SET_NULL("ON DELETE SET NULL");

			private final String text;

			ForeignKeyBehaviour(String text) {
				this.text = text;
			}

			public String getText() {
				return text;
			}
		}

		public enum ColumnType {
			INTEGER("INTEGER"), BOOLEAN("INTEGER"), TEXT("TEXT");

			private final String text;

			ColumnType(String text) {
				this.text = text;
			}

			public String getText() {
				return text;
			}
		}

		public enum ColumnConstraint {
			NOT_NULL("NOT NULL"), PRIMARY_KEY("PRIMARY KEY");

			private final String text;

			ColumnConstraint(String text) {
				this.text = text;
			}

			public String getText() {
				return text;
			}
		}

	}

	public static class SqlInsertBuilder {

		private final String table;
		private final ContentValues contentValues = new ContentValues();

		private SqlInsertBuilder(String table) {
			this.table = table;
		}

		public SqlInsertSelectBuilder select(String... columns) {
			return new SqlInsertSelectBuilder(table, columns);
		}

		public SqlInsertBuilder text(String column, Object value) {
			contentValues.put(column, value == null ? null : value.toString());
			return this;
		}

		public SqlInsertBuilder integer(String column, Integer value) {
			contentValues.put(column, value);
			return this;
		}

		public SqlInsertBuilder bool(String column, Boolean value) {
			contentValues.put(column, value);
			return this;
		}

		public Long executeOn(SupportSQLiteDatabase db) {
			//TODO Error handling in replacement of SQLiteDatabase#insertOrThrow, all-null-handling
			// ... Handling of everything that was changed since the prior implementation
			return db.insert(this.table, SQLiteDatabase.CONFLICT_FAIL, contentValues);
		}
	}

	public static class SqlDeleteBuilder {

		private final String tableName;

		private final StringBuilder whereClause = new StringBuilder();
		private final List<String> whereArgs = new ArrayList<>();

		public SqlDeleteBuilder(String tableName) {
			this.tableName = tableName;
		}

		public SqlDeleteBuilder where(String column, Criterion criterion) {
			if (whereClause.length() > 0) {
				whereClause.append(" AND ");
			}
			criterion.appendTo(column, whereClause, whereArgs);
			return this;
		}

		public void executeOn(SupportSQLiteDatabase db) {
			db.delete(tableName, whereClause.toString(), whereArgs.toArray(new String[whereArgs.size()]));
		}
	}

	private static final int NOT_FOUND = -1;
	private static void appendColumns(StringBuilder query, String[] columns, String sourceTableName, boolean appendSourceTableName) {
		boolean notFirst = false;

		for (String column : columns) {
			if (notFirst) {
				query.append(',');
			}

			if (appendSourceTableName && column.indexOf('.') == NOT_FOUND) {
				query.append('"').append(sourceTableName).append("\".\"").append(column).append('"');
			} else {
				column = column.replace(".", "\".\"");
				query.append('"').append(column).append('"');
			}

			notFirst = true;
		}
	}

}
