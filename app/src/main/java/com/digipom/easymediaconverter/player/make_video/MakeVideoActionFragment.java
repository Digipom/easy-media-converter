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
package com.digipom.easymediaconverter.player.make_video;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.digipom.easymediaconverter.R;
import com.digipom.easymediaconverter.player.MainButtonInterfaces;
import com.digipom.easymediaconverter.player.PlayerViewModel;
import com.digipom.easymediaconverter.utils.IntentUtils;
import com.digipom.easymediaconverter.utils.ObjectUtils;
import com.digipom.easymediaconverter.utils.logger.Logger;

import java.util.Objects;

import static android.app.Activity.RESULT_OK;

public class MakeVideoActionFragment extends Fragment implements MainButtonInterfaces.HandleMainButtonTapListener,
        MainButtonInterfaces.HandleSecondaryFABTapListener {
    public interface OnMakeVideoActionFragmentInteractionListener {
        void onMakeVideoSelected(@NonNull Uri targetUri,
                                 @NonNull String targetFileName,
                                 @Nullable Uri optionalImageCoverUri,
                                 @Nullable String optionalImageCoverFileName);
    }

    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1;
    private static final int GET_CONTENT_REQUEST_CODE = 2;

    @NonNull
    public static MakeVideoActionFragment create() {
        return new MakeVideoActionFragment();
    }

    private MakeVideoActionViewModel viewModel;
    private ImageView coverImage;
    private ImageButton resetCoverImageToDefault;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_make_video_action, container, false);
        coverImage = view.findViewById(R.id.cover_image);
        resetCoverImageToDefault = view.findViewById(R.id.reset_cover_image_to_default);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MakeVideoActionViewModel.class);
        final PlayerViewModel sharedViewModel = new ViewModelProvider(Objects.requireNonNull(getActivity())).get(PlayerViewModel.class);
        viewModel.setMediaItem(sharedViewModel.getMediaItem());

        viewModel.coverImage().observe(getViewLifecycleOwner(), new Observer<Drawable>() {
            @Override
            public void onChanged(Drawable drawable) {
                coverImage.setImageDrawable(drawable);
            }
        });
        viewModel.isCoverImageDefault().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isCoverImageDefault) {
                if (!ObjectUtils.returnDefaultIfNull(isCoverImageDefault, false)) {
                    resetCoverImageToDefault.setVisibility(View.VISIBLE);
                } else {
                    resetCoverImageToDefault.setVisibility(View.INVISIBLE);
                }
            }
        });

        resetCoverImageToDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    viewModel.setCoverImageToDefault();
                }
            }
        });
    }

    @Override
    public boolean shouldShowMainButton() {
        return true;
    }

    @Override
    public void onMainButtonTapped() {
        final String mimeType = viewModel.getMimeTypeForOutputSelection();
        final String outputFilename = viewModel.getDefaultOutputFilename();

        // We need a destination URI.
        final Intent createDocumentIntent = IntentUtils.getCreateDocumentRequest(mimeType, outputFilename);
        startActivityForResult(createDocumentIntent, CREATE_DOCUMENT_REQUEST_CODE);
    }

    @Override
    public boolean shouldShowSecondaryFAB() {
        return true;
    }

    @Override
    public void onSecondaryFABTapped() {
        final Intent intent = IntentUtils.getSingleImageContentRequest();
        startActivityForResult(Intent.createChooser(intent, null),
                GET_CONTENT_REQUEST_CODE);
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
                            final Uri customCoverImageUri = viewModel.getCustomCoverImageUri();
                            final String customCoverImageFilename = viewModel.getCustomCoverImageFilename();
                            ((OnMakeVideoActionFragmentInteractionListener) activity).onMakeVideoSelected(
                                    target, outputFilename, customCoverImageUri, customCoverImageFilename);
                        }
                    });
        } else if (requestCode == GET_CONTENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                try {
                    if (data != null && data.getData() != null) {
                        final Uri imageUri = data.getData();
                        viewModel.setCoverImage(imageUri);
                    } else {
                        // TODO report to user and log
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
}
