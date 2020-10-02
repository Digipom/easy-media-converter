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
package com.digipom.easymediaconverter.player.combine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.media.MediaItem;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.player.combine.CombineActionViewModel.DurationViewHolderHelper;
import com.digipom.easymediaconverter.player.combine.CombineActionViewModel.ListItem;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.digipom.easymediaconverter.utils.ListUtils.FadingItemTouchHelperCallback;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.Objects;

import static android.app.Activity.RESULT_OK;
import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;

public class CombineActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener, MainButtonInterfaces.HandleSecondaryFABTapListener {
    public interface OnCombineActionFragmentInteractionListener {
        void onCombineActionSelected(@NonNull MediaItem[] inputItems,
                                     @NonNull Uri targetUri,
                                     @NonNull String targetFileName);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;
    private static final int GET_CONTENT_REQUEST_CODE = 2;

    @NonNull
    public static CombineActionFragment create() {
        return new CombineActionFragment();
    }

    private CombineActionViewModel viewModel;
    private PlayerViewModel sharedViewModel;
    private ItemTouchHelper itemTouchHelper;
    private CombineAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_combine_action, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CombineActionViewModel.class);
        sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setFirstMediaItem(sharedViewModel.getMediaItem());

        final View view = Objects.requireNonNull(getView());
        final RecyclerView recyclerView = view.findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        adapter = new CombineAdapter();
        recyclerView.setAdapter(adapter);
        adapter.addItem(sharedViewModel.getMediaItem());

        itemTouchHelper = new ItemTouchHelper(new FadingItemTouchHelperCallback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return adapter.getMovementFlags(viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder targetViewHolder) {
                adapter.onMoved(viewHolder, targetViewHolder);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
                adapter.onSwiped(viewHolder);
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
        viewModel.showMergeFab().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                if (getActivity() != null) {
                    ((MainButtonInterfaces.MainButtonController) getActivity()).onButtonStateUpdated();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCUMENT_REQUEST_CODE) {
            final FragmentActivity activity = getActivity();
            IntentUtils.handleNewlyCreatedDocumentFromActivityResult(
                    Objects.requireNonNull(activity), resultCode, data, new IntentUtils.NewDocumentHandler() {
                        @Override
                        public void onReceivedUriForNewDocument(@NonNull Uri target) {
                            final String outputFilename = viewModel.getDefaultOutputFilename();
                            final MediaItem[] itemsToCombine = viewModel.getAllMediaItemsInList();
                            ((OnCombineActionFragmentInteractionListener) activity).onCombineActionSelected(itemsToCombine, target, outputFilename);
                        }
                    });
        } else if (requestCode == GET_CONTENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    final MediaItem[] items = MediaItem.constructMediaItemsFromGetContentResponse(
                            Objects.requireNonNull(getContext()), data);
                    for (MediaItem item : items) {
                        adapter.addItem(item);
                    }
                } catch (Exception e) {
                    Logger.w(e);
                    // TODO report to user and log
                }
            } else {
                // TODO log and distinguish between cancel and error
            }
        }
    }

    // HandleMainButtonTapListener

    @Override
    public boolean shouldShowMainButton() {
        return Objects.requireNonNull(viewModel.showMergeFab().getValue());
    }

    @Override
    public void onMainButtonTapped() {
        final String mimeType = viewModel.getMimeTypeForOutputSelection();
        final String outputFilename = viewModel.getDefaultOutputFilename();

        // We need a destination URI.
        final Intent createDocumentIntent = IntentUtils.getCreateDocumentRequest(mimeType, outputFilename);
        startActivityForResult(createDocumentIntent, CREATE_DOCUMENT_REQUEST_CODE);
    }

    // HandleSecondaryFABTapListener

    @Override
    public boolean shouldShowSecondaryFAB() {
        return true;
    }

    @Override
    public void onSecondaryFABTapped() {
        final Intent intent = IntentUtils.getMultipleMediaContentRequest();
        startActivityForResult(Intent.createChooser(intent, null),
                GET_CONTENT_REQUEST_CODE);
    }

    // Adapter

    public class CombineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_combine_action_listitem, parent, false);
            final View dragHandle = view.findViewById(R.id.drag_handle);
            final View discardButton = view.findViewById(R.id.discard_button);
            final AudioItemWithDurationViewHolder viewHolder = new AudioItemWithDurationViewHolder(view,
                    view.findViewById(R.id.card_interior_layout),
                    dragHandle,
                    discardButton,
                    (TextView) view.findViewById(R.id.name),
                    (TextView) view.findViewById(R.id.date),
                    (TextView) view.findViewById(R.id.size),
                    new DurationViewHolderHelper((TextView) view.findViewById(R.id.duration),
                            CombineActionFragment.this, viewModel.getLoadableDurationCache(),
                            new DurationViewHolderHelper.OnDurationLoadedListener() {
                                @Override
                                public void onDurationLoadedForItemAndViewHolderHasChanged() {
                                    adapter.notifyDataSetChanged();
                                }
                            }));
            dragHandle.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(viewHolder);
                    }
                    return false;
                }
            });
            discardButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeItem(viewHolder.getAdapterPosition());
                }
            });
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
            onBindRegularViewHolder((AudioItemWithDurationViewHolder) holder, position);
        }

        private boolean canDrag() {
            return viewModel.getItemCount() > 1;
        }

        private boolean canSwipe(int position) {
            return !(viewModel.getItem(position).mediaItem.equals(sharedViewModel.getMediaItem()));
        }

        private void onBindRegularViewHolder(@NonNull final AudioItemWithDurationViewHolder holder, int position) {
            final ListItem listItem = viewModel.getItem(position);
            holder.bindWithMediaListItem(Objects.requireNonNull(getContext()),
                    listItem, canDrag(), canSwipe(position),
                    viewModel.getItemCount() > 1);
        }

        @Override
        public int getItemCount() {
            return viewModel.getItemCount();
        }

        int getMovementFlags(@NonNull RecyclerView.ViewHolder viewHolder) {
            final boolean canDrag = canDrag();
            final boolean canSwipe = canSwipe(viewHolder.getAdapterPosition());
            final int dragFlags = canDrag ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
            final int swipeFlags = canSwipe ? ItemTouchHelper.START | ItemTouchHelper.END : 0;
            return makeMovementFlags(dragFlags, swipeFlags);
        }

        void onMoved(@NonNull RecyclerView.ViewHolder viewHolder,
                     @NonNull RecyclerView.ViewHolder target) {
            int fromPosition = viewHolder.getAdapterPosition();
            int toPosition = target.getAdapterPosition();
            viewModel.onItemMoved(fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            removeItem(position);
        }

        private void removeItem(int position) {
            viewModel.onItemRemoved(position);
            notifyItemRemoved(position);
            // If we only have one item left, also remove the drag handle
            if (viewModel.getItemCount() == 1) {
                notifyItemChanged(0);
            }
        }

        void addItem(@NonNull MediaItem mediaItem) {
            int position = viewModel.onItemAdded(mediaItem);
            if (position >= 0) {
                notifyItemInserted(position);
            }
            if (viewModel.getItemCount() == 2) {
                // Add a drag handle
                notifyItemChanged(0);
            }
        }
    }

    private static class AudioItemWithDurationViewHolder extends RecyclerView.ViewHolder {
        private final View cardInteriorLayout;
        private final View dragHandle;
        private final View discardView;
        private final TextView nameView;
        private final TextView dateView;
        private final TextView sizeView;
        private final DurationViewHolderHelper helper;

        AudioItemWithDurationViewHolder(@NonNull View itemView,
                                        @NonNull View cardInteriorLayout,
                                        @NonNull View dragHandle, @NonNull View discardView,
                                        @NonNull TextView nameView, @NonNull TextView dateView,
                                        @NonNull TextView sizeView,
                                        @NonNull DurationViewHolderHelper helper) {
            super(itemView);
            this.cardInteriorLayout = cardInteriorLayout;
            this.dragHandle = dragHandle;
            this.discardView = discardView;
            this.nameView = nameView;
            this.dateView = dateView;
            this.sizeView = sizeView;
            this.helper = helper;
        }

        void bindWithMediaListItem(@NonNull Context context,
                                   @NonNull final ListItem listItem,
                                   boolean canDragAnyItem,
                                   boolean canRemoveThisItemWithDiscardButton,
                                   boolean canRemoveAnyItemWithDiscardButton) {
            if (canDragAnyItem) {
                dragHandle.setVisibility(View.VISIBLE);
            } else {
                dragHandle.setVisibility(View.GONE);
            }
            if (canRemoveThisItemWithDiscardButton) {
                discardView.setVisibility(View.VISIBLE);
            } else {
                if (canRemoveAnyItemWithDiscardButton) {
                    discardView.setVisibility(View.INVISIBLE);
                } else {
                    discardView.setVisibility(View.GONE);
                }
            }
            updateMargins(context);
            nameView.setText(listItem.mediaItem.getDisplayName());
            helper.bindWithMediaItem(listItem.mediaItem);
            if (listItem.mediaItem.getOptionalLastModifiedDate() > 0) {
                dateView.setText(DateUtils.formatDateTime(context, listItem.mediaItem.getOptionalLastModifiedDate(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            } else {
                dateView.setText("");
            }
            sizeView.setText(android.text.format.Formatter.formatFileSize(context, listItem.mediaItem.getSize()));
        }

        private void updateMargins(@NonNull Context context) {
            boolean useStartMargin = dragHandle.getVisibility() == View.GONE;
            boolean useEndMargin = discardView.getVisibility() == View.GONE;
            int marginSize = context.getResources().getDimensionPixelSize(R.dimen.text_margin);
            final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) cardInteriorLayout.getLayoutParams();
            params.setMarginStart(useStartMargin ? marginSize : 0);
            params.setMarginEnd(useEndMargin ? marginSize : 0);
            cardInteriorLayout.setLayoutParams(params);
        }
    }
}
