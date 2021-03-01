package org.cryptomator.data.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ColumnConstraint.NOT_NULL;
import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ColumnConstraint.PRIMARY_KEY;
import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ColumnType.BOOLEAN;
import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ColumnType.INTEGER;
import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ColumnType.TEXT;

class Sql {

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

	public static ValueHolder toInteger(final Long value) {
		return (column, contentValues) -> contentValues.put(column, value);
	}

	public static ValueHolder toNull() {
		return (column, contentValues) -> contentValues.putNull(column);
	}

	private static SQLiteDatabase unwrap(Database wrapped) {
		return (SQLiteDatabase) wrapped.getRawDatabase();
	}

	public interface ValueHolder {

		void put(String column, ContentValues contentValues);

	}

	public interface Criterion {

		void appendTo(String column, StringBuilder whereClause, List<String> whereArgs);
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

		public void executeOn(Database wrapped) {
			if (contentValues.size() == 0) {
				throw new IllegalStateException("At least one value must be set");
			}
			SQLiteDatabase db = unwrap(wrapped);
			db.update(tableName, contentValues, whereClause.toString(), whereArgs.toArray(new String[whereArgs.size()]));
		}

	}

	public static class SqlDropIndexBuilder {

		private final String index;

		private SqlDropIndexBuilder(String index) {
			this.index = index;
		}

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
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

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			db.execSQL(format("CREATE UNIQUE INDEX \"%s\" ON \"%s\" (%s)", indexName, table, columns));
		}
	}

	public static class SqlDropTableBuilder {

		private final String table;

		private SqlDropTableBuilder(String table) {
			this.table = table;
		}

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			db.execSQL(format("DROP TABLE \"%s\"", table));
		}

	}

	public static class SqlAlterTableBuilder {

		private final String table;
		private final StringBuilder columns = new StringBuilder();
		private String newName;

		private SqlAlterTableBuilder(String table) {
			this.table = table;
		}

		public SqlAlterTableBuilder renameTo(String newName) {
			this.newName = newName;
			return this;
		}

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			db.execSQL(format("ALTER TABLE \"%s\" RENAME TO \"%s\"", table, newName));
		}
	}

	public static class SqlInsertSelectBuilder {

		private static final int NOT_FOUND = -1;
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

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			StringBuilder query = new StringBuilder() //
					.append("INSERT INTO \"").append(table).append("\" (");
			appendColumns(query, columns, false);
			query.append(") SELECT ");
			appendColumns(query, selectedColumns, true);
			query.append(" FROM \"").append(sourceTableName).append('"');
			query.append(joinClauses);
			db.execSQL(query.toString());
		}

		private void appendColumns(StringBuilder query, String[] columns, boolean appendSourceTableName) {
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

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
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

		public Long executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			return db.insertOrThrow(table, null, contentValues);
		}
	}

	public static class SqlDeleteBuilder {

		private final String table;
		private String whereClause;
		private String[] whereArgs;

		public SqlDeleteBuilder(String table) {
			this.table = table;
		}

		public SqlDeleteBuilder whereClause(String whereClause) {
			this.whereClause = whereClause;
			return this;
		}

		public SqlDeleteBuilder whereArgs(String[] whereArgs) {
			this.whereArgs = whereArgs;
			return this;
		}

		public void executeOn(Database wrapped) {
			SQLiteDatabase db = unwrap(wrapped);
			db.delete(table, whereClause, whereArgs);
		}
	}

}
