package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.util.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

public interface DataSource extends Serializable, Closeable {

	Optional<Long> size(Context context);

	Optional<Date> modifiedDate(Context context);

	InputStream open(Context context) throws IOException;

	DataSource decorate(DataSource delegate);

}
