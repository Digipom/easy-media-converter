/*
 * Copyright (c) 2020 Kevin Brothaler. All rights reserved.
 *
 * https://github.com/Digipom/easy-media-converter
 *
 * This file is part of Easy Media Converter.
 *
 * Easy Media Converter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Easy Media Converter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Easy Media Converter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.digipom.easymediaconverter.main.recents;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.application.BaseApplication;
import com.digipom.easymediaconverter.edit.EditAction;
import com.digipom.easymediaconverter.errors.ErrorDialogFragment;
import com.digipom.easymediaconverter.ffmpeg.FFMpegController;
import com.digipom.easymediaconverter.main.MainViewModel;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.CancellableRequestListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.CompletedRequestListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.FailedRequestListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.ListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.PreviousHeaderListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.RecentHeaderListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.RecentlyOpenedListItem;
import com.digipom.easymediaconverter.main.recents.RecentsListViewModel.RequestListItem;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.utils.ExecutorUtils;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.digipom.easymediaconverter.utils.ListUtils.FadingItemTouchHelperCallback;
import com.digipom.easymediaconverter.utils.UriUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

import static com.digipom.easymediaconverter.utils.AlertDialogUtils.createWithCondensedFontUsingOnShowListener;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link RecentsListFragmentInteractionListener}
 * interface.
 */
public class RecentsListFragment extends Fragment {
    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface RecentsListFragmentInteractionListener {
        void onRecentlyOpenedItemTapped(@NonNull MediaItem recentsItem);

        void onCompletedItemTapped(@NonNull MediaItem[] targets);
    }

    private RecyclerView recyclerView;
    private View emptyView;
    private RecentsItemRecyclerViewAdapter adapter;
    private RecentsListViewModel viewModel;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RecentsListFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        final View layout = inflater.inflate(R.layout.fragment_recents, container, false);
        recyclerView = layout.findViewById(R.id.list);
        emptyView = layout.findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        adapter = new RecentsItemRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // Don't want the flashing animation when the same item updates.
                return true;
            }
        });
        recyclerView.setHasFixedSize(true);
        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new FadingItemTouchHelperCallback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                boolean isSwipeable = adapter.isRemovableItem(viewHolder.getAdapterPosition());
                return makeMovementFlags(0, isSwipeable ? ItemTouchHelper.START | ItemTouchHelper.END : 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder targetViewHolder) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
                adapter.onUserSwipedAwayItem(viewHolder.getAdapterPosition());
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
        recyclerView.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (recyclerView.getAlpha() == 0) {
                    recyclerView.setVisibility(View.GONE);
                }
            }
        });
        emptyView.animate().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (emptyView.getAlpha() == 0) {
                    emptyView.setVisibility(View.GONE);
                }
            }
        });
        return layout;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RecentsListViewModel.class);
        final MainViewModel sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(MainViewModel.class);
        viewModel.setRecentlyOpenedRepository(sharedViewModel.getRecentlyOpenedRepository());
        viewModel.list().observe(getViewLifecycleOwner(), new Observer<List<ListItem>>() {
            @Override
            public void onChanged(@Nullable List<ListItem> items) {
                if (Objects.requireNonNull(items).isEmpty()) {
                    recyclerView.animate().alpha(0);
                    emptyView.setVisibility(View.VISIBLE);
                    emptyView.animate().alpha(1);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    recyclerView.animate().alpha(1);
                    emptyView.animate().alpha(0);
                }
                adapter.submitList(items);
            }
        });
        viewModel.progressForCurrentItem().observe(getViewLifecycleOwner(), new Observer<Float>() {
            @Override
            public void onChanged(Float possibleProgress) {
                adapter.onProgressChanged();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshList();
    }

    class RecentsItemRecyclerViewAdapter extends ListAdapter<ListItem, RecyclerView.ViewHolder> {
        private static final int RECENT_HEADER_LIST_ITEM_TYPE = 1;
        private static final int PREVIOUS_HEADER_LIST_ITEM_TYPE = 2;

        private static final int ONGOING_LIST_ITEM_TYPE = 3;
        private static final int QUEUED_LIST_ITEM_TYPE = 4;

        private static final int RECENTLY_OPENED_LIST_ITEM_TYPE = 5;
        private static final int COMPLETED_LIST_ITEM_TYPE = 6;
        private static final int FAILED_LIST_ITEM_TYPE = 7;

        private static final int FOOTER_LIST_ITEM_TYPE = 8;

        RecentsItemRecyclerViewAdapter() {
            super(new DiffUtil.ItemCallback<ListItem>() {
                // Durations aren't mentioned here, but they're already being animated if they update
                // on the screen.
                @Override
                public boolean areItemsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
                    if (oldItem instanceof RecentlyOpenedListItem && newItem instanceof RecentlyOpenedListItem) {
                        // The URI uniquely identifies a recently opened item.
                        return ((RecentlyOpenedListItem) oldItem).recentItem.mediaItem.getUri().equals(((RecentlyOpenedListItem) newItem).recentItem.mediaItem.getUri());
                    } else if (oldItem instanceof RequestListItem && newItem instanceof RequestListItem) {
                        // Use the unique request ID to track requests.
                        return ((RequestListItem) oldItem).id == ((RequestListItem) newItem).id;
                    } else {
                        // For any of the other list types, just compare if it's the same class type.
                        return oldItem.getClass().equals(newItem.getClass());
                    }
                }

                @SuppressLint("DiffUtilEquals")
                @Override
                public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
                    if ((oldItem instanceof RecentlyOpenedListItem && newItem instanceof RecentlyOpenedListItem)
                            || (oldItem instanceof CompletedRequestListItem && newItem instanceof CompletedRequestListItem)
                            || (oldItem instanceof FailedRequestListItem && newItem instanceof FailedRequestListItem)) {
                        // Contents are never the same because the timestamp would have updated.
                        return false;
                    } else if (oldItem instanceof CancellableRequestListItem && newItem instanceof CancellableRequestListItem) {
                        return ((CancellableRequestListItem) oldItem).request.id == ((CancellableRequestListItem) newItem).request.id;
                    } else {
                        // For any of the other list types, just compare if it's the same class type.
                        return oldItem.getClass().equals(newItem.getClass());
                    }
                }
            });
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == RECENT_HEADER_LIST_ITEM_TYPE
                    || viewType == PREVIOUS_HEADER_LIST_ITEM_TYPE) {
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_header_listitem, parent, false);
                return new HeaderViewHolder(view,
                        (TextView) view.findViewById(R.id.header_textview),
                        view.findViewById(R.id.clear_imageview));
            } else if (viewType == ONGOING_LIST_ITEM_TYPE
                    || viewType == QUEUED_LIST_ITEM_TYPE) {
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_ongoing_listitem, parent, false);
                final ProgressBar progressBar = view.findViewById(R.id.progress_bar);
                progressBar.setMax(10000);
                return new CancellableItemViewHolder(view,
                        (TextView) view.findViewById(R.id.header_textview),
                        (TextView) view.findViewById(R.id.inputs_textview),
                        progressBar,
                        (TextView) view.findViewById(R.id.time_remaining));
            } else if (viewType == RECENTLY_OPENED_LIST_ITEM_TYPE) {
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_recently_opened_listitem, parent, false);
                return new RecentlyOpenedViewHolder(view,
                        (TextView) view.findViewById(R.id.header_textview),
                        (TextView) view.findViewById(R.id.name));
            } else if (viewType == COMPLETED_LIST_ITEM_TYPE) {
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_completed_listitem, parent, false);
                return new CompletedItemViewHolder(view,
                        (TextView) view.findViewById(R.id.header_textview),
                        (TextView) view.findViewById(R.id.outputs_textview),
                        view.findViewById(R.id.share),
                        view.findViewById(R.id.delete));
            } else if (viewType == FAILED_LIST_ITEM_TYPE) {
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_failed_listitem, parent, false);
                return new FailedItemViewHolder(view,
                        (TextView) view.findViewById(R.id.header_textview),
                        (TextView) view.findViewById(R.id.inputs_textview),
                        (TextView) view.findViewById(R.id.short_failure_message),
                        view.findViewById(R.id.more_button));
            } else {
                // Footer
                final View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_recents_list_footer, parent, false);
                return new FooterItemViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
            if (getContext() == null) {
                return;
            }

            final int itemType = getItemViewType(position);
            if (itemType == RECENT_HEADER_LIST_ITEM_TYPE) {
                ((HeaderViewHolder) holder).headerView.setText(getString(R.string.recent));
                ((HeaderViewHolder) holder).clearButton.setVisibility(View.GONE);
            } else if (itemType == PREVIOUS_HEADER_LIST_ITEM_TYPE) {
                ((HeaderViewHolder) holder).headerView.setText(getString(R.string.previous));
                ((HeaderViewHolder) holder).clearButton.setVisibility(View.VISIBLE);
                ((HeaderViewHolder) holder).clearButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewModel.onClearPreviousTapped();
                    }
                });
            } else if (itemType == ONGOING_LIST_ITEM_TYPE || itemType == QUEUED_LIST_ITEM_TYPE) {
                final CancellableRequestListItem item = (CancellableRequestListItem) getItem(position);
                final CancellableItemViewHolder viewHolder = (CancellableItemViewHolder) holder;
                final int taskType = item.request.editAction.descriptionForOngoingTaskType();
                viewHolder.header.setText(getString(taskType));
                viewHolder.header.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawableForOngoingTaskType(item.request.editAction), null, null, null);
                viewHolder.inputs.setText(descriptionForMediaItems(item.request.sources));
                if (itemType == ONGOING_LIST_ITEM_TYPE) {
                    viewHolder.progressBar.setVisibility(View.VISIBLE);
                    final Float possibleProgress = item.request.progress().getValue();
                    if (possibleProgress == null) {
                        viewHolder.progressBar.setIndeterminate(true);
                        viewHolder.timeRemaining.setVisibility(View.INVISIBLE);
                    } else {
                        final long now = System.currentTimeMillis();
                        final long end = now + item.request.estimatedTimeRemainingMs();

                        if (end - now > 0) {
                            viewHolder.progressBar.setIndeterminate(false);
                            viewHolder.progressBar.setProgress((int) (viewHolder.progressBar.getMax() * possibleProgress));
                            viewHolder.timeRemaining.setVisibility(View.VISIBLE);
                            CharSequence timeSpanString = DateUtils.getRelativeTimeSpanString(end, now, 0);
                            viewHolder.timeRemaining.setText(timeSpanString);
                        } else {
                            viewHolder.progressBar.setIndeterminate(true);
                            viewHolder.timeRemaining.setVisibility(View.INVISIBLE);
                        }
                    }
                } else {
                    viewHolder.progressBar.setVisibility(View.GONE);
                    viewHolder.timeRemaining.setVisibility(View.GONE);
                }
            } else if (itemType == RECENTLY_OPENED_LIST_ITEM_TYPE) {
                final RecentlyOpenedListItem item = (RecentlyOpenedListItem) getItem(position);
                final RecentlyOpenedViewHolder viewHolder = (RecentlyOpenedViewHolder) holder;
                viewHolder.header.setText(getString(R.string.opened_relative_time, DateUtils.getRelativeTimeSpanString(item.recentItem.getLastAccessTimestamp(), System.currentTimeMillis(), 0)));
                viewHolder.header.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_music_note_black_24dp), null, null, null);
                viewHolder.name.setText(item.recentItem.mediaItem.getDisplayName());
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Notify the active callbacks interface (the activity, if the
                        // fragment is attached to one) that an item has been selected.
                        final RecentsListFragmentInteractionListener listener = (RecentsListFragmentInteractionListener) getActivity();
                        if (listener != null && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            listener.onRecentlyOpenedItemTapped(item.recentItem.mediaItem);
                        }
                    }
                });
            } else if (itemType == COMPLETED_LIST_ITEM_TYPE) {
                final CompletedRequestListItem item = (CompletedRequestListItem) getItem(position);
                final CompletedItemViewHolder viewHolder = (CompletedItemViewHolder) holder;
                final int taskType = item.request.editAction.descriptionForCompletedTaskType();
                viewHolder.header.setText(getString(taskType));
                viewHolder.header.setText(getString(R.string.action_relative_time, getString(taskType), DateUtils.getRelativeTimeSpanString(item.request.timestamp, System.currentTimeMillis(), 0)));
                viewHolder.header.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawableForOngoingTaskType(item.request.editAction), null, null, null);
                viewHolder.outputs.setText(descriptionForMediaItems(item.request.targets));

                viewHolder.shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            final Intent sendIntent = IntentUtils.getShareItemIntent(item.request.targets);

                            try {
                                startActivity(Intent.createChooser(sendIntent,
                                        getResources().getText(R.string.share)));
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(getContext(), R.string.noShareApp, Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
                viewHolder.deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            ConfirmDeleteDialogFragment.show(getParentFragmentManager(),
                                    item.request.targets,
                                    item.request.id);
                        }
                    }
                });
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final RecentsListFragmentInteractionListener listener = (RecentsListFragmentInteractionListener) getActivity();
                        if (listener != null && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            listener.onCompletedItemTapped(item.request.targets);
                        }
                    }
                });
            } else if (itemType == FAILED_LIST_ITEM_TYPE) {
                final FailedRequestListItem item = (FailedRequestListItem) getItem(position);
                final FailedItemViewHolder viewHolder = (FailedItemViewHolder) holder;
                final int taskType = item.request.editAction.descriptionForOngoingTaskType();
                viewHolder.header.setText(getString(R.string.error_doing_action, getString(taskType).toLowerCase(Locale.getDefault())));
                viewHolder.header.setCompoundDrawablesRelativeWithIntrinsicBounds(getTintedDrawable(R.drawable.ic_warning_black_24dp), null, null, null);
                viewHolder.inputs.setText(descriptionForMediaItems(item.request.sources));
                viewHolder.shortMessage.setText(item.request.getShortMessage());
                viewHolder.moreButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getActivity() != null && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            ErrorDialogFragment.showDialog(Objects.requireNonNull(getParentFragmentManager()),
                                    item.request.getFullMessage());
                        }
                    }
                });
            }
        }

        @NonNull
        private String descriptionForMediaItems(@NonNull MediaItem[] sourceItems) {
            if (sourceItems.length == 1) {
                return sourceItems[0].getDisplayName();
            } else {
                final String[] displayNames = new String[sourceItems.length];
                for (int i = 0; i < sourceItems.length; ++i) {
                    displayNames[i] = sourceItems[i].getDisplayName();
                }
                return TextUtils.join(", ", displayNames);
            }
        }

        private Drawable getTintedDrawableForOngoingTaskType(@NonNull EditAction action) {
            final Drawable drawable = Objects.requireNonNull(
                    ContextCompat.getDrawable(requireContext(),
                            drawableForOngoingTaskType(action))).mutate();
            DrawableCompat.setTint(drawable, Color.WHITE);
            return drawable;
        }

        private Drawable getTintedDrawable(@DrawableRes int drawableResId) {
            final Drawable drawable = Objects.requireNonNull(
                    ContextCompat.getDrawable(requireContext(),
                            drawableResId)).mutate();
            DrawableCompat.setTint(drawable, Color.WHITE);
            return drawable;
        }

        @DrawableRes
        private int drawableForOngoingTaskType(@NonNull EditAction action) {
            switch (action) {
                case CONVERT:
                    return R.drawable.ic_convert_black_24dp;
                case CONVERT_TO_VIDEO:
                    return R.drawable.ic_music_video_black_24dp;
                case EXTRACT_AUDIO:
                    return R.drawable.ic_music_note_black_24dp;
                case TRIM:
                    return R.drawable.ic_trim_black_24dp;
                case CUT:
                    return R.drawable.ic_cut_black_24dp;
                case ADJUST_SPEED:
                    return R.drawable.ic_adjust_speed_24dp;
                case ADJUST_VOLUME:
                    return R.drawable.ic_adjust_volume_black_24dp;
                case ADD_SILENCE:
                    return R.drawable.ic_add_silence_black_24dp;
                case NORMALIZE:
                    return R.drawable.ic_normalize_24dp;
                case SPLIT:
                    return R.drawable.ic_split_black_24dp;
                case COMBINE:
                    return R.drawable.ic_combine_black_24dp;
                case SET_AS_RINGTONE:
                    return R.drawable.ic_set_as_ringtone_black_24dp;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public int getItemViewType(int position) {
            final ListItem item = getItem(position);
            if (item instanceof RecentHeaderListItem) {
                return RECENT_HEADER_LIST_ITEM_TYPE;
            } else if (item instanceof PreviousHeaderListItem) {
                return PREVIOUS_HEADER_LIST_ITEM_TYPE;
            } else if (item instanceof CancellableRequestListItem) {
                if (((CancellableRequestListItem) item).isCurrent) {
                    return ONGOING_LIST_ITEM_TYPE;
                } else {
                    return QUEUED_LIST_ITEM_TYPE;
                }
            } else if (item instanceof RecentlyOpenedListItem) {
                return RECENTLY_OPENED_LIST_ITEM_TYPE;
            } else if (item instanceof CompletedRequestListItem) {
                return COMPLETED_LIST_ITEM_TYPE;
            } else if (item instanceof FailedRequestListItem) {
                return FAILED_LIST_ITEM_TYPE;
            } else {
                return FOOTER_LIST_ITEM_TYPE;
            }
        }

        void onProgressChanged() {
            for (int i = 0; i < getItemCount(); ++i) {
                if (getItemViewType(i) == ONGOING_LIST_ITEM_TYPE) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }

        void onUserSwipedAwayItem(int position) {
            viewModel.onUserSwipedAwayItem(position);
        }

        boolean isRemovableItem(int position) {
            final int itemViewType = getItemViewType(position);
            return itemViewType == ONGOING_LIST_ITEM_TYPE
                    || itemViewType == QUEUED_LIST_ITEM_TYPE
                    || itemViewType == RECENTLY_OPENED_LIST_ITEM_TYPE
                    || itemViewType == COMPLETED_LIST_ITEM_TYPE
                    || itemViewType == FAILED_LIST_ITEM_TYPE;
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView headerView;
            final View clearButton;

            HeaderViewHolder(@NonNull View itemView,
                             @NonNull TextView headerView,
                             @NonNull View clearButton) {
                super(itemView);
                this.headerView = headerView;
                this.clearButton = clearButton;
            }
        }

        class CancellableItemViewHolder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView inputs;
            final ProgressBar progressBar;
            final TextView timeRemaining;

            CancellableItemViewHolder(@NonNull View itemView,
                                      @NonNull TextView header,
                                      @NonNull TextView inputs,
                                      @NonNull ProgressBar progressBar,
                                      @NonNull TextView timeRemaining) {
                super(itemView);
                this.header = header;
                this.inputs = inputs;
                this.progressBar = progressBar;
                this.timeRemaining = timeRemaining;
            }
        }

        class CompletedItemViewHolder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView outputs;
            final View shareButton;
            final View deleteButton;

            CompletedItemViewHolder(@NonNull View itemView,
                                    @NonNull TextView header,
                                    @NonNull TextView outputs,
                                    @NonNull View shareButton,
                                    @NonNull View deleteButton) {
                super(itemView);
                this.header = header;
                this.outputs = outputs;
                this.shareButton = shareButton;
                this.deleteButton = deleteButton;
            }
        }

        private class RecentlyOpenedViewHolder extends RecyclerView.ViewHolder {
            private final TextView header;
            private final TextView name;

            RecentlyOpenedViewHolder(@NonNull View itemView,
                                     @NonNull TextView header,
                                     @NonNull TextView name) {
                super(itemView);
                this.header = header;
                this.name = name;
            }
        }

        class FailedItemViewHolder extends RecyclerView.ViewHolder {
            final TextView header;
            final TextView inputs;
            final TextView shortMessage;
            final View moreButton;

            FailedItemViewHolder(@NonNull View itemView,
                                 @NonNull TextView header,
                                 @NonNull TextView inputs,
                                 @NonNull TextView shortMessage,
                                 @NonNull View moreButton) {
                super(itemView);
                this.header = header;
                this.inputs = inputs;
                this.shortMessage = shortMessage;
                this.moreButton = moreButton;
            }
        }

        // Only a marker class
        class FooterItemViewHolder extends RecyclerView.ViewHolder {
            FooterItemViewHolder(View itemView) {
                super(itemView);
            }
        }
    }

    public static class ConfirmDeleteDialogFragment extends DialogFragment {
        private static final String TAG = ConfirmDeleteDialogFragment.class.getName();
        private static final String EXTRA_MEDIA_ITEMS = "EXTRA_MEDIA_ITEMS";
        private static final String EXTRA_ASSOCIATED_REQUEST_ID = "EXTRA_ASSOCIATED_REQUEST_ID";

        private MainViewModel mainViewModel;

        public static void show(@NonNull FragmentManager fragmentManager,
                                @NonNull MediaItem[] items,
                                int requestId) {
            final ConfirmDeleteDialogFragment fragment = new ConfirmDeleteDialogFragment();
            final Bundle args = new Bundle();
            args.putParcelableArray(EXTRA_MEDIA_ITEMS, items);
            args.putInt(EXTRA_ASSOCIATED_REQUEST_ID, requestId);
            fragment.setArguments(args);
            fragment.show(fragmentManager, TAG);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            final FFMpegController ffMpegController = ((BaseApplication) requireContext().getApplicationContext()).getServiceLocator().getFFMpegController();
            mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
            final Bundle args = Objects.requireNonNull(getArguments());
            final MediaItem[] items = (MediaItem[]) Objects.requireNonNull(args.getParcelableArray(EXTRA_MEDIA_ITEMS));
            final int requestId = args.getInt(EXTRA_ASSOCIATED_REQUEST_ID);
            final String message = getString(R.string.deleteConfirmation,
                    getResources().getQuantityString(R.plurals.items, items.length,
                            items[0].getDisplayName(), items.length));

            final AlertDialog.Builder builder = new AlertDialog.Builder(
                    new ContextThemeWrapper(
                            Objects.requireNonNull(getActivity()), R.style.AppTheme_MaterialAlertDialog));
            builder.setMessage(message);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                        final Executor backgroundExecutor = ExecutorUtils.newSingleThreadExecutorWithTimeout();
                        ffMpegController.removeCompletedRequest(requestId);
                        for (MediaItem item : items) {
                            mainViewModel.getRecentlyOpenedRepository().removeMatchingRecents(item.getUri());
                        }

                        // Should probably be in a viewmodel
                        final Context context = requireContext().getApplicationContext();
                        for (final MediaItem item : items) {
                            backgroundExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    Logger.d("User requested to delete " + item.getUri() + " with name " + item.getDisplayName());
                                    UriUtils.deleteResource(context, item.getUri());
                                }
                            });
                        }
                    }
                }
            });
            return createWithCondensedFontUsingOnShowListener(builder);
        }
    }
}
