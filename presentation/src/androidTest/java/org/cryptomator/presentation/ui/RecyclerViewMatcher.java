package org.cryptomator.presentation.ui;

import android.content.res.Resources;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Created by dannyroa on 5/10/15.
 */
public class RecyclerViewMatcher {

	private final int recyclerViewId;

	public RecyclerViewMatcher(int recyclerViewId) {
		this.recyclerViewId = recyclerViewId;
	}

	public Matcher<View> atPositionOnView(final int position, final int targetViewId) {

		return new TypeSafeMatcher<View>() {
			Resources resources = null;
			View childView;

			public void describeTo(Description description) {
				String idDescription = Integer.toString(recyclerViewId);
				if (this.resources != null) {
					try {
						idDescription = this.resources.getResourceName(recyclerViewId);
					} catch (Resources.NotFoundException var4) {
						idDescription = String.format("%s (resource name not found)", recyclerViewId);
					}
				}

				description.appendText("with id: " + idDescription);
			}

			public boolean matchesSafely(View view) {

				this.resources = view.getResources();

				if (childView == null) {
					RecyclerView recyclerView = view.getRootView().findViewById(recyclerViewId);
					if (recyclerView != null && recyclerView.getId() == recyclerViewId) {
						childView = recyclerView.findViewHolderForAdapterPosition(position).itemView;
					} else {
						return false;
					}
				}

				if (targetViewId == -1) {
					return view == childView;
				} else {
					View targetView = childView.findViewById(targetViewId);
					return view == targetView;
				}

			}
		};
	}
}
