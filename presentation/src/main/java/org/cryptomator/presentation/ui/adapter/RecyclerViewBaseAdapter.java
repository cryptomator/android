package org.cryptomator.presentation.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class RecyclerViewBaseAdapter<Item, Callback, ViewHolder extends RecyclerViewBaseAdapter.ItemViewHolder, Binding extends ViewBinding> extends RecyclerView.Adapter<ViewHolder> {

	final List<Item> itemCollection;

	protected Callback callback;

	private Comparator<Item> comparator;

	protected RecyclerViewBaseAdapter() {
		this.itemCollection = new ArrayList<>();
	}

	protected RecyclerViewBaseAdapter(Comparator<Item> comparator) {
		this.itemCollection = new ArrayList<>();
		this.comparator = comparator;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		Binding binding = getItemBinding(LayoutInflater.from(parent.getContext()), parent, viewType);
		return createViewHolder(binding, viewType);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		holder.bind(position);
	}

	@Override
	public int getItemCount() {
		if (itemCollection == null) {
			return 0;
		} else {
			return itemCollection.size();
		}
	}

	public boolean isEmpty() {
		return (itemCollection == null || itemCollection.size() == 0);
	}

	public void clear() {
		itemCollection.clear();
		notifyDataSetChanged();
	}

	public void addAll(Collection<? extends Item> items) {
		itemCollection.addAll(items);
		sort();
		notifyDataSetChanged();
	}

	public List<Item> getAll() {
		return itemCollection;
	}

	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	public int positionOf(Item item) {
		return itemCollection.indexOf(item);
	}

	public void deleteItems(List<? extends Item> items) {
		for (Item item : items) {
			deleteItem(item);
		}
	}

	public void deleteItem(Item item) {
		int positionOf = positionOf(item);
		itemCollection.remove(item);
		notifyItemRemoved(positionOf);
	}

	void addItem(Item item) {
		itemCollection.add(item);
		sort();
		notifyItemInserted(positionOf(item));
	}

	void replaceItem(Item item) {
		replaceItem(positionOf(item), item);
	}

	void replaceItem(int position, Item item) {
		itemCollection.set(position, item);
		notifyItemChanged(position);
	}

	boolean contains(Item item) {
		return itemCollection.contains(item);
	}

	public Item getItem(int position) {
		return itemCollection.get(position);
	}

	protected abstract Binding getItemBinding(LayoutInflater inflater, ViewGroup parent, int viewType);

	protected abstract ViewHolder createViewHolder(Binding binding, int viewType);

	private void sort() {
		if (comparator != null) {
			Collections.sort(itemCollection, comparator);
		}
	}

	void updateComparator(Comparator<Item> comparator) {
		this.comparator = comparator;
	}

	Comparator<Item> getComparator() {
		return comparator;
	}

	public abstract class ItemViewHolder extends RecyclerView.ViewHolder {

		protected ItemViewHolder(View itemView) {
			super(itemView);
		}

		public abstract void bind(int position);
	}
}
