/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.ui.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import rs.ltt.android.R;
import rs.ltt.android.databinding.ItemThreadOverviewBinding;
import rs.ltt.android.databinding.ItemThreadOverviewLoadingBinding;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.ui.BindingAdapters;
import rs.ltt.android.util.Touch;

public class ThreadOverviewAdapter extends PagedListAdapter<ThreadOverviewItem, ThreadOverviewAdapter.AbstractThreadOverviewViewHolder> {


    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadOverviewAdapter.class);

    private boolean isLoading = false;
    private boolean initialLoadComplete = false;

    private static final int THREAD_ITEM_VIEW_TYPE = 0;
    private static final int LOADING_ITEM_VIEW_TYPE = 1;

    private OnFlaggedToggled onFlaggedToggled;
    private OnThreadClicked onThreadClicked;
    private SelectionTracker<String> selectionTracker;
    private Future<MailboxWithRoleAndName> importantMailbox; //TODO this needs to be a LiveData and needs to trigger a refresh when changed


    public ThreadOverviewAdapter() {
        super(new DiffUtil.ItemCallback<ThreadOverviewItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull ThreadOverviewItem oldItem, @NonNull ThreadOverviewItem newItem) {
                return oldItem.threadId.equals(newItem.threadId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ThreadOverviewItem oldItem, @NonNull ThreadOverviewItem newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    @NonNull
    @Override
    public AbstractThreadOverviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        if (viewType == THREAD_ITEM_VIEW_TYPE) {
            return new ThreadOverviewViewHolder(DataBindingUtil.inflate(layoutInflater, R.layout.item_thread_overview, parent, false));
        } else {
            return new ThreadOverviewLoadingViewHolder(DataBindingUtil.inflate(layoutInflater, R.layout.item_thread_overview_loading, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull AbstractThreadOverviewViewHolder holder, final int position) {
        if (holder instanceof ThreadOverviewLoadingViewHolder) {
            onBindViewHolder((ThreadOverviewLoadingViewHolder) holder);
        } else if (holder instanceof ThreadOverviewViewHolder) {
            onBindViewHolder((ThreadOverviewViewHolder) holder, position);
        }
    }

    private void onBindViewHolder(final ThreadOverviewViewHolder threadOverviewHolder, final int position) {
        final ThreadOverviewItem item = getItem(position);
        if (item == null) {
            return;
        }
        final boolean selected = selectionTracker != null && selectionTracker.isSelected(item.threadId);
        final Context context = threadOverviewHolder.binding.getRoot().getContext();
        threadOverviewHolder.binding.getRoot().setActivated(selected);
        threadOverviewHolder.setThread(item, position);
        threadOverviewHolder.binding.starToggle.setOnClickListener(v -> {
            if (onFlaggedToggled != null) {
                final boolean target = !item.showAsFlagged();
                BindingAdapters.setIsFlagged(threadOverviewHolder.binding.starToggle, target);
                onFlaggedToggled.onFlaggedToggled(item.threadId, target);
            }
        });
        Touch.expandTouchArea(threadOverviewHolder.binding.getRoot(), threadOverviewHolder.binding.starToggle, 16);
        threadOverviewHolder.binding.foreground.setOnClickListener(v -> {
            if (selectionTracker != null && selectionTracker.hasSelection()) {
                LOGGER.debug("Do not process click on thread because thread was selected");
                return;
            }
            if (onThreadClicked != null) {
                onThreadClicked.onThreadClicked(item, isImportant(item));
            }
        });
        threadOverviewHolder.binding.avatar.setOnClickListener(v -> {
            if (selectionTracker != null) {
                selectionTracker.select(item.threadId);
            }
        });
        if (selected) {
            threadOverviewHolder.binding.threadLayout.setBackground(ContextCompat.getDrawable(context, R.drawable.selected_background));
        } else {
            final TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            threadOverviewHolder.binding.threadLayout.setBackgroundResource(outValue.resourceId);
        }
    }

    private void onBindViewHolder(final ThreadOverviewLoadingViewHolder threadOverviewLoadingViewHolder) {
        threadOverviewLoadingViewHolder.binding.loading.setVisibility(isLoading() ? View.VISIBLE : View.GONE);
    }

    private MailboxWithRoleAndName getImportantMailbox() {
        if (this.importantMailbox != null && this.importantMailbox.isDone()) {
            try {
                return this.importantMailbox.get();
            } catch (Exception e) {
                return null;
            }
        } else {
            LOGGER.warn("Mailbox with IMPORTANT role was not available for rendering");
            return null;
        }
    }

    public boolean isImportant(ThreadOverviewItem item) {
        return item.isInMailbox(getImportantMailbox());
    }

    public void setLoading(final boolean loading) {
        final boolean before = isLoading();
        this.isLoading = loading;
        refreshLoadingIndicator(before);
    }

    private void refreshLoadingIndicator(final boolean before) {
        if (before != isLoading()) {
            notifyItemChanged(super.getItemCount());
        }
    }

    private boolean isLoading() {
        return this.isLoading || !initialLoadComplete;
    }

    @Override
    public void submitList(final PagedList<ThreadOverviewItem> pagedList) {
        submitList(pagedList, null);
    }

    @Override
    public void submitList(final PagedList<ThreadOverviewItem> pagedList, final Runnable runnable) {
        final boolean before = isLoading();
        this.initialLoadComplete = true;
        if (pagedList != null && pagedList.size() == 0) {
            refreshLoadingIndicator(before);
        }
        super.submitList(pagedList, runnable);
    }

    public void setImportantMailbox(Future<MailboxWithRoleAndName> importantMailbox) {
        this.importantMailbox = importantMailbox;
    }

    public void setTracker(SelectionTracker<String> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    @Override
    public ThreadOverviewItem getItem(int position) {
        return super.getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        return position < super.getItemCount() ? THREAD_ITEM_VIEW_TYPE : LOADING_ITEM_VIEW_TYPE;
    }

    public void setOnFlaggedToggledListener(OnFlaggedToggled listener) {
        this.onFlaggedToggled = listener;
    }

    public void setOnThreadClickedListener(OnThreadClicked listener) {
        this.onThreadClicked = listener;
    }

    @Override
    public int getItemCount() {
        return super.getItemCount() + 1;
    }

    public boolean isInitialLoad() {
        return !this.initialLoadComplete;
    }

    abstract static class AbstractThreadOverviewViewHolder extends RecyclerView.ViewHolder {

        AbstractThreadOverviewViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public class ThreadOverviewViewHolder extends AbstractThreadOverviewViewHolder {

        final public ItemThreadOverviewBinding binding;
        private int position;

        ThreadOverviewViewHolder(@NonNull ItemThreadOverviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void setThread(final ThreadOverviewItem thread, int position) {
            this.binding.setThread(thread);
            this.position = position;
            this.binding.setIsImportant(isImportant(thread));
        }

        public ItemDetailsLookup.ItemDetails<String> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<String>() {
                @Override
                public int getPosition() {
                    return position;
                }

                @Nullable
                @Override
                public String getSelectionKey() {
                    return binding.getThread().threadId;
                }
            };
        }
    }

    public static class ThreadOverviewLoadingViewHolder extends AbstractThreadOverviewViewHolder {

        final ItemThreadOverviewLoadingBinding binding;

        ThreadOverviewLoadingViewHolder(@NonNull ItemThreadOverviewLoadingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public interface OnThreadClicked {
        void onThreadClicked(ThreadOverviewItem threadOverviewItem, boolean important);
    }
}
