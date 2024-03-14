/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ** Notice of Modification **
 *
 * This file has been altered from its original version by the Cryptomator team.
 * For a detailed history of modifications, please refer to the version control log.
 *
 * The original file can be found at https://android.googlesource.com/platform/frameworks/base/+/7d3ffbae618e9e728644a96647ed709bf39ae75/core/java/android/database/sqlite/SQLiteDatabase.java
 *
 * --
 *
 * https://cryptomator.org/
 */

package org.cryptomator.data.db.cachecontrol;

import android.content.ContentValues;
import android.os.Build;

final class AOP_SQLiteDatabase {

	private static final String[] CONFLICT_VALUES = new String[] {"", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "};

	InsertStatement insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
		//acquireReference();
		try {
			StringBuilder sql = new StringBuilder();
			sql.append("INSERT");
			sql.append(CONFLICT_VALUES[conflictAlgorithm]);
			sql.append(" INTO ");
			sql.append(table);
			sql.append('(');

			Object[] bindArgs = null;
			//int size = (initialValues != null && !initialValues.isEmpty()) ? initialValues.size() : 0;
			int size = (initialValues != null && !isEmpty(initialValues)) ? initialValues.size() : 0;
			if (size > 0) {
				bindArgs = new Object[size];
				int i = 0;
				for (String colName : initialValues.keySet()) {
					sql.append((i > 0) ? "," : "");
					sql.append(colName);
					bindArgs[i++] = initialValues.get(colName);
				}
				sql.append(')');
				sql.append(" VALUES (");
				for (i = 0; i < size; i++) {
					sql.append((i > 0) ? ",?" : "?");
				}
			} else {
				sql.append(nullColumnHack).append(") VALUES (NULL");
			}
			sql.append(')');

			return new InsertStatement(sql.toString(), bindArgs);
		} finally {
			//releaseReference();
		}
	}

	private boolean isEmpty(ContentValues contentValues) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return contentValues.isEmpty();
		} else {
			return contentValues.size() == 0;
		}
	}

	static class InsertStatement {

		private final String sql;
		private final Object[] bindArgs;

		public InsertStatement(String sql, Object[] bindArgs) {
			this.sql = sql;
			this.bindArgs = bindArgs;
		}

		public String getSql() {
			return sql;
		}

		public Object[] getBindArgs() {
			return bindArgs;
		}
	}
}

