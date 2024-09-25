package org.cryptomator.data.db.migrations;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.cryptomator.data.util.Utils;

import java.util.ArrayList;
import java.util.List;

import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnConstraint.NOT_NULL;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnConstraint.PRIMARY_KEY;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.BOOLEAN;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.INTEGER;
import static org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ColumnType.TEXT;
import static java.lang.String.format;

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

	public static Criterion notEq(final String value) {
		return (column, whereClause, whereArgs) -> {
			whereClause.append('"').append(column).append("\" != ?");
			whereArgs.add(value);
		};
	}

	public static Criterion isNull() {
		return (column, whereClause, whereArgs) -> whereClause.append('"').append(column).append("\" IS NULL");
	}

	public static Criterion isNotNull() {
		return (column, whereClause, whereArgs) -> whereClause.append('"').append(column).append("\" IS NOT NULL");
	}

	public static Criterion eq(final Long value) {
		return (column, whereClause, whereArgs) -> whereClause.append('"').append(column).append("\" = ").append(value);
	}

	public static Criterion like(final String value) {
		return (column, whereClause, whereArgs) -> {
			whereClause.append('"').append(column).append("\" LIKE(?)");
			whereArgs.add(value);
		};
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
		private String groupBy;
		private String having;
		private String orderBy;
		private String limit;

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

		public SqlQueryBuilder groupBy(String groupBy) {
			this.groupBy = Utils.requireNullOrNotBlank(groupBy);
			return this;
		}

		public SqlQueryBuilder having(String having) {
			this.having = Utils.requireNullOrNotBlank(having);
			return this;
		}

		public SqlQueryBuilder orderBy(String orderBy) {
			this.orderBy = Utils.requireNullOrNotBlank(orderBy);
			return this;
		}

		public SqlQueryBuilder limit(String limit) {
			this.limit = Utils.requireNullOrNotBlank(limit);
			return this;
		}

		public Cursor executeOn(SupportSQLiteDatabase db) {
			if (tableName == null || tableName.trim().isEmpty()) {
				throw new IllegalArgumentException();
			}
			String query = SQLiteQueryBuilder.buildQueryString( //
					/* distinct */ false, //
					tableName, //
					Utils.emptyToNull(columns.toArray(new String[columns.size()])), //
					Utils.blankToNull(whereClause.toString()), //
					groupBy, //
					having, //
					orderBy, //
					limit  //
			);
			//In contrast to "SupportSQLiteDatabase#update" "query" doesn't define how the contents of "whereArgs" are bound.
			//As of now we always pass an "Array<String>", but this has to be kept in mind if we ever change this. See: "SqlUpdateBuilder#executeOn"
			return db.query(query, whereArgs.toArray());
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

			//The behavior of "SupportSQLiteDatabase#update" is a bit strange, which caused me to investigate:
			//The docs say that the contents of "whereArgs" are bound as "Strings", even if the parameter is of type "Array<Object/Any>".
			//The internal binding methods are type-safe, but resolve to just putting all args into an "Array<Object/Any>" in "SQLiteProgram" anyway.
			//This array is also used by "SQLiteDatabase#update". Apparently the contents of the array are then bound as "Strings".
			//As of now we always pass an "Array<String>", but all of this has to be kept in mind if we ever change this.
			db.update(tableName, SQLiteDatabase.CONFLICT_NONE, contentValues, Utils.blankToNull(whereClause.toString()), whereArgs.toArray(new String[whereArgs.size()]));
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

		public void executeOn(SupportSQLiteDatabase db) {
			StringBuilder query = new StringBuilder().append("INSERT INTO \"").append(table).append("\" (");
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
			return join(targetTable, "id", sourceColumn);
		}

		public SqlInsertSelectBuilder pre15Join(String targetTable, String sourceColumn) {
			return join(targetTable, "_id", sourceColumn);
		}

		public SqlInsertSelectBuilder join(String targetTable, String targetColumn, String sourceColumn) {
			sourceColumn = sourceColumn.replace(".", "\".\"");
			joinClauses.append(" JOIN \"") //
					.append(targetTable) //
					.append("\" ON \"") //
					.append(sourceColumn) //
					.append("\" = \"") //
					.append(targetTable) //
					.append("\".\"") //
					.append(targetColumn) //
					.append("\" ");

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
			column("id", INTEGER, PRIMARY_KEY);
			return this;
		}

		public SqlCreateTableBuilder pre15Id() {
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
			return foreignKey(column, targetTable, "id", behaviours);
		}

		public SqlCreateTableBuilder pre15ForeignKey(String column, String targetTable, ForeignKeyBehaviour... behaviours) {
			return foreignKey(column, targetTable, "_id", behaviours);
		}

		public SqlCreateTableBuilder foreignKey(String column, String targetTable, String targetColumn, ForeignKeyBehaviour... behaviours) {
			foreignKeys //
					.append(", CONSTRAINT FK_") //
					.append(column) //
					.append("_") //
					.append(targetTable) //
					.append(" FOREIGN KEY (") //
					.append(column) //
					.append(") REFERENCES ") //
					.append(targetTable) //
					.append("(") //
					.append(targetColumn) //
					.append(")");

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

		public Long executeOn(SupportSQLiteDatabase db) {
			if (contentValues.size() == 0) {
				throw new IllegalStateException("At least one value must be set");
			}

			//In contrast to "SupportSQLiteDatabase#update" "insert" doesn't define how the contents of "contentValues" are bound.
			//As opposed to the other methods in this class, we do actually pass "Integers" and "Strings" here and again they appear
			//to end up in the "Array<Object/Any>" in "SQLiteProgram". Currently there is no issue,
			//but this has to be kept in mind if we ever change this method. See: "SqlUpdateBuilder#executeOn"
			return db.insert(this.table, SQLiteDatabase.CONFLICT_NONE, contentValues);
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
			//"SupportSQLiteDatabase#delete" always binds the contents of "whereArgs" as "Strings".
			//As of now we always pass an "Array<String>", but this has to be kept in mind if we ever change this. See: "SqlUpdateBuilder#executeOn"
			db.delete(tableName, Utils.blankToNull(whereClause.toString()), whereArgs.toArray(new String[whereArgs.size()]));
		}
	}
}
